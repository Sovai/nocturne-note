package com.example.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FormattingBar(
  canUndo: Boolean,
  canRedo: Boolean,
  onUndo: () -> Unit,
  onRedo: () -> Unit,
  onBold: () -> Unit,
  onItalic: () -> Unit,
  onUnderline: () -> Unit,
  onStrike: () -> Unit,
  onBullet: () -> Unit,
  onNumbered: () -> Unit,
  onCheckbox: () -> Unit,
  onHeading: (Int) -> Unit,
  onCode: () -> Unit,
  onCodeBlock: () -> Unit,
  onQuote: () -> Unit,
  onTable: () -> Unit,
  onLink: () -> Unit,
  onDivider: () -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 10.dp, vertical = 4.dp)
      .clip(RoundedCornerShape(14.dp))
      .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
      .height(46.dp)
      .testTag("formatting_accessory_bar"),
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    tonalElevation = 3.dp
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .padding(horizontal = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
      // Undo / Redo
      FormatIconButton(
        icon = Icons.AutoMirrored.Filled.Undo,
        description = "Undo",
        testTag = "format_undo_button",
        enabled = canUndo,
        onClick = onUndo
      )
      FormatIconButton(
        icon = Icons.AutoMirrored.Filled.Redo,
        description = "Redo",
        testTag = "format_redo_button",
        enabled = canRedo,
        onClick = onRedo
      )

      VerticalDivider(modifier = Modifier.height(20.dp).padding(horizontal = 4.dp))

      // Basic Text Formatting: Bold, Italics, Underline, Strikethrough
      FormatIconButton(
        icon = Icons.Default.FormatBold,
        description = "Bold",
        testTag = "format_bold_button",
        onClick = onBold
      )
      FormatIconButton(
        icon = Icons.Default.FormatItalic,
        description = "Italic",
        testTag = "format_italic_button",
        onClick = onItalic
      )
      FormatIconButton(
        icon = Icons.Default.FormatUnderlined,
        description = "Underline",
        testTag = "format_underline_button",
        onClick = onUnderline
      )
      FormatIconButton(
        icon = Icons.Default.FormatStrikethrough,
        description = "Strikethrough",
        testTag = "format_strike_button",
        onClick = onStrike
      )

      VerticalDivider(modifier = Modifier.height(20.dp).padding(horizontal = 4.dp))

      // Lists & Tasks: Bulleted List, Numbered List, Checkbox Task
      FormatIconButton(
        icon = Icons.Default.FormatListBulleted,
        description = "Bulleted List",
        testTag = "format_bullet_button",
        onClick = onBullet
      )
      FormatIconButton(
        icon = Icons.Default.FormatListNumbered,
        description = "Numbered List",
        testTag = "format_numbered_button",
        onClick = onNumbered
      )
      FormatIconButton(
        icon = Icons.Default.CheckBox,
        description = "Checkbox Task",
        testTag = "format_checkbox_button",
        onClick = onCheckbox
      )

      VerticalDivider(modifier = Modifier.height(20.dp).padding(horizontal = 4.dp))

      // Headings
      FormatTextButton(label = "H1", testTag = "format_h1_button", onClick = { onHeading(1) })
      FormatTextButton(label = "H2", testTag = "format_h2_button", onClick = { onHeading(2) })
      FormatTextButton(label = "H3", testTag = "format_h3_button", onClick = { onHeading(3) })

      VerticalDivider(modifier = Modifier.height(20.dp).padding(horizontal = 4.dp))

      // Quote & Code
      FormatIconButton(
        icon = Icons.Default.FormatQuote,
        description = "Quote",
        testTag = "format_quote_button",
        onClick = onQuote
      )
      FormatIconButton(
        icon = Icons.Default.Code,
        description = "Inline Code",
        testTag = "format_code_button",
        onClick = onCode
      )
      FormatIconButton(
        icon = Icons.Default.DataObject,
        description = "Code Block",
        testTag = "format_codeblock_button",
        onClick = onCodeBlock
      )

      VerticalDivider(modifier = Modifier.height(20.dp).padding(horizontal = 4.dp))

      // Table, Link, Divider
      FormatIconButton(
        icon = Icons.Default.TableChart,
        description = "Table Template",
        testTag = "format_table_button",
        onClick = onTable
      )
      FormatIconButton(
        icon = Icons.Default.Link,
        description = "Insert Link",
        testTag = "format_link_button",
        onClick = onLink
      )
      FormatIconButton(
        icon = Icons.Default.HorizontalRule,
        description = "Horizontal Line",
        testTag = "format_divider_button",
        onClick = onDivider
      )
    }
  }
}

@Composable
private fun FormatIconButton(
  icon: ImageVector,
  description: String,
  testTag: String,
  enabled: Boolean = true,
  onClick: () -> Unit
) {
  Surface(
    modifier = Modifier
      .size(34.dp)
      .clip(RoundedCornerShape(8.dp))
      .clickable(enabled = enabled, onClick = onClick)
      .testTag(testTag),
    color = Color.Transparent
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(
        imageVector = icon,
        contentDescription = description,
        modifier = Modifier.size(19.dp),
        tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
      )
    }
  }
}

@Composable
private fun FormatTextButton(
  label: String,
  testTag: String,
  onClick: () -> Unit
) {
  Surface(
    modifier = Modifier
      .size(34.dp)
      .clip(RoundedCornerShape(8.dp))
      .clickable(onClick = onClick)
      .testTag(testTag),
    color = Color.Transparent
  ) {
    Box(contentAlignment = Alignment.Center) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium.copy(
          fontWeight = FontWeight.Bold,
          fontSize = 13.sp
        ),
        color = MaterialTheme.colorScheme.primary
      )
    }
  }
}
