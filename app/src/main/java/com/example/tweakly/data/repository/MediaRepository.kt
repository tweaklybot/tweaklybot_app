package com.example.tweakly.data.repository

import android.content.Context
import android.provider.MediaStore
import com.example.tweakly.data.local.dao.MediaDao
import com.example.tweakly.data.local.entity.DbMediaType
import com.example.tweakly.data.local.entity.MediaEntity
import com.example.tweakly.data.local.entity.SyncStatus
import com.example.tweakly.data.model.MediaItem
import com.example.tweakly.data.model.MediaType
import com.example.tweakly.data.model.SyncStatusUi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: MediaDao
) {
    fun getAll(): Flow<List<MediaItem>> = dao.getAll().map { it.map(MediaEntity::toUi) }
    fun getPhotos(): Flow<List<MediaItem>> = dao.getByType(DbMediaType.PHOTO).map { it.map(MediaEntity::toUi) }
    fun getVideos(): Flow<List<MediaItem>> = dao.getByType(DbMediaType.VIDEO).map { it.map(MediaEntity::toUi) }
    fun getScreenshots(): Flow<List<MediaItem>> = dao.getByType(DbMediaType.SCREENSHOT).map { it.map(MediaEntity::toUi) }

    suspend fun getById(id: Long): MediaEntity? = dao.getById(id)

    suspend fun loadFromMediaStore() = withContext(Dispatchers.IO) {
        val items = mutableListOf<MediaEntity>()
        items += queryImages()
        items += queryVideos()
        dao.insertAll(items)
    }

    private fun queryImages(): List<MediaEntity> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.DATA
        )
        val list = mutableListOf<MediaEntity>()
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, null, null,
            "${MediaStore.Images.Media.DATE_TAKEN} DESC"
        )?.use { cursor ->
            val idCol   = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val wCol    = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val hCol    = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val dataCol = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = "content://media/external/images/media/$id"
                val filePath = if (dataCol >= 0) cursor.getString(dataCol) ?: "" else ""
                val isScreenshot = filePath.contains("screenshot", ignoreCase = true) ||
                        filePath.contains("Screenshots", ignoreCase = true)
                list += MediaEntity(
                    id = id, uri = uri,
                    displayName = cursor.getString(nameCol) ?: "photo_$id.jpg",
                    dateTaken = cursor.getLong(dateCol),
                    size = cursor.getLong(sizeCol),
                    width = cursor.getInt(wCol), height = cursor.getInt(hCol),
                    mediaType = if (isScreenshot) DbMediaType.SCREENSHOT else DbMediaType.PHOTO,
                    syncStatus = SyncStatus.PENDING
                )
            }
        }
        return list
    }

    private fun queryVideos(): List<MediaEntity> {
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATE_TAKEN,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DURATION
        )
        val list = mutableListOf<MediaEntity>()
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection, null, null,
            "${MediaStore.Video.Media.DATE_TAKEN} DESC"
        )?.use { cursor ->
            val idCol  = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol= cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val dateCol= cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_TAKEN)
            val sizeCol= cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val wCol   = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val hCol   = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                list += MediaEntity(
                    id = id,
                    uri = "content://media/external/video/media/$id",
                    displayName = cursor.getString(nameCol) ?: "video_$id.mp4",
                    dateTaken = cursor.getLong(dateCol),
                    size = cursor.getLong(sizeCol),
                    width = cursor.getInt(wCol), height = cursor.getInt(hCol),
                    duration = cursor.getLong(durCol),
                    mediaType = DbMediaType.VIDEO, syncStatus = SyncStatus.PENDING
                )
            }
        }
        return list
    }

    suspend fun updateSync(id: Long, status: SyncStatus, remotePath: String? = null) =
        dao.updateSync(id, status, remotePath)

    companion object {
        fun MediaEntity.toUi() = MediaItem(
            id = id, uri = uri, displayName = displayName,
            dateTaken = dateTaken, size = size, width = width, height = height,
            duration = duration,
            mediaType = when (mediaType) {
                DbMediaType.VIDEO -> MediaType.VIDEO
                DbMediaType.SCREENSHOT -> MediaType.SCREENSHOT
                else -> MediaType.PHOTO
            },
            syncStatus = when (syncStatus) {
                SyncStatus.SYNCED -> SyncStatusUi.SYNCED
                SyncStatus.FAILED -> SyncStatusUi.FAILED
                else -> SyncStatusUi.PENDING
            },
            remotePath = remotePath
        )
    }
}
