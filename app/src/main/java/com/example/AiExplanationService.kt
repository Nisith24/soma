package com.example

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class AiExplanationService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun logError(tag: String, msg: String, tr: Throwable? = null) {
        try {
            if (tr != null) {
                Log.e(tag, msg, tr)
            } else {
                Log.e(tag, msg)
            }
        } catch (e: Throwable) {
            println("[$tag] ERROR: $msg" + (tr?.let { "\n${it.stackTraceToString()}" } ?: ""))
        }
    }

    private fun getResolvedApiKey(): String {
        return "AIzaSyBE4iFpeiBWfVNBGvvyYzhG1gPnNvjjeMc"
    }

    suspend fun generateExplanation(question: String, options: List<String>, correctAnswer: String, providedApiKey: String = ""): String? {
        return withContext(Dispatchers.IO) {
            val defaultKey = getResolvedApiKey()
            val apiKey = if (providedApiKey.isNotBlank() && providedApiKey != "MY_GEMINI_API_KEY") providedApiKey else defaultKey
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                logError("AiExplanationService", "Gemini API key is not set or uses default. Check Secrets Panel.")
                return@withContext """
                    <b>Gemini API Key is Not Set:</b><br>
                    No valid API key was found in your workspace environment.<br><br>
                    <b>How to configure your API key securely:</b><br>
                    1. Open the <b>Secrets Panel</b> in the AI Studio editor sidepane.<br>
                    2. Add a new secret with Key: <b>GEMINI_API_KEY</b>.<br>
                    3. Paste your active API key from Google AI Studio as the value.<br>
                    4. Check the box to enable it, and retry tapping this button!
                """.trimIndent()
            }

            val promptText = """
                You are an expert medical educator and reviewing professor. Provide a holistic, top-tier textbook-style explanation for this medical MCQ question.
                
                Question: $question
                Options: ${options.joinToString(", ")}
                Correct Answer: $correctAnswer
                
                IMPORTANT FORMATTING RULES:
                - You MUST output your response entirely in standard HTML tags (e.g., <b>, <i>, <ul>, <li>, <br>).
                - DO NOT wrap the output in Markdown code blocks (like ```html). Return ONLY the raw HTML string.
                - Do NOT include <html> or <body> tags. Just the HTML content.
                
                STRUCTURE YOUR RESPONSE WITH THESE SECTIONS AND EMOJIS:
                
                <br>🩺 <b>Clinical Core:</b><br>
                (Provide the main concept succinctly)
                
                <br><br>✅ <b>Why it's correct ($correctAnswer):</b><br>
                (Explain the pathophysiology, mechanism, or presentation in detail like a medical textbook)
                
                <br><br>❌ <b>Why other options are incorrect:</b><br>
                <ul>
                (Use <li> to list each incorrect option and briefly explain why it's wrong or what condition it actually represents)
                </ul>
                
                <br>🧠 <b>High-Yield / Clinical Pearl:</b><br>
                (Provide a quick tip, flow list, e.g., "A → B → C", or a classic mnemonic)
            """.trimIndent()

            val jsonBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", promptText)
                            })
                        })
                    })
                })
            }

            val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())

            val modelName = "gemini-3.1-flash-lite"
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/${modelName}:generateContent?key=$apiKey")
                .post(requestBody)
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val responseBodyStr = response.body?.string()
                    if (!response.isSuccessful) {
                        logError("AiExplanationService", "API request failed: ${response.code} ${response.message}\nBody: $responseBodyStr")
                        val errorDetail = when (response.code) {
                            400 -> """
                                ⚠️ <b>Bad Request (400)</b><br>
                                The request was rejected. This can happen if the question content triggered the safety screening filters of the Gemini model (e.g. containing sensitive medical terminology that was falsely classified).
                            """.trimIndent()
                            403 -> """
                                🔑 <b>Authentication Error (403)</b><br>
                                The Gemini API signature or key is invalid.<br><br>
                                <b>Resolution steps:</b><br>
                                1. Ensure you have copied the correct key from your Google AI Studio dashboard.<br>
                                2. Go to <b>User Profile &rarr; Audio & Gemini Voice Settings</b>.<br>
                                3. Paste the key in the input box, and press the <b>Save API Key</b> button.
                            """.trimIndent()
                            429 -> """
                                ⏳ <b>Rate Limit Reached (429)</b><br>
                                Your key has hit Google's Gemini free-tier quota limit.<br><br>
                                <b>Resolution steps:</b><br>
                                1. Wait 60 seconds and attempt generating again.<br>
                                2. Or configure a pay-as-you-go or alternative service key in your Profile Settings to lift limits.
                            """.trimIndent()
                            500, 503, 504 -> """
                                ☁️ <b>Service Temporary Unavailable (${response.code})</b><br>
                                Google Gemini backend servers are facing high traffic loads or undergoing maintenance.<br><br>
                                <b>Resolution steps:</b><br>
                                Please wait a moment and try tapping the AutoAwesome button again shortly.
                            """.trimIndent()
                            else -> """
                                ❌ <b>API Request Failed (${response.code})</b><br>
                                ${responseBodyStr ?: response.message}
                            """.trimIndent()
                        }
                        return@withContext errorDetail
                    }
                    if (responseBodyStr != null) {
                        val responseJson = JSONObject(responseBodyStr)
                        val candidates = responseJson.optJSONArray("candidates")
                        if (candidates != null && candidates.length() > 0) {
                            val firstCandidate = candidates.getJSONObject(0)
                            val content = firstCandidate.optJSONObject("content")
                            val parts = content?.optJSONArray("parts")
                            if (parts != null && parts.length() > 0) {
                                var explanatoryText = parts.getJSONObject(0).optString("text")
                                explanatoryText = explanatoryText.removePrefix("```html").removeSuffix("```").trim()
                                explanatoryText = explanatoryText.removePrefix("```").trim()
                                return@withContext explanatoryText
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                logError("AiExplanationService", "Network error", e)
                return@withContext "<b>Network Error:</b><br>${e.message}"
            } catch (e: Exception) {
                logError("AiExplanationService", "Parsing error", e)
                return@withContext "<b>Internal App Error:</b><br>${e.message}"
            }
            return@withContext "<b>Error:</b><br>Could not generate explanation. Response format was unrecognized."
        }
    }

    suspend fun generateTtsAudioBase64(text: String, apiKey: String, voiceName: String): String? {
        return withContext(Dispatchers.IO) {
            val defaultKey = getResolvedApiKey()
            val actualKey = if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") apiKey else defaultKey
            if (actualKey.isEmpty() || actualKey == "MY_GEMINI_API_KEY") {
                logError("AiExplanationService", "Gemini API key is not set for TTS.")
                return@withContext null
            }

            val jsonBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", text)
                            })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().apply {
                        put("AUDIO")
                    })
                    put("speechConfig", JSONObject().apply {
                        put("voiceConfig", JSONObject().apply {
                            put("prebuiltVoiceConfig", JSONObject().apply {
                                put("voiceName", voiceName)
                            })
                        })
                    })
                })
            }

            val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-preview-tts:generateContent?key=$actualKey")
                .post(requestBody)
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val responseStr = response.body?.string()
                    if (!response.isSuccessful) {
                        logError("AiExplanationService", "TTS API request failed: ${response.code} - $responseStr")
                        return@withContext null
                    }
                    if (responseStr != null) {
                        val responseJson = JSONObject(responseStr)
                        val candidates = responseJson.optJSONArray("candidates")
                        if (candidates != null && candidates.length() > 0) {
                            val part = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)
                            val inlineData = part?.optJSONObject("inlineData")
                            if (inlineData != null) {
                                return@withContext inlineData.optString("data")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                logError("AiExplanationService", "TTS generation error", e)
            }
            return@withContext null
        }
    }
}

