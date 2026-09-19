package com.instagrum.local.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
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
        val rawMime = context.contentResolver.getType(source).orEmpty().lowercase()
        val directory = File(context.filesDir, "media")
        check(directory.isDirectory || directory.mkdirs()) { "Private media storage is unavailable." }

        var file = File(directory, "${UUID.randomUUID()}.tmp")
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

            val isVideo = if (kind == MediaKind.VIDEO || kind == MediaKind.REEL || rawMime.startsWith("video/")) {
                hasKnownVideoSignature(file)
            } else false

            if (isVideo) {
                val metadata = MediaMetadataRetriever()
                try {
                    metadata.setDataSource(file.path)
                    val hasVideo = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
                    require(hasVideo == "yes") { "No playable video was found in this file." }
                } finally {
                    metadata.release()
                }
                val ext = if (rawMime.contains("webm")) "webm" else if (rawMime.contains("quicktime")) "mov" else "mp4"
                val finalVideo = File(directory, "${UUID.randomUUID()}.$ext")
                file.renameTo(finalVideo)
                file = finalVideo
                return@withContext Media(
                    path = Uri.fromFile(file).toString(),
                    kind = if (kind == MediaKind.REEL) MediaKind.REEL else MediaKind.VIDEO
                )
            }

            require(hasKnownImageSignature(file)) { "Unsupported or damaged image." }

            val ext = when {
                rawMime.contains("png") -> "png"
                rawMime.contains("webp") -> "webp"
                rawMime.contains("gif") -> "gif"
                rawMime.contains("bmp") -> "bmp"
                rawMime.contains("heic") || rawMime.contains("heif") -> "heic"
                rawMime.contains("avif") -> "avif"
                isHeicOrAvif(file) -> "heic"
                isPng(file) -> "png"
                isWebp(file) -> "webp"
                isGif(file) -> "gif"
                isBmp(file) -> "bmp"
                else -> "jpg"
            }
            val renamed = File(directory, "${UUID.randomUUID()}.$ext")
            file.renameTo(renamed)
            file = renamed

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)

            if ((bounds.outWidth <= 0 || bounds.outHeight <= 0) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    val src = ImageDecoder.createSource(file)
                    val bmp = ImageDecoder.decodeBitmap(src) { decoder, _, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                    if (bmp.width > 0 && bmp.height > 0) {
                        bounds.outWidth = bmp.width
                        bounds.outHeight = bmp.height
                        val converted = File(directory, "${UUID.randomUUID()}.jpg")
                        FileOutputStream(converted).use { out ->
                            bmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
                        }
                        bmp.recycle()
                        file.delete()
                        file = converted
                    }
                } catch (_: Throwable) {}
            }

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                try {
                    context.contentResolver.openInputStream(source)?.use { input ->
                        val bmp = BitmapFactory.decodeStream(input)
                        if (bmp != null && bmp.width > 0 && bmp.height > 0) {
                            bounds.outWidth = bmp.width
                            bounds.outHeight = bmp.height
                            val converted = File(directory, "${UUID.randomUUID()}.jpg")
                            FileOutputStream(converted).use { out ->
                                bmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
                            }
                            bmp.recycle()
                            file.delete()
                            file = converted
                        }
                    }
                } catch (_: Throwable) {}
            }

            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unsupported or damaged image." }
            require(bounds.outWidth.toLong() * bounds.outHeight <= 150_000_000L) { "Please choose an image below 150 megapixels." }

            if (ext in setOf("heic", "heif", "avif") && file.extension != "jpg") {
                try {
                    val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, _, _ ->
                            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        }
                    } else {
                        BitmapFactory.decodeFile(file.path)
                    }
                    if (bmp != null) {
                        val converted = File(directory, "${UUID.randomUUID()}.jpg")
                        FileOutputStream(converted).use { out ->
                            bmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
                        }
                        bmp.recycle()
                        file.delete()
                        file = converted
                    }
                } catch (_: Throwable) {}
            }

            Media(path = Uri.fromFile(file).toString(), kind = MediaKind.IMAGE)
        } catch (failure: Throwable) {
            file.delete()
            throw failure
        }
    }

    private fun hasKnownImageSignature(file: File): Boolean {
        val header = ByteArray(32)
        val count = file.inputStream().use { it.read(header) }
        if (count < 2) return false
        fun b(i: Int) = header[i].toInt() and 0xFF

        val jpeg = count >= 2 && b(0) == 0xFF && b(1) == 0xD8
        val png = count >= 8 && b(0) == 0x89 && b(1) == 0x50 && b(2) == 0x4E && b(3) == 0x47 &&
                b(4) == 0x0D && b(5) == 0x0A && b(6) == 0x1A && b(7) == 0x0A
        val gif = count >= 6 && header.copyOfRange(0, 6).toString(Charsets.US_ASCII).let { it == "GIF87a" || it == "GIF89a" }
        val webp = count >= 12 && header.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF" &&
                header.copyOfRange(8, 12).toString(Charsets.US_ASCII) == "WEBP"
        val bmp = count >= 2 && b(0) == 0x42 && b(1) == 0x4D
        val ftypImage = count >= 12 && header.copyOfRange(4, 8).toString(Charsets.US_ASCII) == "ftyp" &&
                header.copyOfRange(8, 12).toString(Charsets.US_ASCII).lowercase().let { brand ->
                    brand.startsWith("heic") || brand.startsWith("heix") || brand.startsWith("heim") ||
                    brand.startsWith("heis") || brand.startsWith("mif1") || brand.startsWith("msf1") ||
                    brand.startsWith("avif") || brand.startsWith("avis") || brand.startsWith("mp42")
                }

        return jpeg || png || gif || webp || bmp || ftypImage
    }

    private fun hasKnownVideoSignature(file: File): Boolean {
        val header = ByteArray(32)
        val count = file.inputStream().use { it.read(header) }
        if (count < 4) return false
        fun b(i: Int) = header[i].toInt() and 0xFF
        val ftypVideo = count >= 12 && header.copyOfRange(4, 8).toString(Charsets.US_ASCII) == "ftyp"
        val webm = count >= 4 && b(0) == 0x1A && b(1) == 0x45 && b(2) == 0xDF && b(3) == 0xA3
        return ftypVideo || webm
    }

    private fun isHeicOrAvif(file: File): Boolean {
        val header = ByteArray(16)
        val count = file.inputStream().use { it.read(header) }
        return count >= 12 && header.copyOfRange(4, 8).toString(Charsets.US_ASCII) == "ftyp" &&
                header.copyOfRange(8, 12).toString(Charsets.US_ASCII).lowercase().let { brand ->
                    brand.startsWith("heic") || brand.startsWith("heix") || brand.startsWith("heim") ||
                    brand.startsWith("heis") || brand.startsWith("mif1") || brand.startsWith("msf1") ||
                    brand.startsWith("avif") || brand.startsWith("avis")
                }
    }

    private fun isPng(file: File): Boolean {
        val header = ByteArray(8)
        val count = file.inputStream().use { it.read(header) }
        fun b(i: Int) = header[i].toInt() and 0xFF
        return count >= 8 && b(0) == 0x89 && b(1) == 0x50 && b(2) == 0x4E && b(3) == 0x47
    }

    private fun isWebp(file: File): Boolean {
        val header = ByteArray(12)
        val count = file.inputStream().use { it.read(header) }
        return count >= 12 && header.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF" &&
                header.copyOfRange(8, 12).toString(Charsets.US_ASCII) == "WEBP"
    }

    private fun isGif(file: File): Boolean {
        val header = ByteArray(6)
        val count = file.inputStream().use { it.read(header) }
        return count >= 6 && header.copyOfRange(0, 6).toString(Charsets.US_ASCII).let { it == "GIF87a" || it == "GIF89a" }
    }

    private fun isBmp(file: File): Boolean {
        val header = ByteArray(2)
        val count = file.inputStream().use { it.read(header) }
        fun b(i: Int) = header[i].toInt() and 0xFF
        return count >= 2 && b(0) == 0x42 && b(1) == 0x4D
    }
}
