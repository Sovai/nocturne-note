package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.tts.ReadAloudState
import com.example.tts.SpeechEngine
import com.example.tts.SpeechPreferences
import com.example.ui.components.SpeechPlayerBar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h800dp")
class SpeechPlayerBarLayoutTest {
  @get:Rule val composeRule = createComposeRule()

  @Test fun edgeButtonsRemainVisibleAt320Dp() = verifyTransport(SpeechEngine.EDGE, 320, 1f)

  @Test fun longEngineAndLargeFontKeepButtonsVisibleAt280Dp() =
    verifyTransport(SpeechEngine.OMNIVOICE, 280, 2f)

  private fun verifyTransport(engine: SpeechEngine, width: Int, fontScale: Float) {
    var skipped = 0
    var stopped = false
    composeRule.setContent {
      val density = LocalDensity.current.density
      CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
        MaterialTheme {
          Box(Modifier.width(width.dp)) {
            SpeechPlayerBar(
              state = ReadAloudState(active = true, playing = true, paragraph = 4,
                totalParagraphs = 120, chunks = List(120) { "ខ្មែរ" }),
              preferences = SpeechPreferences(engine = engine),
              androidVoices = emptyList(), omniInstalled = true,
              onToggle = {}, onStop = { stopped = true }, onSeek = {},
              onSkip = { skipped = it }, onSpeed = {}, onEngine = {},
              onAndroidVoice = {}, onEdgeVoice = {}, onEdgePitch = {},
              onMmsTrim = {}, onMmsPause = {}, onMmsThreads = {}
            )
          }
        }
      }
    }
    val player = composeRule.onNodeWithTag("read_aloud_player").fetchSemanticsNode().boundsInRoot
    listOf("previous", "toggle", "next", "stop").forEach { control ->
      val node = composeRule.onNodeWithTag("read_aloud_$control")
      node.assertIsDisplayed()
      node.assertWidthIsAtLeast(48.dp)
      val bounds = node.fetchSemanticsNode().boundsInRoot
      assertTrue("$control must fit entirely within the player", bounds.left >= player.left &&
        bounds.right <= player.right && bounds.top >= player.top && bounds.bottom <= player.bottom)
    }
    composeRule.onNodeWithTag("read_aloud_next").performClick()
    composeRule.onNodeWithTag("read_aloud_stop").performClick()
    composeRule.runOnIdle {
      assertEquals(1, skipped)
      assertTrue(stopped)
    }
  }
}
