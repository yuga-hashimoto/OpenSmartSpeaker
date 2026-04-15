package com.opensmarthome.speaker.ui.home

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opensmarthome.speaker.ui.theme.SpeakerTextPrimary
import com.opensmarthome.speaker.ui.theme.SpeakerTextSecondary
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun ClockWidget(time: LocalDateTime, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "colon")
    val colonAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "colonAlpha"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = buildString {
                append(time.format(DateTimeFormatter.ofPattern("HH")))
                append(":")
                append(time.format(DateTimeFormatter.ofPattern("mm")))
            },
            style = MaterialTheme.typography.displayLarge,
            color = SpeakerTextPrimary,
            fontWeight = FontWeight.Thin
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = time.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")),
            style = MaterialTheme.typography.titleLarge,
            color = SpeakerTextSecondary,
            fontWeight = FontWeight.Light
        )
    }
}
