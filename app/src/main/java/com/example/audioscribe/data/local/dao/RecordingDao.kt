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

    @Query("SELECT * FROM recording_sessions ORDER BY createdAtMs DESC")
    fun observeAllSessions(): Flow<List<RecordingSessionEntity>>
}