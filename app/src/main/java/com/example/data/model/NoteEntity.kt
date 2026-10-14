package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val title: String = "",
  val content: String = "",
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis(),
  val isPinned: Boolean = false,
  val tags: String = "",
  val formatHint: String = "MD",
  val wordCount: Int = 0,
  val characterCount: Int = 0
) {
  val readingTimeMinutes: Int
    get() = (wordCount / 200).coerceAtLeast(1)

  fun computeCounts(): Pair<Int, Int> {
    val words = if (content.isBlank()) 0 else content.trim().split("\\s+".toRegex()).size
    val chars = content.length
    return Pair(words, chars)
  }
}
