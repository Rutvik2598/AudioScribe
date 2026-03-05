package com.example.audioscribe.ui.recording

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.audioscribe.ui.theme.BackgroundColor
import com.example.audioscribe.ui.theme.OrangeAccent
import com.example.audioscribe.ui.theme.TealDark
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun RecordingScreen(
    onBack: () -> Unit,
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
    val silenceWarning by viewModel.silenceWarning.collectAsState()

    val isRecording = uiState == RecordingUiState.RECORDING ||
            uiState == RecordingUiState.PAUSED ||
            uiState == RecordingUiState.PAUSED_PHONE_CALL ||
            uiState == RecordingUiState.PAUSED_AUDIO_FOCUS

    // Tab state
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Transcript", "Summary")

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TealDark
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundColor)
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                if (isRecording) {
                    // Stop button — pill with timer and stop icon
                    Button(
                        onClick = { viewModel.stopRecording() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TealDark)
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(
                                id = android.R.drawable.ic_media_pause
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = timerText,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "\u25A0  Stop",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    // Play button
                    Button(
                        onClick = { viewModel.startRecording() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TealDark)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Start Recording",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        },
        containerColor = BackgroundColor,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
        ) {
            // Title
            Text(
                text = if (isRecording) "I'm listening and taking notes..."
                else "Start capturing by pressing play button",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                fontStyle = if (isRecording) FontStyle.Italic else FontStyle.Normal,
                color = TealDark
            )

            // Subtitle with date/time when recording
            if (isRecording) {
                Spacer(modifier = Modifier.height(4.dp))
                val dateFormat = SimpleDateFormat("MMM dd \u2022 h:mm a", Locale.getDefault())
                Text(
                    text = dateFormat.format(Date()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            }

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

            // Silence warning banner
            if (silenceWarning) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = "No audio detected \u2013 Check microphone",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Transcript / Summary tabs — always visible
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column {
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.White,
                        contentColor = TealDark,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = OrangeAccent
                            )
                        }
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = {
                                    Text(
                                        text = title,
                                        fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selectedTab == index) TealDark else Color.Gray
                                    )
                                }
                            )
                        }
                    }

                    when (selectedTab) {
                        // Transcript tab
                        0 -> Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp)
                                .verticalScroll(transcriptionScrollState)
                        ) {
                            if (transcription.isNotEmpty()) {
                                Text(
                                    text = transcription,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TealDark
                                )
                            }
                            if (isTranscribing) {
                                Spacer(modifier = Modifier.height(8.dp))
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = TealDark
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
                            if (!isTranscribing && transcription.isEmpty() && transcriptionError == null) {
                                Text(
                                    text = "The transcript will update every 60s, it will appear here automatically as you speak",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Gray
                                )
                            }
                        }

                        // Summary tab
                        1 -> Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp)
                                .verticalScroll(summaryScrollState)
                        ) {
                            if (summary.isNotEmpty()) {
                                Text(
                                    text = summary,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TealDark
                                )
                            }
                            if (isSummarizing) {
                                Spacer(modifier = Modifier.height(8.dp))
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = TealDark
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
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}