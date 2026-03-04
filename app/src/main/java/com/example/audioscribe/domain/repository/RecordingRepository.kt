package com.example.audioscribe.domain.repository

import com.example.audioscribe.domain.entity.ChunkInfo
import kotlinx.coroutines.flow.Flow

interface RecordingRepository {
    suspend fun createSession(sessionId: String)

    suspend fun saveChunk(
        sessionId: String,
        chunkIndex: Int,
        startTimeMs: Long,
        endTimeMs: Long,
        avgAmplitude: Int,
        bytes: ByteArray
    )

    suspend fun updateSessionStatus(sessionId: String, status: String)

    fun observeSessionStatus(sessionId: String): Flow<String>

    fun observeChunks(sessionId: String): Flow<List<ChunkInfo>>
}