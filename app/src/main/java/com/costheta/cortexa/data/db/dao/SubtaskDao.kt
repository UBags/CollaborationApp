package com.costheta.cortexa.data.db.dao

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import com.costheta.cortexa.data.models.Subtask
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for interacting with the Subtask entity.
 * Defines methods for inserting, querying, updating, and deleting subtasks.
 */
@Dao
interface SubtaskDao {

    /**
     * Inserts a new Subtask into the database.
     * If a subtask with the same primary key exists, it will be replaced.
     * @param subtask The Subtask object to insert.
     * @return The row ID of the newly inserted subtask.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubtask(subtask: Subtask): Long

    /**
     * Inserts a list of Subtasks into the database.
     * If a subtask with the same primary key exists, it will be replaced.
     * @param subtasks The list of Subtask objects to insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllSubtasks(subtasks: List<Subtask>)

    /**
     * Updates an existing Subtask in the database.
     * @param subtask The Subtask object to update.
     * @return The number of rows updated (should be 1 if successful).
     */
    @Update
    suspend fun updateSubtask(subtask: Subtask): Int

    /**
     * Updates a list of existing Subtasks in the database.
     * @param subtasks The list of Subtask objects to update.
     * @return The number of rows updated.
     */
    @Update
    suspend fun updateAllSubtasks(subtasks: List<Subtask>): Int

    /**
     * Deletes a Subtask from the database by its ID.
     * @param subtaskId The ID of the Subtask to delete.
     * @return The number of rows deleted (should be 1 if successful).
     */
    @Query("DELETE FROM subtasks WHERE subtaskId = :subtaskId")
    suspend fun deleteSubtask(subtaskId: Long): Int

    /**
     * Deletes a specific Subtask object from the database.
     * @param subtask The Subtask object to delete.
     */
    @Delete
    suspend fun deleteSubtask(subtask: Subtask)

    /**
     * Retrieves all Subtasks for a specific event type and event ID.
     * The list is ordered by completion status (incomplete first) and then by position.
     * @param eventType The type of the parent event (e.g., "Assignment", "ToDo").
     * @param eventRefId The ID of the parent event.
     * @return A Flow that emits a list of Subtask objects.
     */
    @Query("SELECT * FROM subtasks WHERE eventType = :eventType AND eventRefId = :eventRefId ORDER BY completionState ASC, position ASC")
    fun getSubtasksForEvent(eventType: String, eventRefId: Long): Flow<List<Subtask>>

    /**
     * Retrieves a single Subtask by its unique ID.
     * @param subtaskId The ID of the Subtask to retrieve.
     * @return The Subtask object, or null if not found.
     */
    @Query("SELECT * FROM subtasks WHERE subtaskId = :subtaskId")
    suspend fun getSubtaskById(subtaskId: Long): Subtask?

    /**
     * Retrieves the maximum position number for subtasks of a given event.
     * Used to determine the next available position for a new subtask.
     * @param eventType The type of the parent event.
     * @param eventRefId The ID of the parent event.
     * @return The maximum position, or 0 if no subtasks exist for the event.
     */
    @Query("SELECT MAX(position) FROM subtasks WHERE eventType = :eventType AND eventRefId = :eventRefId")
    suspend fun getMaxPositionForEvent(eventType: String, eventRefId: Long): Int?

    /**
     * Deletes all unvalidated Subtasks for a specific event.
     * This is used when a new event is discarded or an edit is not saved.
     * @param eventType The type of the parent event.
     * @param eventRefId The ID of the parent event.
     * @return The number of rows deleted.
     */
    @Query("DELETE FROM subtasks WHERE eventType = :eventType AND eventRefId = :eventRefId AND validatedItem = 0")
    suspend fun deleteUnvalidatedSubtasksForEvent(eventType: String, eventRefId: Long): Int

    /**
     * Updates the validatedItem status for all subtasks of a specific event.
     * Called when the parent event is successfully saved.
     * @param eventType The type of the parent event.
     * @param eventRefId The ID of the parent event.
     * @param validatedStatus The new validated status (true/false).
     * @return The number of rows updated.
     */
    @Query("UPDATE subtasks SET validatedItem = :validatedStatus WHERE eventType = :eventType AND eventRefId = :eventRefId")
    suspend fun updateSubtasksValidationStatusForEvent(eventType: String, eventRefId: Long, validatedStatus: Boolean): Int

    /**
     * Deletes all Subtasks that have not been validated (i.e., temporary/incomplete entries).
     * This is useful for cleaning up after unexpected app closures or user discards.
     * Used by CleanupWorker.
     * @return The number of rows deleted.
     */
    @Query("DELETE FROM subtasks WHERE validatedItem = 0")
    suspend fun deleteUnvalidatedSubtasks(): Int

    /**
     * Deletes all Subtasks associated with a specific event.
     * This is called when the parent event itself is deleted.
     * @param eventType The type of the parent event.
     * @param eventRefId The ID of the parent event.
     * @return The number of rows deleted.
     */
    @Query("DELETE FROM subtasks WHERE eventType = :eventType AND eventRefId = :eventRefId")
    suspend fun deleteAllSubtasksForEvent(eventType: String, eventRefId: Long): Int

    /**
     * Retrieves all unique event types from the subtasks table.
     * Used for the "Copy Subtasks" dialog to list available event types.
     */
    @Query("SELECT DISTINCT eventType FROM subtasks WHERE validatedItem = 1")
    suspend fun getUniqueEventTypes(): List<String>

    /**
     * Retrieves all validated events (eventRefId and description) for a given event type.
     * Used for the "Copy Subtasks" dialog to list specific events to copy from.
     */
    @Query("SELECT eventRefId, description FROM subtasks WHERE eventType = :eventType AND validatedItem = 1 GROUP BY eventRefId, description")
    suspend fun getValidatedSubtasksForEventType(eventType: String): List<SubtaskIdentifier>

    // Data class to hold just the eventRefId and description for event selection
    data class SubtaskIdentifier(
        val eventRefId: Long,
        val description: String // Re-using description for event name for simplicity in selection
    )
}
