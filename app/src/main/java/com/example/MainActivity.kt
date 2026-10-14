package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.NoteListScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.NocturneTheme
import com.example.ui.viewmodel.NoteViewModel

enum class AppScreen {
  LIST,
  EDITOR,
  SETTINGS
}

class MainActivity : ComponentActivity() {

  private val viewModel: NoteViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Handle incoming file/text intent (VIEW / SEND)
    handleIntent(intent)

    setContent {
      val themeMode by viewModel.themeMode.collectAsState()
      var currentScreen by remember { mutableStateOf(AppScreen.LIST) }
      var previousScreen by remember { mutableStateOf(AppScreen.LIST) }

      NocturneTheme(themeMode = themeMode) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("app_root_container")
        ) {
          AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
              if (targetState == AppScreen.EDITOR || targetState == AppScreen.SETTINGS) {
                (slideInHorizontally { width -> width / 3 } + fadeIn()) togetherWith
                  (slideOutHorizontally { width -> -width / 3 } + fadeOut())
              } else {
                (slideInHorizontally { width -> -width / 3 } + fadeIn()) togetherWith
                  (slideOutHorizontally { width -> width / 3 } + fadeOut())
              }
            },
            label = "ScreenTransition"
          ) { screen ->
            when (screen) {
              AppScreen.LIST -> {
                NoteListScreen(
                  viewModel = viewModel,
                  onOpenNote = { note ->
                    viewModel.openNote(note)
                    previousScreen = AppScreen.LIST
                    currentScreen = AppScreen.EDITOR
                  },
                  onNewNote = {
                    previousScreen = AppScreen.LIST
                    currentScreen = AppScreen.EDITOR
                  },
                  onOpenSettings = {
                    previousScreen = AppScreen.LIST
                    currentScreen = AppScreen.SETTINGS
                  }
                )
              }
              AppScreen.EDITOR -> {
                EditorScreen(
                  viewModel = viewModel,
                  onNavigateBack = {
                    currentScreen = AppScreen.LIST
                  },
                  onOpenSettings = {
                    previousScreen = AppScreen.EDITOR
                    currentScreen = AppScreen.SETTINGS
                  }
                )
              }
              AppScreen.SETTINGS -> {
                SettingsScreen(
                  viewModel = viewModel,
                  onNavigateBack = {
                    currentScreen = previousScreen
                  }
                )
              }
            }
          }
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleIntent(intent)
  }

  private fun handleIntent(intent: Intent?) {
    if (intent == null) return
    val action = intent.action
    val type = intent.type

    when (action) {
      Intent.ACTION_VIEW -> {
        val uri: Uri? = intent.data
        if (uri != null) {
          viewModel.importDocumentFromUri(this, uri)
        }
      }
      Intent.ACTION_SEND -> {
        if ("text/plain" == type) {
          val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
          if (!sharedText.isNullOrBlank()) {
            viewModel.pasteClipboardText(sharedText)
          }
        } else {
          val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
          if (uri != null) {
            viewModel.importDocumentFromUri(this, uri)
          }
        }
      }
    }
  }
}
