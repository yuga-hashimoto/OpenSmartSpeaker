package com.opensmarthome.speaker.ui.voice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opensmarthome.speaker.ui.theme.SpeakerBackground
import com.opensmarthome.speaker.ui.theme.SpeakerTextPrimary
import com.opensmarthome.speaker.ui.theme.SpeakerTextSecondary
import com.opensmarthome.speaker.ui.theme.SpeakerTextTertiary
import com.opensmarthome.speaker.voice.pipeline.VoicePipelineState

@Composable
fun VoiceOverlay(
    voiceState: VoicePipelineState,
    sttText: String,
    responseText: String,
    modifier: Modifier = Modifier
) {
    val visible = voiceState !is VoicePipelineState.Idle &&
            voiceState !is VoicePipelineState.WakeWordListening

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 4 },
        exit = fadeOut(tween(500))
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(SpeakerBackground.copy(alpha = 0.94f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                VoiceStateAnimation(state = voiceState)

                Spacer(Modifier.height(36.dp))

                if (sttText.isNotBlank() && voiceState is VoicePipelineState.Listening) {
                    Text(
                        text = sttText,
                        style = MaterialTheme.typography.titleLarge,
                        color = SpeakerTextSecondary
                    )
                    Spacer(Modifier.height(16.dp))
                }

                if (responseText.isNotBlank() &&
                    (voiceState is VoicePipelineState.Speaking || voiceState is VoicePipelineState.PreparingSpeech)
                ) {
                    Text(
                        text = responseText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = SpeakerTextPrimary,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                }

                Text(
                    text = when (voiceState) {
                        is VoicePipelineState.Listening -> "Listening..."
                        is VoicePipelineState.Processing -> "Processing..."
                        is VoicePipelineState.Thinking -> "Thinking..."
                        is VoicePipelineState.PreparingSpeech -> "Preparing..."
                        is VoicePipelineState.Speaking -> ""
                        is VoicePipelineState.Error -> voiceState.message
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = SpeakerTextTertiary
                )
            }
        }
    }
}
