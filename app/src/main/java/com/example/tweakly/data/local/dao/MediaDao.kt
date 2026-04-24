package com.example.tweakly.data.local.dao

import androidx.room.*
import com.example.tweakly.data.local.entity.DbMediaType
import com.example.tweakly.data.local.entity.MediaEntity
import com.example.tweakly.data.local.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {

    @Query("SELECT * FROM media_items ORDER BY dateTaken DESC")
    fun getAll(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media_items ORDER BY dateTaken ASC")
    fun getAllAscDate(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media_items ORDER BY size DESC")
    fun getAllBySize(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media_items ORDER BY displayName ASC")
    fun getAllByName(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media_items WHERE mediaType = :type ORDER BY dateTaken DESC")
    fun getByType(type: DbMediaType): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media_items WHERE isFavorite = 1 ORDER BY dateTaken DESC")
    fun getFavorites(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): MediaEntity?

    @Query("SELECT * FROM media_items WHERE syncStatus = :status")
    suspend fun getByStatus(status: SyncStatus): List<MediaEntity>

    @Query("SELECT * FROM media_items WHERE faceGroupId = :groupId ORDER BY dateTaken DESC")
    fun getByFaceGroup(groupId: String): Flow<List<MediaEntity>>

    @Query("SELECT DISTINCT faceGroupId FROM media_items WHERE faceGroupId IS NOT NULL")
    fun getFaceGroups(): Flow<List<String>>

    @Query("SELECT * FROM media_items WHERE displayName LIKE '%' || :query || '%' ORDER BY dateTaken DESC")
    suspend fun searchByName(query: String): List<MediaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MediaEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: MediaEntity)

    @Query("UPDATE media_items SET syncStatus=:status, remotePath=:remotePath, lastUpdated=:time WHERE id=:id")
    suspend fun updateSync(id: Long, status: SyncStatus, remotePath: String? = null,
                           time: Long = System.currentTimeMillis())

    @Query("UPDATE media_items SET isFavorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: Long, fav: Boolean)

    @Query("UPDATE media_items SET faceGroupId = :groupId WHERE id = :id")
    suspend fun setFaceGroup(id: Long, groupId: String?)

    @Query("DELETE FROM media_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM media_items WHERE syncStatus = 'SYNCED'")
    suspend fun getAllSynced(): List<MediaEntity>

    @Query("SELECT COUNT(*) FROM media_items WHERE syncStatus = 'SYNCED'")
    fun syncedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM media_items WHERE syncStatus = 'PENDING'")
    fun pendingCount(): Flow<Int>
}
