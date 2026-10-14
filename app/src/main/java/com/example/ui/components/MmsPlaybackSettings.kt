package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.tts.SpeechPreferences

@Composable
fun MmsPlaybackSettings(
  preferences: SpeechPreferences,
  onTrim: (Boolean) -> Unit,
  onPause: (Int) -> Unit,
  onThreads: (Int) -> Unit,
  modifier: Modifier = Modifier
) {
  Card(modifier = modifier.fillMaxWidth().testTag("mms_playback_settings"),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("MMS playback", style = MaterialTheme.typography.titleMedium)
      Text("This Khmer MMS model has one fixed voice. These settings control pacing and processing, not voice identity.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text("Trim excessive silence", style = MaterialTheme.typography.bodyMedium)
          Text("Remove long quiet tails within each synthesized chunk",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = preferences.mmsTrimSilence, onCheckedChange = onTrim,
          modifier = Modifier.testTag("mms_trim_silence"))
      }
      Text("Additional pause between chunks · ${preferences.mmsPauseMs} ms",
        style = MaterialTheme.typography.bodyMedium)
      Slider(
        value = preferences.mmsPauseMs.toFloat(),
        onValueChange = { onPause((it / 20).toInt() * 20) },
        valueRange = 0f..600f,
        modifier = Modifier.testTag("mms_pause_slider"))
      Text("Synthesis CPU threads", style = MaterialTheme.typography.bodyMedium)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (1..4).forEach { count ->
          FilterChip(selected = preferences.mmsThreads == count,
            onClick = { onThreads(count) },
            label = { Text(count.toString()) })
        }
      }
      Text("New processing settings take effect on the next chunk generation. Replaying cached chunks avoids regeneration.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}
