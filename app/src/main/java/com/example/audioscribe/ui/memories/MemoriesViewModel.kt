package com.example.audioscribe.ui.memories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audioscribe.domain.entity.RecordingSession
import com.example.audioscribe.domain.repository.RecordingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MemoriesViewModel @Inject constructor(
    repository: RecordingRepository
) : ViewModel() {

    val sessions: StateFlow<List<RecordingSession>> =
        repository.observeAllSessions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
