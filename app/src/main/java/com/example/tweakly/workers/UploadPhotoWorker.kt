package com.example.tweakly.workers

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.example.tweakly.data.local.dao.PhotoDao
import com.example.tweakly.data.local.entity.SyncStatus
import com.example.tweakly.data.remote.TweaklyApi
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@HiltWorker
class UploadPhotoWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val api: TweaklyApi,
    private val photoDao: PhotoDao
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val photoId = inputData.getLong(KEY_PHOTO_ID, -1L)
        if (photoId == -1L) return Result.failure()

        val photo = photoDao.getById(photoId) ?: return Result.failure()

        return try {
            photoDao.updateSyncStatus(photoId, SyncStatus.PENDING)

            // Copy to temp file
            val tempFile = copyUriToTempFile(Uri.parse(photo.uri), photo.displayName)
                ?: return Result.failure()

            // Build multipart
            val filePart = MultipartBody.Part.createFormData(
                "file", photo.displayName,
                tempFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
            )
            val yearMonth = SimpleDateFormat("yyyy/MM", Locale.getDefault()).format(Date(photo.dateTaken))
            val path = "photos/$yearMonth/${photo.displayName}".toRequestBody("text/plain".toMediaTypeOrNull())
            val commitMsg = "Upload via Tweakly: ${photo.displayName}".toRequestBody("text/plain".toMediaTypeOrNull())

            val response = api.uploadFile(filePart, path, commitMsg)
            tempFile.delete()

            if (response.success) {
                photoDao.updateSyncStatus(photoId, SyncStatus.SYNCED, response.path)
                Result.success()
            } else {
                photoDao.updateSyncStatus(photoId, SyncStatus.FAILED)
                Result.retry()
            }
        } catch (e: Exception) {
            photoDao.updateSyncStatus(photoId, SyncStatus.FAILED)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private fun copyUriToTempFile(uri: Uri, name: String): File? {
        return try {
            val tempFile = File(context.cacheDir, "upload_${name}")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output -> input.copyTo(output) }
            }
            tempFile
        } catch (e: Exception) { null }
    }

    companion object {
        const val KEY_PHOTO_ID = "photoId"

        fun buildConstraints(wifiOnly: Boolean = true) = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()

        fun createWorkRequest(photoId: Long, wifiOnly: Boolean = true): OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<UploadPhotoWorker>()
                .setInputData(workDataOf(KEY_PHOTO_ID to photoId))
                .setConstraints(buildConstraints(wifiOnly))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30_000L, java.util.concurrent.TimeUnit.MILLISECONDS)
                .build()
        }
    }
}
