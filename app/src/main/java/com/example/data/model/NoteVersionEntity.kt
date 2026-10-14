package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "note_versions",
  foreignKeys = [
    ForeignKey(
      entity = NoteEntity::class,
      parentColumns = ["id"],
      childColumns = ["noteId"],
      onDelete = ForeignKey.CASCADE
    )
  ],
  indices = [Index(value = ["noteId"])]
)
data class NoteVersionEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val noteId: Long,
  val title: String,
  val content: String,
  val timestamp: Long = System.currentTimeMillis(),
  val wordCount: Int = 0,
  val charCount: Int = 0,
  val reason: String = "Saved State"
)
