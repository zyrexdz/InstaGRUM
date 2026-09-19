package com.instagrum.local.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider

import com.instagrum.local.model.MediaKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])

class AndroidStorageTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun importedImageIsIndependentOfOriginalFile() = runBlocking {
        val original = File.createTempFile("source", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(32, 48, Bitmap.Config.ARGB_8888)
        original.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val media = MediaStore.import(context, Uri.fromFile(original), MediaKind.IMAGE)
        val copy = File(Uri.parse(media.path).path!!)
        try {
            assertTrue(copy.canonicalPath.startsWith(File(context.filesDir, "media").canonicalPath))
            assertArrayEquals(original.readBytes(), copy.readBytes())
            assertTrue(original.delete())
            assertTrue(copy.exists())
            assertTrue(copy.length() > 0)
        } finally {
            original.delete(); copy.delete()
        }
    }

    @Test
    fun badImportDoesNotKeepPartialFileOrFallBackToProviderUri() = runBlocking {
        val source = File.createTempFile("invalid", ".png", context.cacheDir).apply { writeText("not an image") }
        val mediaDir = File(context.filesDir, "media")
        val before = mediaDir.listFiles().orEmpty().map { it.name }.toSet()
        try {
            assertTrue(runCatching { MediaStore.import(context, Uri.fromFile(source), MediaKind.IMAGE) }.isFailure)
            assertEquals(before, mediaDir.listFiles().orEmpty().map { it.name }.toSet())
        } finally {
            source.delete()
        }
    }

    @Test
    fun remoteMediaCannotBeImported() = runBlocking {
        assertTrue(runCatching {
            MediaStore.import(
                context,
                Uri.parse("https://example.com/photo.jpg"),
                MediaKind.IMAGE
            )
        }.isFailure)
    }

    @Test
    fun everyBundledPhotoDecodesWithoutNetwork() {
        repeat(9) { index ->
            context.assets.open("photos/$index.jpg").use { input ->
                val bitmap = BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply { inSampleSize = 8 })
                assertNotNull("Bundled image $index could not be decoded", bitmap)
                assertTrue(bitmap!!.width > 0 && bitmap.height > 0)
                bitmap.recycle()
            }
        }
    }

    @Test
    fun eachAccountKeepsItsOwnSnapshotAndSurvivesSwitching() = runBlocking {
        val repository = AppRepository(context)
        val now = 1_730_000_000_000L
        val first = InitialState.create(now).copy(
            profile = com.instagrum.local.model.Profile("first.account", "First"),
            profileCreated = true,
            activeAccountId = "default"
        )
        repository.save(first)

        val second = repository.create(
            "account-two",
            InitialState.create(now).copy(
                profile = com.instagrum.local.model.Profile("second.account", "Second"),
                profileCreated = true
            )
        )
        assertEquals("second.account", second.profile.username)
        assertTrue("both accounts must be listed", second.accounts.size >= 2)

        val back = repository.switch("default")
        assertNotNull(back)
        assertEquals("first.account", back!!.profile.username)
        assertEquals("default", back.activeAccountId)

        // Reopening the app must restore the account that was last in use.
        assertEquals("first.account", repository.load()?.profile?.username)
    }

    @Test
    fun applicationHasNoInternetPermission() {
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            android.content.pm.PackageManager.GET_PERMISSIONS
        )
        assertFalse(info.requestedPermissions.orEmpty().contains("android.permission.INTERNET"))
    }
}
