package com.example.tweakly.data.local.dao

import androidx.room.*
import com.example.tweakly.data.local.entity.PhotoEntity
import com.example.tweakly.data.local.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {

    @Query("SELECT * FROM photos ORDER BY dateTaken DESC")
    fun getAllPhotos(): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE id = :id")
    suspend fun getById(id: Long): PhotoEntity?

    @Query("SELECT * FROM photos WHERE syncStatus = :status")
    suspend fun getByStatus(status: SyncStatus): List<PhotoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(photos: List<PhotoEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(photo: PhotoEntity)

    @Query("UPDATE photos SET syncStatus = :status, remotePath = :remotePath, lastUpdated = :time WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, status: SyncStatus, remotePath: String? = null, time: Long = System.currentTimeMillis())

    @Query("DELETE FROM photos WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM photos")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM photos WHERE syncStatus = 'SYNCED'")
    fun getSyncedCount(): Flow<Int>
}
