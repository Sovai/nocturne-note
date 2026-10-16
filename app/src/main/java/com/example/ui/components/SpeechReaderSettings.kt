package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.example.tts.EdgeTtsClient
import com.example.tts.EdgeVoice
import com.example.tts.SpeechEngine
import com.example.tts.SpeechLanguage
import com.example.tts.SpeechPreferences
import java.util.Locale

@Composable
fun SpeechReaderSettings(
  settings: SpeechPreferences,
  androidVoices: List<String>,
  edgeVoices: List<EdgeVoice>,
  onEngine: (SpeechEngine) -> Unit,
  onLanguage: (String) -> Unit,
  onAndroidVoice: (String) -> Unit,
  onEdgeVoice: (String) -> Unit,
  onEdgePitch: (Int) -> Unit,
  onSpeed: (Float) -> Unit,
  onBackground: (Boolean) -> Unit
) {
  var languageDialog by remember { mutableStateOf(false) }
  var edgeVoiceDialog by remember { mutableStateOf(false) }
  var androidVoiceDialog by remember { mutableStateOf(false) }
  var search by remember { mutableStateOf("") }
  val selectedLocale = if (settings.languageTag == "auto")
    SpeechLanguage.resolve("", "auto") else settings.languageTag
  val visibleVoices = edgeVoices.filter { it.locale == selectedLocale }
    .ifEmpty { edgeVoices.filter { it.locale.substringBefore('-') == selectedLocale.substringBefore('-') } }
  val currentVoice = edgeVoices.firstOrNull { it.name == settings.edgeVoice }
  val locales = edgeVoices.map { it.locale }.distinct()
    .sortedWith(compareBy({ Locale.forLanguageTag(it).getDisplayLanguage(Locale.getDefault()) }, { it }))

  Card(
    modifier = Modifier.fillMaxWidth().testTag("settings_read_aloud"),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.RecordVoiceOver, contentDescription = null,
          tint = MaterialTheme.colorScheme.primary)
        Text("Read Aloud", style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold)
      }
      // Exactly two choices in one compact row; the fallback runs automatically.
      Row(modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SpeechEngine.entries.forEach { engine ->
          val selected = settings.engine == engine
          Surface(
            modifier = Modifier.weight(1f)
              .clickable { onEngine(engine) }
              .testTag("settings_engine_" + engine.name.lowercase()),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.outlineVariant),
            color = if (selected) MaterialTheme.colorScheme.primaryContainer
              else MaterialTheme.colorScheme.surface
          ) {
            Column(modifier = Modifier.padding(horizontal = 11.dp, vertical = 10.dp),
              verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Text(if (engine == SpeechEngine.EDGE) "Edge Read Aloud" else "Android TTS",
                style = MaterialTheme.typography.labelLarge, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
              Text(if (engine == SpeechEngine.EDGE) "Online · default" else "System voices",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
          }
        }
      }
      Text("If Edge is unavailable, Android TTS takes over automatically.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      SettingsPickerRow(
        title = "Reading language",
        value = if (settings.languageTag == "auto") "Automatic · detect script / device language"
          else "${Locale.forLanguageTag(settings.languageTag).getDisplayName(Locale.getDefault())} · ${settings.languageTag}",
        tag = "settings_speech_language",
        onClick = { search = ""; languageDialog = true }
      )
      if (settings.engine == SpeechEngine.EDGE) {
        SettingsPickerRow(
          title = "Voice",
          value = currentVoice?.displayName ?: "Automatic · matching language",
          tag = "settings_edge_voice",
          onClick = { search = ""; edgeVoiceDialog = true }
        )
      } else {
        SettingsPickerRow(
          title = "System voice",
          value = settings.androidVoiceName.ifBlank { "System default" },
          tag = "settings_android_voice",
          onClick = { search = ""; androidVoiceDialog = true }
        )
      }

      Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween) {
          Text("Reading speed", style = MaterialTheme.typography.bodyMedium)
          Text(String.format(Locale.US, "%.2f×", settings.speed),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = settings.speed, onValueChange = onSpeed, valueRange = 0.75f..1.5f,
          modifier = Modifier.testTag("settings_speech_speed"))
      }

      if (settings.engine == SpeechEngine.EDGE) {
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
          Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Voice pitch", style = MaterialTheme.typography.bodyMedium)
            Text("${settings.edgePitchHz} Hz", style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.primary)
          }
          Slider(value = settings.edgePitchHz.toFloat(), onValueChange = { onEdgePitch(it.toInt()) },
            valueRange = -40f..40f, modifier = Modifier.testTag("edge_pitch_slider"))
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
          Text("Background playback", style = MaterialTheme.typography.bodyMedium)
          Text("Show playback controls while the screen is off",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = settings.backgroundPlayback, onCheckedChange = onBackground,
          modifier = Modifier.testTag("settings_background_playback"))
      }
      Text(
        "Edge sends the passage being read to Microsoft's online service. It is an unofficial, " +
          "no-key connection and may be blocked. Android TTS uses installed system voices. " +
          "Leaving a note stops its reader.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }

  if (languageDialog) SpeechSelectionDialog(
    title = "Reading language",
    search = search,
    onSearch = { search = it },
    items = listOf("auto" to "Automatic · detect script / device language") +
      locales.map { it to "${Locale.forLanguageTag(it).getDisplayName(Locale.getDefault())} · $it" },
    selected = settings.languageTag,
    onSelect = { onLanguage(it); languageDialog = false },
    onDismiss = { languageDialog = false }
  )
  if (edgeVoiceDialog) SpeechSelectionDialog(
    title = "Voice · $selectedLocale",
    search = search,
    onSearch = { search = it },
    items = listOf("" to "Automatic · matching language") +
      visibleVoices.map { it.name to it.displayName },
    selected = settings.edgeVoice,
    onSelect = { onEdgeVoice(it); edgeVoiceDialog = false },
    onDismiss = { edgeVoiceDialog = false }
  )
  if (androidVoiceDialog) SpeechSelectionDialog(
    title = "Android system voice",
    search = search,
    onSearch = { search = it },
    items = listOf("" to "System default") + androidVoices.map { it to it },
    selected = settings.androidVoiceName,
    onSelect = { onAndroidVoice(it); androidVoiceDialog = false },
    onDismiss = { androidVoiceDialog = false }
  )
}

@Composable
private fun SettingsPickerRow(title: String, value: String, tag: String, onClick: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
      .testTag(tag).padding(vertical = 3.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
      Text(value, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Icon(Icons.Default.ChevronRight, contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
private fun SpeechSelectionDialog(
  title: String, search: String, onSearch: (String) -> Unit,
  items: List<Pair<String, String>>, selected: String,
  onSelect: (String) -> Unit, onDismiss: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title, style = MaterialTheme.typography.titleMedium) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = search, onValueChange = onSearch,
          label = { Text("Search") }, singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("speech_picker_search"))
        val filtered = items.filter { it.second.contains(search, ignoreCase = true) ||
          it.first.contains(search, ignoreCase = true) }
        LazyColumn(modifier = Modifier.heightIn(max = 310.dp)) {
          items(filtered, key = { it.first }) { item ->
            Row(modifier = Modifier.fillMaxWidth().clickable { onSelect(item.first) }
              .padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Text(item.second, modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium)
              if (item.first == selected) Icon(Icons.Default.Check, null,
                tint = MaterialTheme.colorScheme.primary)
            }
          }
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
  )
}
