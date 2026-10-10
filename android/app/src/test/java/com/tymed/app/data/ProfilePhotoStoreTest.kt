package com.tymed.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProfilePhotoStoreTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    /** Writes a real (large, non-square) bitmap to a temp file and returns a `file://` Uri to
     * it — [android.content.ContentResolver.openInputStream] handles `file://` Uris directly, no
     * ContentProvider needed, so this exercises [ProfilePhotoStore.save]'s real decode path. */
    private fun sourceUri(widthPx: Int = 1200, heightPx: Int = 800): Uri {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val file = File.createTempFile("source", ".png", context.cacheDir)
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        return Uri.fromFile(file)
    }

    @Test
    fun `save downscales to the max dimension and returns a decodable file`() {
        val path = ProfilePhotoStore.save(context, sourceUri(1200, 800))

        assertNotNull("expected a saved file path", path)
        val saved = BitmapFactory.decodeFile(path)
        assertNotNull("saved path should decode back to a real bitmap", saved)
        assertTrue("width should be downscaled to at most 512px", saved!!.width <= 512)
        assertTrue("height should be downscaled to at most 512px", saved.height <= 512)
        // Aspect ratio preserved: 1200x800 is 3:2, so the longer (width) side lands at 512.
        assertEquals(512, saved.width)
    }

    @Test
    fun `save leaves a small image's dimensions alone rather than upscaling`() {
        val path = ProfilePhotoStore.save(context, sourceUri(100, 80))

        val saved = BitmapFactory.decodeFile(path!!)
        assertEquals(100, saved!!.width)
        assertEquals(80, saved.height)
    }

    @Test
    fun `delete removes the file`() {
        val path = ProfilePhotoStore.save(context, sourceUri())!!
        assertTrue(File(path).exists())

        ProfilePhotoStore.delete(path)

        assertFalse(File(path).exists())
    }

    @Test
    fun `save returns null for a Uri that can't be opened`() {
        val path = ProfilePhotoStore.save(context, Uri.fromFile(File(context.cacheDir, "does-not-exist.png")))

        assertNull(path)
    }
}
