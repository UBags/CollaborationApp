package com.costheta.cortexa.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.costheta.cortexa.data.models.SyncedCalendarEvent
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for SyncedCalendarEvent entities.
 */
@Dao
interface SyncedCalendarEventDao {

    /**
     * Inserts or updates a list of calendar events.
     * An existing event is replaced if its googleEventId matches.
     */
    @Upsert
    suspend fun upsertAll(events: List<SyncedCalendarEvent>)

    /**
     * Retrieves all synced calendar events that occur after a given time.
     * @param startTimeMillis The start time in UTC milliseconds to query events after.
     * @return A Flow emitting a list of future calendar events.
     */
    @Query("SELECT * FROM synced_calendar_events WHERE startDateTimeMillis >= :startTimeMillis ORDER BY startDateTimeMillis ASC")
    fun getAllFutureEvents(startTimeMillis: Long): Flow<List<SyncedCalendarEvent>>

    /**
     * Deletes events from the local database whose googleEventId is not in the provided list.
     * This is used to remove events that were deleted from the user's Google Calendar.
     * @param eventIdsToKeep A list of googleEventId longs that should be kept in the database.
     */
    @Query("DELETE FROM synced_calendar_events WHERE googleEventId NOT IN (:eventIdsToKeep)")
    suspend fun deleteStaleEvents(eventIdsToKeep: List<Long>)

    /**
     * Retrieves a single SyncedCalendarEvent by its local database ID.
     * @param localId The primary key of the event in the local database.
     * @return The SyncedCalendarEvent object if found, otherwise null.
     */
    @Query("SELECT * FROM synced_calendar_events WHERE localId = :localId")
    suspend fun getById(localId: Long): SyncedCalendarEvent?

    /**
     * Clears the entire table.
     */
    @Query("DELETE FROM synced_calendar_events")
    suspend fun clearAll()

}
