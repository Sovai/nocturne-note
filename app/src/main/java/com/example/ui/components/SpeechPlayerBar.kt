package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.tts.ReadAloudState
import java.util.Locale
import kotlin.math.roundToInt

/** Fixed-width transport actions remain visible even on narrow phones. */
@Composable
fun SpeechPlayerBar(
  state: ReadAloudState,
  onToggle: () -> Unit,
  onStop: () -> Unit,
  onSeek: (Int) -> Unit,
  onSkip: (Int) -> Unit,
  onSpeed: (Float) -> Unit,
  onSettings: () -> Unit
) {
  if (!state.active && state.error == null) return
  var seeking by remember { mutableStateOf<Float?>(null) }
  Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh,
    modifier = Modifier.fillMaxWidth().testTag("read_aloud_player")) {
    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
      verticalArrangement = Arrangement.spacedBy(0.dp)) {
      Row(modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically) {
        Text(
          state.error ?: if (state.loading) "Preparing speech…" else
            state.engine.ifBlank { "Read Aloud" },
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
          color = if (state.error != null) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1, overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f).padding(start = 6.dp)
        )
        if (state.active) TextButton(onClick = {
          onSpeed(if (state.speed >= 1.5f) 0.75f else (state.speed + 0.25f).coerceAtMost(1.5f))
        }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 3.dp),
          modifier = Modifier.testTag("read_aloud_speed")) {
          Text(String.format(Locale.US, "%.2f×", state.speed),
            style = MaterialTheme.typography.labelMedium)
        }
        IconButton(onClick = onSettings, modifier = Modifier.size(40.dp)
          .testTag("read_aloud_settings")) {
          Icon(Icons.Default.Settings, contentDescription = "Read Aloud settings")
        }
      }
      if (state.active && state.chunks.isNotEmpty()) {
        val last = (state.totalParagraphs - 1).coerceAtLeast(1)
        Slider(
          value = (seeking ?: state.paragraph.toFloat()).coerceIn(0f, last.toFloat()),
          onValueChange = { seeking = it },
          onValueChangeFinished = {
            seeking?.let { onSeek(it.roundToInt()) }
            seeking = null
          },
          valueRange = 0f..last.toFloat(),
          modifier = Modifier.fillMaxWidth().testTag("read_aloud_seek")
        )
      }
      Row(modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        if (state.active) {
          IconButton(onClick = { onSkip(-1) }, modifier = Modifier.size(48.dp)
            .testTag("read_aloud_previous")) {
            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous passage")
          }
          IconButton(onClick = onToggle, enabled = !state.loading, modifier = Modifier.size(48.dp)
            .testTag("read_aloud_toggle")) {
            Icon(if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
              contentDescription = if (state.playing) "Pause" else "Resume")
          }
          IconButton(onClick = { onSkip(1) }, modifier = Modifier.size(48.dp)
            .testTag("read_aloud_next")) {
            Icon(Icons.Default.SkipNext, contentDescription = "Next passage")
          }
          Text("${state.paragraph + 1}/${state.totalParagraphs}",
            style = MaterialTheme.typography.labelSmall, maxLines = 1,
            modifier = Modifier.weight(1f).padding(start = 6.dp))
        }
        IconButton(onClick = onStop, modifier = Modifier.size(48.dp)
          .testTag("read_aloud_stop")) {
          Icon(Icons.Default.Close, contentDescription = "Stop and close reader")
        }
      }
    }
  }
}
