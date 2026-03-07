package com.example.audioscribe.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.audioscribe.data.local.dao.RecordingDao
import com.example.audioscribe.data.local.entity.AudioChunkEntity
import com.example.audioscribe.data.local.entity.RecordingSessionEntity

@Database(
    entities = [RecordingSessionEntity::class, AudioChunkEntity::class],
    version = 3
)
abstract class AppDatabase: RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
}