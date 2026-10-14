package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import com.example.ui.theme.withKhmerFont
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.MarkdownBlock
import com.example.util.MarkdownParser

@Composable
fun MarkdownPreviewView(
  content: String,
  fontSize: Int = 16,
  fontFamily: FontFamily = FontFamily.Default,
  modifier: Modifier = Modifier
) {
  val blocks = remember(content) { MarkdownParser.parseBlocks(content) }
  val context = LocalContext.current

  SelectionContainer {
    Column(
      modifier = modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 20.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      if (blocks.isEmpty()) {
        Text(
          text = "Empty document. Start typing in the editor to see preview.",
          style = MaterialTheme.typography.bodyMedium.copy(
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
          )
        )
      } else {
        blocks.forEach { block ->
          RenderMarkdownBlock(
            block = block,
            baseFontSize = fontSize.sp,
            fontFamily = fontFamily,
            context = context
          )
        }
      }
      Spacer(modifier = Modifier.height(60.dp))
    }
  }
}

@Composable
private fun RenderMarkdownBlock(
  block: MarkdownBlock,
  baseFontSize: TextUnit,
  fontFamily: FontFamily,
  context: Context
) {
  when (block) {
    is MarkdownBlock.Heading -> {
      val (style, size) = when (block.level) {
        1 -> MaterialTheme.typography.headlineLarge to (baseFontSize.value + 10).sp
        2 -> MaterialTheme.typography.headlineMedium to (baseFontSize.value + 6).sp
        3 -> MaterialTheme.typography.titleLarge to (baseFontSize.value + 3).sp
        else -> MaterialTheme.typography.titleMedium to (baseFontSize.value + 1).sp
      }
      Text(
        text = MarkdownParser.parseInlineMarkdown(block.text).withKhmerFont(),
        style = style.copy(
          fontSize = size,
          fontWeight = FontWeight.Bold,
          fontFamily = fontFamily
        ),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 8.dp)
      )
    }

    is MarkdownBlock.Paragraph -> {
      Text(
        text = MarkdownParser.parseInlineMarkdown(block.text).withKhmerFont(),
        style = MaterialTheme.typography.bodyLarge.copy(
          fontSize = baseFontSize,
          lineHeight = (baseFontSize.value * 1.6f).sp,
          fontFamily = fontFamily
        ),
        color = MaterialTheme.colorScheme.onSurface
      )
    }

    is MarkdownBlock.Blockquote -> {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
          .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
          .padding(vertical = 10.dp, horizontal = 14.dp)
      ) {
        Box(
          modifier = Modifier
            .width(4.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
          text = MarkdownParser.parseInlineMarkdown(block.text).withKhmerFont(),
          style = MaterialTheme.typography.bodyLarge.copy(
            fontSize = baseFontSize,
            fontStyle = FontStyle.Italic,
            fontFamily = fontFamily
          ),
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }

    is MarkdownBlock.CodeBlock -> {
      Surface(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(8.dp))
          .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
        color = MaterialTheme.colorScheme.surfaceContainer
      ) {
        Column {
          // Code header with language and copy
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .background(MaterialTheme.colorScheme.surfaceContainerHigh)
              .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = block.language.ifBlank { "code" }.uppercase(),
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
              color = MaterialTheme.colorScheme.primary
            )
            IconButton(
              onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("code", block.code)
                clipboard.setPrimaryClip(clip)
              },
              modifier = Modifier.size(24.dp)
            ) {
              Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy Code",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
          // Code content with horizontal scrolling
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState())
              .padding(12.dp)
          ) {
            Text(
              text = block.code.withKhmerFont(),
              style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = (baseFontSize.value - 2).sp,
                lineHeight = (baseFontSize.value * 1.4f).sp
              ),
              color = MaterialTheme.colorScheme.onSurface
            )
          }
        }
      }
    }

    is MarkdownBlock.TaskItem -> {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          imageVector = if (block.isChecked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
          contentDescription = if (block.isChecked) "Completed" else "Uncompleted",
          tint = if (block.isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
          text = MarkdownParser.parseInlineMarkdown(block.text).withKhmerFont(),
          style = MaterialTheme.typography.bodyLarge.copy(
            fontSize = baseFontSize,
            fontFamily = fontFamily
          ),
          color = if (block.isChecked) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface
        )
      }
    }

    is MarkdownBlock.BulletItem -> {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(start = (block.level * 16).dp),
        verticalAlignment = Alignment.Top
      ) {
        Text(
          text = "•",
          style = MaterialTheme.typography.bodyLarge.copy(
            fontSize = (baseFontSize.value + 4).sp,
            fontWeight = FontWeight.Bold
          ),
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.padding(end = 8.dp)
        )
        Text(
          text = MarkdownParser.parseInlineMarkdown(block.text).withKhmerFont(),
          style = MaterialTheme.typography.bodyLarge.copy(
            fontSize = baseFontSize,
            lineHeight = (baseFontSize.value * 1.5f).sp,
            fontFamily = fontFamily
          ),
          color = MaterialTheme.colorScheme.onSurface
        )
      }
    }

    is MarkdownBlock.NumberedItem -> {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
      ) {
        Text(
          text = block.number,
          style = MaterialTheme.typography.bodyLarge.copy(
            fontSize = baseFontSize,
            fontWeight = FontWeight.Bold
          ),
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.width(28.dp)
        )
        Text(
          text = MarkdownParser.parseInlineMarkdown(block.text).withKhmerFont(),
          style = MaterialTheme.typography.bodyLarge.copy(
            fontSize = baseFontSize,
            lineHeight = (baseFontSize.value * 1.5f).sp,
            fontFamily = fontFamily
          ),
          color = MaterialTheme.colorScheme.onSurface
        )
      }
    }

    is MarkdownBlock.Table -> {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState())
          .clip(RoundedCornerShape(8.dp))
          .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
      ) {
        Column {
          // Table Header
          Row(
            modifier = Modifier
              .background(MaterialTheme.colorScheme.surfaceContainerHigh)
              .padding(vertical = 8.dp)
          ) {
            block.headers.forEach { header ->
              Text(
                text = header.withKhmerFont(),
                style = MaterialTheme.typography.bodyMedium.copy(
                  fontWeight = FontWeight.Bold,
                  fontSize = (baseFontSize.value - 1).sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                  .width(140.dp)
                  .padding(horizontal = 12.dp)
              )
            }
          }
          HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
          // Rows
          block.rows.forEachIndexed { index, row ->
            Row(
              modifier = Modifier
                .background(if (index % 2 == 0) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.4f))
                .padding(vertical = 8.dp)
            ) {
              row.forEach { cell ->
                Text(
                  text = MarkdownParser.parseInlineMarkdown(cell).withKhmerFont(),
                  style = MaterialTheme.typography.bodyMedium.copy(fontSize = (baseFontSize.value - 2).sp),
                  color = MaterialTheme.colorScheme.onSurface,
                  modifier = Modifier
                    .width(140.dp)
                    .padding(horizontal = 12.dp)
                )
              }
            }
            if (index < block.rows.size - 1) {
              HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            }
          }
        }
      }
    }

    MarkdownBlock.Divider -> {
      HorizontalDivider(
        modifier = Modifier.padding(vertical = 8.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
      )
    }
  }
}
