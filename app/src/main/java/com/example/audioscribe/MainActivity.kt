package com.example.audioscribe

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.example.audioscribe.service.RecordingForegroundService
import com.example.audioscribe.ui.detail.RecordingDetailScreen
import com.example.audioscribe.ui.home.HomeScreen
import com.example.audioscribe.ui.memories.MemoriesScreen
import com.example.audioscribe.ui.recording.RecordingScreen
import com.example.audioscribe.ui.theme.AudioScribeTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val navigateToRecordingFlow = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)

    private val requestBluetoothPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                requestBluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }

    private val requestPhoneStatePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                requestBluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }

    private val requestAudioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            requestPhoneStatePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        enableEdgeToEdge()

        // Check if launched from recording notification
        if (intent?.getBooleanExtra(RecordingForegroundService.EXTRA_NAVIGATE_TO_RECORDING, false) == true) {
            navigateToRecordingFlow.tryEmit(true)
        }

        setContent {
            AudioScribeTheme {
                val navController = rememberNavController()

                // Handle navigation from notification taps
                LaunchedEffect(Unit) {
                    navigateToRecordingFlow.collectLatest { shouldNavigate ->
                        if (shouldNavigate) {
                            navController.navigate("recording") {
                                launchSingleTop = true
                            }
                        }
                    }
                }

                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            onCaptureNotesClick = { navController.navigate("recording") },
                            onViewMemoriesClick = { navController.navigate("memories") }
                        )
                    }
                    composable("recording") {
                        RecordingScreen(
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable("memories") {
                        MemoriesScreen(
                            onBack = { navController.popBackStack() },
                            onRecordingClick = { sessionId ->
                                navController.navigate("detail/$sessionId")
                            }
                        )
                    }
                    composable(
                        route = "detail/{sessionId}",
                        arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
                    ) {
                        RecordingDetailScreen(
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(RecordingForegroundService.EXTRA_NAVIGATE_TO_RECORDING, false)) {
            navigateToRecordingFlow.tryEmit(true)
        }
    }
}