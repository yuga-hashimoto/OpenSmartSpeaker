package com.opensmarthome.speaker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SmartSpeakerColorScheme = darkColorScheme(
    background = SpeakerBackground,
    surface = SpeakerSurface,
    surfaceVariant = SpeakerSurfaceVariant,
    primary = SpeakerPrimary,
    onPrimary = SpeakerOnPrimary,
    onBackground = SpeakerTextPrimary,
    onSurface = SpeakerTextPrimary,
    onSurfaceVariant = SpeakerTextSecondary,
    primaryContainer = SpeakerSurfaceElevated,
    onPrimaryContainer = SpeakerTextPrimary,
    secondaryContainer = SpeakerSurfaceElevated,
    onSecondaryContainer = SpeakerTextPrimary,
    error = VoiceError,
    onError = SpeakerTextPrimary
)

@Composable
fun OpenSmartSpeakerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SmartSpeakerColorScheme,
        typography = Typography,
        content = content
    )
}
