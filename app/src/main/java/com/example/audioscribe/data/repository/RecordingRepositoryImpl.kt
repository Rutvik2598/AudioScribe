package com.example.audioscribe.data.repository

import android.content.Context
import com.example.audioscribe.data.local.dao.RecordingDao
import com.example.audioscribe.data.local.entity.AudioChunkEntity
import com.example.audioscribe.data.local.entity.RecordingSessionEntity
import com.example.audioscribe.domain.entity.ChunkInfo
import com.example.audioscribe.domain.entity.RecordingSession
import com.example.audioscribe.domain.repository.RecordingRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject

class RecordingRepositoryImpl @Inject constructor(
    private val dao: RecordingDao,
    @ApplicationContext private val context: Context
): RecordingRepository {

    private val silenceWarnings = mutableMapOf<String, MutableStateFlow<Boolean>>()

    override suspend fun createSession(sessionId: String) {
        dao.upsertSession(
            RecordingSessionEntity(
                sessionId = sessionId,
                createdAtMs = System.currentTimeMillis(),
                status = "RECORDING"
            )
        )
    }

    override suspend fun updateSessionStatus(sessionId: String, status: String) {
        dao.updateSessionStatus(sessionId, status)
    }

    override fun observeSessionStatus(sessionId: String): Flow<String> {
        return dao.observeSession(sessionId)
            .filterNotNull()
            .map { it.status }
            .distinctUntilChanged()
    }

    override suspend fun saveChunk(
        sessionId: String,
        chunkIndex: Int,
        startTimeMs: Long,
        endTimeMs: Long,
        avgAmplitude: Int,
        bytes: ByteArray
    ) {
        val dir = File(context.filesDir, "recordings/$sessionId")
        dir.mkdirs()

        val file = File(dir, "chunk_${chunkIndex.toString().padStart(4, '0')}.pcm")
        file.writeBytes(bytes)

        dao.insertChunk(
            AudioChunkEntity(
                sessionId = sessionId,
                chunkIndex = chunkIndex,
                startTimeMs = startTimeMs,
                endTimeMs = endTimeMs,
                avgAmplitude = avgAmplitude,
                filePath = file.absolutePath
            )
        )
    }

    override fun observeChunks(sessionId: String): Flow<List<ChunkInfo>> {
        return dao.observeChunks(sessionId).map { entities ->
            entities.map { ChunkInfo(chunkIndex = it.chunkIndex, filePath = it.filePath) }
        }
    }

    override fun setSilenceWarning(sessionId: String, detected: Boolean) {
        silenceWarnings.getOrPut(sessionId) { MutableStateFlow(false) }.value = detected
    }

    override fun observeSilenceWarning(sessionId: String): Flow<Boolean> {
        return silenceWarnings.getOrPut(sessionId) { MutableStateFlow(false) }
    }

    override suspend fun saveTranscriptionAndSummary(sessionId: String, transcription: String?, summary: String?) {
        dao.updateTranscriptionAndSummary(sessionId, transcription, summary)
    }

    override fun observeAllSessions(): Flow<List<RecordingSession>> {
        return dao.observeAllSessions().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeSession(sessionId: String): Flow<RecordingSession?> {
        return dao.observeSession(sessionId).map { it?.toDomain() }
    }

    override suspend fun findActiveSession(): RecordingSession? {
        return dao.findActiveSession()?.toDomain()
    }

    override suspend fun updateElapsedMs(sessionId: String, elapsedMs: Long) {
        dao.updateElapsedMs(sessionId, elapsedMs)
    }

    override suspend fun updateChunkTranscription(sessionId: String, chunkIndex: Int, text: String) {
        dao.updateChunkTranscription(sessionId, chunkIndex, text)
    }

    override suspend fun getUntranscribedChunks(sessionId: String): List<ChunkInfo> {
        return dao.getUntranscribedChunks(sessionId).map {
            ChunkInfo(chunkIndex = it.chunkIndex, filePath = it.filePath)
        }
    }

    override suspend fun getTranscribedChunks(sessionId: String): List<Pair<Int, String>> {
        return dao.getTranscribedChunks(sessionId).map {
            it.chunkIndex to (it.transcriptionText ?: "")
        }
    }

    private fun RecordingSessionEntity.toDomain() = RecordingSession(
        sessionId = sessionId,
        createdAtMs = createdAtMs,
        status = status,
        transcription = transcription,
        summary = summary,
        elapsedMs = elapsedMs
    )
}