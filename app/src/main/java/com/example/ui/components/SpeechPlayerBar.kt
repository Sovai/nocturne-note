package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.tts.EdgeTtsClient
import com.example.tts.ReadAloudState
import com.example.tts.SpeechEngine
import com.example.tts.SpeechPreferences
import kotlin.math.roundToInt

@Composable
fun SpeechPlayerBar(
  state: ReadAloudState,
  preferences: SpeechPreferences,
  androidVoices: List<String>,
  omniInstalled: Boolean,
  onToggle: () -> Unit,
  onStop: () -> Unit,
  onSeek: (Int) -> Unit,
  onSkip: (Int) -> Unit,
  onSpeed: (Float) -> Unit,
  onEngine: (SpeechEngine) -> Unit,
  onAndroidVoice: (String) -> Unit,
  onEdgeVoice: (String) -> Unit,
  onEdgePitch: (Int) -> Unit,
  onMmsTrim: (Boolean) -> Unit,
  onMmsPause: (Int) -> Unit,
  onMmsThreads: (Int) -> Unit
) {
  if (!state.active && state.error == null) return
  var modelMenu by remember { mutableStateOf(false) }
  var voiceMenu by remember { mutableStateOf(false) }
  var mmsOptions by remember { mutableStateOf(false) }
  var edgeOptions by remember { mutableStateOf(false) }
  var seeking by remember { mutableStateOf<Float?>(null) }
  Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh,
    modifier = Modifier.fillMaxWidth().testTag("read_aloud_player")) {
    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
      if (state.loading || state.error != null) Text(
        text = state.error ?: if (state.loading)
          state.generationStage.ifBlank { "Generating speech…" } else "Read Aloud",
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
      if (state.active) {
        Row(verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.fillMaxWidth()) {
          TextButton(onClick = { modelMenu = true },
            modifier = Modifier.weight(1f).testTag("read_aloud_engine")) {
            Text(preferences.engine.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
          DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
            SpeechEngine.entries.forEach { engine ->
              DropdownMenuItem(
                text = { Text(engine.label) },
                enabled = engine != SpeechEngine.OMNIVOICE || omniInstalled,
                onClick = { onEngine(engine); modelMenu = false }
              )
            }
          }
          if (preferences.engine == SpeechEngine.ANDROID ||
            preferences.engine == SpeechEngine.AUTO) {
            TextButton(onClick = { voiceMenu = true }, modifier = Modifier.widthIn(max = 88.dp)) {
              Text(if (preferences.androidVoiceName.isBlank()) "Voice"
                else preferences.androidVoiceName, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
          if (preferences.engine == SpeechEngine.EDGE) {
            IconButton(onClick = { edgeOptions = true },
              modifier = Modifier.testTag("edge_player_settings")) {
              Icon(Icons.Default.Settings, contentDescription = "Edge Khmer voices and pitch")
            }
          }
          if (preferences.engine == SpeechEngine.MMS ||
            (preferences.engine == SpeechEngine.AUTO && state.engine.startsWith("Khmer MMS"))) {
            IconButton(onClick = { mmsOptions = true }, modifier = Modifier.testTag("mms_player_settings")) {
              Icon(Icons.Default.Settings, contentDescription = "MMS playback settings")
            }
          }
          TextButton(onClick = { onSpeed(if (state.speed >= 1.5f) 0.75f
            else (state.speed + 0.25f).coerceAtMost(1.5f)) },
            modifier = Modifier.widthIn(max = 80.dp)) {
            Text(String.format("%.2f×", state.speed), maxLines = 1,
              overflow = TextOverflow.Ellipsis)
          }
        }
      }
      // Keep transport controls outside the engine/options row. The engine
      // label can shrink, but Next and Close must never require scrolling.
      Row(verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()) {
        if (state.active) {
          IconButton(onClick = { onSkip(-1) },
            modifier = Modifier.testTag("read_aloud_previous")) {
            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous chunk")
          }
          IconButton(onClick = onToggle, enabled = !state.loading,
            modifier = Modifier.testTag("read_aloud_toggle")) {
            Icon(if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
              contentDescription = if (state.playing) "Pause" else "Resume")
          }
          IconButton(onClick = { onSkip(1) },
            modifier = Modifier.testTag("read_aloud_next")) {
            Icon(Icons.Default.SkipNext, contentDescription = "Next chunk")
          }
          Text(
            text = "${state.paragraph + 1}/${state.totalParagraphs}",
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
          )
        }
        IconButton(onClick = onStop, modifier = Modifier.testTag("read_aloud_stop")) {
          Icon(Icons.Default.Close, contentDescription = "Stop and close reader")
        }
      }
    }
  }
  if (edgeOptions) AlertDialog(
    onDismissRequest = { edgeOptions = false },
    title = { Text("Edge Read Aloud · online") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Khmer voice")
        listOf(EdgeTtsClient.PISETH to "Piseth · Male",
          EdgeTtsClient.SREYMOM to "Sreymom · Female").forEach { (id, label) ->
          androidx.compose.material3.FilterChip(
            selected = preferences.edgeVoice == id,
            onClick = { onEdgeVoice(id) }, label = { Text(label) })
        }
        Text("Pitch · ${preferences.edgePitchHz} Hz")
        Slider(value = preferences.edgePitchHz.toFloat(),
          onValueChange = { onEdgePitch(it.toInt()) }, valueRange = -40f..40f)
        Text("Internet required. Sends selected passage to Microsoft.",
          style = MaterialTheme.typography.bodySmall)
      }
    },
    confirmButton = { TextButton(onClick = { edgeOptions = false }) { Text("Done") } }
  )
  if (mmsOptions) AlertDialog(
    onDismissRequest = { mmsOptions = false },
    title = { Text("MMS playback settings") },
    text = {
      androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("MMS has one fixed Khmer voice. Adjust how it plays.")
        androidx.compose.foundation.layout.Row {
          Text("Trim excessive silence", modifier = Modifier.weight(1f))
          Switch(checked = preferences.mmsTrimSilence, onCheckedChange = onMmsTrim)
        }
        Text("Extra pause · " + preferences.mmsPauseMs + " ms")
        Slider(value = preferences.mmsPauseMs.toFloat(),
          onValueChange = { onMmsPause((it.toInt() / 20) * 20) },
          valueRange = 0f..600f)
        Text("Synthesis threads · " + preferences.mmsThreads)
        Slider(value = preferences.mmsThreads.toFloat(),
          onValueChange = { onMmsThreads(it.roundToInt()) },
          valueRange = 1f..4f, steps = 2)
      }
    },
    confirmButton = { TextButton(onClick = { mmsOptions = false }) { Text("Done") } }
  )
}
