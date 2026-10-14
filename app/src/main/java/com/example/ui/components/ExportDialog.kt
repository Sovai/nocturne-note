package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.DocumentExporter

@Composable
fun ExportDialog(
  onSaveToDevice: (DocumentExporter.ExportFormat) -> Unit,
  onShareToApps: (DocumentExporter.ExportFormat) -> Unit,
  onDismiss: () -> Unit
) {
  var selectedFormat by remember { mutableStateOf(DocumentExporter.ExportFormat.PDF) }

  val formats = listOf(
    DocumentExporter.ExportFormat.PDF to Icons.Default.PictureAsPdf,
    DocumentExporter.ExportFormat.HTML to Icons.Default.Code,
    DocumentExporter.ExportFormat.MARKDOWN to Icons.Default.Description,
    DocumentExporter.ExportFormat.PLAIN_TEXT to Icons.Default.TextFields
  )

  AlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier.testTag("export_dialog"),
    icon = {
      Icon(
        imageVector = Icons.Default.FileDownload,
        contentDescription = "Export Document",
        tint = MaterialTheme.colorScheme.primary
      )
    },
    title = {
      Text(text = "Export Document", style = MaterialTheme.typography.titleLarge)
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Text(
          text = "Select your desired export format:",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        formats.forEach { (format, icon) ->
          val isSelected = selectedFormat == format
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(10.dp))
              .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
              .border(
                1.dp,
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                RoundedCornerShape(10.dp)
              )
              .clickable { selectedFormat = format }
              .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = icon,
              contentDescription = format.displayName,
              tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = format.displayName,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = when (format) {
                  DocumentExporter.ExportFormat.PDF -> "Paginated, styled document with headers"
                  DocumentExporter.ExportFormat.HTML -> "Responsive web document with dark/light CSS"
                  DocumentExporter.ExportFormat.MARKDOWN -> "Universal markdown syntax file"
                  DocumentExporter.ExportFormat.PLAIN_TEXT -> "Clean unformatted raw text"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
              )
            }
            RadioButton(
              selected = isSelected,
              onClick = { selectedFormat = format },
              modifier = Modifier.testTag("export_radio_${format.extension}")
            )
          }
        }
      }
    },
    confirmButton = {
      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        OutlinedButton(
          onClick = {
            onShareToApps(selectedFormat)
            onDismiss()
          },
          modifier = Modifier.testTag("export_share_button")
        ) {
          Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Share")
        }

        Button(
          onClick = {
            onSaveToDevice(selectedFormat)
            onDismiss()
          },
          modifier = Modifier.testTag("export_save_device_button")
        ) {
          Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Save")
        }
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.testTag("export_dialog_cancel_button")
      ) {
        Text("Cancel")
      }
    }
  )
}
