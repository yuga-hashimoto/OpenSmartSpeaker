package com.opensmarthome.speaker.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Filter
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.opensmarthome.speaker.tool.info.WeatherInfo
import com.opensmarthome.speaker.ui.theme.DeviceClimate
import com.opensmarthome.speaker.ui.theme.SpeakerSurfaceVariant
import com.opensmarthome.speaker.ui.theme.SpeakerTextPrimary
import com.opensmarthome.speaker.ui.theme.SpeakerTextSecondary

/**
 * Alexa-style current-weather tile: condition icon + big temperature,
 * location name underneath, humidity + wind in a muted secondary row.
 *
 * Keeps the surface colour aligned with the existing sensor-based
 * [WeatherWidget] so swapping between sensor-only and online sources
 * doesn't visually jump.
 */
@Composable
fun OnlineWeatherCard(
    weather: WeatherInfo,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(SpeakerSurfaceVariant, RoundedCornerShape(20.dp))
            .padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = conditionIcon(weather.condition),
                contentDescription = weather.condition,
                tint = DeviceClimate,
                modifier = Modifier.size(44.dp),
            )
            Spacer(modifier = Modifier.size(14.dp))
            Text(
                text = "${formatTemp(weather.temperatureC)}\u00B0",
                style = MaterialTheme.typography.displaySmall,
                color = SpeakerTextPrimary,
                fontWeight = FontWeight.Light,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "${weather.condition} \u00B7 ${weather.location}",
            style = MaterialTheme.typography.titleMedium,
            color = SpeakerTextSecondary,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.WaterDrop,
                    contentDescription = null,
                    tint = SpeakerTextSecondary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = "${weather.humidity}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SpeakerTextSecondary,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Air,
                    contentDescription = null,
                    tint = SpeakerTextSecondary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = "${formatTemp(weather.windKph)} km/h",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SpeakerTextSecondary,
                )
            }
        }
    }
}

private fun formatTemp(value: Double): String = "%.0f".format(value)

/**
 * Rough condition-string → icon mapping. Strings come from
 * [com.opensmarthome.speaker.tool.info.OpenMeteoWeatherProvider.weatherCodeToText].
 */
private fun conditionIcon(condition: String): ImageVector {
    val c = condition.lowercase()
    return when {
        "thunder" in c -> Icons.Filled.Thunderstorm
        "snow" in c -> Icons.Filled.AcUnit
        "drizzle" in c || "rain" in c || "shower" in c -> Icons.Filled.Umbrella
        "fog" in c -> Icons.Filled.Filter
        "cloud" in c -> Icons.Filled.Cloud
        "clear" in c -> Icons.Filled.WbSunny
        else -> Icons.Filled.Cloud
    }
}
