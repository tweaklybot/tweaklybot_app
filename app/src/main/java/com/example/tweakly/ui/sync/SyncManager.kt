package com.example.tweakly.ui.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.WorkManager
import com.example.tweakly.data.local.dao.MediaDao
import com.example.tweakly.data.local.entity.SyncStatus
import com.example.tweakly.data.repository.SubscriptionRepository
import com.example.tweakly.workers.UploadWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workManager: WorkManager,
    private val mediaDao: MediaDao,
    private val subscriptionRepo: SubscriptionRepository
) {
    /** Returns true if device is on an unmetered (WiFi/Ethernet) network */
    fun isOnWifi(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val cap = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
               cap.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val cap = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Enqueue upload for a single media item.
     * - Checks subscription limits before queuing
     * - Uses quality from subscription tier
     * - Returns SyncDecision explaining what happened
     */
    suspend fun enqueueUpload(mediaId: Long): SyncDecision {
        val sub = subscriptionRepo.state.first()

        if (sub.isStorageFull) {
            return SyncDecision.StorageFull(
                usedGB = sub.usedStorageGB,
                limitGB = sub.limits.storageGB
            )
        }

        val entity = mediaDao.getById(mediaId)
            ?: return SyncDecision.Error("Файл не найден")

        val isVideo = entity.duration > 0
        if (isVideo && !sub.limits.videoUploadEnabled) {
            return SyncDecision.FeatureLocked(
                feature = "Загрузка видео",
                reason = "Доступно только в Premium"
            )
        }

        if (isVideo && entity.size > sub.limits.maxVideoSizeMB * 1024 * 1024L) {
            return SyncDecision.FileTooLarge(
                fileSizeMB = entity.size / (1024 * 1024),
                limitMB = sub.limits.maxVideoSizeMB.toLong()
            )
        }

        val wifiOnly = sub.limits.forcedWifiOnly  // free tier always forces WiFi
        val quality = sub.limits.uploadQuality

        val req = UploadWorker.buildRequest(mediaId, quality, wifiOnly)
        workManager.enqueue(req)

        val networkNote = when {
            !isOnline()          -> "Файл в очереди — нет интернета"
            wifiOnly && !isOnWifi() -> "Файл в очереди — ожидание WiFi"
            else                 -> null
        }

        return SyncDecision.Queued(quality = quality, note = networkNote)
    }

    /** Enqueue all PENDING items */
    suspend fun enqueueAllPending(): Int {
        val pending = mediaDao.getByStatus(SyncStatus.PENDING)
        var queued = 0
        for (item in pending) {
            val result = enqueueUpload(item.id)
            if (result is SyncDecision.Queued) queued++
        }
        return queued
    }
}

sealed class SyncDecision {
    data class Queued(val quality: Int, val note: String?) : SyncDecision()
    data class StorageFull(val usedGB: Double, val limitGB: Int) : SyncDecision()
    data class FeatureLocked(val feature: String, val reason: String) : SyncDecision()
    data class FileTooLarge(val fileSizeMB: Long, val limitMB: Long) : SyncDecision()
    data class Error(val message: String) : SyncDecision()
}
