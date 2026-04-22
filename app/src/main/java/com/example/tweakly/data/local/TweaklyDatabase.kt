package com.example.tweakly.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.tweakly.data.local.dao.MediaDao
import com.example.tweakly.data.local.entity.MediaEntity

@Database(entities = [MediaEntity::class], version = 1, exportSchema = false)
abstract class TweaklyDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
}
