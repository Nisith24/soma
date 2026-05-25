package com.example

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.example.voice.AudioEngine

val LocalTtsManager = staticCompositionLocalOf<TtsManager?> { null }
val LocalVoiceOrchestrator = staticCompositionLocalOf<com.example.voice.VoiceOrchestrator?> { null }

enum class AudioState {
    IDLE, BUFFERING, PLAYING
}

class TtsManager(private val context: Context) {
    private val _currentTag = MutableStateFlow<String?>(null)
    val currentTag: StateFlow<String?> = _currentTag.asStateFlow()

    private val _audioState = MutableStateFlow(AudioState.IDLE)
    val audioState: StateFlow<AudioState> = _audioState.asStateFlow()

    private val audioEngine by lazy { 
        AudioEngine(context).apply {
            onStartCallback = { tag ->
                if (_currentTag.value == tag) {
                    _audioState.value = AudioState.PLAYING
                }
            }
            onDoneCallback = {
                _audioState.value = AudioState.IDLE
                _currentTag.value = null
            }
            onErrorCallback = {
                _audioState.value = AudioState.IDLE
                _currentTag.value = null
            }
            onNativeTtsStopCallback = {
                _audioState.value = AudioState.IDLE
                _currentTag.value = null
            }
        }
    }

    fun togglePlayPause(tag: String, text: String, pitch: Float, speed: Float) {
        if (_currentTag.value == tag && (_audioState.value == AudioState.PLAYING || _audioState.value == AudioState.BUFFERING)) {
            // Stop playing
            stop()
        } else {
            // Start playing new
            stop() // Stop any current
            _currentTag.value = tag
            _audioState.value = AudioState.BUFFERING
            
            val questionText = if (tag.startsWith("q_")) {
                tag.substring(2)
            } else if (tag.startsWith("e_")) {
                tag.substring(2).removeSuffix("_ai").removeSuffix("_db")
            } else {
                null
            }
            
            audioEngine.speak(text, tag, pitch, speed, deleteFileOnCompletion = false, questionText = questionText)
        }
    }

    fun stop() {
        audioEngine.stop()
        _audioState.value = AudioState.IDLE
        _currentTag.value = null
    }

    fun shutdown() {
        audioEngine.release()
    }
}
