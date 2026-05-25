package com.example.voice

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign

@Composable
fun HandsFreeIndicator(
    state: HandsFreeState,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (state.voiceState == VoiceState.WAITING_FOR_INPUT) 1.25f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic_scale"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (state.thinkingTimeRemaining != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                shape = CircleShape,
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                Text(
                    text = "Thinking: ${state.thinkingTimeRemaining}s",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        } else if (state.transcript.isNotBlank()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                shape = CircleShape,
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                Text(
                    text = state.transcript,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        val bgColor = when (state.voiceState) {
            VoiceState.WAITING_FOR_INPUT -> MaterialTheme.colorScheme.primary
            VoiceState.ERROR -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.surfaceVariant
        }
        val iconColor = when (state.voiceState) {
            VoiceState.WAITING_FOR_INPUT -> MaterialTheme.colorScheme.onPrimary
            VoiceState.ERROR -> MaterialTheme.colorScheme.onError
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }

        Box(
            modifier = Modifier
                .size(42.dp)
                .scale(scale)
                .clip(CircleShape)
                .clickable { state.onMicClick() }
                .background(bgColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Hands Free Mic",
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
