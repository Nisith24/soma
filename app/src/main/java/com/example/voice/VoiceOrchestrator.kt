package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class VoiceOrchestrator(private val context: Context) {

    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _transcript = MutableStateFlow("")
    val transcript: StateFlow<String> = _transcript.asStateFlow()
    
    private val _voiceError = MutableStateFlow<String?>(null)
    val voiceError: StateFlow<String?> = _voiceError.asStateFlow()

    private val _micLevel = MutableStateFlow(0f)
    val micLevel: StateFlow<Float> = _micLevel.asStateFlow()
    
    private val _activeUtteranceId = MutableStateFlow<String?>(null)
    val activeUtteranceId: StateFlow<String?> = _activeUtteranceId.asStateFlow()
    
    private val _activeUtteranceRange = MutableStateFlow<IntRange?>(null)
    val activeUtteranceRange: StateFlow<IntRange?> = _activeUtteranceRange.asStateFlow()

    private val _currentSpokenWord = MutableStateFlow<String?>(null)
    val currentSpokenWord: StateFlow<String?> = _currentSpokenWord.asStateFlow()

    private val spokenTexts = java.util.concurrent.ConcurrentHashMap<String, String>()

    private val _thinkTimeRemaining = MutableStateFlow<Int?>(null)
    val thinkTimeRemaining: StateFlow<Int?> = _thinkTimeRemaining.asStateFlow()

    private var questionListeningStartTime = 0L
    private var isListeningToQuestion = false
    private var thinkTimerJob: kotlinx.coroutines.Job? = null

    private var speechRecognizer: SpeechRecognizer? = null
    private val audioEngine by lazy { 
        AudioEngine(context).apply {
            onStartCallback = { utteranceId ->
                isTtsSpeaking = true
                _activeUtteranceId.value = utteranceId
                _activeUtteranceRange.value = null
                _currentSpokenWord.value = null
            }
            onRangeStartCallback = { utteranceId, start, end ->
                _activeUtteranceId.value = utteranceId
                _activeUtteranceRange.value = start..end
                if (utteranceId != null) {
                    val fullText = spokenTexts[utteranceId]
                    if (fullText != null) {
                        val safeStart = start.coerceIn(0, fullText.length)
                        val safeEnd = end.coerceIn(safeStart, fullText.length)
                        if (safeStart < safeEnd) {
                            _currentSpokenWord.value = fullText.substring(safeStart, safeEnd)
                        }
                    }
                }
            }
            onDoneCallback = { utteranceId ->
                handleUtteranceDone(utteranceId)
            }
            onErrorCallback = { utteranceId ->
                isTtsSpeaking = false
                _activeUtteranceId.value = null
                _activeUtteranceRange.value = null
                _currentSpokenWord.value = null
                setVoiceState(VoiceState.ERROR, "AudioEngine Error")
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var onAnswerCallback: ((String) -> Unit)? = null
    private var isWaitingForExplanation: Boolean = false

    private var options: List<String> = emptyList()
    private var questionText: String = ""
    private var correctAnswer: String = ""
    private var explanation: String = ""
    private var currentSpeed: Float = 1.0f
    private var currentPitch: Float = 1.0f

    // Helper flag to ignore TTS completion if we stopped it manually
    private var isTtsSpeaking = false
    private var pendingSessionStart = false
    private var silenceRetries = 0

    init {
        // AudioEngine initializes itself on creation
        initializeSpeechRecognizer()
    }

    private fun initializeSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                setupRecognitionListener()
            } catch (e: Exception) {
                Log.e("VoiceOrchestrator", "Error initializing global speech recognizer", e)
            }
        }
    }

    private fun setupRecognitionListener() {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                _micLevel.value = rmsdB
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                _micLevel.value = 0f
            }
            override fun onError(error: Int) {
                _micLevel.value = 0f
                if (_voiceState.value == VoiceState.WAITING_FOR_INPUT) {
                    val errorStr = getErrorText(error)
                    if (isListeningToQuestion) {
                        val elapsedSecs = ((System.currentTimeMillis() - questionListeningStartTime) / 1000).toInt()
                        if (elapsedSecs < 60) {
                            startListening(isWaitingForExplanation = false)
                        } else {
                            _thinkTimeRemaining.value = null
                            isListeningToQuestion = false
                            processSilenceExpiration()
                        }
                    } else {
                        // if it's NO_MATCH or SPEECH_TIMEOUT, we can process silence
                        if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT || error == SpeechRecognizer.ERROR_AUDIO || error == SpeechRecognizer.ERROR_CLIENT) {
                            processSilence()
                        } else {
                            setVoiceState(VoiceState.ERROR, errorStr)
                        }
                    }
                }
            }
            override fun onResults(results: Bundle?) {
                _micLevel.value = 0f
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val result = matches[0]
                    _transcript.value = result
                    silenceRetries = 0 // Reset silence
                    processFuzzyMatch(result)
                } else {
                    if (isListeningToQuestion) {
                        val elapsedSecs = ((System.currentTimeMillis() - questionListeningStartTime) / 1000).toInt()
                        if (elapsedSecs < 60) {
                            startListening(isWaitingForExplanation = false)
                        } else {
                            _thinkTimeRemaining.value = null
                            isListeningToQuestion = false
                            processSilenceExpiration()
                        }
                    } else {
                        processSilence()
                    }
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    _transcript.value = matches[0]
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun handleUtteranceDone(utteranceId: String?) {
        isTtsSpeaking = false
        _activeUtteranceId.value = null
        _activeUtteranceRange.value = null
        _currentSpokenWord.value = null
        scope.launch {
            when (utteranceId) {
                "read_question" -> {
                    setVoiceState(VoiceState.DICTATING_OPTIONS)
                    val optionsText = "Options are: " + options.mapIndexed { idx, opt -> getSpokenOptionText(idx, opt) + "." }.joinToString(" ")
                    speak(optionsText, "read_options")
                }
                "read_options" -> {
                    isListeningToQuestion = true
                    questionListeningStartTime = System.currentTimeMillis()
                    _thinkTimeRemaining.value = 60
                    
                    thinkTimerJob?.cancel()
                    thinkTimerJob = scope.launch {
                        while (isListeningToQuestion) {
                            val elapsedSecs = ((System.currentTimeMillis() - questionListeningStartTime) / 1000).toInt()
                            val remaining = (60 - elapsedSecs).coerceAtLeast(0)
                            _thinkTimeRemaining.value = remaining
                            
                            if (remaining <= 0) {
                                _thinkTimeRemaining.value = null
                                isListeningToQuestion = false
                                processSilenceExpiration()
                                break
                            }
                            delay(1000)
                        }
                    }
                    startListening(isWaitingForExplanation = false)
                }
                "answer_feedback", "explanation_end", "reprompt" -> {
                    startListening(isWaitingForExplanation = (utteranceId == "answer_feedback" || utteranceId == "explanation_end"))
                }
            }
        }
    }

    fun startSession(
        questionText: String, 
        options: List<String>, 
        correctAnswer: String,
        explanation: String,
        speed: Float,
        pitch: Float,
        onAnswer: (String) -> Unit
    ) {
        onAnswerCallback = onAnswer
        isWaitingForExplanation = false
        this.questionText = questionText
        this.options = options
        this.correctAnswer = correctAnswer
        this.explanation = explanation
        this.currentSpeed = speed
        this.currentPitch = pitch
        this.silenceRetries = 0
        this._transcript.value = ""
        
        setVoiceState(VoiceState.PREPARING)
        
        startDictation()
    }

    private fun startDictation() {
        pendingSessionStart = false
        setVoiceState(VoiceState.DICTATING_QUESTION)
        speak(questionText, "read_question")
    }

    private fun speak(text: String, utteranceId: String) {
        isListeningToQuestion = false
        _thinkTimeRemaining.value = null
        thinkTimerJob?.cancel()
        _currentSpokenWord.value = null
        
        spokenTexts[utteranceId] = audioEngine.cleanText(text)
        audioEngine.speak(text, utteranceId, currentPitch, currentSpeed, deleteFileOnCompletion = false, questionText = this.questionText)
    }

    private fun startListening(isWaitingForExplanation: Boolean = false) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            setVoiceState(VoiceState.ERROR, "Speech recognition not available")
            return
        }
        
        this.isWaitingForExplanation = isWaitingForExplanation
        _transcript.value = "" // Only caption what the user speaks now

        scope.launch {
            setVoiceState(VoiceState.WAITING_FOR_INPUT)
            
            try {
                speechRecognizer?.cancel()
            } catch (e: Exception) {
                Log.e("VoiceOrchestrator", "Error cancelling speech recognizer", e)
            }
            
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            try {
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e("VoiceOrchestrator", "Error starting listening", e)
                setVoiceState(VoiceState.ERROR, "Call to SpeechRecognizer failed")
            }
        }
    }

    private fun processSilence() {
        setVoiceState(VoiceState.PROCESSING_RESPONSE)
        _transcript.value = "Silence detected"
        scope.launch {
            delay(600)
            if (silenceRetries < 2) {
                silenceRetries++
                speakFeedback("I didn't hear anything. Please state your answer.", "reprompt")
            } else {
                silenceRetries = 0
                val feedbackText = "Still no answer. Let's move on. The correct option was Option $correctAnswer. ${explanation.ifBlank { "There is no further explanation available." }} Say next to continue."
                speakFeedback(feedbackText, "explanation_end")
            }
        }
    }

    private fun processSilenceExpiration() {
        setVoiceState(VoiceState.PROCESSING_RESPONSE)
        _transcript.value = "Time's up"
        scope.launch {
            val feedbackText = "Time's up. The correct option was Option $correctAnswer. ${explanation.ifBlank { "There is no further explanation available." }} Say next to continue."
            speakFeedback(feedbackText, "explanation_end")
        }
    }

    private fun processFuzzyMatch(spokenText: String) {
        setVoiceState(VoiceState.PROCESSING_RESPONSE)
        val normalized = spokenText.lowercase().trim()
        onAnswerCallback?.invoke(normalized)
    }

    private fun setVoiceState(state: VoiceState, error: String? = null) {
        _voiceState.value = state
        _voiceError.value = error
    }

    fun stopSession() {
        setVoiceState(VoiceState.IDLE)
        isListeningToQuestion = false
        _thinkTimeRemaining.value = null
        _currentSpokenWord.value = null
        thinkTimerJob?.cancel()
        audioEngine.stop()
        try {
            speechRecognizer?.cancel()
        } catch (e: Exception) {
            Log.e("VoiceOrchestrator", "Error cancel/destroy speechRecognizer", e)
        }
        isTtsSpeaking = false
    }

    fun forceListen() {
        audioEngine.stop()
        isTtsSpeaking = false
        _activeUtteranceId.value = null
        _activeUtteranceRange.value = null
        _currentSpokenWord.value = null
        startListening(isWaitingForExplanation = isWaitingForExplanation)
    }

    fun release() {
        stopSession()
        thinkTimerJob?.cancel()
        isListeningToQuestion = false
        _thinkTimeRemaining.value = null
        _currentSpokenWord.value = null
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e("VoiceOrchestrator", "Error destroying speech recognizer on release", e)
        }
        speechRecognizer = null
        audioEngine.release()
    }

    fun speakFeedback(text: String, utteranceId: String = "answer_feedback") {
        setVoiceState(VoiceState.DICTATING_OPTIONS)
        speak(text, utteranceId)
    }

    private fun getErrorText(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No match"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RecognitionService busy"
            SpeechRecognizer.ERROR_SERVER -> "Error from server"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
            else -> "Speech recognition issue"
        }
    }
}
