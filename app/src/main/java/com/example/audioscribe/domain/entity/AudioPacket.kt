package com.example.audioscribe.domain.entity

data class AudioPacket(
    val bytes: ByteArray,
    val amplitude: Int
)
