// src/main/java/com/costheta/cortexa/data/models/TimetableModels.kt
package com.costheta.cortexa.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Represents a Timetable entity in the database.
 * A timetable can be a 'LECTURE', 'PERSONAL', etc.
 */
@Entity(tableName = "timetables")
data class Timetable(
    @PrimaryKey(autoGenerate = true)
    val id: Long? = null,
    val name: String,
    val type: TimetableType, // e.g., LECTURE, PERSONAL
    val validityStartDate: LocalDate,
    val validityEndDate: LocalDate,
    val defaultNotificationOffsetMinutes1: Int, // e.g., 30 minutes before
    val defaultNotificationOffsetMinutes2: Int, // e.g., 15 minutes before
    val isMasterSilenced: Boolean = false, // Master switch to silence all notifications for this timetable
    val isActive: Boolean = true, // True if currently active, false if archived
    val creationDate: LocalDate = LocalDate.now(), // Date when the session was created
    val creationTimeZone: String? // The timezone ID (e.g., "Asia/Kolkata") where the timetable was created
)

/**
 * Enum to define the type of timetable.
 */
enum class TimetableType {
    LECTURE,
    PERSONAL
}

/**
 * Represents a Class Session entity in the database.
 * A class session belongs to a timetable (via foreign key) or can be an ad-hoc engagement.
 */
@Entity(
    tableName = "class_sessions",
    foreignKeys = [ForeignKey(
        entity = Timetable::class,
        parentColumns = ["id"],
        childColumns = ["timetableId"],
        onDelete = ForeignKey.CASCADE // If a timetable is deleted, its sessions are also deleted
    )],
    indices = [Index(value = ["timetableId"])]
)
data class ClassSession(
    @PrimaryKey(autoGenerate = true)
    val sessionId: Long? = null,
    val timetableId: Long?, // Null for ad-hoc engagements
    val courseName: String,
    val dayOfWeek: DayOfWeek,
    val startTime: LocalTime,
    val durationMinutes: Int,
    val location: String?,
    val notes: String?,
    val notificationOffsetMinutes1: Int?, // Null means use timetable default
    val notificationOffsetMinutes2: Int?, // Null means use timetable default
    val validityStartDate: LocalDate = LocalDate.MIN, // For ad-hoc engagements, defines their active period
    val validityEndDate: LocalDate = LocalDate.MAX,   // For ad-hoc engagements, defines their active period
    val isSilenced: Boolean = false, // Silence state for this specific session (overrides timetable default)
    val creationDate: LocalDate = LocalDate.now(), // Date when the session was created

    // Fields for timezone-aware scheduling
    val startDateTimeMillis: Long = 0,
    val endDateTimeMillis: Long = 0
)

/**
 * NEW: Represents an exception to a recurring ClassSession on a specific date.
 * This allows for one-off cancellations or rescheduling of a single class instance.
 */
@Entity(
    tableName = "session_exceptions",
    foreignKeys = [ForeignKey(
        entity = ClassSession::class,
        parentColumns = ["sessionId"],
        childColumns = ["originalSessionId"],
        onDelete = ForeignKey.CASCADE // If the parent session is deleted, its exceptions are also deleted
    )],
    // This index ensures that there can only be one exception for a given session on a given date.
    indices = [Index(value = ["originalSessionId", "originalDate"], unique = true)]
)
data class SessionException(
    @PrimaryKey(autoGenerate = true)
    val exceptionId: Long? = null,
    val originalSessionId: Long,   // The ID of the recurring ClassSession being modified
    val originalDate: LocalDate,     // The specific date of the session instance that is being cancelled or moved

    val classSessionExceptionType: ClassSessionExceptionType, // The type of exception: CANCELLED or MODIFIED

    // These fields are only used if the exceptionType is MODIFIED
    val newDate: LocalDate?,
    val newStartTime: LocalTime?,
    val newDurationMinutes: Int?,
    val newNotes: String?
)

/**
 * NEW: Enum to define the type of session exception.
 */
enum class ClassSessionExceptionType {
    CANCELLED, // The session instance on the originalDate is cancelled.
    MODIFIED   // The session instance is moved to a new date/time.
}
