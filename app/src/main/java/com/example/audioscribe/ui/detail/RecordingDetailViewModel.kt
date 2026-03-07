package com.example.audioscribe.ui.detail

import androidx.lifecycle.SavedStateHandle
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
class RecordingDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: RecordingRepository
) : ViewModel() {

    private val sessionId: String = checkNotNull(savedStateHandle["sessionId"])

    val session: StateFlow<RecordingSession?> =
        repository.observeSession(sessionId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
