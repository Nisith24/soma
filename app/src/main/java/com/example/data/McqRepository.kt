package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.McqField
import com.example.AppThemeMode
import com.example.local.AppDatabase
import com.example.local.BookmarkEntity
import com.example.local.toBookmarkEntity
import com.example.local.toEntity
import com.example.local.toField
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class McqRepository(private val context: Context, private val db: AppDatabase) {
    private val prefs: SharedPreferences = context.getSharedPreferences("mcq_prefs", Context.MODE_PRIVATE)
    private val mcqDao = db.mcqDao()
    private val bookmarkDao = db.bookmarkDao()

    suspend fun getAiExplanation(questionText: String): String? {
        return db.aiExplanationDao().getAiExplanation(questionText)
    }

    suspend fun insertAiExplanation(entity: com.example.local.AiExplanationEntity) {
        db.aiExplanationDao().insertAiExplanation(entity)
    }

    fun getAllQuestions(): Flow<List<McqField>> {
        return mcqDao.getAllQuestions().map { entities -> entities.map { it.toField() } }
    }

    fun getAllBookmarks(): Flow<List<McqField>> {
        return bookmarkDao.getAllBookmarks().map { entities -> entities.map { it.toField() } }
    }

    suspend fun insertQuestions(fields: List<McqField>) {
        mcqDao.insertAll(fields.map { it.toEntity() })
    }

    suspend fun deleteQuestionsBySource(sourceName: String?) {
        if (sourceName == null) {
            mcqDao.deleteNullSource()
        } else {
            mcqDao.deleteBySource(sourceName)
        }
    }

    suspend fun toggleBookmark(question: McqField, isBookmarked: Boolean) {
        if (isBookmarked) {
            bookmarkDao.deleteBookmark(question.question)
        } else {
            bookmarkDao.insertBookmark(question.toBookmarkEntity())
        }
    }

    fun getDisplayName(): String = prefs.getString("display_name", "Nisith Praveen") ?: "Nisith Praveen"
    fun getEmail(): String = prefs.getString("email", "nisithpraveen@gmail.com") ?: "nisithpraveen@gmail.com"
    fun getDailyGoal(): Int = prefs.getInt("daily_goal", 10)
    fun getCustomTheme(): AppThemeMode {
        val themeStr = prefs.getString("custom_theme", AppThemeMode.LIGHT.name) ?: AppThemeMode.LIGHT.name
        return try { AppThemeMode.valueOf(themeStr) } catch (e: Exception) { AppThemeMode.LIGHT }
    }

    fun saveProfile(name: String, email: String) {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            prefs.edit().putString("display_name", name).putString("email", email).commit()
        }
    }

    fun saveDailyGoal(goal: Int) {
        prefs.edit().putInt("daily_goal", goal).apply()
    }

    fun saveTheme(theme: AppThemeMode) {
        prefs.edit().putString("custom_theme", theme.name).apply()
    }

    fun areDefaultsLoaded(): Boolean = prefs.getBoolean("defaults_loaded", false)
    fun setDefaultsLoaded(loaded: Boolean) {
        prefs.edit().putBoolean("defaults_loaded", loaded).apply()
    }

    fun getStreakCount(): Int = prefs.getInt("streak_count", 0)
    fun saveStreakCount(streak: Int) {
        prefs.edit().putInt("streak_count", streak).apply()
    }

    fun getLastActiveTime(): Long = prefs.getLong("last_active_time", 0L)
    fun saveLastActiveTime(time: Long) {
        prefs.edit().putLong("last_active_time", time).apply()
    }

    fun getAnswersToday(): Int = prefs.getInt("answers_today", 0)
    fun saveAnswersToday(count: Int) {
        prefs.edit().putInt("answers_today", count).apply()
    }

    fun getLastAnsweredDate(): String = prefs.getString("last_answered_date", "") ?: ""
    fun saveLastAnsweredDate(dateStr: String) {
        prefs.edit().putString("last_answered_date", dateStr).apply()
    }

    fun getSavedOptions(): Map<String, String> {
        val mapStr = prefs.getString("saved_options", null)
        if (mapStr.isNullOrEmpty()) return emptyMap()
        return try {
            Json.decodeFromString(mapStr)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun saveSelectedOptions(options: Map<String, String>) {
        prefs.edit().putString("saved_options", Json.encodeToString(options)).apply()
    }
}
