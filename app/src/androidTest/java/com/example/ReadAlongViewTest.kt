package com.example

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.text.font.FontFamily
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.components.ReadAlongView
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadAlongViewTest {
  @get:Rule val composeRule = createComposeRule()

  @Test fun seekFarAheadThenContinueFromSelectedChunk() {
    val current = mutableIntStateOf(0)
    composeRule.setContent {
      MaterialTheme {
        ReadAlongView(
          chunks = List(196) { "ខ្មែររឿងលេខ $it" },
          currentIndex = current.intValue,
          fontSize = 16,
          fontFamily = FontFamily.Default,
          onSeek = { current.intValue = it }
        )
      }
    }
    composeRule.onNodeWithTag("read_along_chunks").performScrollToIndex(102)
    composeRule.onNodeWithTag("read_chunk_102").performClick()
    composeRule.runOnIdle { assertEquals(102, current.intValue) }
    composeRule.onNodeWithTag("read_chunk_102").assertExists()
  }
}
