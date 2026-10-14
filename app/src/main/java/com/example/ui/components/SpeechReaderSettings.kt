package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.tts.SpeechEngine
import com.example.tts.SpeechPreferences
import com.example.tts.SpeechVoice

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpeechReaderSettings(
  settings: SpeechPreferences,
  onEngine: (SpeechEngine) -> Unit,
  onVoice: (SpeechVoice) -> Unit,
  onSpeed: (Float) -> Unit,
  onAddress: (String) -> Unit,
  onToken: (String) -> Unit,
  onTest: () -> Unit
) {
  Card(
    modifier = Modifier.fillMaxWidth().testTag("settings_read_aloud"),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text("Read Aloud", style = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.SemiBold))
      Text("Speech engine", style = MaterialTheme.typography.bodyMedium)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SpeechEngine.entries.forEach { engine ->
          FilterChip(
            selected = settings.engine == engine,
            onClick = { onEngine(engine) },
            label = { Text(engine.label) },
            modifier = Modifier.testTag("settings_engine_" + engine.name.lowercase())
          )
        }
      }
      Text(
        "Automatic prefers OmniVoice when a local PC is configured, then offline MMS, then Android TTS.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Text("Speech speed · " + settings.speed + "×",
        style = MaterialTheme.typography.bodyMedium)
      Slider(
        value = settings.speed,
        onValueChange = onSpeed,
        valueRange = 0.75f..1.5f,
        modifier = Modifier.testTag("settings_speech_speed")
      )
      Text("OmniVoice voice style", style = MaterialTheme.typography.bodyMedium)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SpeechVoice.entries.forEach { voice ->
          FilterChip(selected = settings.voice == voice,
            onClick = { onVoice(voice) },
            label = { Text(voice.label) })
        }
      }
      Text(
        "Voice styles apply to OmniVoice only. MMS has one voice. For the most stable Khmer voice, use a Khmer reference recording configured on the PC server.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Text("OmniVoice on your PC (same Wi-Fi network)",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold)
      OutlinedTextField(
        value = settings.serverUrl,
        onValueChange = onAddress,
        label = { Text("Local server URL") },
        placeholder = { Text("http://192.168.1.10:8765") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("settings_omnivoice_url")
      )
      OutlinedTextField(
        value = settings.serverToken,
        onValueChange = onToken,
        label = { Text("Server access token") },
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("settings_omnivoice_token")
      )
      Button(
        onClick = onTest,
        enabled = settings.serverConfigured,
        modifier = Modifier.testTag("settings_omnivoice_test")
      ) { Text("Test OmniVoice connection") }
      if (settings.serverStatus.isNotEmpty()) {
        Text(settings.serverStatus,
          color = if (settings.serverStatus.startsWith("Connected"))
            MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
          style = MaterialTheme.typography.bodySmall)
      }
      Text(
        "No subscription fees. The PC must be running OmniVoice. Only connect over a trusted private LAN: HTTP sends note text and the token unencrypted. Never expose the PC server to the internet.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall
      )
    }
  }
}
