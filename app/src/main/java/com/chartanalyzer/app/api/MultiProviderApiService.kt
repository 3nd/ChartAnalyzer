package com.chartanalyzer.app.api

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.chartanalyzer.app.models.AiProvider
import com.chartanalyzer.app.models.AiProviders
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

/**
 * MultiProviderApiService
 *
 * Routes chart analysis to the appropriate AI backend based on the selected provider.
 * Supported providers and their API formats:
 *
 *   ANTHROPIC / CLAUDE_HAIKU  → POST https://api.anthropic.com/v1/messages
 *                                 Headers: x-api-key, anthropic-version
 *                                 Body: { model, max_tokens, system, messages[{role,content:[image,text]}] }
 *
 *   GEMINI / BARD             → POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key=
 *                                 Body: { system_instruction, contents:[{parts:[{inline_data:{mime_type,data}}, {text}]}] }
 *
 *   OPENAI / GPT_OSS          → POST https://api.openai.com/v1/chat/completions
 *                                 Headers: Authorization: Bearer {key}, Organization: {org}
 *                                 Body: { model, max_tokens, messages:[{role,content:[{type:image_url,image_url:{url:data:image/jpeg;base64,...}},{type:text}]}] }
 */
class MultiProviderApiService {

    companion object {
        private const val TAG = "MultiProviderAPI"
        private const val MAX_TOKENS = 2000
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // ─── Main dispatcher ──────────────────────────────────────────────────────

    suspend fun analyzeChart(
        bitmap: Bitmap,
        question: String,
        activeFrameworks: List<TechnicalFramework>,
        provider: AiProvider,
        credentials: Map<String, String>,
        selectedModelId: String? = null
    ): AnalysisResult = withContext(Dispatchers.IO) {
        val modelId = selectedModelId
            ?: provider.models.firstOrNull { it.isDefault }?.id
            ?: provider.models.firstOrNull()?.id
            ?: return@withContext AnalysisResult(false, "", "No model configured for ${provider.name}")

        val systemPrompt = buildSystemPrompt(activeFrameworks)
        val base64Image  = bitmapToBase64(bitmap)

        return@withContext when (provider.id) {
            "anthropic", "claude_haiku" -> callAnthropic(
                base64Image, question, systemPrompt, modelId,
                credentials["anthropic_key"] ?: ""
            )
            "gemini", "bard" -> callGemini(
                base64Image, question, systemPrompt, modelId,
                credentials["google_ai_key"] ?: ""
            )
            "openai", "gpt_oss", "chatgpt_free" -> callOpenAI(
                base64Image, question, systemPrompt, modelId,
                credentials["openai_key"] ?: "",
                credentials["openai_org"],
                "https://api.openai.com/v1"
            )
            "xai_grok" -> callOpenAI(          // xAI uses OpenAI-compatible API
                base64Image, question, systemPrompt, modelId,
                credentials["openai_key"] ?: "",
                null,
                "https://api.x.ai/v1"
            )
            "genai_mil" -> callOpenAI(          // GenAI.mil uses OpenAI-compatible API
                base64Image, question, systemPrompt, modelId,
                credentials["openai_key"] ?: "",
                null,
                credentials["openai_base_url"]?.trimEnd('/') ?: "https://genai.mil/api/v1"
            )
            else -> AnalysisResult(false, "", "Provider '${provider.id}' is not yet supported for image analysis")
        }
    }

    // ─── Anthropic (Claude) ────────────────────────────────────────────────────

    private fun callAnthropic(
        base64Image: String,
        question: String,
        systemPrompt: String,
        model: String,
        apiKey: String
    ): AnalysisResult {
        if (apiKey.isBlank()) return AnalysisResult(false, "",
            "Anthropic API key is required. Get one at console.anthropic.com")

        val body = JSONObject().apply {
            put("model", model)
            put("max_tokens", MAX_TOKENS)
            put("system", systemPrompt)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "image")
                        put("source", JSONObject().apply {
                            put("type", "base64")
                            put("media_type", "image/jpeg")
                            put("data", base64Image)
                        })
                    })
                    put(JSONObject().apply {
                        put("type", "text")
                        put("text", question)
                    })
                })
            }))
        }

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return executeAndParse(request) { json ->
            val content = json.getJSONArray("content")
            buildString {
                for (i in 0 until content.length()) {
                    val block = content.getJSONObject(i)
                    if (block.getString("type") == "text") append(block.getString("text"))
                }
            }
        }
    }

    // ─── Google Gemini (& Bard — same API) ─────────────────────────────────────

    private fun callGemini(
        base64Image: String,
        question: String,
        systemPrompt: String,
        model: String,
        apiKey: String
    ): AnalysisResult {
        if (apiKey.isBlank()) return AnalysisResult(false, "",
            "Google AI API key is required. Get one free at aistudio.google.com")

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        val body = JSONObject().apply {
            put("system_instruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().apply {
                    put("text", systemPrompt)
                }))
            })
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().apply {
                    // Image part
                    put(JSONObject().apply {
                        put("inline_data", JSONObject().apply {
                            put("mime_type", "image/jpeg")
                            put("data", base64Image)
                        })
                    })
                    // Text part
                    put(JSONObject().apply { put("text", question) })
                })
            }))
            put("generationConfig", JSONObject().apply {
                put("maxOutputTokens", MAX_TOKENS)
                put("temperature", 0.2)
            })
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return executeAndParse(request) { json ->
            json.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
        }
    }

    // ─── OpenAI (ChatGPT, GPT-4o, GPT-OSS) ──────────────────────────────────

    private fun callOpenAI(
        base64Image: String,
        question: String,
        systemPrompt: String,
        model: String,
        apiKey: String,
        orgId: String?,
        baseUrl: String
    ): AnalysisResult {
        if (apiKey.isBlank()) return AnalysisResult(false, "",
            "OpenAI API key is required. Get one at platform.openai.com/api-keys")

        val body = JSONObject().apply {
            put("model", model)
            put("max_tokens", MAX_TOKENS)
            put("messages", JSONArray().apply {
                // System message
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                // User message with image + text
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().apply {
                                put("url", "data:image/jpeg;base64,$base64Image")
                                put("detail", "high")
                            })
                        })
                        put(JSONObject().apply {
                            put("type", "text")
                            put("text", question)
                        })
                    })
                })
            })
        }

        val reqBuilder = Request.Builder()
            .url("$baseUrl/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
        if (!orgId.isNullOrBlank()) reqBuilder.addHeader("OpenAI-Organization", orgId)
        val request = reqBuilder.post(body.toString().toRequestBody("application/json".toMediaType())).build()

        return executeAndParse(request) { json ->
            json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        }
    }

    // ─── Text-only analysis (for providers without vision or raw data mode) ──────

    suspend fun analyzeText(
        question: String,
        activeFrameworks: List<TechnicalFramework>,
        provider: AiProvider,
        credentials: Map<String, String>,
        selectedModelId: String? = null
    ): AnalysisResult = withContext(Dispatchers.IO) {
        val modelId = selectedModelId
            ?: provider.models.firstOrNull { it.isDefault }?.id
            ?: provider.models.firstOrNull()?.id
            ?: return@withContext AnalysisResult(false, "", "No model configured")

        val systemPrompt = buildSystemPrompt(activeFrameworks)

        return@withContext when (provider.id) {
            "anthropic", "claude_haiku" -> callAnthropicText(
                question, systemPrompt, modelId,
                credentials["anthropic_key"] ?: ""
            )
            "gemini", "bard" -> callGeminiText(
                question, systemPrompt, modelId,
                credentials["google_ai_key"] ?: ""
            )
            "openai", "gpt_oss" -> callOpenAIText(
                question, systemPrompt, modelId,
                credentials["openai_key"] ?: "",
                credentials["openai_org"],
                credentials["openai_base_url"]?.trimEnd('/') ?: "https://api.openai.com/v1"
            )
            else -> AnalysisResult(false, "", "Provider '${provider.id}' not supported for text analysis")
        }
    }

    private fun callAnthropicText(
        question: String, systemPrompt: String, model: String, apiKey: String
    ): AnalysisResult {
        if (apiKey.isBlank()) return AnalysisResult(false, "", "Anthropic API key required")
        val body = JSONObject().apply {
            put("model", model); put("max_tokens", MAX_TOKENS)
            put("system", systemPrompt)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", question)
            }))
        }
        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        return executeAndParse(request) { json ->
            val content = json.getJSONArray("content")
            buildString { for (i in 0 until content.length()) {
                val block = content.getJSONObject(i)
                if (block.getString("type") == "text") append(block.getString("text"))
            }}
        }
    }

    private fun callGeminiText(
        question: String, systemPrompt: String, model: String, apiKey: String
    ): AnalysisResult {
        if (apiKey.isBlank()) return AnalysisResult(false, "", "Google AI API key required")
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val body = JSONObject().apply {
            put("system_instruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().apply { put("text", systemPrompt) }))
            })
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().apply { put("text", question) }))
            }))
            put("generationConfig", JSONObject().apply {
                put("maxOutputTokens", MAX_TOKENS); put("temperature", 0.2)
            })
        }
        val request = Request.Builder().url(url)
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        return executeAndParse(request) { json ->
            json.getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts")
                .getJSONObject(0).getString("text")
        }
    }

    private fun callOpenAIText(
        question: String, systemPrompt: String, model: String,
        apiKey: String, orgId: String?, baseUrl: String
    ): AnalysisResult {
        if (apiKey.isBlank()) return AnalysisResult(false, "", "OpenAI API key required")
        val body = JSONObject().apply {
            put("model", model); put("max_tokens", MAX_TOKENS)
            put("messages", JSONArray().apply {
                put(JSONObject().apply { put("role","system"); put("content", systemPrompt) })
                put(JSONObject().apply { put("role","user"); put("content", question) })
            })
        }
        val reqBuilder = Request.Builder()
            .url("$baseUrl/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
        if (!orgId.isNullOrBlank()) reqBuilder.addHeader("OpenAI-Organization", orgId)
        return executeAndParse(reqBuilder.post(body.toString().toRequestBody("application/json".toMediaType())).build()) { json ->
            json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        }
    }

    private fun executeAndParse(
        request: Request,
        extractText: (JSONObject) -> String
    ): AnalysisResult {
        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errMsg = try {
                    val j = JSONObject(body)
                    j.optJSONObject("error")?.optString("message")
                        ?: j.optString("message", "HTTP ${response.code}")
                } catch (_: Exception) { "HTTP ${response.code}" }
                Log.e(TAG, "API error: $errMsg")
                return AnalysisResult(false, "", errMsg)
            }

            val text = extractText(JSONObject(body))
            AnalysisResult(true, text)
        } catch (e: Exception) {
            Log.e(TAG, "Request failed: ${e.message}")
            AnalysisResult(false, "", e.message ?: "Network error — check your connection")
        }
    }

    // ─── Shared helpers ───────────────────────────────────────────────────────

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
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun buildSystemPrompt(frameworks: List<TechnicalFramework>): String {
        val formulaBlock = frameworks.joinToString("\n\n") { fw ->
            "### ${fw.name} [${fw.category.label}]\n${fw.formula}\nContext: ${fw.description}"
        }
        return """You are an elite quantitative technical analyst. You have mastered the following ${frameworks.size} mathematical frameworks:

$formulaBlock

ANALYSIS PROTOCOL:
1. Scan the chart for all visible structures relevant to the active frameworks
2. Apply the mathematical formulas conceptually to what you observe in the image
3. Reference specific price levels, patterns, and signals visible in the chart
4. Identify confluence zones where multiple frameworks align (highest probability trades)
5. Provide a structured, actionable analysis with clear BULLISH / BEARISH / NEUTRAL bias
6. Mention specific entry zones, invalidation levels, and targets where possible
7. End with a concise Summary: bias, key levels, and highest-priority signal

FORMAT YOUR RESPONSE WITH THESE SECTIONS:
📊 Chart Overview
🔍 Key Patterns Identified
⚡ Confluence Zones
📈 Signal Analysis
🎯 Key Levels (Support / Resistance / Targets)
⚠️ Risk & Invalidation
✅ Summary & Bias

Be specific with price levels. Use the formulas as your analytical lens."""
    }
}
