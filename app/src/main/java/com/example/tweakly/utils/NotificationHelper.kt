package com.example.tweakly.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_SYNC = "tweakly_sync"
        const val NOTIF_UPLOAD_BASE = 1000
        const val NOTIF_SYNC_SUMMARY = 999
    }

    init { createChannel() }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_SYNC, "Синхронизация",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Прогресс загрузки фото и видео в облако" }
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(ch)
        }
    }

    fun showUploadProgress(mediaId: Long, fileName: String, progress: Int) {
        val notif = NotificationCompat.Builder(context, CHANNEL_SYNC)
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setContentTitle("Загрузка: $fileName")
            .setContentText("$progress%")
            .setProgress(100, progress, progress == 0)
            .setOngoing(progress < 100)
            .setSilent(true)
            .build()
        try {
            NotificationManagerCompat.from(context)
                .notify((NOTIF_UPLOAD_BASE + mediaId).toInt(), notif)
        } catch (_: SecurityException) { /* no POST_NOTIFICATIONS permission */ }
    }

    fun showUploadComplete(fileName: String) {
        val notif = NotificationCompat.Builder(context, CHANNEL_SYNC)
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setContentTitle("Загружено: $fileName")
            .setAutoCancel(true)
            .setSilent(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIF_SYNC_SUMMARY, notif)
        } catch (_: SecurityException) {}
    }

    fun showUploadError(fileName: String) {
        val notif = NotificationCompat.Builder(context, CHANNEL_SYNC)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Ошибка загрузки")
            .setContentText(fileName)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify((fileName.hashCode()), notif)
        } catch (_: SecurityException) {}
    }

    fun cancelUpload(mediaId: Long) {
        NotificationManagerCompat.from(context).cancel((NOTIF_UPLOAD_BASE + mediaId).toInt())
    }
}
