package com.example.audioscribe.data.remote

import android.util.Base64
import android.util.Log
import com.example.audioscribe.BuildConfig
import com.example.audioscribe.data.remote.model.GeminiContent
import com.example.audioscribe.data.remote.model.GeminiPart
import com.example.audioscribe.data.remote.model.GeminiRequest
import com.example.audioscribe.data.remote.model.InlineData
import com.google.gson.JsonParser
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GeminiService"

/**
 * Sends audio (WAV bytes) to Gemini 2.5 Flash via REST API and returns the transcription text.
 * Parses the raw JSON manually to handle varying response shapes (thinking mode, etc.).
 */
@Singleton
class GeminiTranscriptionService @Inject constructor(
    private val geminiApi: GeminiApi
) {

    /**
     * Extract text from a Gemini REST API raw JSON response.
     * Walks: candidates[0].content.parts[] → collects all "text" fields
     * where the part is NOT a thinking/thought part.
     */
    private fun extractText(rawJson: String): String {
        val root = JsonParser.parseString(rawJson).asJsonObject

        val candidates = root.getAsJsonArray("candidates") ?: run {
            Log.w(TAG, "No 'candidates' in response")
            return ""
        }
        if (candidates.size() == 0) {
            Log.w(TAG, "Empty 'candidates' array")
            return ""
        }

        val candidate = candidates[0].asJsonObject
        val content = candidate.getAsJsonObject("content") ?: run {
            Log.w(TAG, "No 'content' in candidate: $candidate")
            return ""
        }
        val parts = content.getAsJsonArray("parts") ?: run {
            Log.w(TAG, "No 'parts' in content: $content")
            return ""
        }

        val texts = mutableListOf<String>()
        for (part in parts) {
            val obj = part.asJsonObject
            // Skip "thought" parts (Gemini 2.5 thinking mode)
            if (obj.has("thought") && obj.get("thought").asBoolean) continue
            if (obj.has("text")) {
                val t = obj.get("text").asString
                if (t.isNotBlank()) texts.add(t)
            }
        }
        return texts.joinToString(" ").trim()
    }

    /**
     * Transcribe the given WAV audio bytes.
     */
    suspend fun transcribe(wavBytes: ByteArray): String {
        val base64Audio = Base64.encodeToString(wavBytes, Base64.NO_WRAP)

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(
                        GeminiPart(
                            inlineData = InlineData(
                                mimeType = "audio/wav",
                                data = base64Audio
                            )
                        ),
                        GeminiPart(
                            text = "Transcribe the audio exactly as spoken. " +
                                    "Return ONLY the transcription text with no extra commentary, labels, formatting, or timestamps. " +
                                    "If the audio contains only silence or no intelligible speech, return an empty string."
                        )
                    )
                )
            )
        )

        val responseBody = geminiApi.generateContent(
            apiKey = BuildConfig.GEMINI_API_KEY,
            request = request
        )
        val rawJson = responseBody.string()
        Log.d(TAG, "Transcribe raw response: $rawJson")

        return extractText(rawJson)
    }

    /**
     * Generate a summary of the full transcription text so far.
     */
    suspend fun summarize(fullTranscription: String): String {
        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(
                        GeminiPart(
                            text = "Below is a live transcription of an audio recording. " +
                                    "Provide a clear, concise summary capturing the key points. " +
                                    "Return ONLY the summary text with no extra labels or formatting.\n\n" +
                                    fullTranscription
                        )
                    )
                )
            )
        )

        val responseBody = geminiApi.generateContent(
            apiKey = BuildConfig.GEMINI_API_KEY,
            request = request
        )
        val rawJson = responseBody.string()
        Log.d(TAG, "Summary raw response: $rawJson")

        return extractText(rawJson)
    }
}
