package com.instagrum.local

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.instagrum.local.data.SimulatorRuntime
import com.instagrum.local.model.Action
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** UI lifecycle adapter; the application runtime owns the only simulation and persistence writer. */
class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val runtime = SimulatorRuntime.get(application)
    val state = runtime.state
    val error = runtime.error

    init {
        viewModelScope.launch { runtime.load() }
        viewModelScope.launch {
            while (isActive) {
                delay(500)
                if (runtime.foreground) runtime.tick()
            }
        }
    }

    fun dispatch(action: Action) = runtime.enqueue(action)

    fun exportBackup(target: android.net.Uri, context: android.content.Context, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val message = runCatching {
                val bytes = runtime.exportBackup()
                context.contentResolver.openOutputStream(target)?.use { it.write(bytes) }
                    ?: error("Could not open that location.")
                "Backup saved. Keep the file to restore your accounts later."
            }.getOrElse { "Backup failed: ${it.message}" }
            onResult(message)
        }
    }

    fun importBackup(source: android.net.Uri, context: android.content.Context, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val message = runCatching {
                val bytes = context.contentResolver.openInputStream(source)?.use { it.readBytes() }
                    ?: error("Could not read that file.")
                "Restored ${runtime.importBackup(bytes)} account(s)."
            }.getOrElse { "Restore failed: ${it.message}" }
            onResult(message)
        }
    }

    fun deleteAccount() {
        viewModelScope.launch { runtime.deleteActiveAccount() }
    }

    fun retry() {
        viewModelScope.launch { runtime.retry() }
    }

    fun setForeground(active: Boolean) = runtime.setForeground(active)
    fun openNotification(id: String) = dispatch(Action.OpenActivity(id))
}
