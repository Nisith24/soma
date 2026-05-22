package com.example

import kotlinx.serialization.Serializable

@Serializable
sealed class Screen {
    @Serializable data object Home : Screen()
    @Serializable data object Profile : Screen()
    @Serializable data object Statistics : Screen()
    @Serializable data object Auth : Screen()
    @Serializable data object JsonUpload : Screen()
}

@Serializable
data class McqResponse(
    val status: String? = null,
    val saved_to: String? = null,
    val results: List<McqResult> = emptyList()
)

@Serializable
data class McqResult(
    val url: String? = null,
    val fields: List<McqField> = emptyList()
)

@Serializable
data class McqField(
    val subject: String? = null,
    val topic: String? = null,
    val question: String = "",
    val options: List<String> = emptyList(),
    val correct_answer: String = "",
    val explanation: String? = null,
    val images: String? = null,
    val audio: String? = null,
    val source_url: String? = null
)

enum class AppThemeMode {
    LIGHT, DARK, SYSTEM
}

@Serializable
enum class NotificationUrgency {
    LOW, MEDIUM, HIGH
}

@Serializable
data class AppNotification(
    val id: String,
    val title: String,
    val text: String,
    val category: String, // "goal", "streak", "last_active", "subject", "general"
    val urgency: NotificationUrgency = NotificationUrgency.MEDIUM,
    val actionLabel: String? = null,
    val actionType: String? = null, // "practice_subject", "set_goal", "see_stats", "view_bookmarks", "reset_progress"
    val actionParam: String? = null
)

sealed class McqUiState {
    data object Loading : McqUiState()
    data class Success(val questions: List<McqField>) : McqUiState()
    data class Error(val message: String) : McqUiState()
}

data class StudyStats(
    val totalAnswered: Int = 0,
    val correctCount: Int = 0,
    val accuracyPercentage: Int = 0
)

data class TopicStats(
    val total: Int = 0,
    val finished: Int = 0
)

data class ExamSettings(
    val isExamMode: Boolean = false,
    val questionCount: Int = 0,
    val timeLimitMinutes: Int = 0,
    val negativeMarking: Boolean = false,
    val sessionStartTimeMs: Long = 0L
)

fun formatSourceDisplayName(source: String?): String {
    if (source.isNullOrEmpty()) return "Unknown Source"
    if (source == "Legacy / Untagged" || source == "Quick Prep Mocks / Legacy") return "Quick Prep Mocks / Legacy"
    
    return try {
        val clean = source.substringAfterLast('/').substringBefore('?').substringBefore('#')
        val name = clean.ifBlank { source.substringAfter("://").substringBefore('/') }.ifBlank { source }
        val nameWithoutSuffix = name.replace(Regex("(?i)\\.(json|csv|txt|pdf|xml)$"), "")
        val decoded = java.net.URLDecoder.decode(nameWithoutSuffix, "UTF-8").replace(Regex("[_\\-]"), " ")
        decoded.split(' ').joinToString(" ") { it.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase() else c.toString() } }.trim().ifBlank { source }
    } catch (e: Exception) {
        source
    }
}

