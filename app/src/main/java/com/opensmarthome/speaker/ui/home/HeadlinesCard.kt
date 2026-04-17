package com.opensmarthome.speaker.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.opensmarthome.speaker.tool.info.NewsItem
import com.opensmarthome.speaker.ui.theme.SpeakerSurfaceVariant
import com.opensmarthome.speaker.ui.theme.SpeakerTextPrimary
import com.opensmarthome.speaker.ui.theme.SpeakerTextSecondary

/**
 * Horizontal news tile strip shown under the clock on a tablet dashboard.
 * Each tile is a fixed-width card so the row keeps a consistent rhythm
 * independent of title length. Summaries are truncated to two lines.
 *
 * Quiet empty state: when no headlines have loaded yet we render nothing.
 * The calling screen typically shows the clock + weather above this row
 * so a blank strip below would look broken rather than "loading".
 */
@Composable
fun HeadlinesCard(
    headlines: List<NewsItem>,
    modifier: Modifier = Modifier,
    title: String = "Headlines",
) {
    if (headlines.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Article,
                contentDescription = null,
                tint = SpeakerTextSecondary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = SpeakerTextSecondary,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(headlines, key = { it.link.ifBlank { it.title } }) { item ->
                HeadlineTile(item)
            }
        }
    }
}

@Composable
private fun HeadlineTile(item: NewsItem) {
    Column(
        modifier = Modifier
            .width(280.dp)
            .background(SpeakerSurfaceVariant, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleSmall,
            color = SpeakerTextPrimary,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (item.summary.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = item.summary,
                style = MaterialTheme.typography.bodySmall,
                color = SpeakerTextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
