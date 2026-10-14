package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.EditorSearchState

@Composable
fun FindAndReplaceBar(
  searchState: EditorSearchState,
  onQueryChange: (String) -> Unit,
  onReplaceChange: (String) -> Unit,
  onNext: () -> Unit,
  onPrevious: () -> Unit,
  onReplaceCurrent: () -> Unit,
  onReplaceAll: () -> Unit,
  onToggleCase: () -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier
) {
  var showReplaceRow by remember { mutableStateOf(false) }

  ElevatedCard(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 12.dp, vertical = 6.dp)
      .testTag("in_editor_find_replace_bar"),
    shape = RoundedCornerShape(14.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(8.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      // Find Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          imageVector = Icons.Default.Search,
          contentDescription = "Search",
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(20.dp).padding(start = 2.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))

        // Search Input
        Row(
          modifier = Modifier
            .weight(1f)
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          BasicTextField(
            value = searchState.query,
            onValueChange = onQueryChange,
            modifier = Modifier
              .weight(1f)
              .testTag("find_query_input"),
            textStyle = TextStyle(
              color = MaterialTheme.colorScheme.onSurface,
              fontSize = 14.sp
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            singleLine = true,
            decorationBox = { innerTextField ->
              if (searchState.query.isEmpty()) {
                Text(
                  "Find in document...",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
              }
              innerTextField()
            }
          )

          // Match count
          if (searchState.query.isNotEmpty()) {
            val countText = if (searchState.matchIndices.isEmpty()) {
              "0/0"
            } else {
              "${searchState.currentMatchIndex + 1}/${searchState.matchIndices.size}"
            }
            Text(
              text = countText,
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
              color = if (searchState.matchIndices.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(horizontal = 4.dp)
            )
          }
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Previous Match
        IconButton(
          onClick = onPrevious,
          enabled = searchState.matchIndices.isNotEmpty(),
          modifier = Modifier.size(34.dp).testTag("find_prev_button")
        ) {
          Icon(
            imageVector = Icons.Default.KeyboardArrowUp,
            contentDescription = "Previous Match",
            modifier = Modifier.size(20.dp)
          )
        }

        // Next Match
        IconButton(
          onClick = onNext,
          enabled = searchState.matchIndices.isNotEmpty(),
          modifier = Modifier.size(34.dp).testTag("find_next_button")
        ) {
          Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = "Next Match",
            modifier = Modifier.size(20.dp)
          )
        }

        // Case Sensitivity Toggle
        IconButton(
          onClick = onToggleCase,
          modifier = Modifier.size(34.dp).testTag("find_case_sensitive_button")
        ) {
          Text(
            text = "Aa",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = if (searchState.isCaseSensitive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
          )
        }

        // Toggle Replace Row
        IconButton(
          onClick = { showReplaceRow = !showReplaceRow },
          modifier = Modifier.size(34.dp).testTag("find_toggle_replace_button")
        ) {
          Icon(
            imageVector = if (showReplaceRow) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = "Toggle Replace",
            modifier = Modifier.size(20.dp)
          )
        }

        // Close Search
        IconButton(
          onClick = onClose,
          modifier = Modifier.size(34.dp).testTag("find_close_button")
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close Find",
            modifier = Modifier.size(18.dp)
          )
        }
      }

      // Replace Row
      AnimatedVisibility(
        visible = showReplaceRow,
        enter = expandVertically(),
        exit = shrinkVertically()
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Spacer(modifier = Modifier.width(28.dp))

          // Replace Input
          Row(
            modifier = Modifier
              .weight(1f)
              .height(36.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant)
              .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
              .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            BasicTextField(
              value = searchState.replaceText,
              onValueChange = onReplaceChange,
              modifier = Modifier
                .weight(1f)
                .testTag("replace_query_input"),
              textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp
              ),
              cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
              singleLine = true,
              decorationBox = { innerTextField ->
                if (searchState.replaceText.isEmpty()) {
                  Text(
                    "Replace with...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                  )
                }
                innerTextField()
              }
            )
          }

          Spacer(modifier = Modifier.width(8.dp))

          OutlinedButton(
            onClick = onReplaceCurrent,
            enabled = searchState.matchIndices.isNotEmpty() && searchState.currentMatchIndex != -1,
            modifier = Modifier.height(36.dp).testTag("replace_one_button"),
            contentPadding = ButtonDefaults.TextButtonContentPadding
          ) {
            Text("Replace", fontSize = 12.sp)
          }

          Spacer(modifier = Modifier.width(4.dp))

          OutlinedButton(
            onClick = onReplaceAll,
            enabled = searchState.matchIndices.isNotEmpty(),
            modifier = Modifier.height(36.dp).testTag("replace_all_button"),
            contentPadding = ButtonDefaults.TextButtonContentPadding
          ) {
            Text("All", fontSize = 12.sp)
          }
        }
      }
    }
  }
}
