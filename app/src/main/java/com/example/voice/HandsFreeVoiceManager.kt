package com.example.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.McqField

data class HandsFreeState(
    val voiceState: VoiceState,
    val transcript: String,
    val micLevel: Float,
    val errorText: String?,
    val activeUtteranceId: String?,
    val activeUtteranceRange: IntRange?,
    val onMicClick: () -> Unit,
    val thinkingTimeRemaining: Int? = null,
    val currentSpokenWord: String? = null
)

@Composable
fun rememberHandsFreeState(
    enabled: Boolean,
    question: McqField,
    selectedOption: String?,
    onOptionSelected: (String) -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onDisableHandsFree: () -> Unit,
    ttsSpeed: Float = 1.0f,
    ttsPitch: Float = 1.0f
): HandsFreeState? {
    if (!enabled) return null
    
    val context = LocalContext.current
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMicPermission = isGranted
        if (!isGranted) {
            onDisableHandsFree()
        }
    }

    LaunchedEffect(hasMicPermission) {
        if (!hasMicPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    if (hasMicPermission) {
        val orchestrator = com.example.LocalVoiceOrchestrator.current ?: remember { VoiceOrchestrator(context) }
        val voiceState by orchestrator.voiceState.collectAsState()
        val transcript by orchestrator.transcript.collectAsState()
        val micLevel by orchestrator.micLevel.collectAsState()
        val errorText by orchestrator.voiceError.collectAsState()
        val activeUtteranceId by orchestrator.activeUtteranceId.collectAsState()
        val activeUtteranceRange by orchestrator.activeUtteranceRange.collectAsState()
        val thinkTimeRemaining by orchestrator.thinkTimeRemaining.collectAsState()
        val currentSpokenWord by orchestrator.currentSpokenWord.collectAsState()

        LaunchedEffect(selectedOption) {
            if (selectedOption != null) {
                // Cancel/stop any running TTS dictation or active session so we immediately interrupt!
                orchestrator.stopSession()
                val matchIndex = question.options.indexOf(selectedOption)
                if (matchIndex != -1) {
                    val feedbackMsg = getAnswerFeedbackText(question, matchIndex, selectedOption)
                    orchestrator.speakFeedback(feedbackMsg, "answer_feedback")
                }
            }
        }

        LaunchedEffect(question, ttsSpeed, ttsPitch) {
            if (selectedOption == null) {
                orchestrator.startSession(
                    questionText = question.question,
                    options = question.options,
                    correctAnswer = question.correct_answer ?: "A",
                    explanation = question.explanation ?: "",
                    speed = ttsSpeed,
                    pitch = ttsPitch,
                    onAnswer = { spokenAnswer ->
                        val clean = spokenAnswer.lowercase().trim()
                        when {
                            clean == "next" || clean == "skip" || clean.contains("next page") || clean == "proceed" -> {
                                onNext()
                            }
                            clean == "previous" || clean == "back" -> {
                                onPrev()
                            }
                            clean == "repeat" || clean == "say again" || clean == "once more" || clean == "sorry" || clean == "reread" -> {
                                orchestrator.startSession(
                                    questionText = question.question,
                                    options = question.options,
                                    correctAnswer = question.correct_answer ?: "A",
                                    explanation = question.explanation ?: "",
                                    speed = ttsSpeed,
                                    pitch = ttsPitch,
                                    onAnswer = {} // Inner callbacks handled naturally by orchestrator state
                                )
                            }
                            clean == "stop" || clean == "pause" || clean == "exit" -> {
                                onDisableHandsFree()
                            }
                            clean == "explanation" || clean == "explain" || clean.contains("why") || clean.contains("tell me why") || clean == "continue" || clean == "ok" || clean == "then" -> {
                                val explanationText = question.explanation ?: "There is no explanation available for this question."
                                orchestrator.speakFeedback("$explanationText. Say next to continue.", "explanation_end")
                            }
                            else -> {
                                val matchIndex = findMatchingOptionIndex(spokenAnswer, question.options)
                                if (matchIndex != null) {
                                    val selectedChoice = question.options[matchIndex]
                                    onOptionSelected(selectedChoice)
                                    val feedbackMsg = getAnswerFeedbackText(question, matchIndex, selectedChoice)
                                    orchestrator.speakFeedback(feedbackMsg, "answer_feedback")
                                } else {
                                    orchestrator.speakFeedback("I didn't quite catch that. Please say option A, B, C, or D.", "reprompt")
                                }
                            }
                        }
                    }
                )
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                orchestrator.stopSession()
            }
        }

        return HandsFreeState(
            voiceState = voiceState,
            transcript = transcript,
            micLevel = micLevel,
            errorText = errorText,
            activeUtteranceId = activeUtteranceId,
            activeUtteranceRange = activeUtteranceRange,
            onMicClick = { orchestrator.forceListen() },
            thinkingTimeRemaining = thinkTimeRemaining,
            currentSpokenWord = currentSpokenWord
        )
    }
    
    return null
}

fun getAnswerFeedbackText(question: McqField, matchIndex: Int, selectedChoice: String): String {
    val correctIndex = question.options.indexOfFirst { opt ->
        val cleanOpt = opt.lowercase().trim()
        val cleanCorr = question.correct_answer.lowercase().trim()
        cleanOpt.startsWith("$cleanCorr.", ignoreCase = true)
                || cleanOpt.startsWith("$cleanCorr)", ignoreCase = true)
                || cleanOpt.startsWith("$cleanCorr ", ignoreCase = true)
                || cleanOpt.equals(cleanCorr, ignoreCase = true)
                || cleanOpt.endsWith(cleanCorr, ignoreCase = true)
    }
    val actualCorrectIndex = if (correctIndex != -1) correctIndex else (findLetterPrefixIndex(question.correct_answer) ?: 0)
    val isCorrect = actualCorrectIndex == matchIndex
    
    val spokenSelected = getSpokenOptionText(matchIndex, selectedChoice)
    val correctOptionText = if (correctIndex != -1) question.options[correctIndex] else question.correct_answer
    val spokenCorrect = getSpokenOptionText(actualCorrectIndex, correctOptionText)
    
    return if (isCorrect) {
        "Your answer is Correct! $spokenSelected."
    } else {
        val correctOptPrefix = "Option ${'A' + actualCorrectIndex}"
        "Your answer is Incorrect. The correct Option is: $correctOptPrefix. $spokenCorrect."
    }
}

fun findMatchingOptionIndex(spoken: String, options: List<String>): Int? {
    val clean = spoken.lowercase().trim()
    
    // 1. Exact full option match (most holistic)
    for (i in options.indices) {
        val optClean = options[i].lowercase().trim()
        if (clean == optClean || clean == optClean.replace(Regex("[^a-z0-9 ]"), "")) {
            return i
        }
    }

    // 2. Exact word match where the spoken text is a large substring of the option
    for (i in options.indices) {
        val optClean = options[i].lowercase().trim()
        if (optClean.length > 2 && clean.contains(optClean)) {
            return i
        }
    }

    // 3. Direct contains checks for explicit prefixes anywhere in the text
    if (clean.contains("option a") || clean.contains("choice a") || clean.contains("select a") || 
        clean.contains("option 1") || clean.contains("choice 1") || clean.contains("select 1")) {
        return 0
    }
    if (clean.contains("option b") || clean.contains("choice b") || clean.contains("select b") || 
        clean.contains("option 2") || clean.contains("choice 2") || clean.contains("select 2") ||
        clean.contains("beta")) {
        return 1
    }
    if (clean.contains("option c") || clean.contains("choice c") || clean.contains("select c") || 
        clean.contains("option 3") || clean.contains("choice 3") || clean.contains("select 3") ||
        clean.contains("gamma")) {
        return 2
    }
    if (clean.contains("option d") || clean.contains("choice d") || clean.contains("select d") || 
        clean.contains("option 4") || clean.contains("choice 4") || clean.contains("select 4") ||
        clean.contains("delta")) {
        return 3
    }

    // 4. Word-by-word matching for exact letter/alpha words
    val spokenWords = clean.replace(Regex("[^a-zA-Z0-9 ]"), " ").split("\\s+".toRegex())
    if (spokenWords.contains("a") || spokenWords.contains("alpha") || spokenWords.contains("one") || spokenWords.contains("first")) {
        return 0
    }
    if (spokenWords.contains("b") || spokenWords.contains("beta") || spokenWords.contains("two") || spokenWords.contains("second")) {
        return 1
    }
    if (spokenWords.contains("c") || spokenWords.contains("gamma") || spokenWords.contains("three") || spokenWords.contains("third")) {
        return 2
    }
    if (spokenWords.contains("d") || spokenWords.contains("delta") || spokenWords.contains("four") || spokenWords.contains("fourth")) {
        return 3
    }
    
    // 5. Smart substring checks (excluding generic stopwords to prevent false matching)
    for (i in options.indices) {
        val optClean = options[i].lowercase().trim()
        if (clean.contains(optClean) || optClean.contains(clean)) {
            return i
        }
    }
    
    val stopWords = setOf("option", "choose", "select", "question", "correct", "answer", "which", "what", "with", "from", "that", "this", "your", "the", "and")
    for (i in options.indices) {
        val words = options[i].lowercase().trim().split("\\s+".toRegex())
        for (w in words) {
            val cleanW = w.replace(Regex("[^a-zA-Z0-9]"), "")
            if (cleanW.length > 3 && cleanW !in stopWords && clean.contains(cleanW)) {
                return i
            }
        }
    }
    
    return null
}

fun findLetterPrefixIndex(letter: String): Int? {
    val clean = letter.uppercase().trim()
    if (clean.length == 1 && clean[0] in 'A'..'Z') {
        return clean[0] - 'A'
    }
    return null
}

fun getSpokenOptionText(index: Int, rawOption: String): String {
    val cleanOpt = rawOption.trim()
    val label = ('A' + index).toString()
    val alreadyHasPrefix = cleanOpt.startsWith("$label.", ignoreCase = true)
            || cleanOpt.startsWith("$label)", ignoreCase = true)
            || cleanOpt.startsWith("$label ", ignoreCase = true)
            || cleanOpt.startsWith("option $label", ignoreCase = true)
    return if (alreadyHasPrefix) {
        cleanOpt
    } else {
        "Option $label: $cleanOpt"
    }
}
