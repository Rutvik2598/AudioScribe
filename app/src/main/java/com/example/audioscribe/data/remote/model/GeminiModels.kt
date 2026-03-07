package com.example.audioscribe.data.remote.model

import com.google.gson.annotations.SerializedName

// ── Request ──────────────────────────────────────────────────────────────

data class GeminiRequest(
    val contents: List<GeminiContent>
)

data class GeminiContent(
    val parts: List<GeminiPart>
)

data class GeminiPart(
    val text: String? = null,
    @SerializedName("inline_data")
    val inlineData: InlineData? = null
)

data class InlineData(
    @SerializedName("mime_type")
    val mimeType: String,
    val data: String   // base64-encoded
)
