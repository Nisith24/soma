package com.example.voice

import android.content.Context
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class GeminiTtsService(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun synthesizeSpeech(text: String, apiKey: String, voiceName: String): File? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e("GeminiTtsService", "No explicit API key provided.")
            return@withContext null
        }

        val audioDir = context.getDir("ai_audio_forever", Context.MODE_PRIVATE)
        val hashId = "${text.hashCode()}_${voiceName.hashCode()}"
        val tempFile = File(audioDir, "gemini_tts_$hashId.wav")

        if (tempFile.exists()) {
            return@withContext tempFile
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-live-preview:generateContent?key=$apiKey"
        
        try {
            val jsonBody = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().apply {
                        put("text", text)
                    }))
                }))
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().put("AUDIO"))
                    put("speechConfig", JSONObject().apply {
                        put("voiceConfig", JSONObject().apply {
                            put("prebuiltVoiceConfig", JSONObject().apply {
                                put("voiceName", voiceName)
                            })
                        })
                    })
                })
            }
            
            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string()
                    Log.e("GeminiTtsService", "Error ${response.code}: $err")
                    return@withContext null
                }
                
                val responseString = response.body?.string() ?: return@withContext null
                val rootObj = JSONObject(responseString)
                val candidates = rootObj.optJSONArray("candidates") ?: return@withContext null
                if (candidates.length() == 0) return@withContext null
                
                val content = candidates.getJSONObject(0).optJSONObject("content") ?: return@withContext null
                val parts = content.optJSONArray("parts") ?: return@withContext null
                if (parts.length() == 0) return@withContext null
                
                val inlineData = parts.getJSONObject(0).optJSONObject("inlineData") ?: return@withContext null
                val base64Audio = inlineData.optString("data")
                
                if (base64Audio.isEmpty()) return@withContext null
                
                val audioBytes = Base64.decode(base64Audio, Base64.DEFAULT)
                
                // Saving generated persistent audio file
                FileOutputStream(tempFile).use { fos ->
                    fos.write(audioBytes)
                }
                
                return@withContext tempFile
            }
        } catch (e: Exception) {
            Log.e("GeminiTtsService", "Exception during Gemini TTS synthesis", e)
            return@withContext null
        }
    }
}
