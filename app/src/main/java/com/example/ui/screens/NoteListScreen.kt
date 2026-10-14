package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFolderUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.NoteEntity
import com.example.ui.components.ImportDialog
import com.example.ui.viewmodel.FilterCategory
import com.example.ui.viewmodel.NoteSortOrder
import com.example.ui.viewmodel.NoteViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteListScreen(
  viewModel: NoteViewModel,
  onOpenNote: (NoteEntity) -> Unit,
  onNewNote: () -> Unit,
  onOpenSettings: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val filteredNotes by viewModel.filteredNotes.collectAsState()
  val searchQuery by viewModel.searchQuery.collectAsState()
  val activeFilter by viewModel.activeFilter.collectAsState()
  val sortOrder by viewModel.sortOrder.collectAsState()

  var showImportDialog by remember { mutableStateOf(false) }
  var showSortMenu by remember { mutableStateOf(false) }

  // File Picker for importing .md, .txt, .pdf, and other text documents
  val openDocumentLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri: Uri? ->
    uri?.let {
      viewModel.importDocumentFromUri(context, it)
      onNewNote()
    }
  }

  Scaffold(
    modifier = modifier
      .fillMaxSize()
      .testTag("note_list_screen"),
    topBar = {
      TopAppBar(
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = "🌙",
                fontSize = 17.sp
              )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = "NocturneNote",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
              )
              Text(
                text = "Focus & Simplicity",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
              )
            }
          }
        },
        actions = {
          // Updated Import Button with DriveFolderUpload icon
          IconButton(
            onClick = { showImportDialog = true },
            modifier = Modifier.testTag("topbar_import_button")
          ) {
            Icon(
              imageVector = Icons.Default.DriveFolderUpload,
              contentDescription = "Import Document",
              tint = MaterialTheme.colorScheme.primary
            )
          }

          // Settings Button (opens full Settings page)
          IconButton(
            onClick = onOpenSettings,
            modifier = Modifier.testTag("topbar_settings_button")
          ) {
            Icon(
              imageVector = Icons.Default.Settings,
              contentDescription = "Settings",
              tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface
        )
      )
    },
    floatingActionButton = {
      FloatingActionButton(
        onClick = onNewNote,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.testTag("create_note_fab")
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 16.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(Icons.Default.Add, contentDescription = "New Note")
          Spacer(modifier = Modifier.width(8.dp))
          Text("Write", fontWeight = FontWeight.SemiBold)
        }
      }
    }
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {
      // Search Bar
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { viewModel.searchQuery.value = it },
          modifier = Modifier
            .weight(1f)
            .testTag("global_search_input"),
          placeholder = { Text("Search notes, tags, content...") },
          leadingIcon = {
            Icon(
              Icons.Default.Search,
              contentDescription = "Search",
              tint = MaterialTheme.colorScheme.primary
            )
          },
          trailingIcon = {
            if (searchQuery.isNotEmpty()) {
              IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                Icon(Icons.Default.Clear, contentDescription = "Clear search")
              }
            }
          },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
          )
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Sort Button
        Box {
          IconButton(
            onClick = { showSortMenu = true },
            modifier = Modifier
              .size(48.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
              .testTag("sort_button")
          ) {
            Icon(
              Icons.Default.Sort,
              contentDescription = "Sort Notes",
              tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }

          DropdownMenu(
            expanded = showSortMenu,
            onDismissRequest = { showSortMenu = false }
          ) {
            NoteSortOrder.values().forEach { option ->
              DropdownMenuItem(
                text = {
                  Text(
                    text = option.displayName,
                    fontWeight = if (sortOrder == option) FontWeight.Bold else FontWeight.Normal,
                    color = if (sortOrder == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                  )
                },
                onClick = {
                  viewModel.sortOrder.value = option
                  showSortMenu = false
                }
              )
            }
          }
        }
      }

      // Filter Chips Row
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState())
          .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        FilterCategory.values().forEach { category ->
          val isSelected = activeFilter == category
          FilterChip(
            selected = isSelected,
            onClick = { viewModel.activeFilter.value = category },
            label = { Text(category.displayName, fontSize = 12.sp) },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
              selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            modifier = Modifier.testTag("filter_chip_${category.name.lowercase()}")
          )
        }
      }

      // Notes List / Empty State
      if (filteredNotes.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
          contentAlignment = Alignment.Center
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Box(
              modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
              )
            }
            Text(
              text = if (searchQuery.isNotBlank()) "No matching notes found" else "No notes in this view",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = if (searchQuery.isNotBlank()) "Try another keyword or clear search filters" else "Tap Write below or Import a file to get started",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          items(filteredNotes, key = { it.id }) { note ->
            NoteCard(
              note = note,
              searchHighlight = searchQuery,
              onClick = { onOpenNote(note) },
              onTogglePin = { viewModel.togglePinNote(note.id, note.isPinned) },
              onDelete = { viewModel.deleteNote(note) }
            )
          }
          item {
            Spacer(modifier = Modifier.height(72.dp))
          }
        }
      }
    }
  }

  // Import Dialog
  if (showImportDialog) {
    ImportDialog(
      onOpenFilePicker = {
        openDocumentLauncher.launch(
          arrayOf(
            "text/*",
            "application/pdf",
            "application/json",
            "application/xml",
            "*/*"
          )
        )
      },
      onPasteClipboard = { text ->
        viewModel.pasteClipboardText(text)
        onNewNote()
      },
      onDismiss = { showImportDialog = false }
    )
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(
  note: NoteEntity,
  searchHighlight: String,
  onClick: () -> Unit,
  onTogglePin: () -> Unit,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier
) {
  var showMenu by remember { mutableStateOf(false) }
  val dateFormatted = remember(note.updatedAt) {
    SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()).format(Date(note.updatedAt))
  }

  Card(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(14.dp))
      .border(
        1.dp,
        if (note.isPinned) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
        RoundedCornerShape(14.dp)
      )
      .combinedClickable(
        onClick = onClick,
        onLongClick = { showMenu = true }
      )
      .testTag("note_card_${note.id}"),
    colors = CardDefaults.cardColors(
      containerColor = if (note.isPinned) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer
    ),
    shape = RoundedCornerShape(14.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp)
    ) {
      // Header: Title, Pin Indicator & Menu
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          modifier = Modifier.weight(1f),
          verticalAlignment = Alignment.CenterVertically
        ) {
          if (note.isPinned) {
            Icon(
              imageVector = Icons.Default.PushPin,
              contentDescription = "Pinned Note",
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(16.dp).padding(end = 4.dp)
            )
          }
          Text(
            text = note.title.ifBlank { "Untitled Note" },
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
          )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          // Format Badge (MD, TXT, PDF)
          Surface(
            shape = RoundedCornerShape(4.dp),
            color = when (note.formatHint) {
              "PDF" -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
              "MD" -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
              else -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
            },
            modifier = Modifier.padding(horizontal = 6.dp)
          ) {
            Text(
              text = note.formatHint,
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
              color = when (note.formatHint) {
                "PDF" -> MaterialTheme.colorScheme.tertiary
                "MD" -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.secondary
              },
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
          }

          // Options Menu
          Box {
            IconButton(
              onClick = { showMenu = true },
              modifier = Modifier.size(28.dp).testTag("note_menu_button_${note.id}")
            ) {
              Icon(
                Icons.Default.MoreVert,
                contentDescription = "Note actions",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }

            DropdownMenu(
              expanded = showMenu,
              onDismissRequest = { showMenu = false }
            ) {
              DropdownMenuItem(
                text = { Text(if (note.isPinned) "Unpin Note" else "Pin to Top") },
                leadingIcon = {
                  Icon(if (note.isPinned) Icons.Outlined.PushPin else Icons.Default.PushPin, contentDescription = null)
                },
                onClick = {
                  onTogglePin()
                  showMenu = false
                }
              )
              DropdownMenuItem(
                text = { Text("Delete Note", color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                  Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                },
                onClick = {
                  onDelete()
                  showMenu = false
                }
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(6.dp))

      // Content Snippet
      val cleanSnippet = note.content
        .lines()
        .filter { it.isNotBlank() }
        .take(3)
        .joinToString(" ")
        .replace(Regex("[#*`>\\[\\]]"), "")
        .trim()

      if (cleanSnippet.isNotBlank()) {
        Text(
          text = cleanSnippet,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          lineHeight = 18.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
      }

      // Footer: Date & Word count / Reading time
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = dateFormatted,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )

        Text(
          text = "${note.wordCount} words • ${note.readingTimeMinutes} min read",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
        )
      }
    }
  }
}
