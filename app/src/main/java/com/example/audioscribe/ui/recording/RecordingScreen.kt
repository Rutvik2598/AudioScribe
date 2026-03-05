package com.example.audioscribe.ui.recording

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun RecordingScreen(
    modifier: Modifier = Modifier,
    viewModel: RecordingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val timerText by viewModel.timeText.collectAsState()
    val transcription by viewModel.transcriptionText.collectAsState()
    val isTranscribing by viewModel.isTranscribing.collectAsState()
    val transcriptionError by viewModel.transcriptionError.collectAsState()
    val summary by viewModel.summaryText.collectAsState()
    val isSummarizing by viewModel.isSummarizing.collectAsState()
    val summaryError by viewModel.summaryError.collectAsState()
    val storageError by viewModel.storageError.collectAsState()

    val statusText = when(uiState) {
        RecordingUiState.RECORDING -> "Recording..."
        RecordingUiState.STOPPED -> "Stopped"
        RecordingUiState.IDLE -> "Idle"
        RecordingUiState.PAUSED -> "Paused"
        RecordingUiState.PAUSED_PHONE_CALL -> "Paused - Phone call"
        RecordingUiState.PAUSED_AUDIO_FOCUS -> "Paused - Audio focus lost"
    }

    // Tab state
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Transcription", "Summary")

    // Auto-scroll for transcription
    val transcriptionScrollState = rememberScrollState()
    LaunchedEffect(transcription) {
        transcriptionScrollState.animateScrollTo(transcriptionScrollState.maxValue)
    }

    // Auto-scroll for summary
    val summaryScrollState = rememberScrollState()
    LaunchedEffect(summary) {
        summaryScrollState.animateScrollTo(summaryScrollState.maxValue)
    }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Timer + status
            Text(
                text = timerText,
                style = MaterialTheme.typography.displayLarge
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium
            )

            // Storage error banner
            storageError?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Record / Stop button
            Button(
                onClick = {
                    when(uiState) {
                        RecordingUiState.RECORDING,
                        RecordingUiState.PAUSED,
                        RecordingUiState.PAUSED_PHONE_CALL,
                        RecordingUiState.PAUSED_AUDIO_FOCUS -> viewModel.stopRecording()
                        RecordingUiState.IDLE, RecordingUiState.STOPPED  -> viewModel.startRecording()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = when(uiState) {
                        RecordingUiState.RECORDING,
                        RecordingUiState.PAUSED,
                        RecordingUiState.PAUSED_PHONE_CALL,
                        RecordingUiState.PAUSED_AUDIO_FOCUS -> "Stop"
                        RecordingUiState.IDLE, RecordingUiState.STOPPED -> "Record"
                    },
                )
            }

            // Pause / Resume button
            if (uiState == RecordingUiState.RECORDING ||
                uiState == RecordingUiState.PAUSED ||
                uiState == RecordingUiState.PAUSED_AUDIO_FOCUS
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        when (uiState) {
                            RecordingUiState.RECORDING -> viewModel.pauseRecording()
                            RecordingUiState.PAUSED -> viewModel.resumeRecording()
                            RecordingUiState.PAUSED_AUDIO_FOCUS -> viewModel.resumeRecording()
                            else -> {}
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = when (uiState) {
                            RecordingUiState.RECORDING -> "Pause"
                            RecordingUiState.PAUSED -> "Resume"
                            RecordingUiState.PAUSED_AUDIO_FOCUS -> "Resume"
                            else -> ""
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Transcription / Summary
            val hasContent = transcription.isNotEmpty() || isTranscribing || transcriptionError != null
                    || summary.isNotEmpty() || isSummarizing || summaryError != null

            if (hasContent) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 2.dp
                ) {
                    when (selectedTab) {
                        // Transcription tab
                        0 -> Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .verticalScroll(transcriptionScrollState)
                        ) {
                            if (transcription.isNotEmpty()) {
                                Text(
                                    text = transcription,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                            if (isTranscribing) {
                                Spacer(modifier = Modifier.height(8.dp))
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                            transcriptionError?.let { error ->
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = error,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        // Summary tab
                        1 -> Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .verticalScroll(summaryScrollState)
                        ) {
                            if (summary.isNotEmpty()) {
                                Text(
                                    text = summary,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                            if (isSummarizing) {
                                Spacer(modifier = Modifier.height(8.dp))
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                            summaryError?.let { error ->
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = error,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            if (!isSummarizing && summary.isEmpty() && summaryError == null) {
                                Text(
                                    text = "Summary will appear here once transcription is available.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}