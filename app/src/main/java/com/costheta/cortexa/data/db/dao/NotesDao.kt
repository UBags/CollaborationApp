package com.costheta.cortexa.data.db.dao

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import com.costheta.cortexa.data.models.NotesItem

/**
 * Data Access Object (DAO) for interacting with the NotesItem entity.
 * Defines methods for inserting, querying, updating, and deleting Note items.
 */
@Dao
interface NotesDao {

    /**
     * Inserts a new NotesItem into the database.
     * @param notesItem The NotesItem object to insert.
     * @return The row ID of the newly inserted Note item.
     */
    @Insert
    suspend fun insertNote(notesItem: NotesItem): Long

    /**
     * Updates an existing NotesItem in the database.
     * @param notesItem The NotesItem object to update.
     * @return The number of rows updated (should be 1 if successful).
     */
    @Update
    suspend fun updateNote(notesItem: NotesItem): Int

    /**
     * Deletes a NotesItem from the database by its ID.
     * @param noteId The ID of the NotesItem to delete.
     * @return The number of rows deleted (should be 1 if successful).\
     */
    @Query("DELETE FROM notes WHERE noteId = :noteId")
    suspend fun deleteNote(noteId: Long): Int

    /**
     * Retrieves all NotesItems from the database that are marked as validated.
     * Ordered by last modified date (descending).
     * @return A Flow that emits a list of validated NotesItem objects.
     */
    @Query("SELECT * FROM notes WHERE validatedItem = 1 ORDER BY lastModified DESC")
    fun getAllNotes(): Flow<List<NotesItem>>

    /**
     * Retrieves a single NotesItem by its ID.
     * @param noteId The ID of the NotesItem to retrieve.
     * @return The NotesItem object, or null if not found.
     */
    @Query("SELECT * FROM notes WHERE noteId = :noteId")
    suspend fun getNoteById(noteId: Long): NotesItem?

    /**
     * NEW: Deletes all NotesItems that have not been validated (i.e., temporary/incomplete entries).
     * This is useful for cleaning up after unexpected app closures or user discards.
     * @return The number of rows deleted.
     */
    @Query("DELETE FROM notes WHERE validatedItem = 0")
    suspend fun deleteUnvalidatedNotes(): Int

    /**
     * NEW: Retrieves all notes that are not archived and are validated.
     * Ordered by lastModified in ascending order.
     * @return A Flow that emits a list of displayable NotesItem objects.
     */
    @Query("""
        SELECT * FROM notes 
        WHERE validatedItem = 1 
        AND (archivedNote = 0 OR (archivedNote = 1 AND (lastModifiedMillis + 600000) >= :currentTimeMillis))
        ORDER BY lastModifiedMillis DESC
    """)
    fun getAllDisplayableNotes(currentTimeMillis: Long): Flow<List<NotesItem>>

    /**
     * NEW: Retrieves all notes that are archived and are validated.
     * Ordered by lastModified in ascending order.
     * @return A Flow that emits a list of archived NotesItem objects.
     */
    @Query("SELECT * FROM notes WHERE archivedNote = 1 AND validatedItem = 1 ORDER BY lastModified ASC")
    fun getAllArchivedNotes(): Flow<List<NotesItem>>
}
