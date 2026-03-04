package com.example.audioscribe.domain.entity

/**
 * Lightweight projection of a stored audio chunk — just enough info
 * for the ViewModel to locate the file and order correctly.
 */
data class ChunkInfo(
    val chunkIndex: Int,
    val filePath: String
)
