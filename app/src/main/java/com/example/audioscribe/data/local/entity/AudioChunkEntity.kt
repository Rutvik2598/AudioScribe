package com.example.audioscribe.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audio_chunks")
data class AudioChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val chunkIndex: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val avgAmplitude: Int,
    val filePath: String,
    val transcriptionText: String? = null
)
