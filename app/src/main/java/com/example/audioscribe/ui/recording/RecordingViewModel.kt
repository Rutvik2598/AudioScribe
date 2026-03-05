package com.example.audioscribe.ui.recording

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audioscribe.data.remote.GeminiTranscriptionService
import com.example.audioscribe.domain.PcmToWavConverter
import com.example.audioscribe.domain.StorageHelper
import com.example.audioscribe.domain.entity.ChunkInfo
import com.example.audioscribe.domain.repository.RecordingRepository
import com.example.audioscribe.service.RecordingForegroundService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

// Minimum PCM size to bother transcribing (3 seconds @ 16kHz, 16-bit, mono = 96,000 bytes)
// This prevents sending tiny overlap-only chunks or near-silent fragments to Gemini.
private const val MIN_PCM_BYTES_FOR_TRANSCRIPTION = 96_000

@HiltViewModel
class RecordingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recordingRepository: RecordingRepository,
    private val transcriptionService: GeminiTranscriptionService,
    private val storageHelper: StorageHelper
): ViewModel() {

    private val _uiState = MutableStateFlow(RecordingUiState.IDLE)
    val uiState: StateFlow<RecordingUiState> = _uiState

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _timerText = MutableStateFlow("00:00")
    val timeText: StateFlow<String> = _timerText

    // Transcription state
    /** Concatenated transcription shown in the UI. */
    private val _transcriptionText = MutableStateFlow("")
    val transcriptionText: StateFlow<String> = _transcriptionText

    /** True while a chunk is actively being sent to Gemini. */
    private val _isTranscribing = MutableStateFlow(false)
    val isTranscribing: StateFlow<Boolean> = _isTranscribing

    /** Non-null when the last transcription attempt failed. */
    private val _transcriptionError = MutableStateFlow<String?>(null)
    val transcriptionError: StateFlow<String?> = _transcriptionError

    // Summary state
    /** AI-generated summary of the transcription. */
    private val _summaryText = MutableStateFlow("")
    val summaryText: StateFlow<String> = _summaryText

    /** True while the summary is being generated. */
    private val _isSummarizing = MutableStateFlow(false)
    val isSummarizing: StateFlow<Boolean> = _isSummarizing

    /** Non-null when the last summary attempt failed. */
    private val _summaryError = MutableStateFlow<String?>(null)
    val summaryError: StateFlow<String?> = _summaryError

    /** Non-null when a storage-related error occurs. */
    private val _storageError = MutableStateFlow<String?>(null)
    val storageError: StateFlow<String?> = _storageError

    /** True when the microphone has been silent for too long. */
    private val _silenceWarning = MutableStateFlow(false)
    val silenceWarning: StateFlow<Boolean> = _silenceWarning

    private var summaryJob: Job? = null

    // Internal bookkeeping
    private val transcriptions = mutableMapOf<Int, String>()   // chunkIndex → text
    private var transcribedUpTo = -1                            // highest chunk index done

    private var timerJob: Job? = null
    private var startTimeMs: Long = 0L
    private var elapsedTimeBeforePause: Long = 0L
    private var sessionId: String? = null

    private var sessionObserverJob: Job? = null
    private var chunkObserverJob: Job? = null
    private var silenceObserverJob: Job? = null

    // Session-state observer
    fun observeSessionState(sessionId: String) {
        sessionObserverJob?.cancel()
        sessionObserverJob = viewModelScope.launch {
            recordingRepository.observeSessionStatus(sessionId).collect { status ->
                when (status) {
                    "RECORDING" -> {
                        _uiState.value = RecordingUiState.RECORDING
                        _isRecording.value = true
                        startTimer(resume = true)
                    }
                    "PAUSED" -> {
                        _uiState.value = RecordingUiState.PAUSED
                        _isRecording.value = false
                        stopTimer(storeElapsed = true)
                    }
                    "PAUSED_PHONE_CALL" -> {
                        _uiState.value = RecordingUiState.PAUSED_PHONE_CALL
                        _isRecording.value = false
                        stopTimer(storeElapsed = true)
                    }
                    "PAUSED_AUDIO_FOCUS" -> {
                        _uiState.value = RecordingUiState.PAUSED_AUDIO_FOCUS
                        _isRecording.value = false
                        stopTimer(storeElapsed = true)
                    }
                    "STOPPED" -> {
                        _uiState.value = RecordingUiState.STOPPED
                        _isRecording.value = false
                        stopTimer()
                        _timerText.value = "00:00"
                        persistTranscriptionAndSummary(sessionId)
                    }
                    "STOPPED_LOW_STORAGE" -> {
                        _uiState.value = RecordingUiState.STOPPED
                        _isRecording.value = false
                        stopTimer()
                        _storageError.value = "Recording stopped \u2013 Low storage"
                        persistTranscriptionAndSummary(sessionId)
                    }
                    else -> {
                        _uiState.value = RecordingUiState.IDLE
                        _isRecording.value = false
                        stopTimer()
                        _timerText.value = "00:00"
                    }
                }
            }
        }
    }

    // Silence warning observer
    private fun startSilenceObserver(sessionId: String) {
        silenceObserverJob?.cancel()
        silenceObserverJob = viewModelScope.launch {
            recordingRepository.observeSilenceWarning(sessionId).collect { detected ->
                _silenceWarning.value = detected
            }
        }
    }

    // Chunk observer + transcription pipeline
    private fun startChunkObserver(sessionId: String) {
        chunkObserverJob?.cancel()
        chunkObserverJob = viewModelScope.launch {
            recordingRepository.observeChunks(sessionId).collect { allChunks ->
                val sorted = allChunks.sortedBy { it.chunkIndex }
                val newChunks = sorted.filter { it.chunkIndex > transcribedUpTo }
                if (newChunks.isEmpty()) return@collect

                for (chunk in newChunks) {
                    val success = transcribeWithRetry(chunk)
                    if (!success) {
                        // Retry ALL chunks from the beginning
                        Log.w(TAG, "Chunk ${chunk.chunkIndex} failed – retrying ALL chunks")
                        retryAllChunks(sorted)
                        break   // retryAllChunks already processed everything
                    }
                }
            }
        }
    }

    /**
     * Attempt to transcribe a single chunk with up to [MAX_RETRY_ATTEMPTS] retries.
     * Returns true on success, false if all retries were exhausted.
     */
    private suspend fun transcribeWithRetry(chunk: ChunkInfo): Boolean {
        _isTranscribing.value = true
        _transcriptionError.value = null

        // Skip chunks that are too short (e.g. overlap-only fragments)
        val pcmFile = File(chunk.filePath)
        if (!pcmFile.exists() || pcmFile.length() < MIN_PCM_BYTES_FOR_TRANSCRIPTION) {
            Log.w(TAG, "Chunk ${chunk.chunkIndex} skipped – too short (${pcmFile.length()} bytes)")
            transcribedUpTo = chunk.chunkIndex
            _isTranscribing.value = false
            return true  // not a failure, just nothing to transcribe
        }

        var attempt = 0
        while (attempt < MAX_RETRY_ATTEMPTS) {
            try {
                val pcmBytes = pcmFile.readBytes()
                val wavBytes = PcmToWavConverter.convert(pcmBytes)
                Log.d(TAG, "Sending chunk ${chunk.chunkIndex} to Gemini (${pcmBytes.size} PCM bytes)")
                val text = "Mocked transcription"//transcriptionService.transcribe(wavBytes)
                Log.d(TAG, "Chunk ${chunk.chunkIndex} result: \"$text\"")

                // Only store meaningful transcription (skip empty / whitespace-only results)
                if (text.isNotBlank()) {
                    transcriptions[chunk.chunkIndex] = text
                }
                transcribedUpTo = chunk.chunkIndex
                rebuildTranscriptionText()
                _isTranscribing.value = false
                return true
            } catch (e: Exception) {
                attempt++
                Log.e(TAG, "Transcription attempt $attempt failed for chunk ${chunk.chunkIndex}", e)
                if (attempt < MAX_RETRY_ATTEMPTS) delay(RETRY_BACKOFF_MS * attempt)
            }
        }
        _isTranscribing.value = false
        return false
    }

    /**
     * Clear all transcription results and re-transcribe every chunk from scratch.
     */
    private suspend fun retryAllChunks(allChunks: List<ChunkInfo>) {
        transcriptions.clear()
        transcribedUpTo = -1
        rebuildTranscriptionText()

        for (chunk in allChunks.sortedBy { it.chunkIndex }) {
            val success = transcribeWithRetry(chunk)
            if (!success) {
                _transcriptionError.value =
                    "Transcription failed after retries. Check your network and API key."
                return
            }
        }
        _transcriptionError.value = null
    }

    private fun rebuildTranscriptionText() {
        _transcriptionText.value = transcriptions.entries
            .sortedBy { it.key }
            .joinToString(" ") { it.value }

        // Trigger summary generation whenever transcription text changes
        val currentText = _transcriptionText.value
        if (currentText.isNotBlank()) {
            generateSummary(currentText)
        }
    }

    // Summary generation
    private fun generateSummary(fullTranscription: String) {
        summaryJob?.cancel()
        summaryJob = viewModelScope.launch {
            _isSummarizing.value = true
            _summaryError.value = null
            try {
                val summary = "Mocked summary" //transcriptionService.summarize(fullTranscription)
                _summaryText.value = summary
            } catch (e: Exception) {
                Log.e(TAG, "Summary generation failed", e)
                _summaryError.value = "Failed to generate summary."
            } finally {
                _isSummarizing.value = false
            }
        }
    }

    private fun persistTranscriptionAndSummary(sessionId: String) {
        viewModelScope.launch {
            val transcription = _transcriptionText.value.takeIf { it.isNotBlank() }
            val summary = _summaryText.value.takeIf { it.isNotBlank() }
            recordingRepository.saveTranscriptionAndSummary(sessionId, transcription, summary)
        }
    }

    // Recording controls
    @RequiresApi(Build.VERSION_CODES.O)
    fun startRecording() {
        if(_isRecording.value) return

        // Check storage before starting
        if (!storageHelper.hasEnoughStorageToStart(context)) {
            _storageError.value = "Cannot start recording \u2013 Low storage"
            return
        }

        // Reset transcription state for a new session
        transcriptions.clear()
        transcribedUpTo = -1
        _transcriptionText.value = ""
        _transcriptionError.value = null
        _summaryText.value = ""
        _summaryError.value = null
        _storageError.value = null
        _silenceWarning.value = false
        summaryJob?.cancel()

        sessionId = UUID.randomUUID().toString()
        observeSessionState(sessionId!!)
        startChunkObserver(sessionId!!)
        startSilenceObserver(sessionId!!)

        val intent = Intent(context, RecordingForegroundService::class.java).apply {
            action = RecordingForegroundService.ACTION_START
            putExtra(RecordingForegroundService.EXTRA_SESSION_ID, sessionId)
        }
        context.startForegroundService(intent)
    }

    fun stopRecording() {
        if (_uiState.value == RecordingUiState.IDLE || _uiState.value == RecordingUiState.STOPPED) return
        val intent = Intent(context, RecordingForegroundService::class.java).apply {
            action = RecordingForegroundService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun pauseRecording() {
        if (sessionId == null || _uiState.value != RecordingUiState.RECORDING) return
        val intent = Intent(context, RecordingForegroundService::class.java).apply {
            action = RecordingForegroundService.ACTION_PAUSE
            putExtra(RecordingForegroundService.EXTRA_SESSION_ID, sessionId)
        }
        context.startService(intent)
    }

    fun resumeRecording() {
        if (sessionId == null || _uiState.value != RecordingUiState.PAUSED) return
        val intent = Intent(context, RecordingForegroundService::class.java).apply {
            action = RecordingForegroundService.ACTION_RESUME
            putExtra(RecordingForegroundService.EXTRA_SESSION_ID, sessionId)
        }
        context.startService(intent)
    }

    // Timer
    private fun startTimer(resume: Boolean = false) {
        if (!resume) {
            elapsedTimeBeforePause = 0L
        }
        startTimeMs = System.currentTimeMillis()
        timerJob?.cancel()

        timerJob = viewModelScope.launch {
            while(true) {
                val elapsedTimeMs = elapsedTimeBeforePause + ((System.currentTimeMillis() - startTimeMs) / 1000)
                val mm = elapsedTimeMs / 60
                val ss = elapsedTimeMs % 60
                _timerText.value = "%02d:%02d".format(mm, ss)
                delay(1000)
            }
        }
    }

    private fun stopTimer(storeElapsed: Boolean = false) {
        if (storeElapsed) {
            elapsedTimeBeforePause += ((System.currentTimeMillis() - startTimeMs) / 1000)
        } else {
            elapsedTimeBeforePause = 0L
        }
        timerJob?.cancel()
        timerJob = null
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        chunkObserverJob?.cancel()
        sessionObserverJob?.cancel()
        silenceObserverJob?.cancel()
        summaryJob?.cancel()
    }

    private companion object {
        const val TAG = "Recording ViewModel"
        const val MAX_RETRY_ATTEMPTS = 3
        const val RETRY_BACKOFF_MS = 2_000L
    }
}

enum class RecordingUiState {
    IDLE,
    RECORDING,
    PAUSED,
    PAUSED_PHONE_CALL,
    PAUSED_AUDIO_FOCUS,
    STOPPED
}