package com.example

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.NoteListScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.NocturneTheme
import com.example.ui.viewmodel.NoteViewModel
import com.example.ui.viewmodel.EditorViewMode

enum class AppScreen {
  LIST,
  EDITOR,
  SETTINGS
}

class MainActivity : ComponentActivity() {

  private val viewModel: NoteViewModel by viewModels()
  private val openImportedFile = mutableStateOf(false)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Handle incoming file/text intent (VIEW / SEND)
    handleIntent(intent)

    setContent {
      val themeMode by viewModel.themeMode.collectAsState()
      val userMessage by viewModel.userMessage.collectAsState()
      val appSnackbarHostState = remember { SnackbarHostState() }

      LaunchedEffect(userMessage) {
        userMessage?.let { message ->
          viewModel.clearUserMessage()
          appSnackbarHostState.showSnackbar(message)
        }
      }
      var currentScreen by remember { mutableStateOf(AppScreen.LIST) }
      var previousScreen by remember { mutableStateOf(AppScreen.LIST) }

      LaunchedEffect(openImportedFile.value) {
        if (openImportedFile.value) {
          previousScreen = AppScreen.LIST
          currentScreen = AppScreen.EDITOR
          openImportedFile.value = false
        }
      }

      NocturneTheme(themeMode = themeMode) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            // Keep every screen inside the system bars, including while Zen mode
            // removes its app bar. Insets are consumed here to prevent double padding.
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
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
                    viewModel.createNewNote()
                    previousScreen = AppScreen.LIST
                    currentScreen = AppScreen.EDITOR
                  },
                  onImportedNote = {
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
          SnackbarHost(
            hostState = appSnackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
              .navigationBarsPadding().imePadding().padding(bottom = 12.dp)
              .testTag("global_snackbar_host")
          )
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleIntent(intent)
  }

  private fun importAndPreview(uri: Uri) {
    viewModel.importDocumentFromUri(this, uri) {
      viewModel.setViewMode(EditorViewMode.PREVIEW)
      openImportedFile.value = true
    }
  }

  private fun handleIntent(intent: Intent?) {
    if (intent == null) return
    val action = intent.action
    val type = intent.type

    when (action) {
      Intent.ACTION_VIEW -> {
        val uri: Uri? = intent.data
        if (uri != null) {
          importAndPreview(uri)
        }
      }
      Intent.ACTION_SEND -> {
        val sharedText = if (type == "text/plain") {
          intent.getStringExtra(Intent.EXTRA_TEXT)
        } else null

        if (!sharedText.isNullOrBlank()) {
          viewModel.pasteClipboardText(sharedText)
          viewModel.setViewMode(EditorViewMode.PREVIEW)
          openImportedFile.value = true
        } else {
          val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
          if (uri != null) importAndPreview(uri)
        }
      }
    }
  }
}
