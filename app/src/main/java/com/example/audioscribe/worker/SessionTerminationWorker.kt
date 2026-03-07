package com.example.audioscribe.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.audioscribe.data.remote.GeminiTranscriptionService
import com.example.audioscribe.domain.PcmToWavConverter
import com.example.audioscribe.domain.repository.RecordingRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File

/**
 * Finalizes an orphaned recording session after process death.
 * Transcribes any untranscribed chunks and marks the session as STOPPED.
 */
@HiltWorker
class SessionTerminationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val recordingRepository: RecordingRepository,
    private val transcriptionService: GeminiTranscriptionService
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sessionId = inputData.getString(KEY_SESSION_ID) ?: return Result.failure()
        Log.d(TAG, "Finalizing orphaned session: $sessionId")

        // Transcribe any remaining chunks
        val untranscribed = recordingRepository.getUntranscribedChunks(sessionId)
        Log.d(TAG, "Found ${untranscribed.size} untranscribed chunks")

        for (chunk in untranscribed) {
            val pcmFile = File(chunk.filePath)
            if (!pcmFile.exists() || pcmFile.length() < MIN_PCM_BYTES) {
                Log.w(TAG, "Chunk ${chunk.chunkIndex} skipped (missing or too short)")
                continue
            }
            try {
                val pcmBytes = pcmFile.readBytes()
                val wavBytes = PcmToWavConverter.convert(pcmBytes)
                val text = transcriptionService.transcribe(wavBytes)
                if (text.isNotBlank()) {
                    recordingRepository.updateChunkTranscription(sessionId, chunk.chunkIndex, text)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to transcribe chunk ${chunk.chunkIndex}", e)
                return Result.retry()
            }
        }

        // Rebuild the full transcription from all transcribed chunks
        val allTranscribed = recordingRepository.getTranscribedChunks(sessionId)
        val fullTranscription = allTranscribed
            .sortedBy { it.first }
            .joinToString(" ") { it.second }
            .takeIf { it.isNotBlank() }

        // Generate summary if we have transcription
        var summary: String? = null
        if (fullTranscription != null) {
            try {
                summary = transcriptionService.summarize(fullTranscription)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate summary", e)
            }
        }

        // Persist final results and mark session as stopped
        recordingRepository.saveTranscriptionAndSummary(sessionId, fullTranscription, summary)
        recordingRepository.updateSessionStatus(sessionId, "STOPPED")
        Log.d(TAG, "Session $sessionId finalized successfully")

        return Result.success()
    }

    companion object {
        const val KEY_SESSION_ID = "session_id"
        private const val TAG = "SessionTerminationWork"
        private const val MIN_PCM_BYTES = 96_000L
    }
}
