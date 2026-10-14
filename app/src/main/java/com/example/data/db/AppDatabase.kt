package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.NoteDao
import com.example.data.dao.NoteVersionDao
import com.example.data.model.NoteEntity
import com.example.data.model.NoteVersionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [NoteEntity::class, NoteVersionEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
  abstract fun noteDao(): NoteDao
  abstract fun noteVersionDao(): NoteVersionDao

  companion object {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
      return INSTANCE ?: synchronized(this) {
        val instance = Room.databaseBuilder(
          context.applicationContext,
          AppDatabase::class.java,
          "nocturne_notes.db"
        )
          .fallbackToDestructiveMigration()
          .addCallback(DatabaseCallback(scope))
          .build()
        INSTANCE = instance
        instance
      }
    }

    private class DatabaseCallback(
      private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
      override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        INSTANCE?.let { database ->
          scope.launch(Dispatchers.IO) {
            populateInitialNotes(database.noteDao())
          }
        }
      }

      private suspend fun populateInitialNotes(noteDao: NoteDao) {
        val welcomeContent = """
# Welcome to NocturneNote 🌙

NocturneNote is a distraction-free text and Markdown editor crafted for clarity, focus, and rapid thought capture.

### Key Capabilities

* **Clean Zen Writing**: Tap the Zen button or press `Ctrl + D` to enter distraction-free mode.
* **Full Screen Preview**: Switch between Editor, Live Preview, or Split view with `Ctrl + P`.
* **Multi-Format Export**: Export your polished writing directly to **PDF**, **HTML**, Markdown, or Plain Text.
* **Smart Import**: Paste text or import `.md`, `.txt`, `.pdf`, code files, and notes from any file manager.
* **OLED & Slate Themes**: Toggle between Nocturne OLED, Midnight Slate, or Solar Paper in Settings.
* **Fast In-Editor Search**: Find and replace words instantly using `Ctrl + F`.

> "Simplicity is the prerequisite for reliability and profound focus."

Enjoy writing in complete peace.
""".trimIndent()

        val shortcutsContent = """
# Markdown & Shortcuts Cheat Sheet ⚡

Use these fast syntax patterns or the bottom formatting accessory bar to format your writing:

### Quick Markdown Syntax

* **Bold**: `**text**` or `Ctrl + B`
* *Italic*: `*text*` or `Ctrl + I`
* ~~Strikethrough~~: `~~text~~`
* `Inline Code`: `` `code` ``
* Blockquote: `> Your quote here`

### Checklists & Tasks

- [x] Create a new note in NocturneNote
- [x] Explore Dark Mode and OLED themes
- [ ] Try exporting note as a styled PDF
- [ ] Test Distraction-Free Zen mode (`Ctrl + D`)

### Code Block

```kotlin
fun writeWithFocus() {
    val editor = NocturneNote()
    editor.enableZenMode()
}
```

### Table Example

| Feature | Shortcut | Status |
| :--- | :--- | :--- |
| Quick Save | Ctrl + S | Active |
| Zen Mode | Ctrl + D | Active |
| Preview | Ctrl + P | Active |
| Find / Replace | Ctrl + F | Active |
""".trimIndent()

        val guideContent = """
# The Art of Distraction-Free Writing ✍️

When writing drafts, the biggest enemy is visual clutter. Menus, notifications, and floating widgets pull attention away from your trains of thought.

### Three Rules for Focused Output

1. **Write First, Polish Later**: Let the words flow without stopping to refine every single sentence.
2. **Monitor Velocity, Not Perfection**: Use the live word and character metrics at the bottom to gauge progress.
3. **Embrace High-Contrast Night Themes**: Nocturne OLED reduces eye fatigue in dimly lit workspaces and late-night creative sessions.

Happy writing!
""".trimIndent()

        val now = System.currentTimeMillis()
        val welcomeWords = welcomeContent.trim().split("\\s+".toRegex()).size
        val shortcutsWords = shortcutsContent.trim().split("\\s+".toRegex()).size
        val guideWords = guideContent.trim().split("\\s+".toRegex()).size

        noteDao.insertNote(
          NoteEntity(
            title = "Welcome to NocturneNote",
            content = welcomeContent,
            createdAt = now - 60000,
            updatedAt = now - 60000,
            isPinned = true,
            tags = "guide,welcome",
            formatHint = "MD",
            wordCount = welcomeWords,
            characterCount = welcomeContent.length
          )
        )

        noteDao.insertNote(
          NoteEntity(
            title = "Markdown & Shortcuts Cheat Sheet",
            content = shortcutsContent,
            createdAt = now - 30000,
            updatedAt = now - 30000,
            isPinned = true,
            tags = "shortcuts,markdown",
            formatHint = "MD",
            wordCount = shortcutsWords,
            characterCount = shortcutsContent.length
          )
        )

        noteDao.insertNote(
          NoteEntity(
            title = "The Art of Distraction-Free Writing",
            content = guideContent,
            createdAt = now,
            updatedAt = now,
            isPinned = false,
            tags = "productivity,notes",
            formatHint = "MD",
            wordCount = guideWords,
            characterCount = guideContent.length
          )
        )
      }
    }
  }
}
