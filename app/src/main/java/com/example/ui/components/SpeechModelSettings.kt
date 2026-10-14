package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import com.example.tts.KhmerModelState

@Composable
fun SpeechModelSettings(
  state: KhmerModelState,
  onDownload: () -> Unit,
  onDelete: () -> Unit
) {
  var confirmDelete by remember { mutableStateOf(false) }

  Card(
    modifier = Modifier.fillMaxWidth().testTag("settings_speech_model"),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          Icons.Default.RecordVoiceOver,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text("Read Aloud · Khmer", style = MaterialTheme.typography.titleMedium.copy(
          fontWeight = FontWeight.SemiBold))
      }
      Text(
        "Khmer MMS (ONNX) reads on your device. Android text-to-speech is the fallback when the model is not installed or cannot run.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Text(
        when {
          state.downloading -> "Downloading Khmer voice · " + state.progress + "%"
          state.installed -> "Offline Khmer voice installed · " +
            (state.sizeBytes / (1024 * 1024)) + " MB"
          else -> "Not installed · voice download is about 120 MB"
        },
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium
      )
      if (state.downloading) {
        LinearProgressIndicator(
          progress = { state.progress / 100f },
          modifier = Modifier.fillMaxWidth().testTag("settings_speech_download_progress")
        )
      }
      if (state.error != null) {
        Text(state.error, color = MaterialTheme.colorScheme.error,
          style = MaterialTheme.typography.bodySmall)
      }
      if (state.installed) {
        OutlinedButton(
          onClick = { confirmDelete = true },
          enabled = !state.downloading,
          modifier = Modifier.testTag("settings_speech_delete")
        ) {
          Icon(Icons.Default.DeleteOutline, contentDescription = null)
          Spacer(Modifier.width(8.dp))
          Text("Delete downloaded model")
        }
      } else {
        Button(
          onClick = onDownload,
          enabled = !state.downloading,
          modifier = Modifier.testTag("settings_speech_download")
        ) {
          Icon(Icons.Default.Download, contentDescription = null)
          Spacer(Modifier.width(8.dp))
          Text(if (state.downloading) "Downloading…" else if (state.error != null) "Retry download" else "Download Khmer voice")
        }
      }
      Text(
        "MMS model by Meta; ONNX export by willwade. CC BY-NC 4.0 (non-commercial use only). Download requires internet; reading works offline afterward.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }

  if (confirmDelete) {
    AlertDialog(
      onDismissRequest = { confirmDelete = false },
      title = { Text("Delete Khmer voice?") },
      text = { Text("This removes the downloaded model and frees device storage. Android TTS remains available. You can download the Khmer voice again.") },
      confirmButton = {
        TextButton(onClick = { confirmDelete = false; onDelete() }) {
          Text("Delete")
        }
      },
      dismissButton = {
        TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
      }
    )
  }
}
