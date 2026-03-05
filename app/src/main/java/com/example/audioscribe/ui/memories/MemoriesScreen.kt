package com.example.audioscribe.ui.memories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.audioscribe.domain.entity.RecordingSession
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.example.audioscribe.ui.theme.BackgroundColor
import com.example.audioscribe.ui.theme.TealDark
import com.example.audioscribe.ui.theme.TealLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoriesScreen(
    onBack: () -> Unit,
    onRecordingClick: (sessionId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MemoriesViewModel = hiltViewModel()
) {
    val sessions by viewModel.sessions.collectAsState()
    val grouped = groupByDate(sessions)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Memories",
                        fontWeight = FontWeight.Bold,
                        color = TealDark
                    )
                },
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
        containerColor = BackgroundColor,
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
        ) {
            // Notes filter chip
            item {
                Spacer(modifier = Modifier.height(8.dp))
                FilterChip(
                    selected = true,
                    onClick = { },
                    label = { Text("Notes") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TealLight,
                        selectedLabelColor = TealDark
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (sessions.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Text(
                            text = "No recordings yet.",
                            modifier = Modifier.padding(20.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                    }
                }
            } else {
                grouped.forEach { (dateLabel, sessionsInGroup) ->
                    item {
                        Text(
                            text = dateLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    items(sessionsInGroup, key = { it.sessionId }) { session ->
                        MemoryItem(
                            session = session,
                            onClick = { onRecordingClick(session.sessionId) }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryItem(session: RecordingSession, onClick: () -> Unit) {
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.summary?.take(40)
                        ?: session.transcription?.take(40)
                        ?: "Untitled Meeting",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TealDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = timeFormat.format(Date(session.createdAtMs)) + " \u00B7 0m",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}

private fun groupByDate(sessions: List<RecordingSession>): List<Pair<String, List<RecordingSession>>> {
    val calendar = Calendar.getInstance()
    val today = calendar.clone() as Calendar

    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    return sessions.groupBy { session ->
        calendar.timeInMillis = session.createdAtMs
        when {
            isSameDay(calendar, today) -> "Today"
            else -> dateFormat.format(Date(session.createdAtMs))
        }
    }.toList()
}

private fun isSameDay(a: Calendar, b: Calendar): Boolean {
    return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}
