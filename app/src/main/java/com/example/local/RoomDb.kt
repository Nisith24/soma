package com.example.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.McqField
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Entity(tableName = "mcq_questions")
data class McqEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val subject: String?,
    val topic: String?,
    val question: String,
    val options: List<String>,
    val correctAnswer: String,
    val explanation: String?,
    val aiExplanation: String?,
    val images: String?,
    val audio: String?,
    val sourceUrl: String?
)

class McqTypeConverters {
    @TypeConverter
    fun fromList(list: List<String>): String {
        return Json.encodeToString(list)
    }

    @TypeConverter
    fun toList(value: String): List<String> {
        return try {
            Json.decodeFromString(value)
        } catch (e: Exception) {
            emptyList()
        }
    }
}

data class SubjectTopicCount(
    val subject: String?,
    val topic: String?,
    val count: Int
)

data class SourceCount(
    val sourceUrl: String?,
    val count: Int
)

@Dao
interface McqDao {
    @Query("SELECT * FROM mcq_questions ORDER BY id DESC")
    fun getAllQuestions(): Flow<List<McqEntity>>

    @Query("SELECT subject, topic, COUNT(*) as count FROM mcq_questions GROUP BY subject, topic")
    fun getSubjectTopicCounts(): Flow<List<SubjectTopicCount>>

    @Query("SELECT sourceUrl, COUNT(*) as count FROM mcq_questions GROUP BY sourceUrl")
    fun getSourceCounts(): Flow<List<SourceCount>>

    @Query("""
        SELECT subject, topic, COUNT(*) as count 
        FROM mcq_questions 
        WHERE (:sourceUrl IS NULL OR sourceUrl = :sourceUrl) OR (:sourceUrl = '' AND (sourceUrl IS NULL OR sourceUrl = ''))
        GROUP BY subject, topic
    """)
    fun getTopicsForSource(sourceUrl: String): Flow<List<SubjectTopicCount>>

    @Query("""
        SELECT * FROM mcq_questions 
        WHERE (:subject IS NULL OR subject = :subject)
        AND (:topic IS NULL OR topic = :topic)
        AND (:sourceUrl IS NULL OR sourceUrl = :sourceUrl)
        AND (:searchQuery = '' OR question LIKE '%' || :searchQuery || '%' OR subject LIKE '%' || :searchQuery || '%' OR topic LIKE '%' || :searchQuery || '%')
        ORDER BY id DESC LIMIT :limit
    """)
    suspend fun getFilteredQuestions(subject: String?, topic: String?, sourceUrl: String?, searchQuery: String, limit: Int): List<McqEntity>

    @Query("SELECT * FROM mcq_questions ORDER BY id DESC LIMIT 500")
    fun getInitialQuestions(): Flow<List<McqEntity>>

    @Query("SELECT COUNT(*) FROM mcq_questions")
    fun getTotalQuestionCount(): Flow<Int>

    @Query("SELECT * FROM mcq_questions")
    suspend fun getAllQuestionsSync(): List<McqEntity>

    @Query("SELECT * FROM mcq_questions WHERE subject IN (:subjects)")
    suspend fun getQuestionsBySubjects(subjects: List<String>): List<McqEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(questions: List<McqEntity>)

    @Query("DELETE FROM mcq_questions")
    suspend fun deleteAll()
    
    @Query("DELETE FROM mcq_questions WHERE subject = :subject")
    suspend fun deleteBySubject(subject: String)

    @Query("DELETE FROM mcq_questions WHERE sourceUrl = :sourceUrl")
    suspend fun deleteBySource(sourceUrl: String)

    @Query("DELETE FROM mcq_questions WHERE sourceUrl IS NULL OR sourceUrl = ''")
    suspend fun deleteNullSource()

    @Query("UPDATE mcq_questions SET aiExplanation = :aiExplanation WHERE question = :question")
    suspend fun updateAiExplanation(question: String, aiExplanation: String)

    @Query("UPDATE mcq_questions SET audio = :audio WHERE question = :question")
    suspend fun updateVoiceAudio(question: String, audio: String)

    @Query("UPDATE mcq_questions SET aiExplanation = :aiExplanation, audio = :audio WHERE question = :question")
    suspend fun updateAiExplanationAndAudio(question: String, aiExplanation: String, audio: String)
}

@Entity(tableName = "progress_history")
data class ProgressEntity(
    @PrimaryKey val question: String,
    val subject: String?,
    val topic: String?,
    val selectedOption: String,
    val isCorrect: Boolean
)

@Dao
interface ProgressDao {
    @Query("SELECT * FROM progress_history")
    suspend fun getAllProgress(): List<ProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgress(progress: ProgressEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgressList(progressList: List<ProgressEntity>)

    @Query("SELECT * FROM progress_history")
    fun getAllProgressFlow(): Flow<List<ProgressEntity>>

    @Query("DELETE FROM progress_history")
    suspend fun deleteAll()
}

@Database(entities = [McqEntity::class, BookmarkEntity::class, ProgressEntity::class], version = 6, exportSchema = false)
@TypeConverters(McqTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mcqDao(): McqDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun progressDao(): ProgressDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mcq_database"
                ).fallbackToDestructiveMigration(true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val question: String,
    val subject: String?,
    val topic: String?,
    val options: List<String>,
    val correctAnswer: String,
    val explanation: String?,
    val aiExplanation: String?,
    val images: String?,
    val audio: String?,
    val sourceUrl: String?,
    val bookmarkedAt: Long = System.currentTimeMillis()
)

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY bookmarkedAt DESC")
    fun getAllBookmarks(): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE question = :question")
    suspend fun deleteBookmark(question: String)

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE question = :question)")
    fun isBookmarked(question: String): Flow<Boolean>

    @Query("UPDATE bookmarks SET aiExplanation = :aiExplanation WHERE question = :question")
    suspend fun updateAiExplanation(question: String, aiExplanation: String)

    @Query("UPDATE bookmarks SET audio = :audio WHERE question = :question")
    suspend fun updateVoiceAudio(question: String, audio: String)

    @Query("UPDATE bookmarks SET aiExplanation = :aiExplanation, audio = :audio WHERE question = :question")
    suspend fun updateAiExplanationAndAudio(question: String, aiExplanation: String, audio: String)
}

fun McqField.toEntity(): McqEntity {
    return McqEntity(
        subject = this.subject ?: "General",
        topic = this.topic ?: "General",
        question = this.question,
        options = this.options,
        correctAnswer = this.correct_answer,
        explanation = this.explanation,
        aiExplanation = this.ai_explanation,
        images = this.images,
        audio = this.audio,
        sourceUrl = this.source_url
    )
}

fun McqField.toBookmarkEntity(): BookmarkEntity {
    return BookmarkEntity(
        question = this.question,
        subject = this.subject ?: "General",
        topic = this.topic ?: "General",
        options = this.options,
        correctAnswer = this.correct_answer,
        explanation = this.explanation,
        aiExplanation = this.ai_explanation,
        images = this.images,
        audio = this.audio,
        sourceUrl = this.source_url
    )
}

fun McqEntity.toField(): McqField {
    val normalizedCorrect = options.find { opt ->
        val corr = this.correctAnswer
        if (corr.isBlank()) false
        else opt.startsWith("$corr.", ignoreCase = true) ||
             opt.startsWith("$corr)", ignoreCase = true) ||
             opt.startsWith("$corr ", ignoreCase = true) ||
             opt.equals(corr, ignoreCase = true)
    } ?: this.correctAnswer

    return McqField(
        subject = this.subject,
        topic = this.topic,
        question = this.question,
        options = this.options,
        correct_answer = normalizedCorrect,
        explanation = this.explanation,
        ai_explanation = this.aiExplanation,
        images = this.images,
        audio = this.audio,
        source_url = this.sourceUrl
    )
}

fun BookmarkEntity.toField(): McqField {
    val normalizedCorrect = options.find { opt ->
        val corr = this.correctAnswer
        if (corr.isBlank()) false
        else opt.startsWith("$corr.", ignoreCase = true) ||
             opt.startsWith("$corr)", ignoreCase = true) ||
             opt.startsWith("$corr ", ignoreCase = true) ||
             opt.equals(corr, ignoreCase = true)
    } ?: this.correctAnswer

    return McqField(
        subject = this.subject,
        topic = this.topic,
        question = this.question,
        options = this.options,
        correct_answer = normalizedCorrect,
        explanation = this.explanation,
        ai_explanation = this.aiExplanation,
        images = this.images,
        audio = this.audio,
        source_url = this.sourceUrl
    )
}
