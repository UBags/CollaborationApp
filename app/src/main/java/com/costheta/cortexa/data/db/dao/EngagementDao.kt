package com.costheta.cortexa.data.db.dao

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.costheta.cortexa.data.models.Engagement
import com.costheta.cortexa.data.models.EngagementException
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Data Access Object (DAO) for interacting with the Engagement entity.
 * Defines methods for inserting, querying, updating, and deleting ad-hoc engagement data.
 */
@Dao
interface EngagementDao {

    /**
     * Inserts a new Engagement entry into the database.
     * @param engagement The Engagement object to insert.
     * @return The row ID of the newly inserted engagement.
     */
    @Insert
    suspend fun insertEngagement(engagement: Engagement): Long

    /**
     * Updates an existing Engagement entry in the database.
     * @param engagement The Engagement object to update.
     * @return The number of rows updated (should be 1 if successful).
     */
    @Update
    suspend fun updateEngagement(engagement: Engagement): Int

    /**
     * Deletes an Engagement entry from the database by its ID.
     * @param engagementId The ID of the Engagement to delete.
     * @return The number of rows deleted.
     */
    @Query("DELETE FROM engagements WHERE engagementId = :engagementId")
    suspend fun deleteEngagement(engagementId: Long): Int

    /**
     * Retrieves a specific Engagement entry by its ID.
     * @param engagementId The ID of the Engagement to retrieve.
     * @return The Engagement object if found, null otherwise.
     */
    @Query("SELECT * FROM engagements WHERE engagementId = :engagementId")
    suspend fun getEngagementById(engagementId: Long): Engagement?

    /**
     * Retrieves all active and validated Engagement entries, ordered by validity start date and then by start time.
     * @return A Flow emitting a list of active and validated Engagement objects.
     */
    @Query("SELECT * FROM engagements WHERE isActive = 1 AND validatedItem = 1 ORDER BY validityStartDate ASC, startTime ASC")
    fun getAllActiveAndValidatedEngagements(): Flow<List<Engagement>>

    /**
     * Retrieves all Engagement entries, including unvalidated ones, ordered by validity start date and then by start time.
     * This is typically used for displaying all engagements, including inactive ones.
     * @return A list of all Engagement objects.
     */
    @Query("SELECT * FROM engagements ORDER BY validityStartDate ASC, startTime ASC")
    suspend fun getAllEngagements(): List<Engagement>

    /**
     * Retrieves Engagement entries that are scheduled for a specific date,
     * considering their validity period, day selection type, and recurrence.
     *
     * @param date The LocalDate for which to retrieve engagements.
     * @param dayOfWeekString The string representation of the DayOfWeek (e.g., "MONDAY").
     * @return A list of Engagement objects for the specified date.
     */
    @Query("""
        SELECT * FROM engagements
        WHERE validityStartDate <= :date AND validityEndDate >= :date AND
            (
                (daySelectionType = 'SPECIFIC_DAYS' AND selectedDaysOfWeek LIKE '%' || :dayOfWeekString || '%') OR
                (daySelectionType = 'RECURRENCE') OR
                (daySelectionType = 'DAILY')
            )
    """)
    suspend fun getEngagementsForSpecificDate(date: LocalDate, dayOfWeekString: String): List<Engagement>

    /**
     * Retrieves all Engagement entries that have expired (validityEndDate is in the past).
     * This query is used by the CleanupWorker to identify engagements for deletion.
     *
     * @param currentDateTimeString The current date and time as a string (YYYY-MM-DDTHH:MM:SS).
     * @return A list of expired Engagement objects.
     */
    @Query("""
        SELECT * FROM engagements
        WHERE validityEndDate < :currentDateTimeString
    """)
    suspend fun getExpiredEngagements(currentDateTimeString: LocalDate): List<Engagement>

    /**
     * NEW: Deletes all Engagement items that have not been validated (i.e., temporary/incomplete entries).
     * This is useful for cleaning up after unexpected app closures or user discards.
     * @return The number of rows deleted.
     */
    @Query("DELETE FROM engagements WHERE validatedItem = 0")
    suspend fun deleteUnvalidatedEngagements(): Int

    /**
     * NEW: Updates the validatedItem status for a specific Engagement.
     * Called when the Engagement is successfully saved.
     */
    @Query("UPDATE engagements SET validatedItem = :validatedStatus WHERE engagementId = :engagementId")
    suspend fun updateEngagementValidationStatus(engagementId: Long, validatedStatus: Boolean): Int

    /**
     * NEW: Retrieves displayable Engagement entries from the database.
     * Displayable means validated, and (current time < (endDateTimeInMillis + 1 hour)).
     * Ordered by endDateTimeInMillis in ascending order.
     * @param currentTimeMillis The current time in milliseconds.
     * @return A Flow that emits a list of displayable Engagement objects.
     */
    @Query("SELECT * FROM engagements WHERE validatedItem = 1 AND (:currentTimeMillis < (endDateTimeInMillis + 3600 * 1000)) ORDER BY endDateTimeInMillis ASC")
    fun getDisplayableEngagements(currentTimeMillis: Long): Flow<List<Engagement>>

    /**
     * Inserts or replaces an engagement exception. If an exception for the same engagement on the same
     * date already exists, it will be updated.
     * @param exception The EngagementException to insert or update.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEngagementException(exception: EngagementException)

    /**
     * Retrieves all exceptions within a given date range.
     * This is useful for fetching all relevant exceptions for the visible schedule at once.
     * @param startDate The start of the date range.
     * @param endDate The end of the date range.
     * @return A list of EngagementException objects within that range.
     */
    @Query("SELECT * FROM engagement_exceptions WHERE originalDate BETWEEN :startDate AND :endDate")
    suspend fun getEngagementExceptionsForDateRange(startDate: LocalDate, endDate: LocalDate): List<EngagementException>

    /**
     * Retrieves a specific exception for a given engagement on a given date.
     * @param engagementId The ID of the parent Engagement.
     * @param date The original date of the engagement instance.
     * @return An EngagementException object if one exists, otherwise null.
     */
    @Query("SELECT * FROM engagement_exceptions WHERE originalEngagementId = :engagementId AND originalDate = :date")
    suspend fun getExceptionForEngagementOnDate(engagementId: Long, date: LocalDate): EngagementException?

}
