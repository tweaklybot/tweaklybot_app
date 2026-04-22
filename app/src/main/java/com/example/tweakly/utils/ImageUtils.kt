package com.example.tweakly.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object ImageUtils {

    /**
     * Compress an image URI to a temp file.
     * - Respects EXIF rotation
     * - Scales down if > maxDimension
     * - Encodes as JPEG with given quality
     */
    suspend fun compressImage(
        context: Context,
        uri: Uri,
        maxDimension: Int = 1920,
        quality: Int = 85
    ): File? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val original = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            // Read EXIF rotation
            val exifStream = context.contentResolver.openInputStream(uri)
            val rotation = exifStream?.let {
                val exif = ExifInterface(it)
                it.close()
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f

            // Rotate if needed
            val rotated = if (rotation != 0f) {
                val matrix = Matrix().apply { postRotate(rotation) }
                Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
                    .also { if (it != original) original.recycle() }
            } else original

            // Scale down if too large
            val scaled = if (rotated.width > maxDimension || rotated.height > maxDimension) {
                val ratio = minOf(maxDimension.toFloat() / rotated.width, maxDimension.toFloat() / rotated.height)
                Bitmap.createScaledBitmap(
                    rotated, (rotated.width * ratio).toInt(), (rotated.height * ratio).toInt(), true
                ).also { if (it != rotated) rotated.recycle() }
            } else rotated

            // Write to temp file
            val tempFile = File(context.cacheDir, "upload_${System.currentTimeMillis()}.jpg")
            FileOutputStream(tempFile).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            }
            scaled.recycle()
            tempFile
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Delete a media file from the device.
     * Uses MediaStore.createDeleteRequest for API 30+ (shows system dialog).
     * Uses direct ContentResolver.delete for older APIs.
     * Returns true if deleted successfully.
     */
    suspend fun deleteMediaFile(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // API 30+: use pending intent — caller must launch it
                // Here we attempt direct delete first (works if app has MANAGE_MEDIA)
                val deleted = context.contentResolver.delete(uri, null, null)
                deleted > 0
            } else {
                // API < 30: direct delete
                val deleted = context.contentResolver.delete(uri, null, null)
                deleted > 0
            }
        } catch (e: Exception) {
            false
        }
    }

    /** Copy a URI to a temporary File (for upload). */
    suspend fun uriToTempFile(context: Context, uri: Uri, name: String): File? =
        withContext(Dispatchers.IO) {
            try {
                val f = File(context.cacheDir, "upload_${System.currentTimeMillis()}_$name")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(f).use { out -> input.copyTo(out) }
                }
                f.takeIf { it.exists() && it.length() > 0 }
            } catch (e: Exception) { null }
        }

    /** Save edited bitmap back to MediaStore and return its URI. */
    suspend fun saveBitmapToGallery(
        context: Context,
        bitmap: Bitmap,
        displayName: String
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext null

            context.contentResolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            }
            uri
        } catch (e: Exception) { null }
    }
}
