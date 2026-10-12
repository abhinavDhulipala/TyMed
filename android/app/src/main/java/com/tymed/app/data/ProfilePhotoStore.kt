package com.tymed.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Profile photos are copied into app-private storage immediately on pick, downscaled to a small
 * square-ish thumbnail — a picked photo can be several MB, and a content:// Uri's read grant
 * isn't guaranteed to outlive this app session, so holding onto the Uri itself isn't an option.
 * Stored under `filesDir/profile_photos/`, one file per photo, named by a fresh UUID rather than
 * the owning profile's id so a photo picked before a brand-new profile exists yet (the "Add
 * profile" dialog) still has somewhere to live. Call sites are responsible for deleting the old
 * file when a profile's photo is replaced or removed — see `ProfileRepository`. */
object ProfilePhotoStore {
    private const val DIR_NAME = "profile_photos"
    private const val MAX_DIMENSION = 512

    /** Reads [sourceUri], downscales it to at most [MAX_DIMENSION] px on its longest side, and
     * saves it as a JPEG. Returns the absolute path to store on the
     * [com.tymed.app.data.entity.Profile], or null if the image couldn't be read or decoded — the
     * caller falls back to leaving the photo unset rather than failing the whole add/edit. Does
     * disk and bitmap work, so call this off the main thread. */
    fun save(context: Context, sourceUri: Uri): String? {
        val bitmap = decodeScaled(context, sourceUri) ?: return null
        val dir = File(context.filesDir, DIR_NAME).apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        return try {
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) }
            file.absolutePath
        } catch (error: Exception) {
            file.delete()
            null
        } finally {
            bitmap.recycle()
        }
    }

    fun delete(path: String) {
        File(path).delete()
    }

    /** Two-pass decode: first just the bounds (cheap) to pick a power-of-two [BitmapFactory.Options.inSampleSize]
     * that gets close to [MAX_DIMENSION] without decoding a full-resolution bitmap into memory,
     * then a final precise [Bitmap.createScaledBitmap] to land exactly at the target size. */
    private fun decodeScaled(context: Context, uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        } catch (error: Exception) {
            return null
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= MAX_DIMENSION && bounds.outHeight / (sampleSize * 2) >= MAX_DIMENSION) {
            sampleSize *= 2
        }
        val sampled = try {
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        } catch (error: Exception) {
            null
        } ?: return null

        val scale = MAX_DIMENSION.toFloat() / maxOf(sampled.width, sampled.height)
        if (scale >= 1f) return sampled
        val scaled = Bitmap.createScaledBitmap(sampled, (sampled.width * scale).toInt(), (sampled.height * scale).toInt(), true)
        if (scaled !== sampled) sampled.recycle()
        return scaled
    }
}
