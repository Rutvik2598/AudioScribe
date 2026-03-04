package com.example.audioscribe.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recording_sessions")
data class RecordingSessionEntity(
    @PrimaryKey val sessionId: String,
    val createdAtMs: Long,
    val status: String // "RECORDING", "STOPPED", "PAUSED"
)
