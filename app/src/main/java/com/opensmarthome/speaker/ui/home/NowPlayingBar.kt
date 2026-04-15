package com.opensmarthome.speaker.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opensmarthome.speaker.ui.theme.DeviceMediaPlaying
import com.opensmarthome.speaker.ui.theme.SpeakerSurfaceElevated
import com.opensmarthome.speaker.ui.theme.SpeakerTextPrimary
import com.opensmarthome.speaker.ui.theme.SpeakerTextSecondary

data class NowPlayingInfo(
    val deviceName: String,
    val mediaTitle: String?,
    val mediaArtist: String?,
    val isPlaying: Boolean
)

@Composable
fun NowPlayingBar(nowPlaying: NowPlayingInfo, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = SpeakerSurfaceElevated
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                tint = DeviceMediaPlaying,
                modifier = Modifier.size(24.dp)
            )
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = nowPlaying.mediaTitle ?: "Unknown",
                    style = MaterialTheme.typography.bodyLarge,
                    color = SpeakerTextPrimary,
                    maxLines = 1
                )
                if (nowPlaying.mediaArtist != null) {
                    Text(
                        text = nowPlaying.mediaArtist,
                        style = MaterialTheme.typography.bodySmall,
                        color = SpeakerTextSecondary,
                        maxLines = 1
                    )
                }
            }
            if (nowPlaying.isPlaying) {
                IconButton(onClick = { }) {
                    Icon(Icons.Filled.Pause, contentDescription = "Pause", tint = SpeakerTextPrimary)
                }
            }
        }
    }
}
