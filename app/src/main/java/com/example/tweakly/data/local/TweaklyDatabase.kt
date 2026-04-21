package com.example.tweakly.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.tweakly.data.local.dao.PhotoDao
import com.example.tweakly.data.local.entity.PhotoEntity

@Database(
    entities = [PhotoEntity::class],
    version = 1,
    exportSchema = false
)
abstract class TweaklyDatabase : RoomDatabase() {
    abstract fun photoDao(): PhotoDao
}
