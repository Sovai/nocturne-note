package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.ExportDialog
import com.example.ui.components.FindAndReplaceBar
import com.example.ui.components.FormattingBar
import com.example.ui.components.MarkdownPreviewView
import com.example.ui.components.VersionHistorySheet
import com.example.ui.theme.KhmerFontVisualTransformation
import com.example.ui.viewmodel.EditorViewMode
import com.example.ui.viewmodel.NoteViewModel
import com.example.util.DocumentExporter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
  viewModel: NoteViewModel,
  onNavigateBack: () -> Unit,
  onOpenSettings: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current

  val editorState by viewModel.editorState.collectAsState()
  val activeNoteVersions by viewModel.activeNoteVersions.collectAsState()
  val searchState by viewModel.editorSearch.collectAsState()
  val fontChoice by viewModel.fontChoice.collectAsState()
  val fontSize by viewModel.fontSize.collectAsState()
  val showLineNumbers by viewModel.showLineNumbers.collectAsState()
  val lastAutosaveStatus by viewModel.lastAutosaveStatus.collectAsState()

  var showExportDialog by remember { mutableStateOf(false) }
  var showVersionHistorySheet by remember { mutableStateOf(false) }
  var showMoreMenu by remember { mutableStateOf(false) }

  // Target format for saving to device storage
  var pendingExportFormat by remember { mutableStateOf<DocumentExporter.ExportFormat?>(null) }

  val createDocumentLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("application/octet-stream")
  ) { uri: Uri? ->
    val format = pendingExportFormat
    if (uri != null && format != null) {
      viewModel.exportDocumentToUri(context, uri, format)
    }
    pendingExportFormat = null
  }

  // Handle back button navigation
  BackHandler {
    when {
      editorState.isZenMode -> viewModel.exitZenMode()
      searchState.isOpen -> viewModel.closeEditorSearch()
      else -> {
        if (!editorState.isSaved && editorState.content.isNotBlank()) {
          viewModel.saveActiveNote()
        }
        onNavigateBack()
      }
    }
  }

  Scaffold(
    // The activity already applies and consumes safe drawing insets. Keep Scaffold
    // geometry stable when switching between regular and distraction-free modes.
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    modifier = modifier
      .fillMaxSize()
      .onPreviewKeyEvent { event ->
        // Hardware keyboard shortcuts support
        if (event.isCtrlPressed) {
          when {
            event.key == Key.B -> { viewModel.formatBold(); true }
            event.key == Key.I -> { viewModel.formatItalic(); true }
            event.key == Key.U -> { viewModel.formatUnderline(); true }
            event.key == Key.L && event.isShiftPressed -> { viewModel.formatNumberedList(); true }
            event.key == Key.L -> { viewModel.formatBulletList(); true }
            event.key == Key.S -> { viewModel.saveActiveNote(); true }
            event.key == Key.H -> { showVersionHistorySheet = true; true }
            event.key == Key.P -> {
              val nextMode = when (editorState.viewMode) {
                EditorViewMode.EDIT -> EditorViewMode.PREVIEW
                EditorViewMode.PREVIEW -> EditorViewMode.EDIT
                EditorViewMode.SPLIT -> EditorViewMode.EDIT
              }
              viewModel.setViewMode(nextMode)
              true
            }
            event.key == Key.D -> { viewModel.toggleZenMode(); true }
            event.key == Key.F -> { viewModel.openEditorSearch(); true }
            event.key == Key.K -> { viewModel.insertMarkdownSyntax("[", "](https://)", "link"); true }
            event.key == Key.Z -> { viewModel.undo(); true }
            event.key == Key.Y -> { viewModel.redo(); true }
            else -> false
          }
        } else if (event.key == Key.Escape) {
          if (editorState.isZenMode) {
            viewModel.exitZenMode()
            true
          } else if (searchState.isOpen) {
            viewModel.closeEditorSearch()
            true
          } else {
            false
          }
        } else {
          false
        }
      }
      .testTag("editor_screen"),
    topBar = {
      // Hide TopAppBar entirely in Distraction-Free Zen Mode
      if (!editorState.isZenMode) {
        TopAppBar(
          title = {
            BasicTextField(
              value = editorState.title,
              onValueChange = { viewModel.updateTitle(it) },
              modifier = Modifier
                .fillMaxWidth()
                .testTag("editor_title_input"),
              textStyle = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 17.sp
              ),
              cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
              singleLine = true,
              visualTransformation = KhmerFontVisualTransformation,
              decorationBox = { innerTextField ->
                if (editorState.title.isEmpty()) {
                  Text(
                    text = "Untitled Note",
                    style = MaterialTheme.typography.titleMedium.copy(
                      fontWeight = FontWeight.Normal,
                      color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                  )
                }
                innerTextField()
              }
            )
          },
          navigationIcon = {
            IconButton(
              onClick = {
                if (!editorState.isSaved && editorState.content.isNotBlank()) {
                  viewModel.saveActiveNote()
                }
                onNavigateBack()
              },
              modifier = Modifier.testTag("editor_back_button")
            ) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Notes")
            }
          },
          actions = {
            // Elegant View Mode Pill
            ViewModeSelector(
              currentMode = editorState.viewMode,
              onModeSelect = { viewModel.setViewMode(it) }
            )

            // Zen Focus Mode Toggle
            IconButton(
              onClick = { viewModel.toggleZenMode() },
              modifier = Modifier.testTag("zen_mode_toggle_button")
            ) {
              Icon(
                imageVector = Icons.Default.Fullscreen,
                contentDescription = "Distraction-Free Zen Mode",
                tint = MaterialTheme.colorScheme.primary
              )
            }

            // Quick Save Button with State Feedback
            IconButton(
              onClick = { viewModel.saveActiveNote() },
              modifier = Modifier.testTag("editor_save_button")
            ) {
              Icon(
                imageVector = Icons.Default.Save,
                contentDescription = "Save Note",
                tint = if (!editorState.isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
              )
            }

            // Refined More Options Menu (Clutter-free!)
            Box {
              IconButton(
                onClick = { showMoreMenu = true },
                modifier = Modifier.testTag("editor_more_options_button")
              ) {
                Icon(
                  imageVector = Icons.Default.MoreVert,
                  contentDescription = "More options",
                  tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }

              DropdownMenu(
                expanded = showMoreMenu,
                onDismissRequest = { showMoreMenu = false }
              ) {
                DropdownMenuItem(
                  text = { Text("Version History") },
                  leadingIcon = {
                    Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                  },
                  onClick = {
                    showMoreMenu = false
                    showVersionHistorySheet = true
                  }
                )
                DropdownMenuItem(
                  text = { Text("Find & Replace") },
                  leadingIcon = {
                    Icon(Icons.Default.FindInPage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                  },
                  onClick = {
                    showMoreMenu = false
                    viewModel.openEditorSearch()
                  }
                )
                DropdownMenuItem(
                  text = { Text("Export Document") },
                  leadingIcon = {
                    Icon(Icons.Default.FileDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                  },
                  onClick = {
                    showMoreMenu = false
                    showExportDialog = true
                  }
                )
                DropdownMenuItem(
                  text = { Text("Settings") },
                  leadingIcon = {
                    Icon(Icons.Default.Settings, contentDescription = null)
                  },
                  onClick = {
                    showMoreMenu = false
                    onOpenSettings()
                  }
                )
              }
            }
          },
          colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
          )
        )
      }
    }
  ) { paddingValues ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .imePadding()
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        // In-Editor Find & Replace Bar
        AnimatedVisibility(
          visible = searchState.isOpen,
          enter = slideInVertically() + fadeIn(),
          exit = slideOutVertically() + fadeOut()
        ) {
          FindAndReplaceBar(
            searchState = searchState,
            onQueryChange = { viewModel.setEditorSearchQuery(it) },
            onReplaceChange = { viewModel.setEditorReplaceQuery(it) },
            onNext = { viewModel.findNextMatch() },
            onPrevious = { viewModel.findPreviousMatch() },
            onReplaceCurrent = { viewModel.replaceCurrentMatch() },
            onReplaceAll = { viewModel.replaceAllMatches() },
            onToggleCase = { viewModel.toggleCaseSensitive() },
            onClose = { viewModel.closeEditorSearch() }
          )
        }

        // Main Editor / Preview Body (Clean, balanced margins - not pushed to the right!)
        Box(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
        ) {
          when (editorState.viewMode) {
            EditorViewMode.EDIT -> {
              EditorInputView(
                content = editorState.content,
                fontSize = fontSize,
                fontFamily = fontChoice.fontFamily,
                showLineNumbers = showLineNumbers,
                isZenMode = editorState.isZenMode,
                onContentChange = { viewModel.updateContent(it) },
                modifier = Modifier.fillMaxSize()
              )
            }
            EditorViewMode.PREVIEW -> {
              MarkdownPreviewView(
                content = editorState.content,
                fontSize = fontSize,
                fontFamily = fontChoice.fontFamily,
                modifier = Modifier
                  .fillMaxSize()
                  .testTag("fullscreen_markdown_preview")
              )
            }
            EditorViewMode.SPLIT -> {
              Row(modifier = Modifier.fillMaxSize()) {
                EditorInputView(
                  content = editorState.content,
                  fontSize = fontSize,
                  fontFamily = fontChoice.fontFamily,
                  showLineNumbers = showLineNumbers,
                  isZenMode = false,
                  onContentChange = { viewModel.updateContent(it) },
                  modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                )
                Box(
                  modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                )
                MarkdownPreviewView(
                  content = editorState.content,
                  fontSize = fontSize,
                  fontFamily = fontChoice.fontFamily,
                  modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                )
              }
            }
          }
        }

        // Floating Accessory Formatting Bar (visible in Edit mode)
        if (editorState.viewMode != EditorViewMode.PREVIEW && !editorState.isZenMode) {
          FormattingBar(
            canUndo = editorState.canUndo,
            canRedo = editorState.canRedo,
            onUndo = { viewModel.undo() },
            onRedo = { viewModel.redo() },
            onBold = { viewModel.formatBold() },
            onItalic = { viewModel.formatItalic() },
            onUnderline = { viewModel.formatUnderline() },
            onStrike = { viewModel.insertMarkdownSyntax("~~", "~~", "strikethrough") },
            onBullet = { viewModel.formatBulletList() },
            onNumbered = { viewModel.formatNumberedList() },
            onCheckbox = { viewModel.insertCheckboxTask() },
            onHeading = { viewModel.insertHeading(it) },
            onCode = { viewModel.insertMarkdownSyntax("`", "`", "code") },
            onCodeBlock = { viewModel.insertMarkdownSyntax("```\n", "\n```", "code") },
            onQuote = { viewModel.insertMarkdownSyntax("> ", placeholder = "Quote") },
            onTable = { viewModel.insertTableTemplate() },
            onLink = { viewModel.insertMarkdownSyntax("[", "](https://)", "link text") },
            onDivider = { viewModel.insertMarkdownSyntax("\n---\n") }
          )
        }

        // Status & Stats Bar (when not in Zen mode)
        if (!editorState.isZenMode) {
          EditorStatusBar(
            wordCount = editorState.wordCount,
            charCount = editorState.charCount,
            readingTimeMinutes = editorState.readingTimeMinutes,
            isSaved = editorState.isSaved,
            autosaveStatus = lastAutosaveStatus,
            onHistoryClick = { showVersionHistorySheet = true },
            onSettingsClick = onOpenSettings
          )
        }
      }

      // A 48dp top-right exit target stays reachable even when the keyboard is visible.
      if (editorState.isZenMode) {
        Surface(
          modifier = Modifier.align(Alignment.TopEnd)
            .padding(top = 8.dp, end = 12.dp)
            .testTag("zen_exit_top_control"),
          shape = CircleShape,
          color = MaterialTheme.colorScheme.surfaceContainerHigh,
          tonalElevation = 4.dp
        ) {
          IconButton(onClick = { viewModel.exitZenMode() }, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.CloseFullscreen, contentDescription = "Exit fullscreen")
          }
        }
      }

      // Minimalist Floating Pill for Zen Mode (respecting bottom & safe areas)
      if (editorState.isZenMode) {
        Surface(
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 20.dp)
            .clip(RoundedCornerShape(30.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(30.dp))
            .testTag("zen_mode_floating_pill"),
          color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
          tonalElevation = 6.dp
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Text(
              text = "${editorState.wordCount} words • ${editorState.readingTimeMinutes} min",
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Box(
              modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )

            Text(
              text = lastAutosaveStatus,
              style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
              ),
              color = if (editorState.isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )

            // History Button in Zen Pill
            IconButton(
              onClick = { showVersionHistorySheet = true },
              modifier = Modifier.size(28.dp).testTag("zen_history_button")
            ) {
              Icon(
                imageVector = Icons.Default.History,
                contentDescription = "Version History",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }

            // Exit Zen Mode Button
            IconButton(
              onClick = { viewModel.exitZenMode() },
              modifier = Modifier.size(28.dp).testTag("exit_zen_button")
            ) {
              Icon(
                imageVector = Icons.Default.CloseFullscreen,
                contentDescription = "Exit Zen Mode",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
              )
            }
          }
        }
      }
    }
  }

  // Version History Modal Sheet
  if (showVersionHistorySheet) {
    VersionHistorySheet(
      versions = activeNoteVersions,
      currentContent = editorState.content,
      onRestoreVersion = { viewModel.restoreVersion(it) },
      onDismiss = { showVersionHistorySheet = false }
    )
  }

  // Export Modal Dialog
  if (showExportDialog) {
    ExportDialog(
      onSaveToDevice = { format ->
        pendingExportFormat = format
        val safeTitle = (editorState.title.ifBlank { "NocturneNote" }).replace(Regex("[^a-zA-Z0-9_-]"), "_")
        createDocumentLauncher.launch("$safeTitle.${format.extension}")
      },
      onShareToApps = { format ->
        viewModel.shareDocument(context, format)
      },
      onDismiss = { showExportDialog = false }
    )
  }
}

@Composable
private fun ViewModeSelector(
  currentMode: EditorViewMode,
  onModeSelect: (EditorViewMode) -> Unit
) {
  Row(
    modifier = Modifier
      .clip(RoundedCornerShape(10.dp))
      .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
      .padding(2.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    // Edit
    Surface(
      modifier = Modifier
        .size(32.dp)
        .clip(RoundedCornerShape(8.dp))
        .clickable { onModeSelect(EditorViewMode.EDIT) }
        .testTag("view_mode_edit_button"),
      color = if (currentMode == EditorViewMode.EDIT) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          imageVector = Icons.Default.Edit,
          contentDescription = "Edit Mode",
          modifier = Modifier.size(16.dp),
          tint = if (currentMode == EditorViewMode.EDIT) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }

    // Preview
    Surface(
      modifier = Modifier
        .size(32.dp)
        .clip(RoundedCornerShape(8.dp))
        .clickable { onModeSelect(EditorViewMode.PREVIEW) }
        .testTag("view_mode_preview_button"),
      color = if (currentMode == EditorViewMode.PREVIEW) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          imageVector = Icons.Default.Preview,
          contentDescription = "Preview Mode",
          modifier = Modifier.size(16.dp),
          tint = if (currentMode == EditorViewMode.PREVIEW) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }

    // Split
    Surface(
      modifier = Modifier
        .size(32.dp)
        .clip(RoundedCornerShape(8.dp))
        .clickable { onModeSelect(EditorViewMode.SPLIT) }
        .testTag("view_mode_split_button"),
      color = if (currentMode == EditorViewMode.SPLIT) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          imageVector = Icons.Default.VerticalSplit,
          contentDescription = "Split View Mode",
          modifier = Modifier.size(16.dp),
          tint = if (currentMode == EditorViewMode.SPLIT) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

@Composable
private fun EditorInputView(
  content: String,
  fontSize: Int,
  fontFamily: FontFamily,
  showLineNumbers: Boolean,
  isZenMode: Boolean,
  onContentChange: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  val lines = remember(content) { content.lines() }

  Row(
    modifier = modifier
      .background(MaterialTheme.colorScheme.background)
      .padding(
        horizontal = if (isZenMode) 20.dp else 16.dp,
        vertical = if (isZenMode) 16.dp else 12.dp
      )
  ) {
    // Optional Gutter Line Numbers
    if (showLineNumbers) {
      Column(
        modifier = Modifier
          .padding(end = 12.dp)
          .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.End
      ) {
        val totalLines = lines.size.coerceAtLeast(1)
        for (idx in 1..totalLines) {
          Text(
            text = idx.toString(),
            style = TextStyle(
              fontFamily = FontFamily.Monospace,
              fontSize = (fontSize - 3).sp,
              lineHeight = (fontSize * 1.55f).sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            )
          )
        }
      }
    }

    // Main Text Input Area - fully responsive with correct horizontal bounds
    BasicTextField(
      value = content,
      onValueChange = onContentChange,
      modifier = Modifier
        // BasicTextField already scrolls its text; wrapping it in verticalScroll
        // adds an unbounded measurement pass during fullscreen relayout.
        .fillMaxSize()
        .testTag("editor_text_input"),
      textStyle = TextStyle(
        fontFamily = fontFamily,
        fontSize = fontSize.sp,
        lineHeight = (fontSize * 1.55f).sp,
        color = MaterialTheme.colorScheme.onBackground
      ),
      cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
      visualTransformation = KhmerFontVisualTransformation,
      decorationBox = { innerTextField ->
        if (content.isEmpty()) {
          Text(
            text = "Begin writing your thoughts in Markdown or plain text...\nTap Zen mode for distraction-free focus.",
            style = TextStyle(
              fontFamily = fontFamily,
              fontSize = fontSize.sp,
              lineHeight = (fontSize * 1.55f).sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
          )
        }
        innerTextField()
      }
    )
  }
}

@Composable
private fun EditorStatusBar(
  wordCount: Int,
  charCount: Int,
  readingTimeMinutes: Int,
  isSaved: Boolean,
  autosaveStatus: String,
  onHistoryClick: () -> Unit,
  onSettingsClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    modifier = modifier
      .fillMaxWidth()
      .height(34.dp)
      .testTag("editor_status_bar"),
    color = MaterialTheme.colorScheme.surface
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Text(
          text = "$wordCount words",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
          text = "•",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        Text(
          text = "$charCount chars",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
          text = "•",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        Text(
          text = "$readingTimeMinutes min read",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.primary
        )
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Text(
          text = autosaveStatus,
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
          color = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )

        IconButton(
          onClick = onHistoryClick,
          modifier = Modifier.size(24.dp).testTag("status_bar_history_button")
        ) {
          Icon(
            imageVector = Icons.Default.History,
            contentDescription = "Version History",
            modifier = Modifier.size(15.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        IconButton(
          onClick = onSettingsClick,
          modifier = Modifier.size(24.dp).testTag("status_bar_settings_button")
        ) {
          Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = "Settings",
            modifier = Modifier.size(15.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
  }
}
