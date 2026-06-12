package com.chartanalyzer.app.api

import android.graphics.Bitmap
import android.util.Base64
import com.chartanalyzer.app.models.TechnicalFramework
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

data class AnalysisResult(
    val success: Boolean,
    val text: String,
    val error: String? = null
)

class AnthropicApiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val API_URL = "https://api.anthropic.com/v1/messages"
        private const val MODEL = "claude-sonnet-4-20250514"
        private const val MAX_TOKENS = 2000
        private const val ANTHROPIC_VERSION = "2023-06-01"
    }

    suspend fun analyzeChart(
        bitmap: Bitmap,
        question: String,
        activeFrameworks: List<TechnicalFramework>,
        apiKey: String
    ): AnalysisResult = withContext(Dispatchers.IO) {
        try {
            val base64Image = bitmapToBase64(bitmap)
            val systemPrompt = buildSystemPrompt(activeFrameworks)
            val requestBody = buildRequestBody(base64Image, question, systemPrompt)

            val request = Request.Builder()
                .url(API_URL)
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", ANTHROPIC_VERSION)
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorJson = JSONObject(responseBody)
                val errorMsg = errorJson.optJSONObject("error")?.optString("message")
                    ?: "HTTP ${response.code}: $responseBody"
                return@withContext AnalysisResult(false, "", errorMsg)
            }

            val json = JSONObject(responseBody)
            val content = json.getJSONArray("content")
            val text = buildString {
                for (i in 0 until content.length()) {
                    val block = content.getJSONObject(i)
                    if (block.getString("type") == "text") {
                        append(block.getString("text"))
                    }
                }
            }
            AnalysisResult(true, text)
        } catch (e: Exception) {
            AnalysisResult(false, "", e.message ?: "Unknown error occurred")
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val maxDim = 1568
        val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
            val ratio = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height)
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt(),
                (bitmap.height * ratio).toInt(),
                true
            )
        } else bitmap

        val outputStream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    private fun buildSystemPrompt(frameworks: List<TechnicalFramework>): String {
        val formulaBlock = frameworks.joinToString("\n\n") { fw ->
            "### ${fw.name} [${fw.category.label}]\n${fw.formula}\nContext: ${fw.description}"
        }

        return """You are an elite quantitative technical analyst specializing in advanced chart analysis. You have mastered the following ${frameworks.size} mathematical frameworks:

$formulaBlock

ANALYSIS PROTOCOL:
1. Scan the chart for all visible structures relevant to the active frameworks
2. Apply the mathematical formulas conceptually to what you observe in the image
3. Reference specific price levels, patterns, and signals visible in the chart
4. Identify confluence zones where multiple frameworks align (highest probability trades)
5. Provide a structured, actionable analysis with clear BULLISH / BEARISH / NEUTRAL bias
6. Mention specific entry zones, invalidation levels, and targets where possible
7. Note any PineScript/TradingView indicators that would help monitor the setup
8. End with a concise Summary: bias, key levels, and highest-priority signal

FORMAT YOUR RESPONSE WITH THESE SECTIONS (use only those relevant):
📊 Chart Overview
🔍 Key Patterns Identified  
⚡ Confluence Zones
📈 Signal Analysis
🎯 Key Levels (Support / Resistance / Targets)
⚠️ Risk & Invalidation
✅ Summary & Bias

Be specific with price levels. Use the formulas as your analytical lens."""
    }

    private fun buildRequestBody(base64Image: String, question: String, systemPrompt: String): JSONObject {
        val imageContent = JSONObject().apply {
            put("type", "image")
            put("source", JSONObject().apply {
                put("type", "base64")
                put("media_type", "image/jpeg")
                put("data", base64Image)
            })
        }

        val textContent = JSONObject().apply {
            put("type", "text")
            put("text", question)
        }

        val message = JSONObject().apply {
            put("role", "user")
            put("content", JSONArray().apply {
                put(imageContent)
                put(textContent)
            })
        }

        return JSONObject().apply {
            put("model", MODEL)
            put("max_tokens", MAX_TOKENS)
            put("system", systemPrompt)
            put("messages", JSONArray().apply { put(message) })
        }
    }
}
