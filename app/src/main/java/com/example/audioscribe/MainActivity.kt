package com.example.audioscribe

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.audioscribe.ui.recording.RecordingScreen
import com.example.audioscribe.ui.theme.AudioScribeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val requestAudioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // Handle audio permission result if needed
        }

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // Handle notification permission result if needed
        }

    private val requestPhoneStatePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // Handle phone state permission result if needed
        }

    private val requestBluetoothPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // Handle bluetooth permission result if needed
        }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        requestPhoneStatePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestBluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
        enableEdgeToEdge()
        setContent {
            AudioScribeTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // Get the ViewModel instance for later use
                    RecordingScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}