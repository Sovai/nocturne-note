package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.withKhmerFont

/** A virtualized reader that highlights/scrolls only while a reading session is active.
 * Tapping any chunk immediately seeks there, then plays subsequent chunks in sequence.
 */
@Composable
fun ReadAlongView(
  chunks: List<String>,
  currentIndex: Int,
  fontSize: Int,
  fontFamily: FontFamily,
  onSeek: (Int) -> Unit,
  modifier: Modifier = Modifier
) {
  val state = rememberLazyListState()
  LaunchedEffect(chunks, currentIndex) {
    if (currentIndex in chunks.indices) {
      state.animateScrollToItem(currentIndex.coerceAtLeast(0))
    }
  }
  LazyColumn(
    state = state,
    modifier = modifier.fillMaxSize().testTag("read_along_chunks"),
    contentPadding = androidx.compose.foundation.layout.PaddingValues(
      horizontal = 16.dp, vertical = 18.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    itemsIndexed(chunks, key = { index, _ -> index }) { index, chunk ->
      val active = index == currentIndex
      Row(
        modifier = Modifier.fillMaxWidth()
          .background(if (active) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
            RoundedCornerShape(12.dp))
          .clickable { onSeek(index) }
          .padding(horizontal = 12.dp, vertical = 14.dp)
          .testTag("read_chunk_" + index)
      ) {
        Text(text = (index + 1).toString(),
          style = MaterialTheme.typography.labelSmall,
          color = if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = 5.dp, end = 14.dp))
        Text(
          text = chunk.withKhmerFont(),
          style = MaterialTheme.typography.bodyLarge.copy(
            fontFamily = fontFamily, fontSize = fontSize.sp,
            lineHeight = (fontSize * 1.65f).sp,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal),
          color = MaterialTheme.colorScheme.onSurface
        )
      }
    }
  }
}
