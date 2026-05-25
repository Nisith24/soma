package com.example.utils

import android.content.Context
import android.net.Uri
import com.example.McqField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

import java.io.FileOutputStream
import java.util.zip.ZipInputStream

@Serializable
data class ExportedDatabase(
    val formatVersion: Int = 1,
    val questions: List<McqField>,
    val progress: List<ExportedProgress>,
    val settings: Map<String, String> // We can leave this empty if needed
)

@Serializable
data class ExportedProgress(
    val question: String,
    val subject: String?,
    val topic: String?,
    val selectedOption: String,
    val isCorrect: Boolean
)

object ZipExportUtils {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    suspend fun exportDatabaseToZip(
        context: Context,
        uri: Uri,
        allQuestions: List<McqField>,
        allProgress: List<ExportedProgress>,
        settings: Map<String, String>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                ZipOutputStream(outputStream).use { zos ->
                    // 1. Write the main JSON DB file
                    val dbExport = ExportedDatabase(
                        formatVersion = 1,
                        questions = allQuestions,
                        progress = allProgress,
                        settings = settings
                    )
                    
                    val jsonString = json.encodeToString(dbExport)
                    zos.putNextEntry(ZipEntry("database.json"))
                    zos.write(jsonString.toByteArray())
                    zos.closeEntry()
                    
                    // 2. Include AI Voice files
                    val audioDir = context.getDir("ai_audio_forever", Context.MODE_PRIVATE)
                    if (audioDir.exists() && audioDir.isDirectory) {
                        val audioFiles = audioDir.listFiles()
                        if (audioFiles != null) {
                            for (file in audioFiles) {
                                if (file.isFile) {
                                    zos.putNextEntry(ZipEntry("ai_audio_forever/${file.name}"))
                                    FileInputStream(file).use { fis ->
                                        fis.copyTo(zos)
                                    }
                                    zos.closeEntry()
                                }
                            }
                        }
                    }
                    
                    zos.flush()
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun importDatabaseFromZip(
        context: Context,
        uri: Uri
    ): ExportedDatabase? = withContext(Dispatchers.IO) {
        var exportedDatabase: ExportedDatabase? = null
        try {
            val contentResolver = context.contentResolver
            contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        if (entry.name == "database.json") {
                            val jsonString = String(zis.readBytes())
                            exportedDatabase = json.decodeFromString<ExportedDatabase>(jsonString)
                        } else if (entry.name.startsWith("ai_audio_forever/")) {
                            val fileName = entry.name.substring("ai_audio_forever/".length)
                            if (fileName.isNotEmpty()) {
                                val audioDir = context.getDir("ai_audio_forever", Context.MODE_PRIVATE)
                                val file = File(audioDir, fileName)
                                FileOutputStream(file).use { fos ->
                                    zis.copyTo(fos)
                                }
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
            exportedDatabase
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
