package com.example

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class AiExplanationService {

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

    suspend fun generateExplanationStream(question: String, options: List<String>, correctAnswer: String, providedApiKey: String = ""): Flow<String>? {
        return withContext(Dispatchers.IO) {
            val apiKey = providedApiKey.takeIf { it.isNotBlank() && it != "MY_GEMINI_API_KEY" }
            if (apiKey.isNullOrEmpty()) {
                logError("AiExplanationService", "Gemini API key is not set. Check Secrets Panel.")
                return@withContext kotlinx.coroutines.flow.flowOf("""
                    <b>Gemini API Key is Not Set:</b><br>
                    No valid API key was found in your workspace environment.<br><br>
                    <b>How to configure your API key securely:</b><br>
                    1. Go to Profile Settings.<br>
                    2. Add your API key there.<br>
                    3. Try again.
                """.trimIndent())
            }

            val promptText = """
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

            val generativeModel = GenerativeModel(
            modelName = "gemini-3.1-flash-lite", 
            apiKey = apiKey,
                generationConfig = generationConfig {
                    temperature = 0.1f
                },
                systemInstruction = content { text("You are an expert medical educator and reviewing professor. Provide a holistic, top-tier textbook-style explanation for this medical MCQ question. Act as a technical API and strictly output the requested HTML format.") }
            )

            try {
                generativeModel.generateContentStream(promptText).map { response ->
                    response.text ?: ""
                }.catch { e ->
                    logError("AiExplanationService", "Network/Parsing error", e)
                    emit("<br><br><b>Error:</b><br>${e.message}")
                }
            } catch (e: Exception) {
                logError("AiExplanationService", "Initialization error", e)
                null
            }
        }
    }
}
