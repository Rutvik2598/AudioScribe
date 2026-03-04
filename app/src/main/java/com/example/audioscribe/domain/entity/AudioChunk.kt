package com.example.audioscribe.domain.entity

data class AudioChunk (
    val bytes: ByteArray,
    val chunkIndex: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val avgAmplitude: Int
)