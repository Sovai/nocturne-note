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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tts.SpeechEngine
import com.example.tts.SpeechPreferences
import com.example.tts.SpeechVoice

@Composable
fun SpeechReaderSettings(
  settings: SpeechPreferences,
  androidVoices: List<String>,
  onEngine: (SpeechEngine) -> Unit,
  onVoice: (SpeechVoice) -> Unit,
  onAndroidVoice: (String) -> Unit,
  onSpeed: (Float) -> Unit,
  onBackground: (Boolean) -> Unit
) {
  Card(
    modifier = Modifier.fillMaxWidth().testTag("settings_read_aloud"),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
  ) {
    Column(modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text("Read Aloud", style = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.SemiBold))
      Text("Speech engine", style = MaterialTheme.typography.bodyMedium)
      SpeechEngine.entries.forEach { engine ->
        val available = engine != SpeechEngine.OMNIVOICE
        FilterChip(selected = settings.engine == engine,
          onClick = { onEngine(engine) },
          enabled = available,
          label = { Text(engine.label + if (available) "" else " · experimental") },
          modifier = Modifier.testTag("settings_engine_" + engine.name.lowercase()))
      }
      Text("OmniVoice ONNX is an on-device-only research backend. It cannot be selected until full Android inference is validated. No PC connection is required.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text("Speed · " + String.format("%.2f", settings.speed) + "×")
      Slider(value = settings.speed, onValueChange = onSpeed,
        valueRange = 0.75f..1.5f, modifier = Modifier.testTag("settings_speech_speed"))
      Text("Voice", style = MaterialTheme.typography.bodyMedium)
      when (settings.engine) {
        SpeechEngine.MMS -> Text("MMS has one fixed voice.",
          style = MaterialTheme.typography.bodySmall)
        SpeechEngine.OMNIVOICE -> Text("Voice design is pending validated mobile inference.",
          style = MaterialTheme.typography.bodySmall)
        else -> {
          Text("Android fallback voice",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
          if (androidVoices.isEmpty()) {
            Text("No installed Khmer/English system voices detected.",
              style = MaterialTheme.typography.bodySmall)
          } else {
            androidVoices.forEach { name ->
              FilterChip(selected = settings.androidVoiceName == name,
                onClick = { onAndroidVoice(name) },
                label = { Text(name, maxLines = 1) })
            }
          }
        }
      }
      Row(modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f)) {
          Text("Background playback", style = MaterialTheme.typography.bodyMedium)
          Text("Keep reading with screen off and show notification controls.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = settings.backgroundPlayback,
          onCheckedChange = onBackground,
          modifier = Modifier.testTag("settings_background_playback"))
      }
      Text("Leaving a note always stops its reader, even when background playback is enabled.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}
