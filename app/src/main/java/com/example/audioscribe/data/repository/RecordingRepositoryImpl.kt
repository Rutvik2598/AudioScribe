package com.example.audioscribe.data.repository

import android.content.Context
import com.example.audioscribe.data.local.dao.RecordingDao
import com.example.audioscribe.data.local.entity.AudioChunkEntity
import com.example.audioscribe.data.local.entity.RecordingSessionEntity
import com.example.audioscribe.domain.entity.ChunkInfo
import com.example.audioscribe.domain.repository.RecordingRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject

class RecordingRepositoryImpl @Inject constructor(
    private val dao: RecordingDao,
    @ApplicationContext private val context: Context
): RecordingRepository {

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
        val session = RecordingSessionEntity(
            sessionId = sessionId,
            createdAtMs = System.currentTimeMillis(),
            status = status
        )
        dao.upsertSession(session)
    }

    override fun observeSessionStatus(sessionId: String): Flow<String> {
        return dao.observeSession(sessionId)
            .filterNotNull()
            .map { it.status }
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
}