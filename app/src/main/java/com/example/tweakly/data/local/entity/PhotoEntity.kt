package com.example.tweakly.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class SyncStatus { PENDING, SYNCED, FAILED }

@Entity(tableName = "photos")
data class PhotoEntity(
    @PrimaryKey val id: Long,
    val uri: String,
    val displayName: String,
    val dateTaken: Long,
    val size: Long,
    val width: Int,
    val height: Int,
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val remotePath: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()
)
