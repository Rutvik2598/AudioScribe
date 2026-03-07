package com.example.audioscribe.domain

import com.example.audioscribe.domain.entity.AudioChunk
import com.example.audioscribe.domain.entity.AudioPacket

class AudioChunker(
    private val sampleRate: Int = 16000,
    private val channels: Int = 1,
    private val bytesPerSample: Int = 2,
    private val chunkDurationSec: Int = 30,
    private val overlapSec: Int = 2
) {

    private val bytesPerSecond = sampleRate * channels * bytesPerSample
    private val chunkSizeBytes = chunkDurationSec * bytesPerSecond
    private val overlapSizeBytes = overlapSec * bytesPerSecond

    private var chunkIndex = 0

    private var buffer = ByteArray(chunkSizeBytes)
    private var bufferWritePos = 0

    private var amplitudeSum = 0
    private var amplitudeCount = 0

    fun addPacket(packet: AudioPacket): AudioChunk? {
        val data = packet.bytes
        var offset = 0

        amplitudeSum += packet.amplitude
        amplitudeCount++

        while(offset < data.size) {
            val remaining = buffer.size - bufferWritePos
            val toCopy = minOf(remaining, data.size - offset)

            System.arraycopy(data, offset, buffer, bufferWritePos, toCopy)
            offset += toCopy
            bufferWritePos += toCopy

            if(bufferWritePos >= chunkSizeBytes) {
                val chunkBytes = buffer.copyOfRange(0, chunkSizeBytes)
                val avgAmp = if(amplitudeCount > 0) amplitudeSum / amplitudeCount else 0

                val endTime = System.currentTimeMillis()
                val startTime = endTime - (chunkDurationSec * 1000L)

                val chunk = AudioChunk(
                    bytes = chunkBytes,
                    chunkIndex = chunkIndex++,
                    startTimeMs = startTime,
                    endTimeMs = endTime,
                    avgAmplitude = avgAmp
                )

                val overlapStart = chunkSizeBytes - overlapSizeBytes
                val overlapBytes = buffer.copyOfRange(overlapStart, chunkSizeBytes)

                buffer = ByteArray(chunkSizeBytes)
                System.arraycopy(overlapBytes, 0, buffer, 0, overlapBytes.size)
                bufferWritePos = overlapBytes.size

                amplitudeSum = 0
                amplitudeCount = 0

                return chunk
            }
        }
        return null
    }

    /**
     * Call this when stopping recording to flush any remaining audio as a chunk (even if not a full chunk).
     * Returns null if buffer is empty.
     */
    fun flush(): AudioChunk? {
        if (bufferWritePos == 0) return null
        val chunkBytes = buffer.copyOfRange(0, bufferWritePos)
        val avgAmp = if (amplitudeCount > 0) amplitudeSum / amplitudeCount else 0
        val endTime = System.currentTimeMillis()
        val startTime = endTime - (bufferWritePos / bytesPerSecond * 1000L)
        val chunk = AudioChunk(
            bytes = chunkBytes,
            chunkIndex = chunkIndex++,
            startTimeMs = startTime,
            endTimeMs = endTime,
            avgAmplitude = avgAmp
        )
        buffer = ByteArray(chunkSizeBytes)
        bufferWritePos = 0
        amplitudeSum = 0
        amplitudeCount = 0
        return chunk
    }
}