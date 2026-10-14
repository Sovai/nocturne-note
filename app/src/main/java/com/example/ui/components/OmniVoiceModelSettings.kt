package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.tts.OmniVoiceModelState

@Composable
fun OmniVoiceModelSettings(
  state: OmniVoiceModelState,
  onRefresh: () -> Unit,
  onDownload: () -> Unit,
  onDelete: () -> Unit
) {
  LaunchedEffect(Unit) { onRefresh() }
  var confirmDelete by remember { mutableStateOf(false) }
  Card(modifier = Modifier.fillMaxWidth().testTag("settings_omni_model"),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
    Column(modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text("OmniVoice ONNX · On-device",
        style = MaterialTheme.typography.titleMedium)
      Text("Experimental on-device speech engine: Android ONNX generation and Higgs decoding are included. Large model loading may be slow or cause memory pressure. Test short Khmer text first.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text(
        when {
          state.downloading -> "Downloading ${state.progressPercent}%"
          state.installed -> "Model files verified (${state.totalBytes / (1024*1024)} MB)"
          state.published -> "Verified model release available (${state.totalBytes / (1024*1024)} MB)"
          else -> "No published OmniVoice ONNX model files found"
        },
        style = MaterialTheme.typography.bodyMedium)
      if (state.downloading) LinearProgressIndicator(
        progress = { state.progressPercent / 100f }, modifier = Modifier.fillMaxWidth())
      if (state.error != null) Text(state.error,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onRefresh) { Text("Refresh catalog") }
        if (state.installed) {
          OutlinedButton(onClick = { confirmDelete = true }) { Text("Delete models") }
        } else {
          Button(onClick = onDownload,
            enabled = state.published && !state.downloading) { Text("Download") }
        }
      }
      Text("Source: github.com/Sovai/-nocturne-note-models · No model downloads are bundled into the APK.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
  if (confirmDelete) AlertDialog(
    onDismissRequest = { confirmDelete = false },
    title = { Text("Delete OmniVoice models?") },
    text = { Text("Downloaded model weights will be removed. Notes will be preserved.") },
    confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
    dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
  )
}
