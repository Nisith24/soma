package com.example

import android.util.JsonReader
import android.util.JsonToken
import java.io.InputStream
import java.io.InputStreamReader

object JsonStreamParser {
    
    fun parseMultiple(inputStreams: List<Pair<String, InputStream>>, mediaDir: java.io.File? = null): List<McqField> {
        val allFields = mutableListOf<McqField>()
        for ((fileName, stream) in inputStreams) {
            try {
                val reader = JsonReader(InputStreamReader(stream, "UTF-8"))
                reader.isLenient = true
                parseElement(reader, fileName, allFields, mediaDir)
                reader.close()
            } catch (e: Exception) {
                android.util.Log.e("JsonStreamParser", "Failed to parse stream: $fileName", e)
            } finally {
                try { stream.close() } catch(e:Exception){}
            }
        }
        return allFields.distinctBy { it.question.trim().lowercase() }
    }

    private fun parseElement(reader: JsonReader, fileName: String, output: MutableList<McqField>, mediaDir: java.io.File?) {
        if (!reader.hasNext()) return
        val token = reader.peek()
        if (token == JsonToken.BEGIN_ARRAY) {
            reader.beginArray()
            while (reader.hasNext()) {
                val nextToken = reader.peek()
                if (nextToken == JsonToken.BEGIN_OBJECT) {
                    // Could be a McqField or McqResult wrapper
                    parseObjectOrWrapper(reader, fileName, output, mediaDir)
                } else if (nextToken == JsonToken.BEGIN_ARRAY) {
                    parseElement(reader, fileName, output, mediaDir) // Nested array
                } else {
                    reader.skipValue()
                }
            }
            reader.endArray()
        } else if (token == JsonToken.BEGIN_OBJECT) {
            reader.beginObject()
            while (reader.hasNext()) {
                val name = reader.nextName()
                if (name == "results" || name == "data" || name == "fields") {
                    if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                        parseElement(reader, fileName, output, mediaDir)
                    } else {
                        reader.skipValue()
                    }
                } else {
                    reader.skipValue()
                }
            }
            reader.endObject()
        } else {
            reader.skipValue()
        }
    }

    private fun safeNextString(reader: JsonReader): String {
        return try {
            val token = reader.peek()
            if (token == JsonToken.NULL) {
                reader.nextNull()
                ""
            } else if (token == JsonToken.BOOLEAN) {
                reader.nextBoolean().toString()
            } else if (token == JsonToken.NUMBER || token == JsonToken.STRING) {
                reader.nextString()
            } else {
                reader.skipValue()
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun saveMediaIfBase64(data: String, mediaDir: java.io.File?): String {
        if (mediaDir == null || data.isBlank()) return data
        if (data.startsWith("http://") || data.startsWith("https://") || data.startsWith("file://")) {
            return data
        }

        var base64Content = data
        var ext = "bin"
        if (data.startsWith("data:")) {
            val commaIndex = data.indexOf(',')
            if (commaIndex > -1) {
                val header = data.substring(0, commaIndex)
                if (header.contains("image/png")) ext = "png"
                else if (header.contains("image/jpeg") || header.contains("image/jpg")) ext = "jpg"
                else if (header.contains("image/gif")) ext = "gif"
                else if (header.contains("image/webp")) ext = "webp"
                else if (header.contains("audio/mpeg") || header.contains("audio/mp3")) ext = "mp3"
                else if (header.contains("audio/mp4")) ext = "mp4"
                else if (header.contains("audio/wav")) ext = "wav"
                
                base64Content = data.substring(commaIndex + 1)
            }
        } else {
            if (base64Content.length < 100) return data
        }

        try {
            val decodedBytes = android.util.Base64.decode(base64Content, android.util.Base64.DEFAULT)
            if (decodedBytes.isNotEmpty()) {
                if (!mediaDir.exists()) mediaDir.mkdirs()
                val fileName = "media_${java.util.UUID.randomUUID()}.$ext"
                val file = java.io.File(mediaDir, fileName)
                file.writeBytes(decodedBytes)
                return "file://${file.absolutePath}"
            }
        } catch (e: Exception) {
            // Not a valid base64
        }
        return data
    }

    private fun decodeExplanationIfBase64(data: String): String {
        if (data.isBlank()) return data
        if (data.startsWith("http://") || data.startsWith("https://") || data.startsWith("file://")) {
            return data
        }

        var base64Content = data
        if (data.startsWith("data:")) {
            val commaIndex = data.indexOf(',')
            if (commaIndex > -1) {
                base64Content = data.substring(commaIndex + 1)
            }
        }

        try {
            val decodedBytes = android.util.Base64.decode(base64Content, android.util.Base64.DEFAULT)
            if (decodedBytes.isNotEmpty()) {
                val decodedString = String(decodedBytes, kotlin.text.Charsets.UTF_8)
                var controlCharsCount = 0
                for (char in decodedString) {
                    if (char.code < 32 && char != '\n' && char != '\r' && char != '\t') {
                        controlCharsCount++
                    }
                }
                if (controlCharsCount * 10 < decodedString.length) {
                    return decodedString
                }
            }
        } catch (e: Exception) {
            // Decodes failed or non-base64 text
        }
        return data
    }

    private fun parseObjectOrWrapper(reader: JsonReader, fileName: String, output: MutableList<McqField>, mediaDir: java.io.File?) {
        reader.beginObject()
        
        var isWrapper = false
        val field = McqFieldBuilder()
        
        while (reader.hasNext()) {
            val name = reader.nextName()
            if (name == "fields" && reader.peek() == JsonToken.BEGIN_ARRAY) {
                isWrapper = true
                parseElement(reader, fileName, output, mediaDir)
            } else if (!isWrapper) {
                // Parse as McqField
                val token = reader.peek()
                if (token == JsonToken.NULL) {
                    reader.nextNull()
                } else {
                    when (name) {
                        "subject" -> field.subject = safeNextString(reader)
                        "topic" -> field.topic = safeNextString(reader)
                        "question" -> field.question = safeNextString(reader)
                        "correct_answer" -> field.correctAnswer = safeNextString(reader)
                        "explanation" -> field.explanation = decodeExplanationIfBase64(safeNextString(reader))
                        "source_url" -> field.sourceUrl = safeNextString(reader)
                        "images" -> field.images = saveMediaIfBase64(safeNextString(reader), mediaDir)
                        "audio" -> field.audio = saveMediaIfBase64(safeNextString(reader), mediaDir)
                        "options" -> {
                            if (token == JsonToken.BEGIN_ARRAY) {
                                val opts = mutableListOf<String>()
                                reader.beginArray()
                                while (reader.hasNext()) {
                                    val optStr = safeNextString(reader)
                                    if (optStr.isNotEmpty()) opts.add(optStr)
                                }
                                reader.endArray()
                                field.options = opts
                            } else {
                                reader.skipValue()
                            }
                        }
                        else -> reader.skipValue()
                    }
                }
            } else {
                reader.skipValue()
            }
        }
        reader.endObject()
        
        if (!isWrapper && field.question.isNotBlank()) {
            output.add(
                McqField(
                    subject = field.subject?.takeIf { it.isNotBlank() },
                    topic = field.topic?.takeIf { it.isNotBlank() },
                    question = field.question,
                    correct_answer = field.correctAnswer,
                    explanation = field.explanation?.takeIf { it.isNotBlank() },
                    images = field.images?.takeIf { it.isNotBlank() },
                    audio = field.audio?.takeIf { it.isNotBlank() },
                    options = field.options ?: emptyList(),
                    source_url = field.sourceUrl?.takeIf { it.isNotBlank() } ?: fileName
                )
            )
        }
    }

    private class McqFieldBuilder {
        var subject: String? = null
        var topic: String? = null
        var question: String = ""
        var correctAnswer: String = ""
        var explanation: String? = null
        var sourceUrl: String? = null
        var images: String? = null
        var audio: String? = null
        var options: List<String>? = null
    }
}
