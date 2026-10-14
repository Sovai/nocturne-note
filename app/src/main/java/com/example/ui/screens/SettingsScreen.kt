package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FormatLineSpacing
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.SpeechModelSettings
import com.example.ui.components.SpeechReaderSettings
import com.example.ui.components.MmsPlaybackSettings
import com.example.ui.components.OmniVoiceModelSettings
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.EditorFontChoice
import com.example.ui.theme.withKhmerFont
import com.example.ui.viewmodel.NoteViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  viewModel: NoteViewModel,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val currentTheme by viewModel.themeMode.collectAsState()
  val currentFont by viewModel.fontChoice.collectAsState()
  val currentFontSize by viewModel.fontSize.collectAsState()
  val showLineNumbers by viewModel.showLineNumbers.collectAsState()
  val isAutosaveEnabled by viewModel.isAutosaveEnabled.collectAsState()
  val autosaveIntervalMinutes by viewModel.autosaveIntervalMinutes.collectAsState()
  val allNotes by viewModel.allNotes.collectAsState()
  val speechModel by viewModel.speechModelState.collectAsState()
  val omniModel by viewModel.omniVoiceModelState.collectAsState()
  val speechSettings by viewModel.speechPreferences.collectAsState()
  val androidVoices by viewModel.availableAndroidVoices.collectAsState()

  BackHandler(onBack = onNavigateBack)

  Scaffold(
    modifier = modifier
      .fillMaxSize()
      .statusBarsPadding()
      .navigationBarsPadding()
      .testTag("settings_screen"),
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = "Settings",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
          )
        },
        navigationIcon = {
          IconButton(
            onClick = onNavigateBack,
            modifier = Modifier.testTag("settings_back_button")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back"
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface
        )
      )
    }
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // 1. Appearance & Theme (Compact 2x2 grid - no dead space)
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Palette,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Theme & Appearance",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
          }

          // One clear light/dark choice. Light is the first-launch default.
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            ThemeOptionItem(
              title = "Light",
              subtitle = "Default",
              previewColor = Color(0xFFF8FAFC),
              accentColor = Color(0xFF166B7D),
              isSelected = currentTheme == AppThemeMode.LIGHT,
              onClick = { viewModel.setThemeMode(AppThemeMode.LIGHT) },
              modifier = Modifier.weight(1f)
            )
            ThemeOptionItem(
              title = "Dark",
              subtitle = "Night navy",
              previewColor = Color(0xFF081421),
              accentColor = Color(0xFF7BD4DE),
              isSelected = currentTheme == AppThemeMode.DARK,
              onClick = { viewModel.setThemeMode(AppThemeMode.DARK) },
              modifier = Modifier.weight(1f)
            )
          }
        }
      }

      // 2. Editor Typography
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.TextFields,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Editor Typography",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
          }

          Text(
            text = "English Font",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
          )
          Text(
            text = "Khmer always uses Noto Serif Khmer.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          // Two-row picker: six English fonts, three per row.
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EditorFontChoice.entries.chunked(3).forEach { rowFonts ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                rowFonts.forEach { font ->
              val isSelected = currentFont == font
              Surface(
                modifier = Modifier
                  .weight(1f)
                  .height(52.dp)
                  .clip(RoundedCornerShape(10.dp))
                  .border(
                    1.dp,
                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    RoundedCornerShape(10.dp)
                  )
                  .clickable { viewModel.setFontChoice(font) }
                  .testTag("settings_font_${font.name.lowercase()}"),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Text(
                    text = font.label,
                    maxLines = 2,
                    style = MaterialTheme.typography.bodyMedium.copy(
                      fontFamily = font.fontFamily,
                      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    ),
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                  )
                }
              }
                }
              }
            }
          }

          // Font Size Slider with Live Preview
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FormatSize, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Font Size", style = MaterialTheme.typography.bodyMedium)
              }
              Text(
                text = "${currentFontSize}sp",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
              )
            }
            Slider(
              value = currentFontSize.toFloat(),
              onValueChange = { viewModel.setFontSize(it.toInt()) },
              valueRange = 12f..24f,
              steps = 5,
              modifier = Modifier.testTag("settings_font_slider")
            )
            // Live Preview Snippet
            Surface(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(8.dp),
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ) {
              Text(
                text = "សាកល្បងអក្សរខ្មែរ • NocturneNote".withKhmerFont(),
                style = MaterialTheme.typography.bodyMedium.copy(
                  fontSize = currentFontSize.sp,
                  fontFamily = currentFont.fontFamily
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(10.dp)
              )
            }
          }
        }
      }

      // 3. Writing & Autosave
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Autorenew,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Writing & Autosave",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
          }

          // Autosave Toggle
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Automatic Document Save",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
              )
              Text(
                text = "Saves edits periodically to prevent lost thoughts",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Switch(
              checked = isAutosaveEnabled,
              onCheckedChange = { viewModel.toggleAutosave() },
              modifier = Modifier.testTag("settings_autosave_switch")
            )
          }

          // Autosave Interval
          if (isAutosaveEnabled) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "Autosave Interval:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(1, 2, 5).forEach { mins ->
                  FilterChip(
                    selected = autosaveIntervalMinutes == mins,
                    onClick = { viewModel.setAutosaveInterval(mins) },
                    label = { Text("$mins min", fontSize = 11.sp) },
                    modifier = Modifier.testTag("settings_interval_$mins")
                  )
                }
              }
            }
          }

          // Show Line Numbers Toggle
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Gutter Line Numbers",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
              )
              Text(
                text = "Display margin line counters in editor",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Switch(
              checked = showLineNumbers,
              onCheckedChange = { viewModel.toggleLineNumbers() },
              modifier = Modifier.testTag("settings_line_numbers_switch")
            )
          }
        }
      }

      SpeechReaderSettings(
        settings = speechSettings,
        androidVoices = androidVoices,
        onAndroidVoice = { viewModel.setAndroidVoice(it) },
        onEngine = { viewModel.setSpeechEngine(it) },
        onVoice = { viewModel.setSpeechVoice(it) },
        onSpeed = { viewModel.setReadAloudSpeed(it) },
        onBackground = { viewModel.setBackgroundReading(it) }
      )

      OmniVoiceModelSettings(
        state = omniModel,
        onRefresh = { viewModel.refreshOmniVoiceModels() },
        onDownload = { viewModel.downloadOmniVoiceModels() },
        onDelete = { viewModel.deleteOmniVoiceModels() }
      )

      MmsPlaybackSettings(
        preferences = speechSettings,
        onTrim = { viewModel.setMmsTrimSilence(it) },
        onPause = { viewModel.setMmsPauseMs(it) },
        onThreads = { viewModel.setMmsThreads(it) }
      )

      SpeechModelSettings(
        state = speechModel,
        onDownload = { viewModel.downloadKhmerModel() },
        onDelete = { viewModel.deleteKhmerModel() }
      )

      // 4. Privacy & Offline Information
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Security,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.tertiary,
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Privacy & Storage",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
          }

          Text(
            text = "Documents stay on-device. Optional Khmer voice downloads use the internet once. All ${allNotes.size} documents, version snapshots, and exported files remain entirely on this device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      Spacer(modifier = Modifier.height(24.dp))
    }
  }
}

@Composable
private fun ThemeOptionItem(
  title: String,
  subtitle: String,
  previewColor: Color,
  accentColor: Color,
  isSelected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    modifier = modifier
      .height(56.dp)
      .clip(RoundedCornerShape(12.dp))
      .border(
        1.5.dp,
        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
        RoundedCornerShape(12.dp)
      )
      .clickable(onClick = onClick),
    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface
  ) {
    Row(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      // Swatch Circle
      Box(
        modifier = Modifier
          .size(26.dp)
          .clip(CircleShape)
          .background(previewColor)
          .border(1.dp, accentColor, CircleShape),
        contentAlignment = Alignment.Center
      ) {
        if (isSelected) {
          Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(16.dp)
          )
        }
      }

      Column {
        Text(
          text = title,
          style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1
        )
      }
    }
  }
}
