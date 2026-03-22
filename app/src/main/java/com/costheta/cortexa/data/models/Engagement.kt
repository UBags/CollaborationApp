package com.costheta.cortexa.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Ignore
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Represents an Ad-hoc Engagement entity in the database.
 * This model is specifically for engagements that are not part of a regular timetable.
 * It encapsulates all the fields previously used by `ClassSession` for ad-hoc entries.
 */
@Entity(tableName = "engagements")
data class Engagement(
    @PrimaryKey(autoGenerate = true)
    val engagementId: Long? = null,
    val engagementName: String, // Corresponds to courseName in old ad-hoc ClassSession
    val startTime: LocalTime,
    val durationMinutes: Int,
    val validityStartDate: LocalDate,
    val validityEndDate: LocalDate,
    val daySelectionType: String?, // "SPECIFIC_DAYS" or "RECURRENCE"
    val selectedDaysOfWeek: String?, // Comma-separated list of DayOfWeek names (e.g., "MONDAY,FRIDAY")
    val recurrenceIntervalDays: Int?, // N in "recur every N days"
    val notes: String?,
    val isSilenced: Boolean = false, // Silence state for this specific engagement
    val creationDate: LocalDate = LocalDate.now(), // Date when the engagement was created
    val isActive: Boolean = true, // isActive property for Engagement
    val venue: String?,
    val validatedItem: Boolean = false,
    val endDateTimeInMillis: Long = 0L,
    val creationTimezone: String? = null,
    val notification1Minutes: Int = 60, // Default 60 minutes before start
    val notification2Minutes: Int = 30, // Default 30 minutes before start
    val notification3Minutes: Int = 10  // Default 10 minutes before start
) {
    // These fields are used for displaying a single instance of a recurring engagement.
    // They are ignored by Room and populated at runtime.
    @Ignore
    @Transient
    var nextOccurrenceDate: LocalDate? = null

    @Ignore
    @Transient
    var startDateTime: LocalDateTime? = null
}
