package com.instagrum.local.data

import android.content.Context
import com.instagrum.local.model.AccountSummary
import com.instagrum.local.model.AppState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
internal data class BackupAccount(val id: String, val state: AppState)

@Serializable
internal data class BackupPayload(
    val format: Int = 1,
    val activeAccountId: String,
    val accounts: List<BackupAccount>,
)

/** One atomic snapshot per local account. No login, key, database, network or subscription. */
class AppRepository(private val context: Context) {
    private val root = File(context.filesDir, "accounts")
    private val preferences = context.getSharedPreferences("local_accounts", Context.MODE_PRIVATE)
    private val legacy = SnapshotStore(context.filesDir)
    private val backupJson = Json { encodeDefaults = true; ignoreUnknownKeys = true; prettyPrint = false }

    private fun safe(id: String) =
        id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(80).ifBlank { "default" }

    private fun store(id: String) = SnapshotStore(File(root, safe(id)))
    fun activeId(): String = preferences.getString("active", "default") ?: "default"

    suspend fun load(): AppState? = withContext(Dispatchers.IO) {
        root.mkdirs()
        val active = activeId()
        var state = store(active).load()
        if (state == null && active == "default") {
            state = legacy.load()
            if (state != null) store(active).save(state.copy(activeAccountId = active))
        }
        state?.let { decorate(it, active) }
    }

    suspend fun save(state: AppState) = withContext(Dispatchers.IO + NonCancellable) {
        val id = safe(state.activeAccountId)
        root.mkdirs()
        store(id).save(state.copy(activeAccountId = id, accounts = emptyList()))
        preferences.edit().putString("active", id).apply()
    }

    suspend fun allStates(): List<AppState> = withContext(Dispatchers.IO) {
        root.mkdirs()
        root.listFiles().orEmpty().filter { it.isDirectory }.mapNotNull { directory ->
            runCatching { store(directory.name).load()?.copy(activeAccountId = directory.name) }.getOrNull()
        }
    }

    suspend fun switch(id: String): AppState? = withContext(Dispatchers.IO) {
        val clean = safe(id)
        val value = store(clean).load() ?: return@withContext null
        preferences.edit().putString("active", clean).apply()
        decorate(value, clean)
    }

    suspend fun create(id: String, state: AppState): AppState = withContext(Dispatchers.IO + NonCancellable) {
        val clean = safe(id)
        val value = state.copy(activeAccountId = clean, accounts = emptyList())
        store(clean).save(value)
        preferences.edit().putString("active", clean).apply()
        decorate(value, clean)
    }

    suspend fun exportBackup(): ByteArray = withContext(Dispatchers.IO) {
        root.mkdirs()
        val states = allStates()
        val payload = BackupPayload(
            activeAccountId = activeId(),
            accounts = states.map { BackupAccount(it.activeAccountId, it) }
        )
        backupJson.encodeToString(payload).toByteArray(Charsets.UTF_8)
    }

    /** Returns how many accounts were restored. Existing accounts are replaced by id. */
    suspend fun importBackup(bytes: ByteArray): Int = withContext(Dispatchers.IO + NonCancellable) {
        val payload = backupJson.decodeFromString<BackupPayload>(bytes.toString(Charsets.UTF_8))
        require(payload.accounts.isNotEmpty()) { "That backup file contains no accounts." }
        root.mkdirs()
        payload.accounts.forEach { entry ->
            val id = safe(entry.id)
            store(id).save(entry.state.copy(activeAccountId = id, accounts = emptyList()))
        }
        val active = safe(payload.activeAccountId).takeIf { id -> payload.accounts.any { safe(it.id) == id } }
            ?: safe(payload.accounts.first().id)
        preferences.edit().putString("active", active).apply()
        payload.accounts.size
    }

    /** Removes one account's snapshot. The last remaining account cannot be deleted. */
    suspend fun deleteAccount(id: String): AppState? = withContext(Dispatchers.IO + NonCancellable) {
        val clean = safe(id)
        val remaining = allStates().map { it.activeAccountId }.filterNot { it == clean }
        if (remaining.isEmpty()) return@withContext null
        File(root, clean).deleteRecursively()
        val next = remaining.first()
        preferences.edit().putString("active", next).apply()
        store(next).load()?.let { decorate(it, next) }
    }

    /** Deletes imported media that no account references any more. */
    suspend fun collectUnusedMedia(context: Context): Int = withContext(Dispatchers.IO + NonCancellable) {
        val directory = File(context.filesDir, "media")
        if (!directory.isDirectory) return@withContext 0
        val referenced = buildSet {
            allStates().forEach { state ->
                state.posts.forEach { add(it.media.path) }
                state.stories.forEach { add(it.media.path); add(it.overlay.sticker) }
                add(state.profile.avatar)
                state.liveHistory.forEach { add(it.config.thumbnail.path) }
                state.activeLive?.let { add(it.config.thumbnail.path) }
                add(state.draft.media.path)
                add(state.draft.liveConfig.thumbnail.path)
            }
        }.mapNotNull { path -> path.takeIf { it.isNotBlank() }?.substringAfterLast('/') }.toSet()
        directory.listFiles().orEmpty().count { file ->
            file.isFile && file.name !in referenced && file.delete()
        }
    }

    private fun decorate(state: AppState, active: String): AppState = state.copy(
        activeAccountId = active,
        accounts = summaries(active, state)
    )

    private fun summaries(active: String, activeState: AppState): List<AccountSummary> {
        root.mkdirs()
        val ids =
            root.listFiles().orEmpty().filter { it.isDirectory }.map { it.name }.toMutableSet().apply { add(active) }
        return ids.mapNotNull { id ->
            val state = if (id == active) activeState else runCatching { store(id).load() }.getOrNull()
                ?: return@mapNotNull null
            if (!state.profileCreated && id != active) return@mapNotNull null
            AccountSummary(
                id,
                state.profile.username,
                state.profile.displayName,
                state.profile.avatar,
                state.profile.avatarArtwork
            )
        }.sortedWith(compareByDescending<AccountSummary> { it.id == active }.thenBy { it.username })
    }
}
