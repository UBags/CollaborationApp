package com.costheta.cortexa.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.costheta.cortexa.data.models.Exam
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Data Access Object (DAO) for interacting with the Exam entity.
 * Defines methods for inserting, querying, updating, and deleting Exam items.
 */
@Dao
interface ExamDao {

    /**
     * Inserts a new Exam entry into the database.
     * @param exam The Exam object to insert.
     * @return The row ID of the newly inserted exam.
     */
    @Insert
    suspend fun insertExam(exam: Exam): Long

    /**
     * Updates an existing Exam entry in the database.
     * @param exam The Exam object to update.
     */
    @Update
    suspend fun updateExam(exam: Exam)

    /**
     * Deletes an Exam entry from the database by its ID.
     * @param examId The ID of the Exam to delete.
     */
    @Query("DELETE FROM exams WHERE examId = :examId")
    suspend fun deleteExam(examId: Long): Int

    /**
     * Deletes all Exam entries that have not been validated (i.e., temporary/incomplete entries).
     * This is useful for cleaning up after unexpected app closures or user discards.
     * @return The number of rows deleted.
     */
    @Query("DELETE FROM exams WHERE validatedItem = 0")
    suspend fun deleteUnvalidatedExams(): Int

    /**
     * Retrieves all Exam entries that are marked as validated, as a Flow for real-time updates.
     * @return A Flow emitting a list of all validated Exam objects.
     */
    @Query("SELECT * FROM exams WHERE validatedItem = 1 ORDER BY examDate ASC, startTime ASC")
    fun getAllExams(): Flow<List<Exam>>

    /**
     * Retrieves a specific Exam entry by its ID.
     * @param examId The ID of the Exam to retrieve.
     * @return The Exam object if found, null otherwise.
     */
    @Query("SELECT * FROM exams WHERE examId = :examId")
    suspend fun getExamById(examId: Long): Exam?

    /**
     * Retrieves all Exam entries that have expired (examDate + endTime + 1 hour is in the past).
     * This query is used by the CleanupWorker to identify exams for deletion.
     *
     * @param currentDate The current date to check against.
     * @return A list of expired Exam objects.
     */
    @Query("""
        SELECT * FROM exams
        WHERE examDate < :currentDate
    """)
    suspend fun getExpiredExams(currentDate: LocalDate): List<Exam>

    /**
     * Retrieves all Exam entries that are scheduled for a specific date. Used for both clash detection as well for View Schedule.
     *
     * @param date The LocalDate for which to retrieve exams.
     * @return A list of Exam objects for the specified date.
     */
    @Query("SELECT * FROM exams WHERE examDate = :date")
    suspend fun getExamsForSpecificDate(date: LocalDate): List<Exam>

    /**
     * TIMEZONE CHANGE: Retrieves all Exam entries that are considered "displayable".
     * An exam is displayable if its end time + 1 hour is greater than or equal to the current time.
     * This uses the pre-calculated endDateTimeMillis for efficient filtering.
     *
     * @param currentTimeMillis The current time in milliseconds since epoch (UTC).
     * @return A Flow emitting a list of displayable Exam objects.
     */
    @Query("""
        SELECT * FROM exams
        WHERE validatedItem = 1 
        AND (endDateTimeMillis + 3600000) >= :currentTimeMillis
        ORDER BY examDate ASC, startTime ASC
    """)
    fun getDisplayableExams(currentTimeMillis: Long): Flow<List<Exam>>

    /**
     * Retrieves all unique course names from validated exams.
     * @return A list of unique course names.
     */
    @Query("SELECT DISTINCT courseName FROM exams WHERE validatedItem = 1 AND courseName IS NOT NULL AND courseName != ''")
    suspend fun getUniqueExamCourseNames(): List<String>

    /**
     * Retrieves all unique venue names from validated exams.
     * @return A list of unique venue names.
     */
    @Query("SELECT DISTINCT venue FROM exams WHERE validatedItem = 1 AND venue IS NOT NULL AND venue != ''")
    suspend fun getUniqueExamVenues(): List<String>

    /**
     * NEW: Retrieves the IDs of all Exam entries that have not been validated.
     * This is useful for cleaning up associated records (like locations or attachments)
     * before the unvalidated exams themselves are deleted.
     * @return A list of IDs of unvalidated Exam objects.
     */
    @Query("SELECT examId FROM exams WHERE validatedItem = 0")
    suspend fun getUnvalidatedExamIds(): List<Long>
}
