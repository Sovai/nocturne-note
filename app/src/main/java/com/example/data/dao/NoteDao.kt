package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
  @Query("SELECT * FROM notes ORDER BY isPinned DESC, updatedAt DESC")
  fun getAllNotes(): Flow<List<NoteEntity>>

  @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
  fun getNoteById(id: Long): Flow<NoteEntity?>

  @Query("SELECT * FROM notes WHERE title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%' ORDER BY isPinned DESC, updatedAt DESC")
  fun searchNotes(query: String): Flow<List<NoteEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertNote(note: NoteEntity): Long

  @Update
  suspend fun updateNote(note: NoteEntity)

  @Delete
  suspend fun deleteNote(note: NoteEntity)

  @Query("DELETE FROM notes WHERE id = :id")
  suspend fun deleteNoteById(id: Long)

  @Query("UPDATE notes SET isPinned = :isPinned WHERE id = :id")
  suspend fun setPinned(id: Long, isPinned: Boolean)

  @Query("SELECT COUNT(*) FROM notes")
  suspend fun getCount(): Int
}
