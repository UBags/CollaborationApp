package com.costheta.cortexa.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.costheta.cortexa.data.models.SyncedGoogleTask
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncedGoogleTaskDao {

    /**
     * Inserts or updates a list of Google Tasks. If a task with the same
     * googleTaskId already exists, it will be replaced.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tasks: List<SyncedGoogleTask>)

    /**
     * Retrieves all synced tasks from the database.
     */
    @Query("SELECT * FROM synced_google_tasks")
    suspend fun getAll(): List<SyncedGoogleTask>

    /**
     * NEW: Retrieves a single SyncedGoogleTask by its local database ID.
     * @param localId The primary key of the event in the local database.
     * @return The SyncedGoogleTask object if found, otherwise null.
     */
    @Query("SELECT * FROM synced_google_tasks WHERE localId = :localId")
    suspend fun getById(localId: Long): SyncedGoogleTask?

    /**
     * NEW: Retrieves all displayable tasks as a Flow.
     * Displayable means not completed OR completed within the last hour.
     */
    @Query("""
        SELECT * FROM synced_google_tasks
        WHERE isCompleted = 0 OR (isCompleted = 1 AND (lastSyncedTimestamp + 3600000) >= :currentTimeMillis)
        ORDER BY dueDateTimeMillis ASC
    """)
    fun getAllDisplayableTasks(currentTimeMillis: Long): Flow<List<SyncedGoogleTask>>

    /**
     * Deletes a list of tasks from the database based on their Google Task IDs.
     * This is used to remove tasks that have been deleted or completed on the server.
     *
     * @param googleTaskIds A list of the Google Task IDs to delete.
     */
    @Query("DELETE FROM synced_google_tasks WHERE googleTaskId IN (:googleTaskIds)")
    suspend fun deleteTasksByIds(googleTaskIds: List<String>)

    /**
     * Clears the entire synced_google_tasks table.
     */
    @Query("DELETE FROM synced_google_tasks")
    suspend fun clearAll()
}
