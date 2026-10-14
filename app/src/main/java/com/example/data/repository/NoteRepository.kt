package com.example.data.repository

import com.example.data.dao.NoteDao
import com.example.data.dao.NoteVersionDao
import com.example.data.model.NoteEntity
import com.example.data.model.NoteVersionEntity
import kotlinx.coroutines.flow.Flow

class NoteRepository(
  private val noteDao: NoteDao,
  private val versionDao: NoteVersionDao
) {
  fun getAllNotes(): Flow<List<NoteEntity>> = noteDao.getAllNotes()

  fun getNoteById(id: Long): Flow<NoteEntity?> = noteDao.getNoteById(id)

  fun searchNotes(query: String): Flow<List<NoteEntity>> = noteDao.searchNotes(query)

  suspend fun insertNote(note: NoteEntity): Long = noteDao.insertNote(note)

  suspend fun updateNote(note: NoteEntity) = noteDao.updateNote(note)

  suspend fun deleteNote(note: NoteEntity) = noteDao.deleteNote(note)

  suspend fun deleteNoteById(id: Long) = noteDao.deleteNoteById(id)

  suspend fun togglePin(id: Long, isPinned: Boolean) = noteDao.setPinned(id, isPinned)

  // Version History
  fun getVersionsForNote(noteId: Long): Flow<List<NoteVersionEntity>> = versionDao.getVersionsForNote(noteId)

  suspend fun insertVersion(version: NoteVersionEntity): Long {
    val id = versionDao.insertVersion(version)
    versionDao.trimOldVersions(version.noteId, keepCount = 30)
    return id
  }

  suspend fun deleteVersionById(versionId: Long) = versionDao.deleteVersionById(versionId)
}
