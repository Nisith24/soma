package com.example.voice

enum class VoiceState {
    IDLE,
    PREPARING,
    DICTATING_QUESTION,
    DICTATING_OPTIONS,
    WAITING_FOR_INPUT,
    PROCESSING_RESPONSE,
    ERROR,
    MANUAL_OVERRIDE
}
