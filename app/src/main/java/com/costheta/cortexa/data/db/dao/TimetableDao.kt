package com.costheta.cortexa.data.db.dao

import android.util.Log
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Delete
import androidx.room.Update
import com.costheta.cortexa.data.models.ClassSession
import com.costheta.cortexa.data.models.Timetable
import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek
import java.time.LocalDate
import androidx.room.OnConflictStrategy
import com.costheta.cortexa.data.models.SessionException


/**
 * Data Access Object (DAO) for interacting with Timetable, ClassSession, and Engagement entities.
 * Defines methods for inserting, querying, updating, and deleting data.
 */
@Dao
interface TimetableDao {

    /**
     * Inserts a new Timetable entry into the database.
     * @param timetable The Timetable object to insert.
     * @return The row ID of the newly inserted timetable.
     */
    @Insert (onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimetable(timetable: Timetable): Long

    /**
     * Inserts a list of ClassSession entries into the database.
     * @param classSessions The list of ClassSession objects to insert.
     * @return A list of row IDs for the newly inserted class sessions.
     */
    @Insert
    suspend fun insertClassSessions(classSessions: List<ClassSession>): List<Long>

    /**
     * Updates an existing Timetable entry in the database.
     * @param timetable The Timetable object to update.
     */
    @Update
    suspend fun updateTimetable(timetable: Timetable)

    /**
     * Updates an existing ClassSession entry in the database.
     * @param classSession The ClassSession object to update.
     */
    @Update
    suspend fun updateClassSession(classSession: ClassSession)

    /**
     * Updates the 'isSilenced' status for all ClassSessions belonging to a specific timetable.
     * This is used when the master silence switch for a timetable is toggled.
     * @param timetableId The ID of the timetable whose sessions are to be updated.
     * @param isSilenced The new silence status to apply (true for silenced, false for unsilenced).
     * @return The number of rows updated.
     */
    @Query("UPDATE class_sessions SET isSilenced = :isSilenced WHERE timetableId = :timetableId")
    suspend fun updateClassSessionsSilenceStatusForTimetable(timetableId: Long, isSilenced: Boolean): Int

    /**
     * Deletes a Timetable entry from the database by its ID.
     * @param timetable The Timetable object to delete.
     */
    @Delete
    suspend fun deleteTimetable(timetable: Timetable)

    /**
     * Retrieves all active Timetable entries as a Flow for real-time updates.
     * @return A Flow emitting a list of active Timetable objects.
     */
    @Query("SELECT * FROM timetables WHERE isActive = 1")
    fun getAllActiveTimetables(): Flow<List<Timetable>>

    /**
     * Retrieves all Timetable entries from the database.
     * @return A list of all Timetable objects.
     */
    @Query("SELECT * FROM timetables")
    suspend fun getAllTimetables(): List<Timetable>

    /**
     * Retrieves all active Timetable entries of a specific type from the database.
     * @param type The type of timetable (e.g., "LECTURE", "PERSONAL").
     * @return A list of active Timetable objects of the specified type.
     */
    @Query("SELECT * FROM timetables WHERE isActive = 1 AND type = :type")
    suspend fun getAllActiveTimetablesByType(type: String): List<Timetable>

    /**
     * Retrieves all archived (inactive) Timetable entries.
     * @return A list of archived Timetable objects.
     */
    @Query("SELECT * FROM timetables WHERE isActive = 0")
    suspend fun getArchivedTimetables(): List<Timetable>

    /**
     * Retrieves all archived (inactive) Timetable entries from the database.
     * Archived timetables are those with isActive = 0.
     * @return A list of archived Timetable objects.
     */
    @Query("SELECT * FROM timetables WHERE isActive = 0")
    suspend fun getAllArchivedTimetables(): List<Timetable>

    /**
     * Retrieves all archived (inactive) Timetable entries of a specific type from the database.
     * @param type The type of timetable (e.g., "LECTURE", "PERSONAL").
     * @return A list of archived Timetable objects of the specified type.
     */
    @Query("SELECT * FROM timetables WHERE isActive = 0 AND type = :type")
    suspend fun getAllArchivedTimetablesByType(type: String): List<Timetable>

    /**
     * Retrieves a specific Timetable entry by its ID.
     * @param id The ID of the Timetable to retrieve.
     * @return The Timetable object if found, null otherwise.
     */
    @Query("SELECT * FROM timetables WHERE id = :id")
    suspend fun getTimetableById(id: Long): Timetable?
    /**
     * Retrieves a specific Timetable by its ID as a Flow for reactive updates.
     * @param id The ID of the timetable to retrieve.
     * @return A Flow emitting the Timetable object, or null if not found.
     */
    @Query("SELECT * FROM timetables WHERE id = :id")
    fun getTimetableByIdFlow(id: Long): Flow<Timetable?>

    /**
     * Retrieves a specific ClassSession by its sessionId.
     * @param sessionId The ID of the class session to retrieve.
     * @return The ClassSession object, or null if not found.
     */
    @Query("SELECT * FROM class_sessions WHERE sessionId = :sessionId")
    suspend fun getClassSessionById(sessionId: Long): ClassSession?

    /**
     * Retrieves all ClassSession entries associated with a specific timetable ID.
     * @param timetableId The ID of the parent Timetable.
     * @return A list of ClassSession objects.
     */
    @Query("SELECT * FROM class_sessions WHERE timetableId = :timetableId")
    suspend fun getClassSessionsForTimetable(timetableId: Long): List<ClassSession>

    /**
     * Retrieves all ClassSession entries that are associated with an active timetable. To be used for clash detection and view schedule.
     * These are typically Lecture and Personal sessions.
     * Further filtering by day of week and timetable validity dates will be done in the activity.
     * @return A list of ClassSession objects from active timetables.
     */
    @Query("SELECT cs.* FROM class_sessions cs INNER JOIN timetables t ON cs.timetableId = t.id WHERE t.isActive = 1")
    suspend fun getAllClassSessionsForActiveTimetables(): List<ClassSession>

    /**
     * Retrieves ClassSession entries that are scheduled for a specific day of the week
     * and are not ad-hoc.
     * The remaining filtering (timetable activity and validity) will be done in Kotlin.
     *
     * @param dayOfWeek The DayOfWeek enum value.
     * @return A list of ClassSession objects for the specified day.
     */
    @Query("""
        SELECT * FROM class_sessions
        WHERE dayOfWeek = :dayOfWeek
    """)
    suspend fun getRegularClassSessionsForDay(dayOfWeek: DayOfWeek): List<ClassSession>

    /**
     * DEBUG QUERY: Retrieves all ClassSession entries with their associated Timetable details.
     * This is for debugging purposes to inspect the raw data and join conditions.
     */
    @Query("""
        SELECT
            cs.*,
            t.id AS id,
            t.name AS name,
            t.type AS type,
            t.validityStartDate AS validityStartDate,
            t.validityEndDate AS validityEndDate,
            t.defaultNotificationOffsetMinutes1 AS defaultNotificationOffsetMinutes1,
            t.defaultNotificationOffsetMinutes2 AS defaultNotificationOffsetMinutes2,
            t.isMasterSilenced AS isMasterSilenced,
            t.isActive AS isActive,
            t.creationDate AS creationDate,
            t.creationTimeZone AS creationTimeZone
        FROM class_sessions cs
        LEFT JOIN timetables t ON cs.timetableId = t.id
    """)
    suspend fun getAllClassSessionsWithTimetableDetailsDebug(): Map<ClassSession, Timetable?>


    /**
     * Retrieves all ClassSession entries associated with a specific timetable ID and day of the week.
     * This is for regular (non-ad-hoc) timetable sessions.
     * @param timetableId The ID of the parent Timetable.
     * @param dayOfWeek The DayOfWeek enum value.
     * @return A list of ClassSession objects.
     */
    @Query("SELECT * FROM class_sessions WHERE timetableId = :timetableId AND dayOfWeek = :dayOfWeek")
    suspend fun getClassSessionsForTimetableAndDay(timetableId: Long, dayOfWeek: DayOfWeek): List<ClassSession>

    /**
     * Transactional method to insert a Timetable and its associated ClassSessions.
     * Ensures atomicity: either both are inserted, or neither are.
     * @param timetable The Timetable object to insert.
     * @param classSessions The list of ClassSession objects to insert, linked to this timetable.
     * @return The Timetable object with its generated ID.
     */
    @Transaction
    suspend fun insertFullTimetable(timetable: Timetable, classSessions: List<ClassSession>): Timetable {
        // Deactivate any existing active timetables of the same type
        getAllActiveTimetablesByType(timetable.type.name).forEach { activeTt ->
            updateTimetable(activeTt.copy(isActive = false))
        }
        val timetableId = insertTimetable(timetable)
        Log.d("TimetableDao", "Inserted Timetable, received ID: $timetableId")
        val sessionsWithTimetableId = classSessions.map { it.copy(timetableId = timetableId) }
        insertClassSessions(sessionsWithTimetableId)
        return timetable.copy(id = timetableId)
    }

    /**
     * Retrieves all unique course names from active timetables of type "LECTURE".
     * This will be used for autocomplete suggestions in the Add Assignment screen.
     * @return A list of unique course names.
     */
    @Query("SELECT DISTINCT courseName FROM class_sessions WHERE timetableId IN (SELECT id FROM timetables WHERE isActive = 1 AND type = 'LECTURE')")
    suspend fun getUniqueLectureCourseNames(): List<String>

    /**
     * Retrieves ClassSession entries that are scheduled for a specific date,
     * considering their timetable's validity period and the session's day of the week.
     * This is for regular (non-ad-hoc) timetable sessions.
     *
     * @param date The LocalDate for which to retrieve class sessions.
     * @param dayOfWeek The DayOfWeek enum value.
     * @return A list of ClassSession objects for the specified date.
     */
    @Query("""
        SELECT cs.* FROM class_sessions cs
        INNER JOIN timetables t ON cs.timetableId = t.id
        WHERE t.isActive = 1 AND t.validityStartDate <= :date AND t.validityEndDate >= :date
        AND cs.dayOfWeek = :dayOfWeek
    """)
    suspend fun getClassSessionsForDate(date: LocalDate, dayOfWeek: DayOfWeek): List<ClassSession>

    /**
     * Retrieves all ClassSession entries that are NOT ad-hoc.
     * Filtering by day of week and timetable validity dates will be done in Kotlin.
     * @return A list of ClassSession objects that are not ad-hoc.
     */
    @Query("SELECT * FROM class_sessions")
    suspend fun getAllRegularClassSessions(): List<ClassSession>

    /**
     * Retrieves all Timetable entries that have expired (validityEndDate is in the past)
     * and are still active (not yet archived).
     * This query is used by the CleanupWorker to identify timetables for archival.
     *
     * @param currentDate The current date to check against validityEndDate.
     * @return A list of expired but active Timetable objects.
     */
    @Query("SELECT * FROM timetables WHERE validityEndDate < :currentDate AND isActive = 1")
    suspend fun getExpiredTimetables(currentDate: LocalDate): List<Timetable>

    // --- ClassSession Methods ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClassSession(session: ClassSession): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllClassSessions(sessions: List<ClassSession>)

    // --- SessionException Methods (NEW) ---

    /**
     * Inserts or replaces a session exception. If an exception for the same session on the same
     * date already exists, it will be updated.
     * @param exception The SessionException to insert or update.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSessionException(exception: SessionException)

    /**
     * Retrieves a list of all exceptions for a given date.
     * This is useful for the ScheduleView to know which sessions to cancel or modify.
     * @param date The specific date to check for exceptions.
     * @return A list of SessionException objects for that date.
     */
    @Query("SELECT * FROM session_exceptions WHERE originalDate = :date OR newDate = :date")
    suspend fun getSessionExceptionsForDate(date: LocalDate): List<SessionException>

    /**
     * Retrieves a specific exception for a given session on a given date.
     * @param sessionId The ID of the parent ClassSession.
     * @param date The original date of the session instance.
     * @return A SessionException object if one exists, otherwise null.
     */
    @Query("SELECT * FROM session_exceptions WHERE originalSessionId = :sessionId AND originalDate = :date")
    suspend fun getExceptionForSessionOnDate(sessionId: Long, date: LocalDate): SessionException?
}
