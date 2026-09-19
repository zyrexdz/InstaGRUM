package com.instagrum.local.data

import android.content.Context
import com.instagrum.local.model.*
import com.instagrum.local.notifications.LocalNotifications
import com.instagrum.local.simulation.SimulationEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** Foreground and WorkManager share one process-wide writer. No background service or network. */
class SimulatorRuntime private constructor(context: Context) {
    companion object {
        @Volatile
        private var instance: SimulatorRuntime? = null
        fun get(context: Context): SimulatorRuntime = instance ?: synchronized(this) {
            instance ?: SimulatorRuntime(context.applicationContext).also { instance = it }
        }

        internal fun releaseForTests() {
            instance?.scope?.cancel(); instance = null
        }
    }

    private val appContext = context.applicationContext
    private val repository = AppRepository(context)
    private val notifications = LocalNotifications(context)
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val actions = Channel<Action>(Channel.UNLIMITED)
    private val mutableState = MutableStateFlow<AppState?>(null)
    val state = mutableState.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()

    @Volatile
    var foreground = false
        private set

    @Volatile
    private var wantsForeground = false
    private var loaded = false
    private var lastForegroundTick = 0L
    private var lastCheckpoint = 0L

    init {
        scope.launch { for (action in actions) dispatch(action) }
    }

    fun enqueue(action: Action) {
        actions.trySend(action)
    }

    suspend fun load() = mutex.withLock {
        if (loaded) return@withLock
        try {
            val now = System.currentTimeMillis()
            val saved = repository.load()
            val value = InitialState.migrate(saved ?: InitialState.create(now))
            if (saved == null || value != saved) repository.save(value)
            mutableState.value = value
            loaded = true
            mutableError.value = null
            notifications.scheduleBackground(value.profileCreated && value.settings.backgroundActivity)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            mutableError.value = failure.message ?: "Your local profile could not be opened."
        }
    }

    suspend fun dispatch(action: Action) {
        load()
        mutex.withLock {
            val before = mutableState.value ?: return@withLock
            try {
                val now = System.currentTimeMillis()
                if (action is Action.SwitchAccount) {
                    repository.save(before)
                    val switched = repository.switch(action.id) ?: return@withLock
                    mutableState.value = switched.copy(lastSimulationAt = now)
                    lastForegroundTick = now
                    return@withLock
                }
                if (action == Action.CreateAccount) {
                    repository.save(before)
                    val accountId = "account-${UUID.randomUUID()}"
                    val created =
                        repository.create(accountId, InitialState.create(now).copy(activeAccountId = accountId))
                    mutableState.value = created
                    lastForegroundTick = now
                    return@withLock
                }
                var after = if (action == Action.Refresh) {
                    val elapsed = ((now - before.lastSimulationAt.coerceAtLeast(1L)) / 1000.0).coerceIn(1.0, 60.0)
                    SimulationEngine.step(before, elapsed, now).copy(lastSimulationAt = now)
                } else StateReducer.reduce(before, action, now, UUID.randomUUID().toString())
                if (action is Action.SaveSettings || action is Action.ChooseGrowth || action is Action.CompleteProfile) after =
                    after.copy(lastSimulationAt = System.currentTimeMillis())
                if (action is Action.CompleteProfile || action is Action.EditProfile) after = after.copy(
                    accounts = after.accounts.filterNot { it.id == after.activeAccountId } + AccountSummary(
                        after.activeAccountId,
                        after.profile.username,
                        after.profile.displayName,
                        after.profile.avatar,
                        after.profile.avatarArtwork
                    )
                )
                if (action is Action.GridPosition || action is Action.StoryProgress || action is Action.SaveDraft) mutableState.value =
                    after
                else save(before, after)
                // Reclaim imported files once nothing points at them any more.
                if (action is Action.DeletePost || action is Action.DeleteStory || action is Action.EditProfile) {
                    runCatching { repository.collectUnusedMedia(appContext) }
                }
                if (before.settings.backgroundActivity != after.settings.backgroundActivity || action is Action.CompleteProfile) notifications.scheduleBackground(
                    after.profileCreated && after.settings.backgroundActivity
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableError.value = failure.message ?: "Could not save changes."
            }
        }
    }

    suspend fun tick() {
        load()
        mutex.withLock {
            val before = mutableState.value ?: return@withLock
            if (!foreground || mutableError.value != null) return@withLock
            val now = System.currentTimeMillis()
            val seconds = ((now - lastForegroundTick) / 1000.0).coerceIn(0.0, 2.0)
            lastForegroundTick = now
            try {
                val after = SimulationEngine.step(before, seconds, now).copy(lastSimulationAt = now)
                if (after.activity != before.activity || now - lastCheckpoint >= 1500) save(before, after)
                else mutableState.value = after
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableError.value = failure.message ?: "Could not save activity."
            }
        }
    }

    suspend fun advanceBackground() {
        load()
        mutex.withLock {
            val active = mutableState.value ?: return@withLock
            if (mutableError.value != null) return@withLock
            val now = System.currentTimeMillis()
            val stored = repository.allStates().ifEmpty { listOf(active) }
            for (snapshot in stored) {
                val isActive = snapshot.activeAccountId == active.activeAccountId
                if ((foreground && isActive) || !snapshot.profileCreated || !snapshot.settings.backgroundActivity) continue
                val before = if (isActive) active else snapshot
                val after = catchUp(before, now)
                if (after == before) continue
                if (isActive) save(before, after) else repository.save(after)
            }
        }
    }

    private fun catchUp(state: AppState, now: Long): AppState {
        var result = state
        var simulatedAt = state.lastSimulationAt.coerceAtLeast(1L)
        var remaining = ((now - simulatedAt) / 1000.0).coerceIn(0.0, 7 * 86_400.0)
        while (remaining > 0.0) {
            val chunk = remaining.coerceAtMost(3600.0)
            simulatedAt += (chunk * 1000).toLong()
            result = SimulationEngine.step(result, chunk, simulatedAt)
            remaining -= chunk
        }
        return result.copy(lastSimulationAt = now)
    }

    fun setForeground(active: Boolean) {
        wantsForeground = active
        if (active) scope.launch {
            advanceBackground()
            if (wantsForeground) {
                lastForegroundTick = System.currentTimeMillis(); foreground = true
            }
        } else {
            foreground = false
            scope.launch { flush() }
        }
    }

    suspend fun exportBackup(): ByteArray {
        load()
        return mutex.withLock {
            mutableState.value?.let { repository.save(it) }
            repository.exportBackup()
        }
    }

    suspend fun importBackup(bytes: ByteArray): Int {
        load()
        return mutex.withLock {
            val restored = repository.importBackup(bytes)
            val now = System.currentTimeMillis()
            mutableState.value = repository.load()?.copy(lastSimulationAt = now)
            lastForegroundTick = now
            mutableError.value = null
            restored
        }
    }

    suspend fun deleteActiveAccount() {
        load()
        mutex.withLock {
            val current = mutableState.value ?: return@withLock
            val next = repository.deleteAccount(current.activeAccountId) ?: return@withLock
            val now = System.currentTimeMillis()
            mutableState.value = next.copy(lastSimulationAt = now)
            lastForegroundTick = now
        }
    }

    suspend fun retry() {
        if (!loaded) load() else mutex.withLock {
            mutableState.value?.let {
                try {
                    repository.save(it); mutableError.value = null
                } catch (failure: Exception) {
                    mutableError.value = failure.message
                }
            }
        }
    }

    suspend fun flush() = mutex.withLock {
        mutableState.value?.let {
            try {
                repository.save(it); lastCheckpoint = System.currentTimeMillis()
            } catch (failure: Exception) {
                mutableError.value = failure.message
            }
        }
    }

    private suspend fun save(before: AppState, after: AppState) {
        val saved = after.copy(lastSavedAt = System.currentTimeMillis())
        mutableState.value = saved
        repository.save(saved)
        lastCheckpoint = saved.lastSavedAt
        mutableError.value = null
        notifications.deliver(before, saved)
    }
}
