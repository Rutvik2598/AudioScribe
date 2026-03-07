# AudioScribe

An Android application that records audio, transcribes it in real time using the Gemini 2.5 Flash API, and generates summaries — all on-device with a foreground service pipeline.

## Features

- **Real-Time Transcription** — Audio is chunked into 30-second segments with 2-second overlap and transcribed via the Gemini 2.5 Flash REST API as recording progresses.
- **Auto-Summarization** — A summary is generated from the running transcription and updates as new chunks are processed.
- **Foreground Service Recording** — Recording runs in a foreground service with a persistent notification, surviving configuration changes and backgrounding.
- **Silence Detection** — Warns the user when silence is detected for more than 10 seconds (amplitude threshold of 800).
- **Phone Call Handling** — Automatically pauses recording on incoming/outgoing calls and resumes when the call ends.
- **Audio Focus Management** — Responds to `AUDIOFOCUS_GAIN` / `AUDIOFOCUS_LOSS` events to pause and resume gracefully.
- **Low Storage Protection** — Monitors free storage every 10 seconds and stops recording if available space drops below 320 KB.
- **Retry Logic** — Failed transcription chunks are retried up to 3 times with exponential backoff.
- **Overlapping Chunks** — 2-second overlap between consecutive chunks prevents losing context at boundaries.

## Architecture

The project follows **Clean Architecture** with clear separation of concerns:

```
app/src/main/java/com/example/audioscribe/
├── data/                  # Data layer
│   ├── local/             # Room database (DAO, entities, DB)
│   ├── remote/            # Gemini API service & models
│   └── repository/        # Repository implementations
├── domain/                # Domain / business logic
│   ├── entity/            # Domain models (ChunkInfo, etc.)
│   ├── repository/        # Repository interfaces
│   └── ...                # AudioChunker, AudioStreamer, PcmToWavConverter, StorageHelper
├── ui/                    # Presentation layer (Jetpack Compose)
│   ├── home/              # Home screen
│   ├── recording/         # Recording screen & ViewModel
│   ├── memories/          # Recording history list
│   ├── detail/            # Recording detail / playback
│   └── theme/             # Material 3 theming
├── service/               # RecordingForegroundService
├── di/                    # Hilt modules
├── worker/                # WorkManager (SessionTerminationWorker)
├── MainActivity.kt        # Navigation host
└── AudioScribe.kt         # @HiltAndroidApp Application class
```

## Tech Stack

| Category | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 |
| DI | Hilt 2.59.1 |
| Database | Room (KSP) |
| Networking | Retrofit + Gson |
| AI / Transcription | Gemini 2.5 Flash (REST API) |
| Audio | AudioRecord (16 kHz, mono, 16-bit PCM) |
| Concurrency | Kotlin Coroutines, StateFlow |
| Background | Foreground Service, WorkManager |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 36 |

## Recording Pipeline

```
Microphone
   │
   ▼
AudioStreamer  ──►  emits raw PCM packets
   │
   ▼
AudioChunker  ──►  buffers into 30s chunks (2s overlap)
   │
   ▼
PCM saved to disk  ──►  PcmToWavConverter  ──►  WAV file
   │
   ▼
GeminiTranscriptionService.transcribe()  ──►  text per chunk
   │
   ▼
ViewModel rebuilds full transcription  ──►  triggers summarize()
   │
   ▼
Room DB persists transcription + summary on stop
```

## Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/<your-username>/AudioScribe.git
   ```

2. **Add your Gemini API key**

   Create or edit `local.properties` in the project root:
   ```properties
   GEMINI_API_KEY=your_api_key_here
   ```
   The key is read at build time via `buildConfigField` and never checked into version control.

3. **Open in Android Studio** and sync Gradle.

4. **Run** on a physical device (microphone access is required).

## Permissions

| Permission | Purpose |
|---|---|
| `RECORD_AUDIO` | Microphone access for recording |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MICROPHONE` | Keep recording alive in background |
| `READ_PHONE_STATE` | Detect incoming calls to pause recording |
| `POST_NOTIFICATIONS` | Notification for foreground service (Android 13+) |
| `BLUETOOTH_CONNECT` | Bluetooth audio routing (Android 12+) |

## Navigation

```
Home
 ├── "Capture Notes" ──► Recording Screen (record, transcribe, summarize)
 └── "View Memories" ──► Memories List
                           └── tap item ──► Recording Detail (transcript + summary)
```

## License

This project is for educational and portfolio purposes.
