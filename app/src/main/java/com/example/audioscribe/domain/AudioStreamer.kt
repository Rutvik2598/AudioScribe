package com.example.audioscribe.domain

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.example.audioscribe.domain.entity.AudioPacket
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import javax.inject.Inject
import kotlin.math.sqrt

class AudioStreamer @Inject constructor() {

    // Stream audio data from the microphone
    fun startStream(): Flow<AudioPacket> = flow {
        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )

        val sizeInBytes = maxOf(minBufferSize, BUFFER_SIZE)
        val dataBuffer = ByteArray(sizeInBytes)

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            sizeInBytes
        )

        try {
            audioRecord.startRecording()

            while(currentCoroutineContext().isActive) {
                val readResult = audioRecord.read(dataBuffer, 0, dataBuffer.size)

                if(readResult > 0) {
                    val amplitude = calculateAmplitude(dataBuffer, readResult)
                    emit(AudioPacket(dataBuffer.copyOf(readResult), amplitude))
                }

            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            audioRecord.stop()
            audioRecord.release()
        }
    }

    // Calculate the amplitude of the audio data
    private fun calculateAmplitude(buffer: ByteArray, size: Int): Int {
        var sum = 0.0
        for(i in 0 until size step 2) {
            val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
            sum += sample * sample
        }
        return if(size > 0) sqrt(sum / (size / 2)).toInt() else 0
    }


    private companion object {
        const val SAMPLE_RATE = 16000
        const val BUFFER_SIZE = 3200
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }
}