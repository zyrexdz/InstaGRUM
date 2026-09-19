package com.instagrum.local.data

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.instagrum.local.model.Media
import com.instagrum.local.model.MediaKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

object MediaStore {
    const val MAX_BYTES = 250L * 1024 * 1024

    suspend fun import(context: Context, source: Uri, kind: MediaKind): Media = withContext(Dispatchers.IO) {
        require(source.scheme == "content" || source.scheme == "file") { "Choose media stored on this device." }
        val mime = context.contentResolver.getType(source).orEmpty()
        require(mime.isEmpty() || mime.startsWith(if (kind == MediaKind.IMAGE) "image/" else "video/")) {
            "This file does not match the selected media type."
        }
        val directory = File(context.filesDir, "media")
        check(directory.isDirectory || directory.mkdirs()) { "Private media storage is unavailable." }
        val sourceExtension = source.path.orEmpty().substringAfterLast('.', "").lowercase()
        val extension = when {
            kind == MediaKind.IMAGE && sourceExtension in setOf("png", "webp", "gif", "jpg", "jpeg") -> sourceExtension
            kind == MediaKind.VIDEO && sourceExtension in setOf("mp4", "m4v", "mov", "webm", "3gp") -> sourceExtension
            mime.contains("png") -> "png"
            mime.contains("webp") -> "webp"
            mime.contains("gif") -> "gif"
            mime.contains("quicktime") -> "mov"
            kind == MediaKind.VIDEO -> "mp4"
            else -> "jpg"
        }
        val file = File(directory, "${UUID.randomUUID()}.$extension")
        try {
            context.contentResolver.openInputStream(source)?.use { input ->
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_BYTES) throw IOException("Choose a file smaller than 250 MB.")
                        output.write(buffer, 0, count)
                    }
                    check(total > 0) { "This file is empty." }
                    output.fd.sync()
                }
            } ?: throw IOException("Could not open this file. Try another local file.")
            if (kind == MediaKind.IMAGE) {
                require(hasKnownImageSignature(file)) { "Unsupported or damaged image." }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unsupported or damaged image." }
                require(bounds.outWidth.toLong() * bounds.outHeight <= 80_000_000L) { "Please choose an image below 80 megapixels." }
            } else {
                val metadata = MediaMetadataRetriever()
                try {
                    metadata.setDataSource(file.path)
                    require(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes") { "No playable video was found in this file." }
                } finally {
                    metadata.release()
                }
            }
            Media(path = Uri.fromFile(file).toString(), kind = kind)
        } catch (failure: Throwable) {
            file.delete()
            throw failure
        }
    }

    private fun hasKnownImageSignature(file: File): Boolean {
        val header = ByteArray(12)
        val count = file.inputStream().use { it.read(header) }
        fun byte(index: Int, expected: Int): Boolean = count > index && (header[index].toInt() and 0xFF) == expected
        val png =
            count >= 8 && byte(0, 0x89) && byte(1, 0x50) && byte(2, 0x4E) && byte(3, 0x47) && byte(4, 0x0D) && byte(
                5,
                0x0A
            ) && byte(6, 0x1A) && byte(7, 0x0A)
        val jpeg = count >= 3 && byte(0, 0xFF) && byte(1, 0xD8) && byte(2, 0xFF)
        val gif =
            count >= 6 && header.copyOfRange(0, 6).toString(Charsets.US_ASCII).let { it == "GIF87a" || it == "GIF89a" }
        val webp =
            count >= 12 && header.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF" && header.copyOfRange(8, 12)
                .toString(Charsets.US_ASCII) == "WEBP"
        return png || jpeg || gif || webp
    }
}
