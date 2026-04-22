package com.example.tweakly.data.repository

import android.content.Context
import android.provider.MediaStore
import com.example.tweakly.data.local.dao.MediaDao
import com.example.tweakly.data.local.entity.DbMediaType
import com.example.tweakly.data.local.entity.MediaEntity
import com.example.tweakly.data.local.entity.SyncStatus
import com.example.tweakly.data.model.*
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
    fun getAll(sort: SortOrder = SortOrder.DATE_DESC): Flow<List<MediaItem>> = when (sort) {
        SortOrder.DATE_DESC -> dao.getAll()
        SortOrder.DATE_ASC  -> dao.getAllAscDate()
        SortOrder.SIZE_DESC -> dao.getAllBySize()
        SortOrder.NAME_ASC  -> dao.getAllByName()
    }.map { it.map(MediaEntity::toUi) }

    fun getByType(type: DbMediaType): Flow<List<MediaItem>> =
        dao.getByType(type).map { it.map(MediaEntity::toUi) }

    fun getFavorites(): Flow<List<MediaItem>> =
        dao.getFavorites().map { it.map(MediaEntity::toUi) }

    fun getFaceGroups(): Flow<List<String>> = dao.getFaceGroups()

    fun getByFaceGroup(groupId: String): Flow<List<MediaItem>> =
        dao.getByFaceGroup(groupId).map { it.map(MediaEntity::toUi) }

    suspend fun getById(id: Long): MediaEntity? = dao.getById(id)

    suspend fun loadFromMediaStore() = withContext(Dispatchers.IO) {
        val items = mutableListOf<MediaEntity>()
        items += queryImages()
        items += queryVideos()
        dao.insertAll(items)
    }

    suspend fun updateSync(id: Long, status: SyncStatus, remotePath: String? = null) =
        dao.updateSync(id, status, remotePath)

    suspend fun toggleFavorite(id: Long, current: Boolean) =
        dao.setFavorite(id, !current)

    suspend fun setFaceGroup(id: Long, groupId: String?) =
        dao.setFaceGroup(id, groupId)

    suspend fun searchByName(query: String): List<MediaItem> =
        dao.searchByName(query).map { it.toUi() }

    suspend fun deleteLocal(id: Long) = dao.deleteById(id)

    private fun queryImages(): List<MediaEntity> {
        val proj = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN, MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH, MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.DATA)
        val list = mutableListOf<MediaEntity>()
        context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            proj, null, null, "${MediaStore.Images.Media.DATE_TAKEN} DESC")?.use { c ->
            val idC = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nmC = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dtC = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val szC = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val wC  = c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val hC  = c.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val dpC = c.getColumnIndex(MediaStore.Images.Media.DATA)
            while (c.moveToNext()) {
                val id = c.getLong(idC)
                val fp = if (dpC >= 0) c.getString(dpC) ?: "" else ""
                val isScreen = fp.contains("screenshot", ignoreCase = true) ||
                        fp.contains("Screenshots", ignoreCase = true)
                list += MediaEntity(id = id, uri = "content://media/external/images/media/$id",
                    displayName = c.getString(nmC) ?: "photo_$id.jpg",
                    dateTaken = c.getLong(dtC), size = c.getLong(szC),
                    width = c.getInt(wC), height = c.getInt(hC),
                    mediaType = if (isScreen) DbMediaType.SCREENSHOT else DbMediaType.PHOTO,
                    syncStatus = SyncStatus.PENDING)
            }
        }
        return list
    }

    private fun queryVideos(): List<MediaEntity> {
        val proj = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATE_TAKEN, MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH, MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DURATION)
        val list = mutableListOf<MediaEntity>()
        context.contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            proj, null, null, "${MediaStore.Video.Media.DATE_TAKEN} DESC")?.use { c ->
            val idC  = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nmC  = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val dtC  = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_TAKEN)
            val szC  = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val wC   = c.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val hC   = c.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val durC = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            while (c.moveToNext()) {
                val id = c.getLong(idC)
                list += MediaEntity(id = id,
                    uri = "content://media/external/video/media/$id",
                    displayName = c.getString(nmC) ?: "video_$id.mp4",
                    dateTaken = c.getLong(dtC), size = c.getLong(szC),
                    width = c.getInt(wC), height = c.getInt(hC),
                    duration = c.getLong(durC), mediaType = DbMediaType.VIDEO,
                    syncStatus = SyncStatus.PENDING)
            }
        }
        return list
    }

    companion object {
        fun MediaEntity.toUi() = MediaItem(
            id = id, uri = uri, displayName = displayName, dateTaken = dateTaken,
            size = size, width = width, height = height, duration = duration,
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
            remotePath = remotePath, isFavorite = isFavorite, faceGroupId = faceGroupId
        )
    }
}
