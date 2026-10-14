package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditorZenModeTest {

  @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun zenModeRespectsSystemBarsAndExitIsAccessible() {
    composeRule.onNodeWithTag("create_note_fab").performClick()
    composeRule.onNodeWithTag("zen_mode_toggle_button").performClick()

    composeRule.onNodeWithTag("zen_exit_top_control").assertIsDisplayed()
    composeRule.onNodeWithTag("zen_mode_floating_pill").assertIsDisplayed()

    val decor = composeRule.activity.window.decorView
    val insets = WindowInsetsCompat.toWindowInsetsCompat(
      requireNotNull(decor.rootWindowInsets)
    )
    val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
    val navigationBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom

    val editor = composeRule.onNodeWithTag("editor_text_input").fetchSemanticsNode().boundsInWindow
    val exit = composeRule.onNodeWithTag("zen_exit_top_control").fetchSemanticsNode().boundsInWindow
    val floatingPill = composeRule.onNodeWithTag("zen_mode_floating_pill").fetchSemanticsNode().boundsInWindow

    assertTrue("Editor must stay below status bar", editor.top >= statusBarHeight)
    assertTrue("Exit control must stay below status bar", exit.top >= statusBarHeight)
    assertTrue(
      "Floating pill must stay above navigation bar",
      floatingPill.bottom <= decor.height - navigationBarHeight
    )

    composeRule.onNodeWithTag("zen_exit_top_control").performClick()
    composeRule.onNodeWithTag("zen_mode_toggle_button").assertIsDisplayed()
  }

  @Test
  fun zenModeCanBeExitedWithBottomButtonAndOpenedAgain() {
    composeRule.onNodeWithTag("create_note_fab").performClick()
    composeRule.onNodeWithTag("zen_mode_toggle_button").performClick()
    composeRule.onNodeWithTag("exit_zen_button").performClick()
    composeRule.onNodeWithTag("zen_mode_toggle_button").assertIsDisplayed()
    composeRule.onNodeWithTag("zen_mode_toggle_button").performClick()
    composeRule.onNodeWithTag("zen_exit_top_control").assertIsDisplayed()
  }
}
