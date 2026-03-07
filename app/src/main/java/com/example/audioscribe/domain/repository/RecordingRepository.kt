package com.example.audioscribe.domain.repository

import com.example.audioscribe.domain.entity.ChunkInfo
import com.example.audioscribe.domain.entity.RecordingSession
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

    fun setSilenceWarning(sessionId: String, detected: Boolean)

    fun observeSilenceWarning(sessionId: String): Flow<Boolean>

    suspend fun saveTranscriptionAndSummary(sessionId: String, transcription: String?, summary: String?)

    fun observeAllSessions(): Flow<List<RecordingSession>>

    fun observeSession(sessionId: String): Flow<RecordingSession?>

    suspend fun findActiveSession(): RecordingSession?

    suspend fun updateElapsedMs(sessionId: String, elapsedMs: Long)

    suspend fun updateChunkTranscription(sessionId: String, chunkIndex: Int, text: String)

    suspend fun getUntranscribedChunks(sessionId: String): List<ChunkInfo>

    suspend fun getTranscribedChunks(sessionId: String): List<Pair<Int, String>>
}