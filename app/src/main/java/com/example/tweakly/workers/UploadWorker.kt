package com.example.tweakly.workers

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.example.tweakly.data.local.dao.MediaDao
import com.example.tweakly.data.local.entity.SyncStatus
import com.example.tweakly.data.remote.TweaklyApi
import com.example.tweakly.data.repository.AuthRepository
import com.example.tweakly.utils.ImageUtils
import com.example.tweakly.utils.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@HiltWorker
class UploadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val api: TweaklyApi,
    private val mediaDao: MediaDao,
    private val authRepo: AuthRepository,
    private val notificationHelper: NotificationHelper
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val mediaId = inputData.getLong(KEY_MEDIA_ID, -1L)
        if (mediaId == -1L) return Result.failure()
        val quality = inputData.getInt(KEY_QUALITY, 85)

        val entity = mediaDao.getById(mediaId) ?: return Result.failure()

        notificationHelper.showUploadProgress(mediaId, entity.displayName, 0)

        return try {
            authRepo.refreshToken()
            mediaDao.updateSync(mediaId, SyncStatus.PENDING)

            val uri = Uri.parse(entity.uri)
            val isImage = entity.displayName.matches(
                Regex(".*\\.(jpg|jpeg|png|webp|heic)", RegexOption.IGNORE_CASE))

            notificationHelper.showUploadProgress(mediaId, entity.displayName, 30)

            val uploadFile: File? = if (isImage) {
                ImageUtils.compressImage(context, uri, maxDimension = 1920, quality = quality)
            } else {
                ImageUtils.uriToTempFile(context, uri, entity.displayName)
            }

            if (uploadFile == null) {
                mediaDao.updateSync(mediaId, SyncStatus.FAILED)
                notificationHelper.showUploadError(entity.displayName)
                return if (runAttemptCount < 3) Result.retry() else Result.failure()
            }

            notificationHelper.showUploadProgress(mediaId, entity.displayName, 60)

            val yearMonth = SimpleDateFormat("yyyy/MM", Locale.getDefault())
                .format(Date(entity.dateTaken.takeIf { it > 0 } ?: System.currentTimeMillis()))
            val folder = if (entity.duration > 0) "videos" else "photos"
            val remotePath = "$folder/$yearMonth/${entity.displayName}"

            val filePart = MultipartBody.Part.createFormData(
                "file", entity.displayName,
                uploadFile.asRequestBody("application/octet-stream".toMediaTypeOrNull())
            )
            val pathBody = remotePath.toRequestBody("text/plain".toMediaTypeOrNull())
            val msgBody  = "Upload: ${entity.displayName}".toRequestBody("text/plain".toMediaTypeOrNull())

            val response = api.uploadFile(filePart, pathBody, msgBody)
            uploadFile.delete()

            if (response.success) {
                mediaDao.updateSync(mediaId, SyncStatus.SYNCED, response.content?.path ?: remotePath)
                notificationHelper.cancelUpload(mediaId)
                notificationHelper.showUploadComplete(entity.displayName)
                Result.success()
            } else {
                mediaDao.updateSync(mediaId, SyncStatus.FAILED)
                notificationHelper.showUploadError(entity.displayName)
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        } catch (e: Exception) {
            mediaDao.updateSync(mediaId, SyncStatus.FAILED)
            notificationHelper.showUploadError(entity.displayName)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_MEDIA_ID = "media_id"
        const val KEY_QUALITY  = "quality"

        fun buildRequest(mediaId: Long, quality: Int = 85, wifiOnly: Boolean = true) =
            OneTimeWorkRequestBuilder<UploadWorker>()
                .setInputData(workDataOf(KEY_MEDIA_ID to mediaId, KEY_QUALITY to quality))
                .setConstraints(Constraints.Builder()
                    .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .setRequiresStorageNotLow(true)
                    .build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30_000L, java.util.concurrent.TimeUnit.MILLISECONDS)
                .addTag("upload_$mediaId")
                .build()
    }
}
