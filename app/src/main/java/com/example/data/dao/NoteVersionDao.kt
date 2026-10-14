package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.NoteVersionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteVersionDao {
  @Query("SELECT * FROM note_versions WHERE noteId = :noteId ORDER BY timestamp DESC")
  fun getVersionsForNote(noteId: Long): Flow<List<NoteVersionEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertVersion(version: NoteVersionEntity): Long

  @Query("DELETE FROM note_versions WHERE id = :id")
  suspend fun deleteVersionById(id: Long)

  @Query("DELETE FROM note_versions WHERE noteId = :noteId")
  suspend fun deleteAllVersionsForNote(noteId: Long)

  @Query("SELECT COUNT(*) FROM note_versions WHERE noteId = :noteId")
  suspend fun getVersionCountForNote(noteId: Long): Int

  @Query("DELETE FROM note_versions WHERE noteId = :noteId AND id NOT IN (SELECT id FROM note_versions WHERE noteId = :noteId ORDER BY timestamp DESC LIMIT :keepCount)")
  suspend fun trimOldVersions(noteId: Long, keepCount: Int = 30)
}
