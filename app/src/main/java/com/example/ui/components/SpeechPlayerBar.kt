package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tts.ReadAloudState

@Composable
fun SpeechPlayerBar(
  state: ReadAloudState,
  onToggle: () -> Unit,
  onStop: () -> Unit,
  onSkip: (Int) -> Unit,
  onSpeed: (Float) -> Unit
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    modifier = Modifier.fillMaxWidth().testTag("read_aloud_player")
  ) {
    Column {
      if (state.active) {
        LinearProgressIndicator(
          progress = { if (state.totalParagraphs == 0) 0f
            else (state.paragraph + 1f) / state.totalParagraphs },
          modifier = Modifier.fillMaxWidth()
        )
      }
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
      ) {
        Column(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
          Text(
            text = if (state.error != null) "Reader error" else if (state.loading) "Preparing speech…"
              else "Read Aloud",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
          )
          Text(
            text = state.error ?: (
              (if (state.engine.isBlank()) "Preparing" else state.engine) +
                " · " + (state.paragraph + 1) + "/" + state.totalParagraphs
            ),
            style = MaterialTheme.typography.labelSmall,
            color = if (state.error != null) MaterialTheme.colorScheme.error
              else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2
          )
        }
        if (state.active) {
          TextButton(onClick = {
            val speeds = listOf(0.75f, 1f, 1.25f, 1.5f)
            onSpeed(speeds[(speeds.indexOf(state.speed) + 1).mod(speeds.size)])
          }, modifier = Modifier.testTag("read_aloud_speed")) {
            Text(state.speed.toString() + "×")
          }
          IconButton(onClick = { onSkip(-1) }) {
            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous paragraph")
          }
          IconButton(onClick = onToggle, enabled = !state.loading) {
            Icon(if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
              contentDescription = if (state.playing) "Pause reading" else "Resume reading")
          }
          IconButton(onClick = { onSkip(1) }) {
            Icon(Icons.Default.SkipNext, contentDescription = "Next paragraph")
          }
        }
        IconButton(onClick = onStop) {
          Icon(Icons.Default.Close, contentDescription = "Close reader")
        }
      }
    }
  }
}
