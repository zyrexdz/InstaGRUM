package com.instagrum.local.data

import com.instagrum.local.model.AppState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class UnsupportedSnapshotVersion(version: Int) :
    IllegalStateException("Snapshot version $version requires a newer InstaGRUM. Your data has not been overwritten.")

class SnapshotStore(private val directory: File) {
    private companion object {
        const val CURRENT = 2
    }

    private val file = File(directory, "instagrum-local-state.json")
    private val backup = File(directory, "instagrum-local-state.backup.json")
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    @Synchronized
    fun load(): AppState? {
        if (!file.exists() && !backup.exists()) return null
        var failure: Exception? = null
        for (candidate in listOf(file, backup)) {
            if (!candidate.exists()) continue
            try {
                val state = json.decodeFromString<AppState>(candidate.readText())
                if (state.schemaVersion > CURRENT) throw UnsupportedSnapshotVersion(state.schemaVersion)
                return state
            } catch (error: UnsupportedSnapshotVersion) {
                throw error
            } catch (error: Exception) {
                failure = error
            }
        }
        throw IllegalStateException("Your saved data could not be read. It has not been overwritten.", failure)
    }

    @Synchronized
    fun save(state: AppState) {
        check(state.schemaVersion <= CURRENT)
        directory.mkdirs()
        val temporary = File(directory, "instagrum-local-state.tmp")
        val bytes = json.encodeToString(state).toByteArray(Charsets.UTF_8)
        FileOutputStream(temporary).use { stream -> stream.write(bytes); stream.fd.sync() }

        if (file.exists() && runCatching { json.decodeFromString<AppState>(file.readText()) }.isSuccess) {
            Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        try {
            Files.move(
                temporary.toPath(),
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
