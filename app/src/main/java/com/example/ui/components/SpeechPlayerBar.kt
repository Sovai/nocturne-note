package com.example.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tts.ReadAloudState
import com.example.tts.SpeechEngine
import com.example.tts.SpeechPreferences
import kotlin.math.roundToInt

@Composable
fun SpeechPlayerBar(
  state: ReadAloudState,
  preferences: SpeechPreferences,
  androidVoices: List<String>,
  onToggle: () -> Unit,
  onStop: () -> Unit,
  onSeek: (Int) -> Unit,
  onSkip: (Int) -> Unit,
  onSpeed: (Float) -> Unit,
  onEngine: (SpeechEngine) -> Unit,
  onAndroidVoice: (String) -> Unit
) {
  if (!state.active && state.error == null) return
  var modelMenu by remember { mutableStateOf(false) }
  var voiceMenu by remember { mutableStateOf(false) }
  var seeking by remember { mutableStateOf<Float?>(null) }
  Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh,
    modifier = Modifier.fillMaxWidth().testTag("read_aloud_player")) {
    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
      Text(
        text = state.error ?: if (state.loading) "Generating speech…" else "Read Aloud",
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = if (state.error != null) MaterialTheme.colorScheme.error
          else MaterialTheme.colorScheme.onSurface
      )
      if (state.active && state.chunks.isNotEmpty()) {
        val maxIndex = (state.totalParagraphs - 1).coerceAtLeast(0)
        Slider(
          value = seeking ?: state.paragraph.toFloat(),
          onValueChange = { seeking = it },
          onValueChangeFinished = {
            seeking?.let { onSeek(it.roundToInt()) }
            seeking = null
          },
          valueRange = 0f..maxIndex.coerceAtLeast(1).toFloat(),
          modifier = Modifier.fillMaxWidth().testTag("read_aloud_seek")
        )
      }
      Row(horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        if (state.active) {
          TextButton(onClick = { modelMenu = true },
            modifier = Modifier.testTag("read_aloud_engine")) {
            Text(preferences.engine.label, maxLines = 1)
          }
          DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
            SpeechEngine.entries.forEach { engine ->
              DropdownMenuItem(
                text = { Text(engine.label) },
                enabled = engine != SpeechEngine.OMNIVOICE,
                onClick = { onEngine(engine); modelMenu = false }
              )
            }
          }
          if (preferences.engine == SpeechEngine.ANDROID ||
            preferences.engine == SpeechEngine.AUTO) {
            TextButton(onClick = { voiceMenu = true }) {
              Text(if (preferences.androidVoiceName.isBlank()) "Voice"
                else preferences.androidVoiceName.take(12))
            }
            DropdownMenu(expanded = voiceMenu, onDismissRequest = { voiceMenu = false }) {
              DropdownMenuItem(text = { Text("System default") },
                onClick = { onAndroidVoice(""); voiceMenu = false })
              androidVoices.forEach { name ->
                DropdownMenuItem(text = { Text(name) },
                  onClick = { onAndroidVoice(name); voiceMenu = false })
              }
            }
          }
          TextButton(onClick = { onSpeed(if (state.speed >= 1.5f) 0.75f
            else (state.speed + 0.25f).coerceAtMost(1.5f)) }) {
            Text(String.format("%.2f×", state.speed))
          }
          IconButton(onClick = { onSkip(-1) }) {
            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous chunk")
          }
          IconButton(onClick = onToggle, enabled = !state.loading) {
            Icon(if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
              contentDescription = if (state.playing) "Pause" else "Resume")
          }
          IconButton(onClick = { onSkip(1) }) {
            Icon(Icons.Default.SkipNext, contentDescription = "Next chunk")
          }
          Text(
            text = "${state.paragraph + 1}/${state.totalParagraphs}",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 16.dp)
          )
        }
        IconButton(onClick = onStop) {
          Icon(Icons.Default.Close, contentDescription = "Stop and close reader")
        }
      }
    }
  }
}
