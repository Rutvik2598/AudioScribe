package com.example.audioscribe.domain

import android.content.Context
import android.os.StatFs
import javax.inject.Inject

/**
 * Checks available internal storage to prevent recording when space is critically low.
 *
 * Audio format: 16 kHz, 16-bit, mono → 32,000 bytes/second → ~1.92 MB per minute.
 */
class StorageHelper @Inject constructor() {

    companion object {
        // 1 minute of PCM audio at 16 kHz, 16-bit, mono
        private const val BYTES_PER_SECOND = 16_000 * 2 * 1  // 32,000
        private const val ONE_MINUTE_BYTES = BYTES_PER_SECOND * 60L  // ~1.92 MB

        /** Minimum free space required to START a recording (1 min worth). */
        const val MIN_STORAGE_TO_START = ONE_MINUTE_BYTES

        /** When free space drops below this during recording, stop gracefully (10 sec buffer). */
        const val MIN_STORAGE_DURING_RECORDING = BYTES_PER_SECOND * 10L  // ~320 KB
    }

    /** Returns the number of available bytes on the internal storage. */
    fun getAvailableBytes(context: Context): Long {
        val stat = StatFs(context.filesDir.absolutePath)
        return stat.availableBlocksLong * stat.blockSizeLong
    }

    /** True if there is enough space to start a new recording (~1 minute). */
    fun hasEnoughStorageToStart(context: Context): Boolean {
        return getAvailableBytes(context) >= MIN_STORAGE_TO_START
    }

    /** True if storage is critically low and recording should be stopped. */
    fun isStorageCriticallyLow(context: Context): Boolean {
        return getAvailableBytes(context) < MIN_STORAGE_DURING_RECORDING
    }
}
