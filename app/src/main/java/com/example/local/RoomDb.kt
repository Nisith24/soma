package com.example.local

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

@Dao
interface McqDao {
    @Query("SELECT * FROM mcq_questions ORDER BY id DESC")
    fun getAllQuestions(): Flow<List<McqEntity>>

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
}

@Database(entities = [McqEntity::class, BookmarkEntity::class], version = 5, exportSchema = false)
@TypeConverters(McqTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mcqDao(): McqDao
    abstract fun bookmarkDao(): BookmarkDao
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
    return McqField(
        subject = this.subject,
        topic = this.topic,
        question = this.question,
        options = this.options,
        correct_answer = this.correctAnswer,
        explanation = this.explanation,
        ai_explanation = this.aiExplanation,
        images = this.images,
        audio = this.audio,
        source_url = this.sourceUrl
    )
}

fun BookmarkEntity.toField(): McqField {
    return McqField(
        subject = this.subject,
        topic = this.topic,
        question = this.question,
        options = this.options,
        correct_answer = this.correctAnswer,
        explanation = this.explanation,
        ai_explanation = this.aiExplanation,
        images = this.images,
        audio = this.audio,
        source_url = this.sourceUrl
    )
}
