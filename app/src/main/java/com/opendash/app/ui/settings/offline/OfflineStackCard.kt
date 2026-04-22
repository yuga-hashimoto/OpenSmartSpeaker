package com.opendash.app.ui.settings.offline

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.opendash.app.R

/**
 * Diagnostic card summarising the offline STT / TTS readiness.
 * Hosted at the bottom of SettingsScreen so users who've downloaded
 * models and voices can check "is my offline setup actually ready?"
 * without reading logcat.
 */
@Composable
fun OfflineStackCard(
    modifier: Modifier = Modifier,
    viewModel: OfflineStackViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.settings_offline_stack_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_offline_stack_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))
            Section(
                headingRes = R.string.settings_offline_stack_stt_heading,
                overallStatus = state.whisperOverall,
                activeName = state.whisperActiveModelName,
                libRes = R.string.settings_offline_stack_lib,
                libStatus = state.whisperLibraryLoaded,
                modelRes = R.string.settings_offline_stack_model,
                modelStatus = state.whisperActiveModelDownloaded
            )

            Spacer(Modifier.height(12.dp))
            Section(
                headingRes = R.string.settings_offline_stack_tts_heading,
                overallStatus = state.piperOverall,
                activeName = state.piperActiveVoiceName,
                libRes = R.string.settings_offline_stack_lib,
                libStatus = state.piperLibraryLoaded,
                modelRes = R.string.settings_offline_stack_voice,
                modelStatus = state.piperActiveVoiceDownloaded
            )
        }
    }
}

@Composable
private fun Section(
    headingRes: Int,
    overallStatus: OfflineStackViewModel.Status,
    activeName: String,
    libRes: Int,
    libStatus: OfflineStackViewModel.Status,
    modelRes: Int,
    modelStatus: OfflineStackViewModel.Status
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(headingRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(end = 8.dp)
        )
        OverallChip(overallStatus)
    }
    Text(
        text = activeName,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    StatusLine(libRes, libStatus)
    StatusLine(modelRes, modelStatus)
}

@Composable
private fun OverallChip(status: OfflineStackViewModel.Status) {
    val textRes = when (status) {
        OfflineStackViewModel.Status.Ready -> R.string.settings_offline_stack_ready
        OfflineStackViewModel.Status.Missing -> R.string.settings_offline_stack_missing
    }
    val color = when (status) {
        OfflineStackViewModel.Status.Ready -> MaterialTheme.colorScheme.primary
        OfflineStackViewModel.Status.Missing -> MaterialTheme.colorScheme.error
    }
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.labelMedium,
        color = color
    )
}

@Composable
private fun StatusLine(labelRes: Int, status: OfflineStackViewModel.Status) {
    val ok = status == OfflineStackViewModel.Status.Ready
    val prefix = if (ok) "\u2713 " else "\u2717 "
    Text(
        text = prefix + stringResource(labelRes),
        style = MaterialTheme.typography.bodySmall,
        color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    )
}
