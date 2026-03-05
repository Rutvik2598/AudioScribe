package com.example.audioscribe.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.core.app.ServiceCompat
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import com.example.audioscribe.domain.AudioChunker
import com.example.audioscribe.domain.AudioStreamer
import com.example.audioscribe.domain.StorageHelper
import com.example.audioscribe.domain.repository.RecordingRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.UUID
import javax.inject.Inject

@RequiresApi(Build.VERSION_CODES.O)
@AndroidEntryPoint
class RecordingForegroundService: Service() {

    @Inject lateinit var audioStreamer: AudioStreamer
    @Inject lateinit var recordingRepository: RecordingRepository
    @Inject lateinit var storageHelper: StorageHelper

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recordingJob: Job? = null
    private var storageMonitorJob: Job? = null

    private var wakeLock: PowerManager.WakeLock? = null
    private var telephonyManager: TelephonyManager? = null
    private var phoneStateListener: PhoneStateListener? = null
    private var phoneStateCallback: TelephonyCallback? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var wasRecordingBeforePhoneCall = false
    private var isPausedByPhoneCall = false
    private var wasRecordingBeforeAudioFocusLoss = false
    private var isPausedByAudioFocusLoss = false

    private lateinit var sessionId: String

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        audioManager = getSystemService(AUDIO_SERVICE) as? AudioManager
        registerPhoneStateListener()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when(intent?.action) {
            ACTION_START -> startRecording(intent)
            ACTION_STOP -> stopRecording()
            ACTION_PAUSE -> pauseRecording()
            ACTION_RESUME -> resumeRecording()
        }
        return START_STICKY
    }

    private fun pauseRecording() {
        pauseRecordingInternal(
            sessionStatus = "PAUSED",
            notificationStatus = "Paused"
        )
    }

    private fun pauseRecordingForPhoneCall() {
        wasRecordingBeforePhoneCall = true
        isPausedByPhoneCall = true
        pauseRecordingInternal(
            sessionStatus = STATUS_PAUSED_PHONE_CALL,
            notificationStatus = "Paused - Phone call"
        )
    }

    private fun pauseRecordingForAudioFocusLoss() {
        wasRecordingBeforeAudioFocusLoss = true
        isPausedByAudioFocusLoss = true
        pauseRecordingInternal(
            sessionStatus = STATUS_PAUSED_AUDIO_FOCUS,
            notificationStatus = "Paused - Audio focus lost",
            showResumeAction = true
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun resumeRecording() {
        if (recordingJob != null) return

        if (!requestAudioFocus()) {
            isPausedByAudioFocusLoss = true
            wasRecordingBeforeAudioFocusLoss = true
            serviceScope.launch {
                updateStatusAndNotification(
                    status = STATUS_PAUSED_AUDIO_FOCUS,
                    notificationText = "Paused - Audio focus lost",
                    showResumeAction = true
                )
            }
            return
        }

        serviceScope.launch {
            updateStatusAndNotification(
                status = "RECORDING",
                notificationText = "Recording..."
            )
            startRecordingPipeline()
            isPausedByPhoneCall = false
            isPausedByAudioFocusLoss = false
            wasRecordingBeforePhoneCall = false
            wasRecordingBeforeAudioFocusLoss = false
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun pauseRecordingInternal(
        sessionStatus: String,
        notificationStatus: String,
        showResumeAction: Boolean = false
    ) {
        if (recordingJob == null) return
        stopRecordingPipeline(flushChunk = false)
        abandonAudioFocus()
        serviceScope.launch {
            updateStatusAndNotification(
                status = sessionStatus,
                notificationText = notificationStatus,
                showResumeAction = showResumeAction
            )
        }
    }

    private suspend fun updateStatusAndNotification(
        status: String,
        notificationText: String,
        showResumeAction: Boolean = false
    ) {
        recordingRepository.updateSessionStatus(sessionId, status)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(
            NOTIF_ID,
            buildNotification(notificationText, showResumeAction = showResumeAction)
        )
    }

    private fun startRecordingPipeline() {
        recordingJob = serviceScope.launch {
            chunker = AudioChunker()
            audioStreamer.startStream().collect { packet ->
                val chunk = chunker?.addPacket(packet)
                if (chunk != null) {
                    recordingRepository.saveChunk(
                        sessionId,
                        chunk.chunkIndex,
                        chunk.startTimeMs,
                        chunk.endTimeMs,
                        chunk.avgAmplitude,
                        chunk.bytes
                    )
                }
            }
        }
        startStorageMonitor()
    }

    private fun stopRecordingPipeline(flushChunk: Boolean) {
        storageMonitorJob?.cancel()
        storageMonitorJob = null
        recordingJob?.cancel()
        recordingJob = null
        if (flushChunk) {
            chunker?.let {
                val lastChunk = it.flush()
                if (lastChunk != null) {
                    runBlocking {
                        recordingRepository.saveChunk(
                            sessionId,
                            lastChunk.chunkIndex,
                            lastChunk.startTimeMs,
                            lastChunk.endTimeMs,
                            lastChunk.avgAmplitude,
                            lastChunk.bytes
                        )
                    }
                }
            }
        }
        chunker = null
    }

    /**
     * Periodically checks available storage while recording.
     * If storage is critically low, stops the recording gracefully.
     */
    private fun startStorageMonitor() {
        storageMonitorJob?.cancel()
        storageMonitorJob = serviceScope.launch {
            while (true) {
                delay(STORAGE_CHECK_INTERVAL_MS)
                if (storageHelper.isStorageCriticallyLow(this@RecordingForegroundService)) {
                    Log.w("RecordingStorage", "Storage critically low – stopping recording")
                    stopRecordingLowStorage()
                    break
                }
            }
        }
    }

    /**
     * Stops recording because of low storage.
     * Flushes current chunk, sets session status to STOPPED_LOW_STORAGE.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun stopRecordingLowStorage() {
        stopRecordingPipeline(flushChunk = true)
        abandonAudioFocus()
        isPausedByPhoneCall = false
        wasRecordingBeforePhoneCall = false
        isPausedByAudioFocusLoss = false
        wasRecordingBeforeAudioFocusLoss = false

        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null

        runBlocking {
            recordingRepository.updateSessionStatus(sessionId, STATUS_STOPPED_LOW_STORAGE)
        }

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIF_ID, buildNotification("Stopped – Low storage"))

        stopForeground(true)
        stopSelf()
    }

    override fun onBind(p0: Intent?): IBinder? {
        TODO("Not yet implemented")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onDestroy() {
        storageMonitorJob?.cancel()
        unregisterPhoneStateListener()
        abandonAudioFocus()
        super.onDestroy()
        serviceScope.cancel()
    }

    @SuppressLint("ForegroundServiceType")
    private var chunker: AudioChunker? = null

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startRecording(intent: Intent?) {
        if(recordingJob != null) return

        // Get sessionId from Intent
        sessionId = intent?.getStringExtra(EXTRA_SESSION_ID).toString()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "RecordingForegroundService::wakeLock"
        )
        wakeLock?.acquire(1 * 60 * 60 * 1000L /*1 hour*/)


        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            buildNotification("Recording..."),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                0
            },
        )

        serviceScope.launch {
            recordingRepository.createSession(sessionId)

            if (requestAudioFocus()) {
                updateStatusAndNotification(
                    status = "RECORDING",
                    notificationText = "Recording..."
                )
                startRecordingPipeline()
                isPausedByPhoneCall = false
                isPausedByAudioFocusLoss = false
                wasRecordingBeforePhoneCall = false
                wasRecordingBeforeAudioFocusLoss = false
            } else {
                isPausedByAudioFocusLoss = true
                wasRecordingBeforeAudioFocusLoss = true
                updateStatusAndNotification(
                    status = STATUS_PAUSED_AUDIO_FOCUS,
                    notificationText = "Paused - Audio focus lost",
                    showResumeAction = true
                )
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun stopRecording() {
        stopRecordingPipeline(flushChunk = true)
        abandonAudioFocus()
        isPausedByPhoneCall = false
        wasRecordingBeforePhoneCall = false
        isPausedByAudioFocusLoss = false
        wasRecordingBeforeAudioFocusLoss = false

        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null

        // Update session status to STOPPED
        runBlocking {
            recordingRepository.updateSessionStatus(sessionId, "STOPPED")
        }

        // Update notification to show stopped status
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIF_ID, buildNotification("Stopped"))

        // Remove foreground status and stop service after a short delay
        stopForeground(true)
        stopSelf()
    }

    @Suppress("DEPRECATION")
    private fun registerPhoneStateListener() {
        telephonyManager = getSystemService(TELEPHONY_SERVICE) as? TelephonyManager
        val manager = telephonyManager
        if (manager == null) {
            Log.w(CALL_STATE_LOG_TAG, "TelephonyManager unavailable; call state listener not registered")
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        handleCallStateChanged(state)
                    }
                }
                phoneStateCallback = callback
                manager.registerTelephonyCallback(mainExecutor, callback)
            } else {
                val listener = object : PhoneStateListener() {
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        handleCallStateChanged(state)
                    }
                }
                phoneStateListener = listener
                manager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
            }
        } catch (e: SecurityException) {
            Log.w(CALL_STATE_LOG_TAG, "SecurityException while registering call state listener", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun unregisterPhoneStateListener() {
        val manager = telephonyManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = phoneStateCallback ?: return
            manager.unregisterTelephonyCallback(callback)
            phoneStateCallback = null
        } else {
            val listener = phoneStateListener ?: return
            manager.listen(listener, PhoneStateListener.LISTEN_NONE)
            phoneStateListener = null
        }
    }

    private fun handleCallStateChanged(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING,
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                if (recordingJob != null) {
                    pauseRecordingForPhoneCall()
                }
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                if (wasRecordingBeforePhoneCall
                    && isPausedByPhoneCall
                    && !isPausedByAudioFocusLoss
                    && recordingJob == null
                ) {
                    resumeRecording()
                }
                wasRecordingBeforePhoneCall = false
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun requestAudioFocus(): Boolean {
        val manager = audioManager ?: return false
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(audioAttributes)
            .setOnAudioFocusChangeListener { focusChange ->
                handleAudioFocusChanged(focusChange)
            }
            .setAcceptsDelayedFocusGain(true)
            .build()

        audioFocusRequest = focusRequest
        return manager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun abandonAudioFocus() {
        val manager = audioManager ?: return
        val focusRequest = audioFocusRequest ?: return
        manager.abandonAudioFocusRequest(focusRequest)
        audioFocusRequest = null
    }

    private fun handleAudioFocusChanged(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (recordingJob != null) {
                    pauseRecordingForAudioFocusLoss()
                }
            }

            AudioManager.AUDIOFOCUS_GAIN -> {
                if (wasRecordingBeforeAudioFocusLoss
                    && isPausedByAudioFocusLoss
                    && !isPausedByPhoneCall
                    && recordingJob == null
                ) {
                    resumeRecording()
                }
                wasRecordingBeforeAudioFocusLoss = false
            }
        }
    }

    private fun buildNotification(status: String, showResumeAction: Boolean = false): Notification {
        val resumeIntent = Intent(this, RecordingForegroundService::class.java).apply {
            action = ACTION_RESUME
        }
        val resumePendingIntent = PendingIntent.getService(
            this,
            1,
            resumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, RecordingForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Tap action: open the app
        val openAppIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("AudioScribe - Recording")
            .setContentText(status)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openAppPendingIntent)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (showResumeAction) {
            builder.addAction(
                android.R.drawable.ic_media_play,
                "Resume",
                resumePendingIntent
            )
        }

        builder.addAction(
            android.R.drawable.ic_media_pause,
            "Stop",
            stopPendingIntent
        )

        return builder.build()
    }

    private fun createNotificationChannel() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Recording",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Recording channel"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "recording_channel"
        private const val NOTIF_ID = 101

        const val ACTION_START = "ACTION_START_RECORDING"
        const val ACTION_STOP = "ACTION_STOP_RECORDING"
        const val ACTION_PAUSE = "ACTION_PAUSE_RECORDING"
        const val ACTION_RESUME = "ACTION_RESUME_RECORDING"
        const val EXTRA_SESSION_ID = "EXTRA_SESSION_ID"
        const val STATUS_PAUSED_PHONE_CALL = "PAUSED_PHONE_CALL"
        const val STATUS_PAUSED_AUDIO_FOCUS = "PAUSED_AUDIO_FOCUS"
        const val STATUS_STOPPED_LOW_STORAGE = "STOPPED_LOW_STORAGE"
        const val CALL_STATE_LOG_TAG = "RecordingCallState"
        private const val STORAGE_CHECK_INTERVAL_MS = 10_000L  // check every 10 seconds
    }
}