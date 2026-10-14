package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tts.EdgeTtsClient
import com.example.tts.SpeechEngine
import com.example.tts.SpeechPreferences
import com.example.tts.SpeechVoice

@Composable
fun SpeechReaderSettings(
  settings: SpeechPreferences,
  androidVoices: List<String>,
  omniInstalled: Boolean,
  onEngine: (SpeechEngine) -> Unit,
  onVoice: (SpeechVoice) -> Unit,
  onAndroidVoice: (String) -> Unit,
  onEdgeVoice: (String) -> Unit,
  onEdgePitch: (Int) -> Unit,
  onSpeed: (Float) -> Unit,
  onBackground: (Boolean) -> Unit
) {
  var voiceExpanded by remember { mutableStateOf(false) }
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
        val available = engine != SpeechEngine.OMNIVOICE || omniInstalled
        FilterChip(selected = settings.engine == engine,
          onClick = { onEngine(engine) },
          enabled = available,
          label = { Text(engine.label + if (engine == SpeechEngine.OMNIVOICE) " · beta" else "") },
          modifier = Modifier.testTag("settings_engine_" + engine.name.lowercase()))
      }
      Text("OmniVoice ONNX now attempts local Android inference. Beta: large RAM usage and long initial generation are possible. Start with short Khmer text.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text("Speed · " + String.format("%.2f", settings.speed) + "×")
      Slider(value = settings.speed, onValueChange = onSpeed,
        valueRange = 0.75f..1.5f, modifier = Modifier.testTag("settings_speech_speed"))
      Text("Voice", style = MaterialTheme.typography.bodyMedium)
      when (settings.engine) {
        SpeechEngine.MMS -> Text("MMS has one fixed voice.",
          style = MaterialTheme.typography.bodySmall)
        SpeechEngine.OMNIVOICE -> Text("Automatic voice is available. Custom voice design/cloning is not yet enabled.",
          style = MaterialTheme.typography.bodySmall)
        SpeechEngine.EDGE -> {
          Text("Khmer neural voice")
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = settings.edgeVoice == EdgeTtsClient.PISETH,
              onClick = { onEdgeVoice(EdgeTtsClient.PISETH) }, label = { Text("Piseth · Male") })
            FilterChip(selected = settings.edgeVoice == EdgeTtsClient.SREYMOM,
              onClick = { onEdgeVoice(EdgeTtsClient.SREYMOM) }, label = { Text("Sreymom · Female") })
          }
          Text("Pitch · ${settings.edgePitchHz} Hz")
          Slider(value = settings.edgePitchHz.toFloat(),
            onValueChange = { onEdgePitch(it.toInt()) }, valueRange = -40f..40f,
            modifier = Modifier.testTag("edge_pitch_slider"))
          Text("Requires internet. Text from your note is sent to Microsoft's Edge Read Aloud service. This unofficial no-key interface may stop working or impose limits.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        else -> {
          Text("Android fallback voice",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
          if (androidVoices.isEmpty()) {
            Text("No installed Khmer/English system voices detected.",
              style = MaterialTheme.typography.bodySmall)
          } else {
            TextButton(onClick = { voiceExpanded = true }) {
              Text(settings.androidVoiceName.ifBlank { "System default" }, maxLines = 1)
            }
            DropdownMenu(expanded = voiceExpanded,
              onDismissRequest = { voiceExpanded = false }) {
              DropdownMenuItem(text = { Text("System default") },
                onClick = { onAndroidVoice(""); voiceExpanded = false })
              androidVoices.forEach { name ->
                DropdownMenuItem(text = { Text(name) },
                  onClick = { onAndroidVoice(name); voiceExpanded = false })
              }
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
