package com.example.tweakly.data.repository

import android.content.Context
import android.provider.MediaStore
import com.example.tweakly.data.local.dao.PhotoDao
import com.example.tweakly.data.local.entity.PhotoEntity
import com.example.tweakly.data.local.entity.SyncStatus
import com.example.tweakly.data.model.PhotoUiModel
import com.example.tweakly.data.model.SyncStatusUi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhotoRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val photoDao: PhotoDao
) {
    fun getPhotos(): Flow<List<PhotoUiModel>> = photoDao.getAllPhotos().map { entities ->
        entities.map { it.toUiModel() }
    }

    suspend fun loadFromMediaStore() = withContext(Dispatchers.IO) {
        val photos = mutableListOf<PhotoEntity>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, null, null, sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = "content://media/external/images/media/$id"
                photos.add(
                    PhotoEntity(
                        id = id,
                        uri = uri,
                        displayName = cursor.getString(nameCol) ?: "photo_$id.jpg",
                        dateTaken = cursor.getLong(dateCol),
                        size = cursor.getLong(sizeCol),
                        width = cursor.getInt(widthCol),
                        height = cursor.getInt(heightCol),
                        syncStatus = SyncStatus.PENDING
                    )
                )
            }
        }
        photoDao.insertAll(photos)
    }

    suspend fun getPhotoById(id: Long): PhotoEntity? = photoDao.getById(id)

    suspend fun updateSyncStatus(id: Long, status: SyncStatus, remotePath: String? = null) {
        photoDao.updateSyncStatus(id, status, remotePath)
    }

    private fun PhotoEntity.toUiModel() = PhotoUiModel(
        id = id, uri = uri, displayName = displayName,
        dateTaken = dateTaken, size = size, width = width, height = height,
        syncStatus = when (syncStatus) {
            SyncStatus.PENDING -> SyncStatusUi.PENDING
            SyncStatus.SYNCED -> SyncStatusUi.SYNCED
            SyncStatus.FAILED -> SyncStatusUi.FAILED
        },
        remotePath = remotePath
    )
}
