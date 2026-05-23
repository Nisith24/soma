package com.example

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import android.media.MediaPlayer

val LocalTtsManager = staticCompositionLocalOf<TtsManager?> { null }

enum class AudioState {
    IDLE, BUFFERING, PLAYING
}

class TtsManager(private val context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _currentTag = MutableStateFlow<String?>(null)
    val currentTag: StateFlow<String?> = _currentTag.asStateFlow()

    private val _audioState = MutableStateFlow(AudioState.IDLE)
    val audioState: StateFlow<AudioState> = _audioState.asStateFlow()

    private var currentText: String? = null
    private var isPaused = false

    private val aiExplanationService = AiExplanationService()
    private var activeMediaPlayer: MediaPlayer? = null
    private val ioScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TtsManager", "Language not supported")
            } else {
                isInitialized = true
                setupProgressListener()
                enhanceVoiceQuality()
            }
        } else {
            Log.e("TtsManager", "Initialization failed")
        }
    }
    
    private fun enhanceVoiceQuality() {
        tts?.let { textToSpeech ->
            try {
                // Try to find a higher quality local voice for English if available
                val voices = textToSpeech.voices
                if (voices != null) {
                    val highQualityVoice = voices.firstOrNull { 
                        it.locale.language == "en" && !it.isNetworkConnectionRequired
                    }
                    if (highQualityVoice != null) {
                        textToSpeech.voice = highQualityVoice
                    }
                }
            } catch (e: Exception) {
                Log.e("TtsManager", "Failed to set custom voice: ${e.message}")
            }
        }
    }

    private fun setupProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _audioState.value = AudioState.PLAYING
            }

            override fun onDone(utteranceId: String?) {
                _audioState.value = AudioState.IDLE
                _currentTag.value = null
            }

            override fun onError(utteranceId: String?) {
                _audioState.value = AudioState.IDLE
                _currentTag.value = null
            }
            
            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                if (!isPaused) {
                    _audioState.value = AudioState.IDLE
                    _currentTag.value = null
                }
            }
        })
    }

    fun togglePlayPause(tag: String, text: String, pitch: Float, speed: Float) {
        if (!isInitialized) return

        if (_currentTag.value == tag && (_audioState.value == AudioState.PLAYING || _audioState.value == AudioState.BUFFERING)) {
            // Stop playing
            stop()
        } else {
            // Start playing new
            stop() // Stop any current
            _currentTag.value = tag
            val cleaned = cleanText(text)
            currentText = cleaned
            speak(cleaned, pitch, speed, tag)
        }
    }

    private fun cleanText(rawText: String): String {
        var cleaned = rawText
        // Replace break tags and paragraph end tags with space first
        cleaned = cleaned.replace(Regex("(?i)<br\\s*/?>"), " ")
        cleaned = cleaned.replace(Regex("(?i)</p>"), " ")
        cleaned = cleaned.replace(Regex("(?i)</div>"), " ")
        
        // Strip any remaining HTML tags
        cleaned = cleaned.replace(Regex("<[^>]*>"), "")
        
        // Strip Markdown bold/italic
        cleaned = cleaned.replace(Regex("\\*\\*|__"), "")
        cleaned = cleaned.replace(Regex("\\*|_"), "")
        
        // Strip markdown headers, lists, code block backticks
        cleaned = cleaned.replace(Regex("(?m)^\\s*#+\\s+"), "")
        cleaned = cleaned.replace(Regex("(?m)^\\s*-\\s+"), "")
        cleaned = cleaned.replace(Regex("`{1,3}"), "")
        
        // Clean multiple white spaces and newlines
        cleaned = cleaned.replace(Regex("\\s+"), " ")
        
        return cleaned.trim()
    }

    private fun speak(text: String, pitch: Float, speed: Float, tag: String) {
        tts?.apply {
            setPitch(pitch)
            setSpeechRate(speed)
            speak(text, TextToSpeech.QUEUE_FLUSH, null, tag)
        }
    }

    private fun addWavHeader(pcmBytes: ByteArray, sampleRate: Int = 24000): ByteArray {
        val totalSize = pcmBytes.size + 36
        val byteRate = sampleRate * 2 // 16-bit mono = 2 bytes/sample
        val header = ByteArray(44)
        
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        
        header[4] = (totalSize and 0xff).toByte()
        header[5] = ((totalSize shr 8) and 0xff).toByte()
        header[6] = ((totalSize shr 16) and 0xff).toByte()
        header[7] = ((totalSize shr 24) and 0xff).toByte()
        
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        
        header[16] = 16 // Subchunk1Size is 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        
        header[20] = 1 // AudioFormat = 1 (PCM)
        header[21] = 0
        
        header[22] = 1 // NumChannels = 1 (Mono)
        header[23] = 0
        
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        
        header[32] = 2 // BlockAlign = 2
        header[33] = 0
        
        header[34] = 16 // BitsPerSample = 16
        header[35] = 0
        
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        
        val dataSize = pcmBytes.size
        header[40] = (dataSize and 0xff).toByte()
        header[41] = ((dataSize shr 8) and 0xff).toByte()
        header[42] = ((dataSize shr 16) and 0xff).toByte()
        header[43] = ((dataSize shr 24) and 0xff).toByte()
        
        return header + pcmBytes
    }

    private fun triggerNativeFallback(text: String, pitch: Float, speed: Float, tag: String) {
        Log.i("TtsManager", "Triggering native TTS fallback for tag: $tag")
        ioScope.launch(Dispatchers.Main) {
            if (_currentTag.value == tag) {
                _audioState.value = AudioState.BUFFERING
                speak(text, pitch, speed, tag)
            }
        }
    }

    fun playGeminiTts(
        tag: String,
        text: String,
        apiKey: String,
        voiceName: String,
        pitch: Float = 1.0f,
        speed: Float = 1.0f
    ) {
        if (_currentTag.value == tag && (_audioState.value == AudioState.PLAYING || _audioState.value == AudioState.BUFFERING)) {
            stop()
            return
        }

        stop()

        _currentTag.value = tag
        _audioState.value = AudioState.BUFFERING

        val cleaned = cleanText(text)

        ioScope.launch {
            try {
                val base64Audio = aiExplanationService.generateTtsAudioBase64(cleaned, apiKey, voiceName)
                if (base64Audio != null) {
                    if (_currentTag.value == tag) {
                        val bytes = android.util.Base64.decode(base64Audio, android.util.Base64.DEFAULT)
                        val tempFile = java.io.File.createTempFile("gemini_tts", ".mp3", context.cacheDir)
                        
                        var mediaPlayer = MediaPlayer()
                        var prepared = false
                        
                        // Try default format
                        try {
                            java.io.FileOutputStream(tempFile).use { it.write(bytes) }
                            mediaPlayer.setDataSource(tempFile.absolutePath)
                            mediaPlayer.prepare()
                            prepared = true
                        } catch (e: Exception) {
                            Log.w("TtsManager", "Default format failed to prepare, wrapping inside WAV header...", e)
                            mediaPlayer.release()
                            mediaPlayer = MediaPlayer()
                            try {
                                val wavBytes = addWavHeader(bytes, 24000)
                                java.io.FileOutputStream(tempFile).use { it.write(wavBytes) }
                                mediaPlayer.setDataSource(tempFile.absolutePath)
                                mediaPlayer.prepare()
                                prepared = true
                            } catch (e2: Exception) {
                                Log.e("TtsManager", "Fails to prepare both direct-media and raw pcm WAV wrapper", e2)
                            }
                        }

                        if (prepared && _currentTag.value == tag) {
                            activeMediaPlayer = mediaPlayer
                            _audioState.value = AudioState.PLAYING
                            mediaPlayer.start()
                            mediaPlayer.setOnCompletionListener {
                                it.release()
                                if (activeMediaPlayer == it) {
                                    activeMediaPlayer = null
                                }
                                tempFile.delete()
                                if (_currentTag.value == tag) {
                                    _audioState.value = AudioState.IDLE
                                    _currentTag.value = null
                                }
                            }
                        } else {
                            mediaPlayer.release()
                            tempFile.delete()
                            if (_currentTag.value == tag) {
                                triggerNativeFallback(cleaned, pitch, speed, tag)
                            }
                        }
                    }
                } else {
                    if (_currentTag.value == tag) {
                        triggerNativeFallback(cleaned, pitch, speed, tag)
                    }
                }
            } catch (e: Exception) {
                Log.e("TtsManager", "Gemini TTS playback failed overall, falling back", e)
                if (_currentTag.value == tag) {
                    triggerNativeFallback(cleaned, pitch, speed, tag)
                }
            }
        }
    }

    fun stop() {
        if (_audioState.value == AudioState.PLAYING && activeMediaPlayer == null) {
            tts?.stop()
        }
        activeMediaPlayer?.let {
            try {
                if (it.isPlaying) {
                    it.stop()
                }
            } catch (e: Exception) {
                Log.e("TtsManager", "Error stopping active media player", e)
            } finally {
                it.release()
            }
        }
        activeMediaPlayer = null

        _audioState.value = AudioState.IDLE
        _currentTag.value = null
        currentText = null
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
