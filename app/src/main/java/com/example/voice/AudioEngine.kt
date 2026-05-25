package com.example.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

class AudioEngine(private val context: Context) {
    var tts: TextToSpeech? = null
        private set
        
    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val geminiTtsService by lazy { GeminiTtsService(context) }
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: Any? = null

    
    var isTtsReady = false
        private set

    var onStartCallback: ((String?) -> Unit)? = null
    var onDoneCallback: ((String?) -> Unit)? = null
    var onErrorCallback: ((String?) -> Unit)? = null
    var onRangeStartCallback: ((String?, Int, Int) -> Unit)? = null
    var onNativeTtsStopCallback: (() -> Unit)? = null

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                try {
                    val attributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                    tts?.setAudioAttributes(attributes)
                } catch (e: Exception) {}

                tts?.setLanguage(Locale.US)
                val voices = tts?.voices
                if (!voices.isNullOrEmpty()) {
                    val matchingVoices = voices.filter { it.locale?.language == "en" }
                    val bestVoice = matchingVoices.firstOrNull { !it.isNetworkConnectionRequired && it.quality >= 400 }
                        ?: matchingVoices.firstOrNull { it.quality >= 400 }
                        ?: matchingVoices.maxByOrNull { it.quality }
                        ?: matchingVoices.firstOrNull()
                    if (bestVoice != null) tts?.voice = bestVoice
                }
                isTtsReady = true
                setupTtsListener()
            }
        }
    }

    private fun requestAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attributes)
                    .setOnAudioFocusChangeListener { focusChange ->
                        if (focusChange == AudioManager.AUDIOFOCUS_LOSS) stop()
                    }
                    .build()
                audioFocusRequest = request
                audioManager.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    { focusChange -> if (focusChange == AudioManager.AUDIOFOCUS_LOSS) stop() },
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
            }
        } catch (e: Exception) {}
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                (audioFocusRequest as? AudioFocusRequest)?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (e: Exception) {}
    }

    private fun setupTtsListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                onStartCallback?.invoke(utteranceId)
            }
            override fun onDone(utteranceId: String?) {
                abandonAudioFocus()
                onDoneCallback?.invoke(utteranceId)
            }
            override fun onError(utteranceId: String?) {
                abandonAudioFocus()
                onErrorCallback?.invoke(utteranceId)
            }
            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                onRangeStartCallback?.invoke(utteranceId, start, end)
            }
            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                abandonAudioFocus()
                onNativeTtsStopCallback?.invoke()
            }
        })
    }

    fun cleanText(rawText: String): String {
        var cleaned = rawText
        cleaned = cleaned.replace(Regex("(?i)<br\\s*/?>"), " ")
        cleaned = cleaned.replace(Regex("(?i)</p>"), " ")
        cleaned = cleaned.replace(Regex("(?i)</div>"), " ")
        if (cleaned.trim().startsWith("{") && cleaned.trim().endsWith("}")) {
            try { cleaned = cleaned.replace(Regex("[\"{}]"), "") } catch (e: Exception) {}
        }
        cleaned = cleaned.replace(Regex("<[^>]*>"), "")
        cleaned = cleaned.replace(Regex("\\*\\*|__"), "")
        cleaned = cleaned.replace(Regex("\\*|_"), "")
        cleaned = cleaned.replace(Regex("(?m)^\\s*#+\\s+"), "")
        cleaned = cleaned.replace(Regex("(?m)^\\s*-\\s+"), "")
        cleaned = cleaned.replace(Regex("`{1,3}"), "")
        cleaned = cleaned.replace(Regex("\\s+"), " ")
        return cleaned.trim()
    }

    fun speak(text: String, utteranceId: String, pitch: Float = 1.0f, speed: Float = 1.0f, deleteFileOnCompletion: Boolean = false, questionText: String? = null) {
        val cleaned = cleanText(text)
        
        val prefs = context.getSharedPreferences("mcq_prefs", Context.MODE_PRIVATE)
        val useAiVoice = prefs.getBoolean("use_ai_voice", false)
        val apiKey = prefs.getString("gemini_api_key", "") ?: ""

        if (useAiVoice && apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
            val voice = prefs.getString("gemini_voice", "Kore") ?: "Kore"
            
            scope.launch {
                val audioFile = geminiTtsService.synthesizeSpeech(cleaned, apiKey, voice)
                if (audioFile != null) {
                    if (questionText != null) {
                        saveAudioPathToDb(questionText, audioFile.absolutePath)
                    }
                    playAudioFile(audioFile, utteranceId, deleteFileOnCompletion)
                } else {
                    speakNative(cleaned, utteranceId, pitch, speed)
                }
            }
        } else {
            speakNative(cleaned, utteranceId, pitch, speed)
        }
    }

    private fun saveAudioPathToDb(questionText: String, audioPath: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val db = com.example.local.AppDatabase.getInstance(context)
                
                db.mcqDao().updateVoiceAudio(questionText, audioPath)
                db.bookmarkDao().updateVoiceAudio(questionText, audioPath)
            } catch (e: Exception) {
                android.util.Log.e("AudioEngine", "Error saving voice audio path to DB", e)
            }
        }
    }

    private fun playAudioFile(file: File, utteranceId: String, deleteOnCompletion: Boolean) {
        try {
            mediaPlayer?.release()
            requestAudioFocus()
            onStartCallback?.invoke(utteranceId)
            
            mediaPlayer = MediaPlayer().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val attrs = AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                    setAudioAttributes(attrs)
                }
                setDataSource(file.absolutePath)
                setOnPreparedListener { start() }
                setOnCompletionListener {
                    abandonAudioFocus()
                    onDoneCallback?.invoke(utteranceId)
                    if (deleteOnCompletion) file.delete()
                }
                setOnErrorListener { _, _, _ ->
                    abandonAudioFocus()
                    onErrorCallback?.invoke(utteranceId)
                    if (deleteOnCompletion) file.delete()
                    false
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            abandonAudioFocus()
            onErrorCallback?.invoke(utteranceId)
        }
    }

    private fun speakNative(text: String, utteranceId: String, pitch: Float, speed: Float) {
        requestAudioFocus()
        tts?.apply {
            setPitch(pitch)
            setSpeechRate(speed)
            speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        }
    }

    fun stop() {
        abandonAudioFocus()
        tts?.stop()
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
    }

    fun release() {
        stop()
        tts?.shutdown()
    }
}
