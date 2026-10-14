package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.example.tts.KhmerModelManager
import com.example.tts.ReadAloudController
import com.example.tts.SpeechSettingsStore
import com.example.tts.SpeechEngine
import com.example.tts.SpeechVoice
import com.example.tts.OmniVoiceClient
import com.example.ui.editor.MarkdownEditorActions
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.NoteEntity
import com.example.data.model.NoteVersionEntity
import com.example.data.repository.NoteRepository
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.EditorFontChoice
import com.example.util.DocumentExporter
import com.example.util.PdfTextExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class EditorViewMode {
  EDIT,
  PREVIEW,
  SPLIT
}

enum class NoteSortOrder(val displayName: String) {
  MODIFIED_DESC("Recently Updated"),
  MODIFIED_ASC("Oldest First"),
  TITLE_ASC("Title (A-Z)"),
  WORDS_DESC("Longest Reads")
}

enum class FilterCategory(val displayName: String) {
  ALL("All"),
  PINNED("Pinned"),
  MARKDOWN("Markdown"),
  TEXT("Text"),
  PDF("PDF Imports")
}

data class EditorState(
  val noteId: Long = 0,
  val title: String = "",
  val content: String = "",
  val tags: String = "",
  val formatHint: String = "MD",
  val isPinned: Boolean = false,
  val isSaved: Boolean = true,
  val lastSavedTime: Long = System.currentTimeMillis(),
  val viewMode: EditorViewMode = EditorViewMode.PREVIEW,
  val isZenMode: Boolean = false,
  val wordCount: Int = 0,
  val charCount: Int = 0,
  val readingTimeMinutes: Int = 1,
  val canUndo: Boolean = false,
  val canRedo: Boolean = false
)

data class EditorSearchState(
  val isOpen: Boolean = false,
  val query: String = "",
  val replaceText: String = "",
  val isCaseSensitive: Boolean = false,
  val matchIndices: List<Int> = emptyList(),
  val currentMatchIndex: Int = -1
)

class NoteViewModel(application: Application) : AndroidViewModel(application) {

  private val khmerModelManager = KhmerModelManager(application)
  private val speechSettingsStore = SpeechSettingsStore(application)
  private val readAloudController = ReadAloudController(application, khmerModelManager, speechSettingsStore)
  val speechPreferences = speechSettingsStore.state
  fun setSpeechEngine(engine: SpeechEngine) = speechSettingsStore.engine(engine)
  fun setSpeechVoice(voice: SpeechVoice) = speechSettingsStore.voice(voice)
  fun setSpeechServer(url: String) = speechSettingsStore.server(url)
  fun setSpeechToken(token: String) = speechSettingsStore.token(token)
  fun testOmniVoice() {
    speechSettingsStore.status("Connecting…")
    viewModelScope.launch {
      val status = runCatching { OmniVoiceClient().health(speechSettingsStore.state.value) }
        .fold(onSuccess = { if (it) "Connected to OmniVoice" else "Unexpected server response" },
          onFailure = { "Connection failed: " + (it.message ?: "Unknown error") })
      speechSettingsStore.status(status)
    }
  }
  val speechModelState = khmerModelManager.state
  val readAloudState = readAloudController.state

  fun downloadKhmerModel() { viewModelScope.launch { khmerModelManager.download() } }
  fun deleteKhmerModel() { readAloudController.stop(); viewModelScope.launch { khmerModelManager.delete() } }
  fun startReadAloud() {
    val current = _editorTextFieldValue.value
    val selection = current.selection
    val source = if (selection.collapsed || _editorState.value.viewMode != EditorViewMode.EDIT) _editorState.value.content
      else current.text.substring(selection.min, selection.max)
    readAloudController.start(source)
  }
  fun toggleReadAloud() = readAloudController.toggle()
  fun stopReadAloud() = readAloudController.stop()
  fun skipReadAloudParagraph(delta: Int) = readAloudController.skip(delta)
  fun setReadAloudSpeed(speed: Float) = readAloudController.setSpeed(speed)

  private val repository: NoteRepository
  init {
    val db = AppDatabase.getDatabase(application, viewModelScope)
    repository = NoteRepository(db.noteDao(), db.noteVersionDao())
  }

  // App Theme & Typography Settings
  private val themePreferences = application.getSharedPreferences("nocturnenote_appearance", android.content.Context.MODE_PRIVATE)
  private val _themeMode = MutableStateFlow(
    when (themePreferences.getString("theme", AppThemeMode.LIGHT.name)) {
      "DARK", "DARK_SLATE", "DARK_NOCTURNE" -> AppThemeMode.DARK
      else -> AppThemeMode.LIGHT
    }
  )
  val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

  private val fontPreferences = application.getSharedPreferences("nocturnenote_typography", android.content.Context.MODE_PRIVATE)
  private val _fontChoice = MutableStateFlow(
    runCatching {
      EditorFontChoice.valueOf(
        fontPreferences.getString("editor_font", EditorFontChoice.SANS.name)
          ?: EditorFontChoice.SANS.name
      )
    }.getOrDefault(EditorFontChoice.SANS)
  )
  val fontChoice: StateFlow<EditorFontChoice> = _fontChoice.asStateFlow()

  private val _fontSize = MutableStateFlow(16)
  val fontSize: StateFlow<Int> = _fontSize.asStateFlow()

  private val _showLineNumbers = MutableStateFlow(false)
  val showLineNumbers: StateFlow<Boolean> = _showLineNumbers.asStateFlow()

  // Autosave Settings & Status
  val isAutosaveEnabled = MutableStateFlow(true)
  val autosaveIntervalMinutes = MutableStateFlow(2) // 1, 2, or 5 mins
  private val _lastAutosaveStatus = MutableStateFlow("All changes saved")
  val lastAutosaveStatus: StateFlow<String> = _lastAutosaveStatus.asStateFlow()

  // Library / Search State
  val searchQuery = MutableStateFlow("")
  val activeFilter = MutableStateFlow(FilterCategory.ALL)
  val sortOrder = MutableStateFlow(NoteSortOrder.MODIFIED_DESC)

  val allNotes: StateFlow<List<NoteEntity>> = repository.getAllNotes()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  // Filtered & Sorted Notes list
  val filteredNotes: StateFlow<List<NoteEntity>> = combine(
    allNotes,
    searchQuery,
    activeFilter,
    sortOrder
  ) { notes, query, filter, sort ->
    var result = if (query.isBlank()) {
      notes
    } else {
      val q = query.trim().lowercase()
      notes.filter {
        it.title.lowercase().contains(q) ||
          it.content.lowercase().contains(q) ||
          it.tags.lowercase().contains(q)
      }
    }

    result = when (filter) {
      FilterCategory.ALL -> result
      FilterCategory.PINNED -> result.filter { it.isPinned }
      FilterCategory.MARKDOWN -> result.filter { it.formatHint == "MD" }
      FilterCategory.TEXT -> result.filter { it.formatHint == "TXT" }
      FilterCategory.PDF -> result.filter { it.formatHint == "PDF" }
    }

    when (sort) {
      NoteSortOrder.MODIFIED_DESC -> result.sortedWith(compareByDescending<NoteEntity> { it.isPinned }.thenByDescending { it.updatedAt })
      NoteSortOrder.MODIFIED_ASC -> result.sortedWith(compareByDescending<NoteEntity> { it.isPinned }.thenBy { it.updatedAt })
      NoteSortOrder.TITLE_ASC -> result.sortedWith(compareByDescending<NoteEntity> { it.isPinned }.thenBy { it.title.lowercase() })
      NoteSortOrder.WORDS_DESC -> result.sortedWith(compareByDescending<NoteEntity> { it.isPinned }.thenByDescending { it.wordCount })
    }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  // Active Editor State
  private val _editorState = MutableStateFlow(EditorState())
  val editorState: StateFlow<EditorState> = _editorState.asStateFlow()

  // Retain selection and IME state when the formatting toolbar is tapped.
  private val _editorTextFieldValue = MutableStateFlow(TextFieldValue())
  val editorTextFieldValue: StateFlow<TextFieldValue> = _editorTextFieldValue.asStateFlow()

  fun updateEditorTextFieldValue(value: TextFieldValue) {
    _editorTextFieldValue.value = value
    if (value.text != _editorState.value.content) updateContent(value.text)
  }

  private fun syncEditorText(content: String) {
    val field = _editorTextFieldValue.value
    if (field.text != content) {
      _editorTextFieldValue.value = TextFieldValue(
        text = content,
        selection = TextRange(
          field.selection.start.coerceIn(0, content.length),
          field.selection.end.coerceIn(0, content.length)
        )
      )
    }
  }

  // Active Note Versions
  val activeNoteVersions: StateFlow<List<NoteVersionEntity>> = _editorState
    .flatMapLatest { state ->
      if (state.noteId != 0L) {
        repository.getVersionsForNote(state.noteId)
      } else {
        flowOf(emptyList())
      }
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  // In-Editor Search & Replace
  private val _editorSearch = MutableStateFlow(EditorSearchState())
  val editorSearch: StateFlow<EditorSearchState> = _editorSearch.asStateFlow()

  // Undo / Redo History
  private val undoStack = mutableListOf<String>()
  private val redoStack = mutableListOf<String>()

  // Autosave background coroutine jobs
  private var periodicAutosaveJob: Job? = null
  private var debouncedAutosaveJob: Job? = null
  private var lastVersionSnapshotTime: Long = 0L
  @Volatile private var editorGeneration: Long = 0L

  // Notifications / Snackbar messages
  private val _userMessage = MutableStateFlow<String?>(null)
  val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

  init {
    startPeriodicAutosaveLoop()
  }

  fun clearUserMessage() {
    _userMessage.value = null
  }

  fun setThemeMode(mode: AppThemeMode) {
    themePreferences.edit().putString("theme", mode.name).apply()
    _themeMode.value = mode
  }

  fun setFontChoice(choice: EditorFontChoice) {
    fontPreferences.edit().putString("editor_font", choice.name).apply()
    _fontChoice.value = choice
  }

  fun setFontSize(size: Int) {
    _fontSize.value = size.coerceIn(12, 28)
  }

  fun toggleLineNumbers() {
    _showLineNumbers.value = !_showLineNumbers.value
  }

  fun setAutosaveInterval(minutes: Int) {
    autosaveIntervalMinutes.value = minutes.coerceIn(1, 10)
    startPeriodicAutosaveLoop()
  }

  fun toggleAutosave() {
    isAutosaveEnabled.value = !isAutosaveEnabled.value
    if (isAutosaveEnabled.value) {
      startPeriodicAutosaveLoop()
    } else {
      periodicAutosaveJob?.cancel()
      _lastAutosaveStatus.value = "Autosave off"
    }
  }

  // --- Note Management ---

  fun openNote(note: NoteEntity) {
    stopReadAloud()
    editorGeneration++
    debouncedAutosaveJob?.cancel()
    undoStack.clear()
    redoStack.clear()
    undoStack.add(note.content)

    val words = if (note.content.isBlank()) 0 else note.content.trim().split("\\s+".toRegex()).size
    val chars = note.content.length

    _editorState.value = EditorState(
      noteId = note.id,
      title = note.title,
      content = note.content,
      tags = note.tags,
      formatHint = note.formatHint,
      isPinned = note.isPinned,
      isSaved = true,
      lastSavedTime = note.updatedAt,
      viewMode = EditorViewMode.PREVIEW,
      isZenMode = false,
      wordCount = words,
      charCount = chars,
      readingTimeMinutes = (words / 200).coerceAtLeast(1),
      canUndo = false,
      canRedo = false
    )
    _editorTextFieldValue.value = TextFieldValue(note.content, TextRange(0))
    _editorSearch.value = EditorSearchState()
    _lastAutosaveStatus.value = "All changes saved"
    lastVersionSnapshotTime = System.currentTimeMillis()
  }

  fun createNewNote(initialTitle: String = "", initialContent: String = "", formatHint: String = "MD") {
    stopReadAloud()
    editorGeneration++
    debouncedAutosaveJob?.cancel()
    undoStack.clear()
    redoStack.clear()
    undoStack.add(initialContent)

    val words = if (initialContent.isBlank()) 0 else initialContent.trim().split("\\s+".toRegex()).size

    _editorState.value = EditorState(
      noteId = 0,
      title = initialTitle,
      content = initialContent,
      tags = "",
      formatHint = formatHint,
      isPinned = false,
      isSaved = false,
      lastSavedTime = System.currentTimeMillis(),
      viewMode = EditorViewMode.EDIT,
      isZenMode = false,
      wordCount = words,
      charCount = initialContent.length,
      readingTimeMinutes = 1,
      canUndo = false,
      canRedo = false
    )
    _editorTextFieldValue.value = TextFieldValue(initialContent, TextRange(initialContent.length))
    _editorSearch.value = EditorSearchState()
    _lastAutosaveStatus.value = "Unsaved draft"
    lastVersionSnapshotTime = 0L
  }

  fun updateTitle(newTitle: String) {
    _editorState.value = _editorState.value.copy(
      title = newTitle,
      isSaved = false
    )
    _lastAutosaveStatus.value = "Unsaved changes"
    scheduleDebouncedAutosave()
  }

  fun updateContent(newContent: String) {
    syncEditorText(newContent)
    val current = _editorState.value.content
    if (newContent != current) {
      if (undoStack.isEmpty() || undoStack.last() != current) {
        undoStack.add(current)
        if (undoStack.size > 50) undoStack.removeAt(0)
      }
      redoStack.clear()
    }

    val words = if (newContent.isBlank()) 0 else newContent.trim().split("\\s+".toRegex()).size
    val chars = newContent.length

    _editorState.value = _editorState.value.copy(
      content = newContent,
      isSaved = false,
      wordCount = words,
      charCount = chars,
      readingTimeMinutes = (words / 200).coerceAtLeast(1),
      canUndo = undoStack.isNotEmpty(),
      canRedo = false
    )
    _lastAutosaveStatus.value = "Unsaved changes"

    // Update search matches if search bar is open
    if (_editorSearch.value.isOpen && _editorSearch.value.query.isNotBlank()) {
      recomputeSearchMatches(_editorSearch.value.query, _editorSearch.value.isCaseSensitive, newContent)
    }

    scheduleDebouncedAutosave()
  }

  fun undo() {
    if (undoStack.isNotEmpty()) {
      val prevContent = undoStack.removeAt(undoStack.lastIndex)
      val current = _editorState.value.content
      redoStack.add(current)

      val words = if (prevContent.isBlank()) 0 else prevContent.trim().split("\\s+".toRegex()).size
      syncEditorText(prevContent)
      _editorState.value = _editorState.value.copy(
        content = prevContent,
        isSaved = false,
        wordCount = words,
        charCount = prevContent.length,
        readingTimeMinutes = (words / 200).coerceAtLeast(1),
        canUndo = undoStack.isNotEmpty(),
        canRedo = true
      )
      scheduleDebouncedAutosave()
    }
  }

  fun redo() {
    if (redoStack.isNotEmpty()) {
      val nextContent = redoStack.removeAt(redoStack.lastIndex)
      val current = _editorState.value.content
      undoStack.add(current)

      val words = if (nextContent.isBlank()) 0 else nextContent.trim().split("\\s+".toRegex()).size
      syncEditorText(nextContent)
      _editorState.value = _editorState.value.copy(
        content = nextContent,
        isSaved = false,
        wordCount = words,
        charCount = nextContent.length,
        readingTimeMinutes = (words / 200).coerceAtLeast(1),
        canUndo = true,
        canRedo = redoStack.isNotEmpty()
      )
      scheduleDebouncedAutosave()
    }
  }

  /**
   * Manual save invoked by user or Ctrl+S.
   */
  fun saveActiveNote(reason: String = "Manual Save") {
    val state = _editorState.value
    val generation = editorGeneration
    if (state.content.isBlank() && state.title.isBlank()) return

    val autoTitle = if (state.title.isBlank()) {
      val firstLine = state.content.lines().firstOrNull { it.isNotBlank() }?.trim() ?: "Untitled Note"
      firstLine.removePrefix("#").removePrefix("##").trim().take(40)
    } else {
      state.title.trim()
    }

    val now = System.currentTimeMillis()
    val noteEntity = NoteEntity(
      id = state.noteId,
      title = autoTitle,
      content = state.content,
      createdAt = if (state.noteId == 0L) now else state.lastSavedTime,
      updatedAt = now,
      isPinned = state.isPinned,
      tags = state.tags,
      formatHint = state.formatHint,
      wordCount = state.wordCount,
      characterCount = state.charCount
    )

    viewModelScope.launch(Dispatchers.IO) {
      val finalId = if (state.noteId == 0L) {
        repository.insertNote(noteEntity)
      } else {
        repository.updateNote(noteEntity)
        state.noteId
      }

      // Record Version History Snapshot
      repository.insertVersion(
        NoteVersionEntity(
          noteId = finalId,
          title = autoTitle,
          content = state.content,
          timestamp = now,
          wordCount = state.wordCount,
          charCount = state.charCount,
          reason = reason
        )
      )
      withContext(Dispatchers.Main) {
        // An older async write must never overwrite a new note or newer edits.
        if (editorGeneration == generation && _editorState.value.content == state.content &&
          _editorState.value.title == state.title && _editorState.value.noteId == state.noteId) {
          _editorState.value = _editorState.value.copy(
            noteId = finalId,
            title = autoTitle,
            isSaved = true,
            lastSavedTime = now
          )
          lastVersionSnapshotTime = now
          val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(now))
          _lastAutosaveStatus.value = "Saved at $timeStr"
          _userMessage.value = "Note saved"
        }
      }
    }
  }

  /**
   * Periodic Autosave: Runs every few minutes in the background automatically.
   */
  private fun startPeriodicAutosaveLoop() {
    periodicAutosaveJob?.cancel()
    periodicAutosaveJob = viewModelScope.launch(Dispatchers.IO) {
      while (isActive) {
        val intervalMs = autosaveIntervalMinutes.value * 60_000L
        delay(intervalMs)
        if (isAutosaveEnabled.value && !_editorState.value.isSaved && _editorState.value.content.isNotBlank()) {
          performAutosave(createVersionSnapshot = true)
        }
      }
    }
  }

  private fun scheduleDebouncedAutosave() {
    if (!isAutosaveEnabled.value) return
    debouncedAutosaveJob?.cancel()
    debouncedAutosaveJob = viewModelScope.launch(Dispatchers.IO) {
      delay(3000L) // 3 seconds after typing stops
      if (isActive && !_editorState.value.isSaved && _editorState.value.content.isNotBlank()) {
        val now = System.currentTimeMillis()
        val shouldSnapshot = (now - lastVersionSnapshotTime) >= (autosaveIntervalMinutes.value * 60_000L)
        performAutosave(createVersionSnapshot = shouldSnapshot)
      }
    }
  }

  private suspend fun performAutosave(createVersionSnapshot: Boolean) {
    val state = _editorState.value
    val generation = editorGeneration
    if (state.content.isBlank() && state.title.isBlank()) return

    val autoTitle = if (state.title.isBlank()) {
      val firstLine = state.content.lines().firstOrNull { it.isNotBlank() }?.trim() ?: "Untitled Note"
      firstLine.removePrefix("#").removePrefix("##").trim().take(40)
    } else {
      state.title.trim()
    }

    val now = System.currentTimeMillis()
    val noteEntity = NoteEntity(
      id = state.noteId,
      title = autoTitle,
      content = state.content,
      createdAt = if (state.noteId == 0L) now else state.lastSavedTime,
      updatedAt = now,
      isPinned = state.isPinned,
      tags = state.tags,
      formatHint = state.formatHint,
      wordCount = state.wordCount,
      characterCount = state.charCount
    )

    val finalId = if (state.noteId == 0L) {
      repository.insertNote(noteEntity)
    } else {
      repository.updateNote(noteEntity)
      state.noteId
    }

    if (createVersionSnapshot) {
      repository.insertVersion(
        NoteVersionEntity(
          noteId = finalId,
          title = autoTitle,
          content = state.content,
          timestamp = now,
          wordCount = state.wordCount,
          charCount = state.charCount,
          reason = "Autosave"
        )
      )
    }

    withContext(Dispatchers.Main) {
      if (editorGeneration == generation && _editorState.value.content == state.content &&
        _editorState.value.title == state.title && _editorState.value.noteId == state.noteId) {
        _editorState.value = _editorState.value.copy(
          noteId = finalId,
          title = autoTitle,
          isSaved = true,
          lastSavedTime = now
        )
        if (createVersionSnapshot) lastVersionSnapshotTime = now
        val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(now))
        _lastAutosaveStatus.value = "Autosaved at $timeStr"
      }
    }
  }

  /**
   * Restores a past saved state of the document from Version History.
   */
  fun restoreVersion(version: NoteVersionEntity) {
    val current = _editorState.value.content
    if (undoStack.isEmpty() || undoStack.last() != current) {
      undoStack.add(current)
    }

    val words = if (version.content.isBlank()) 0 else version.content.trim().split("\\s+".toRegex()).size
    val chars = version.content.length

    syncEditorText(version.content)
    _editorState.value = _editorState.value.copy(
      title = version.title,
      content = version.content,
      isSaved = false,
      wordCount = words,
      charCount = chars,
      readingTimeMinutes = (words / 200).coerceAtLeast(1),
      canUndo = true,
      canRedo = false
    )

    val dateFormatted = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(version.timestamp))
    saveActiveNote(reason = "Restored from $dateFormatted")
    _userMessage.value = "Restored version from $dateFormatted"
  }

  fun deleteNote(note: NoteEntity) {
    viewModelScope.launch(Dispatchers.IO) {
      repository.deleteNote(note)
      withContext(Dispatchers.Main) {
        _userMessage.value = "Note deleted: ${note.title}"
      }
    }
  }

  fun togglePinNote(noteId: Long, currentPin: Boolean) {
    viewModelScope.launch(Dispatchers.IO) {
      repository.togglePin(noteId, !currentPin)
      if (_editorState.value.noteId == noteId) {
        withContext(Dispatchers.Main) {
          _editorState.value = _editorState.value.copy(isPinned = !currentPin)
        }
      }
    }
  }

  // --- View Mode & Zen Mode ---

  fun setViewMode(mode: EditorViewMode) {
    _editorState.value = _editorState.value.copy(viewMode = mode)
  }

  fun toggleZenMode() {
    val current = _editorState.value.isZenMode
    _editorState.value = _editorState.value.copy(isZenMode = !current)
  }

  fun exitZenMode() {
    _editorState.value = _editorState.value.copy(isZenMode = false)
  }

  // --- Formatting Helpers & Keyboard Actions ---

  private fun applyFormatting(value: TextFieldValue) {
    updateEditorTextFieldValue(value)
  }

  fun insertMarkdownSyntax(prefix: String, suffix: String = "", placeholder: String = "") {
    applyFormatting(MarkdownEditorActions.wrap(_editorTextFieldValue.value, prefix, suffix, placeholder))
  }

  fun formatBold() = insertMarkdownSyntax("**", "**", "bold text")

  fun formatItalic() = insertMarkdownSyntax("*", "*", "italic text")

  fun formatUnderline() = insertMarkdownSyntax("<u>", "</u>", "underlined text")

  fun formatBulletList() {
    applyFormatting(MarkdownEditorActions.prefixLines(_editorTextFieldValue.value, { "- " }, "bullet item"))
  }

  fun formatNumberedList() {
    applyFormatting(MarkdownEditorActions.prefixLines(_editorTextFieldValue.value, { index -> (index + 1).toString() + ". " }, "numbered item"))
  }

  fun insertHeading(level: Int) {
    val prefix = "#".repeat(level.coerceIn(1, 6)) + " "
    applyFormatting(MarkdownEditorActions.prefixLines(_editorTextFieldValue.value, { prefix }, "Heading $level"))
  }

  fun insertCheckboxTask() {
    applyFormatting(MarkdownEditorActions.prefixLines(_editorTextFieldValue.value, { "- [ ] " }, "New task item"))
  }

  fun formatQuote() {
    applyFormatting(MarkdownEditorActions.prefixLines(_editorTextFieldValue.value, { "> " }, "Quote"))
  }

  fun insertMarkdownBlock(block: String) {
    applyFormatting(MarkdownEditorActions.insertBlock(_editorTextFieldValue.value, block))
  }

  fun insertTableTemplate() {
    insertMarkdownBlock(
      """
| Column 1 | Column 2 | Column 3 |
| :--- | :--- | :--- |
| Item 1 | Item 2 | Item 3 |
""".trimIndent()
    )
  }

  // --- In-Editor Find & Replace ---

  fun openEditorSearch() {
    _editorSearch.value = _editorSearch.value.copy(isOpen = true)
  }

  fun closeEditorSearch() {
    _editorSearch.value = EditorSearchState(isOpen = false)
  }

  fun setEditorSearchQuery(query: String) {
    _editorSearch.value = _editorSearch.value.copy(query = query)
    recomputeSearchMatches(query, _editorSearch.value.isCaseSensitive, _editorState.value.content)
  }

  fun setEditorReplaceQuery(replaceText: String) {
    _editorSearch.value = _editorSearch.value.copy(replaceText = replaceText)
  }

  fun toggleCaseSensitive() {
    val newCase = !_editorSearch.value.isCaseSensitive
    _editorSearch.value = _editorSearch.value.copy(isCaseSensitive = newCase)
    recomputeSearchMatches(_editorSearch.value.query, newCase, _editorState.value.content)
  }

  private fun recomputeSearchMatches(query: String, caseSensitive: Boolean, content: String) {
    if (query.isEmpty()) {
      _editorSearch.value = _editorSearch.value.copy(matchIndices = emptyList(), currentMatchIndex = -1)
      return
    }

    val matches = mutableListOf<Int>()
    var startIndex = 0
    while (startIndex < content.length) {
      val found = content.indexOf(query, startIndex, ignoreCase = !caseSensitive)
      if (found == -1) break
      matches.add(found)
      startIndex = found + query.length
    }

    val newIndex = if (matches.isNotEmpty()) 0 else -1
    _editorSearch.value = _editorSearch.value.copy(
      matchIndices = matches,
      currentMatchIndex = newIndex
    )
  }

  fun findNextMatch() {
    val state = _editorSearch.value
    if (state.matchIndices.isEmpty()) return
    val nextIndex = (state.currentMatchIndex + 1) % state.matchIndices.size
    _editorSearch.value = state.copy(currentMatchIndex = nextIndex)
  }

  fun findPreviousMatch() {
    val state = _editorSearch.value
    if (state.matchIndices.isEmpty()) return
    val prevIndex = if (state.currentMatchIndex <= 0) state.matchIndices.size - 1 else state.currentMatchIndex - 1
    _editorSearch.value = state.copy(currentMatchIndex = prevIndex)
  }

  fun replaceCurrentMatch() {
    val search = _editorSearch.value
    if (search.matchIndices.isEmpty() || search.currentMatchIndex == -1) return
    val matchPos = search.matchIndices[search.currentMatchIndex]
    val content = _editorState.value.content
    val qLen = search.query.length

    val newContent = content.substring(0, matchPos) + search.replaceText + content.substring(matchPos + qLen)
    updateContent(newContent)
  }

  fun replaceAllMatches() {
    val search = _editorSearch.value
    if (search.query.isEmpty()) return
    val content = _editorState.value.content
    val newContent = if (search.isCaseSensitive) {
      content.replace(search.query, search.replaceText)
    } else {
      content.replace(Regex(Regex.escape(search.query), RegexOption.IGNORE_CASE), search.replaceText)
    }
    updateContent(newContent)
    _userMessage.value = "Replaced ${search.matchIndices.size} occurrences"
  }

  // --- Document Import ---

  fun importDocumentFromUri(context: Context, uri: Uri, onImported: (() -> Unit)? = null) {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(uri) ?: ""
        var fileName = "Imported Document"

        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
          val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
          if (nameIndex != -1 && cursor.moveToFirst()) {
            fileName = cursor.getString(nameIndex) ?: fileName
          }
        }

        val cleanTitle = fileName.substringBeforeLast(".")
        val extension = fileName.substringAfterLast(".", "").lowercase()

        val extractedText = if (extension == "pdf" || mimeType == "application/pdf") {
          contentResolver.openInputStream(uri)?.use { stream ->
            PdfTextExtractor.extractText(stream)
          } ?: "[Could not read PDF file]"
        } else {
          contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
            reader.readText()
          } ?: ""
        }

        withContext(Dispatchers.Main) {
          val hint = when (extension) {
            "pdf" -> "PDF"
            "md", "markdown" -> "MD"
            else -> "TXT"
          }
          createNewNote(
            initialTitle = cleanTitle,
            initialContent = extractedText,
            formatHint = hint
          )
          _userMessage.value = "Imported: $fileName"
          onImported?.invoke()
        }
      } catch (e: Exception) {
        withContext(Dispatchers.Main) {
          _userMessage.value = "Import failed: ${e.localizedMessage ?: "Unknown error"}"
        }
      }
    }
  }

  fun pasteClipboardText(text: String) {
    if (text.isBlank()) return
    val firstLine = text.lines().firstOrNull { it.isNotBlank() }?.trim() ?: "Pasted Note"
    val title = firstLine.removePrefix("#").trim().take(30)
    createNewNote(
      initialTitle = title,
      initialContent = text,
      formatHint = "MD"
    )
    _userMessage.value = "Pasted note from clipboard"
  }

  // --- Document Export ---

  fun exportDocumentToUri(context: Context, uri: Uri, format: DocumentExporter.ExportFormat) {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
          val title = _editorState.value.title.ifBlank { "Untitled" }
          val content = _editorState.value.content

          when (format) {
            DocumentExporter.ExportFormat.PDF -> DocumentExporter.exportToPdf(title, content, outputStream)
            DocumentExporter.ExportFormat.HTML -> DocumentExporter.exportToHtml(title, content, outputStream)
            DocumentExporter.ExportFormat.MARKDOWN, DocumentExporter.ExportFormat.PLAIN_TEXT -> {
              DocumentExporter.exportToText(content, outputStream)
            }
          }
        }
        withContext(Dispatchers.Main) {
          _userMessage.value = "Exported as ${format.extension.uppercase()} successfully!"
        }
      } catch (e: Exception) {
        withContext(Dispatchers.Main) {
          _userMessage.value = "Export failed: ${e.localizedMessage ?: "Unknown error"}"
        }
      }
    }
  }

  fun shareDocument(context: Context, format: DocumentExporter.ExportFormat) {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val title = _editorState.value.title.ifBlank { "NocturneNote" }
        val content = _editorState.value.content
        val shareIntent = DocumentExporter.createShareIntent(context, title, content, format)
        val chooser = Intent.createChooser(shareIntent, "Share note via...")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        withContext(Dispatchers.Main) {
          context.startActivity(chooser)
        }
      } catch (e: Exception) {
        withContext(Dispatchers.Main) {
          _userMessage.value = "Share failed: ${e.localizedMessage ?: "Unknown error"}"
        }
      }
    }
  }
  override fun onCleared() {
    readAloudController.close()
    super.onCleared()
  }

}
