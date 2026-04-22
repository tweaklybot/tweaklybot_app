package com.example.tweakly.workers

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.example.tweakly.data.local.dao.MediaDao
import com.example.tweakly.data.local.entity.SyncStatus
import com.example.tweakly.data.remote.TweaklyApi
import com.example.tweakly.data.repository.AuthRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import id.zelory.compressor.Compressor
import id.zelory.compressor.constraint.format
import id.zelory.compressor.constraint.quality
import id.zelory.compressor.constraint.resolution
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@HiltWorker
class UploadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val api: TweaklyApi,
    private val mediaDao: MediaDao,
    private val authRepo: AuthRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val mediaId = inputData.getLong(KEY_MEDIA_ID, -1L)
        if (mediaId == -1L) return Result.failure()

        val entity = mediaDao.getById(mediaId) ?: return Result.failure()
        val quality = inputData.getInt(KEY_QUALITY, 85)

        return try {
            // Refresh Firebase token before upload
            authRepo.refreshToken()

            mediaDao.updateSync(mediaId, SyncStatus.PENDING)

            // Copy URI to temp file
            val tempFile = copyUriToTemp(Uri.parse(entity.uri), entity.displayName)
                ?: return Result.failure()

            // Compress if image
            val uploadFile = if (entity.displayName.matches(Regex(".*\\.(jpg|jpeg|png|webp)", RegexOption.IGNORE_CASE))) {
                Compressor.compress(context, tempFile) {
                    resolution(1920, 1920)
                    quality(quality)
                    format(android.graphics.Bitmap.CompressFormat.JPEG)
                }
            } else tempFile

            // Build path: photos/2024/01/filename.jpg
            val yearMonth = SimpleDateFormat("yyyy/MM", Locale.getDefault())
                .format(Date(entity.dateTaken.takeIf { it > 0 } ?: System.currentTimeMillis()))
            val folder = if (entity.duration > 0) "videos" else "photos"
            val remotePath = "$folder/$yearMonth/${entity.displayName}"

            // Multipart upload
            val filePart = MultipartBody.Part.createFormData(
                "file", entity.displayName,
                uploadFile.asRequestBody("application/octet-stream".toMediaTypeOrNull())
            )
            val pathBody = remotePath.toRequestBody("text/plain".toMediaTypeOrNull())
            val msgBody  = "Upload: ${entity.displayName}".toRequestBody("text/plain".toMediaTypeOrNull())

            val response = api.uploadFile(filePart, pathBody, msgBody)

            tempFile.delete()
            if (uploadFile != tempFile) uploadFile.delete()

            if (response.success) {
                mediaDao.updateSync(mediaId, SyncStatus.SYNCED, response.content?.path ?: remotePath)
                Result.success()
            } else {
                mediaDao.updateSync(mediaId, SyncStatus.FAILED)
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        } catch (e: Exception) {
            mediaDao.updateSync(mediaId, SyncStatus.FAILED)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private fun copyUriToTemp(uri: Uri, name: String): File? = try {
        val f = File(context.cacheDir, "upload_${System.currentTimeMillis()}_$name")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(f).use { out -> input.copyTo(out) }
        }
        f.takeIf { it.exists() && it.length() > 0 }
    } catch (e: Exception) { null }

    companion object {
        const val KEY_MEDIA_ID = "media_id"
        const val KEY_QUALITY  = "quality"

        fun buildRequest(
            mediaId: Long,
            quality: Int = 85,
            wifiOnly: Boolean = true
        ): OneTimeWorkRequest {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .build()

            return OneTimeWorkRequestBuilder<UploadWorker>()
                .setInputData(workDataOf(KEY_MEDIA_ID to mediaId, KEY_QUALITY to quality))
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30_000L, java.util.concurrent.TimeUnit.MILLISECONDS)
                .addTag("upload_$mediaId")
                .build()
        }
    }
}
