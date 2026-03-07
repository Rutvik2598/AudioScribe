package com.example.audioscribe.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.audioscribe.data.local.entity.AudioChunkEntity
import com.example.audioscribe.data.local.entity.RecordingSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: RecordingSessionEntity)

    @Insert
    suspend fun insertChunk(chunk: AudioChunkEntity): Long

    @Query("SELECT * FROM recording_sessions WHERE sessionId = :sessionId LIMIT 1")
    fun observeSession(sessionId: String): Flow<RecordingSessionEntity?>

    @Query("SELECT * FROM audio_chunks WHERE sessionId = :sessionId ORDER BY chunkIndex ASC")
    fun observeChunks(sessionId: String): Flow<List<AudioChunkEntity>>

    @Query("UPDATE recording_sessions SET transcription = :transcription, summary = :summary WHERE sessionId = :sessionId")
    suspend fun updateTranscriptionAndSummary(sessionId: String, transcription: String?, summary: String?)

    @Query("UPDATE recording_sessions SET status = :status WHERE sessionId = :sessionId")
    suspend fun updateSessionStatus(sessionId: String, status: String)

    @Query("UPDATE recording_sessions SET elapsedMs = :elapsedMs WHERE sessionId = :sessionId")
    suspend fun updateElapsedMs(sessionId: String, elapsedMs: Long)

    @Query("SELECT * FROM recording_sessions WHERE status IN ('RECORDING', 'PAUSED', 'PAUSED_PHONE_CALL', 'PAUSED_AUDIO_FOCUS') ORDER BY createdAtMs DESC LIMIT 1")
    suspend fun findActiveSession(): RecordingSessionEntity?

    @Query("UPDATE audio_chunks SET transcriptionText = :text WHERE sessionId = :sessionId AND chunkIndex = :chunkIndex")
    suspend fun updateChunkTranscription(sessionId: String, chunkIndex: Int, text: String)

    @Query("SELECT * FROM audio_chunks WHERE sessionId = :sessionId AND transcriptionText IS NULL ORDER BY chunkIndex ASC")
    suspend fun getUntranscribedChunks(sessionId: String): List<AudioChunkEntity>

    @Query("SELECT * FROM audio_chunks WHERE sessionId = :sessionId AND transcriptionText IS NOT NULL ORDER BY chunkIndex ASC")
    suspend fun getTranscribedChunks(sessionId: String): List<AudioChunkEntity>

    @Query("SELECT * FROM recording_sessions ORDER BY createdAtMs DESC")
    fun observeAllSessions(): Flow<List<RecordingSessionEntity>>
}