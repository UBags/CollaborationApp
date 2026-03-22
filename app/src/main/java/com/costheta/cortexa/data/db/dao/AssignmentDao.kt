package com.costheta.cortexa.data.db.dao

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.costheta.cortexa.data.models.Assignment
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Data Access Object (DAO) for interacting with the Assignment entity.
 * Defines methods for inserting, querying, updating, and deleting Assignment items.
 */
@Dao
interface AssignmentDao {

    /**
     * Inserts a new Assignment entry into the database.
     * @param assignment The Assignment object to insert.
     * @return The row ID of the newly inserted assignment.
     */
    @Insert
    suspend fun insertAssignment(assignment: Assignment): Long

    /**
     * Updates an existing Assignment entry in the database.
     * @param assignment The Assignment object to update.
     */
    @Update
    suspend fun updateAssignment(assignment: Assignment)

    /**
     * Deletes an Assignment entry from the database by its ID.
     * @param assignmentId The ID of the Assignment to delete.
     */
    @Query("DELETE FROM assignments WHERE assignmentId = :assignmentId")
    suspend fun deleteAssignment(assignmentId: Long): Int

    /**
     * Deletes all Assignment entries that have not been validated (i.e., temporary/incomplete entries).
     * This is useful for cleaning up after unexpected app closures or user discards.
     * @return The number of rows deleted.
     */
    @Query("DELETE FROM assignments WHERE validatedItem = 0")
    suspend fun deleteUnvalidatedAssignments(): Int

    /**
     * Retrieves all Assignment entries that are marked as validated.
     * The filtering for completed assignments (display for 3 hours after due) will be handled
     * in the `AssignmentListActivity` after fetching all validated items.
     * Ordered by due date and submission time.
     * @return A Flow emitting a list of all validated Assignment objects.
     */
    @Query("SELECT * FROM assignments WHERE validatedItem = 1 ORDER BY dueDate ASC, submissionTime ASC")
    fun getAllAssignments(): Flow<List<Assignment>>

    /**
     * Retrieves assignments that are either incomplete or complete and within 3 hours past their due time.
     * This query leverages the `dueDateTimeMillis` field for efficient time-based filtering directly in SQL.
     *
     * @return A Flow emitting a list of Assignment objects that meet the display criteria.
     */
    @Query("""
        SELECT * FROM assignments
        WHERE validatedItem = 1
          AND (currentProgress < 100
               OR (currentProgress == 100 AND dueDateTimeMillis >= (CAST(strftime('%s', 'now') AS INTEGER) - (3 * 3600)) * 1000)
              )
        ORDER BY dueDate ASC, submissionTime ASC
    """)
    fun getDisplayableAssignments(): Flow<List<Assignment>>

    /**
     * Retrieves a specific Assignment entry by its ID, regardless of its validation status.
     * This is used internally for loading assignments in `AddAssignmentActivity` for editing.
     * @param assignmentId The ID of the Assignment to retrieve.
     * @return The Assignment object if found, null otherwise.
     */
    @Query("SELECT * FROM assignments WHERE assignmentId = :assignmentId")
    suspend fun getAssignmentById(assignmentId: Long): Assignment?

    /**
     * Retrieves all unique course names from the assignments table.
     * This will be used for autocomplete suggestions in the Assignment List search bar.
     * @return A list of unique course names from assignments.
     */
    @Query("SELECT DISTINCT courseName FROM assignments WHERE validatedItem = 1")
    suspend fun getUniqueAssignmentCourseNames(): List<String>

    /**
     * Retrieves all unique assignment topics from the assignments table.
     * @return A list of unique assignment topics.
     */
    @Query("SELECT DISTINCT assignmentTopic FROM assignments WHERE validatedItem = 1")
    suspend fun getUniqueAssignmentTopics(): List<String>

    /**
     * Retrieves all unique assignment names from the assignments table.
     * @return A list of unique assignment names.
     */
    @Query("SELECT DISTINCT assignmentName FROM assignments WHERE validatedItem = 1")
    suspend fun getUniqueAssignmentNames(): List<String>

    /**
     * Retrieves all Assignment entries that are due on a specific date.
     * Used for displaying assignments in the Schedule View.
     *
     * @param date The LocalDate for which to retrieve assignments.
     * @return A list of Assignment objects for the specified date.
     */
    @Query("SELECT * FROM assignments WHERE dueDate = :date AND validatedItem = 1") // Only show validated assignments
    suspend fun getAssignmentsForSpecificDate(date: LocalDate): List<Assignment>
}
