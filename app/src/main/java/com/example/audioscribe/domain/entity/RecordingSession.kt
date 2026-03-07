package com.example.audioscribe.domain.entity

data class RecordingSession(
    val sessionId: String,
    val createdAtMs: Long,
    val status: String,
    val transcription: String?,
    val summary: String?,
    val elapsedMs: Long = 0
)
