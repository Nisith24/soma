package com.example.gemini

import com.example.BuildConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Serializable
data class GenerateContentRequest(
    val contents: List<Content>,
    val systemInstruction: Content? = null,
    val generationConfig: GenerationConfig? = null
)

@Serializable
data class Content(
    val parts: List<Part>
)

@Serializable
data class Part(
    val text: String? = null
)

@Serializable
data class GenerationConfig(
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null,
    val responseModalities: List<String>? = null
)

@Serializable
data class GenerateContentResponse(
    val candidates: List<Candidate>
)

@Serializable
data class Candidate(
    val content: Content
)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.1-pro-preview:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        val json = Json { ignoreUnknownKeys = true }
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        retrofit.create(GeminiApiService::class.java)
    }
}

suspend fun fetchMedicalExplanation(question: String, options: List<String>, correctAnswer: String, topic: String?, subject: String?, existingExplanation: String?): String = withContext(Dispatchers.IO) {
    val apiKey = BuildConfig.GEMINI_API_KEY
    if (apiKey.isEmpty() || apiKey == "AIzaSyDuIqCV4xLF6tAC0ps5BUJch_0_g7kSbXQ") {
        return@withContext "API Key is missing or invalid. Please configure it in the Secrets panel."
    }

    val systemInstructionText = """
        You are a highly capable AI dedicated to medical learning, providing holistic and structured explanations for Multiple Choice Questions. 
        Your explanations should be high-yield, structured, and easy to understand from basics to holistic flow.
        Please adaptively use detailed explanations, step-by-step reasoning, bullet points, or paragraphs where appropriate. 
        Refer to standard medical textbook concepts without cognitive overload. Do not hallucinate. Provide only the explanation directly, without any intro text.
    """.trimIndent()

    val prompt = """
        Explain the answer to this medical MCQ:
        Subject: ${subject ?: "General"}
        Topic: ${topic ?: "General"}
        Question: $question
        Options:
        ${options.joinToString("\n")}
        
        The correct answer is: $correctAnswer
        
        ${if (!existingExplanation.isNullOrBlank()) "The official explanation is: $existingExplanation\nProvide a more holistic and textbook-based structured reason for why this is the case, and perhaps briefly rule out the incorrect options." else "Provide a complete and structured reasoning for why $correctAnswer is correct, and why other options are incorrect."}
    """.trimIndent()

    val request = GenerateContentRequest(
        contents = listOf(Content(parts = listOf(Part(text = prompt)))),
        systemInstruction = Content(parts = listOf(Part(text = systemInstructionText))),
        generationConfig = GenerationConfig(temperature = 0.3f, responseModalities = listOf("TEXT"))
    )

    try {
        val response = RetrofitClient.service.generateContent(apiKey, request)
        response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "No generated response."
    } catch (e: Exception) {
        "Error generating explanation: ${e.message}"
    }
}
