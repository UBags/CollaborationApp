package com.costheta.cortexa.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Represents an Activity entity in the database.
 * Activities are flexible events with a start and end date/time,
 * and can have attachments and subtasks.
 */
@Entity(tableName = "actions")
data class Action(
    @PrimaryKey(autoGenerate = true)
    val actionId: Long? = null,
    val actionName: String,
    val description: String?, // Nullable description
    val startDate: LocalDate,
    val startTime: LocalTime,
    val endDate: LocalDate,
    val endTime: LocalTime,
    val silenceNotifications: Boolean = false, // Option to silence notifications for this activity
    val notificationMinutes1: Int? = null, // First notification offset in minutes before start time
    val notificationMinutes2: Int? = null, // Second notification offset in minutes before start time
    val notificationMinutes3: Int? = null, // NEW: Third notification offset in minutes before start time
    val creationDate: LocalDateTime = LocalDateTime.now(), // Timestamp when the activity was created
    val lastModified: LocalDateTime = LocalDateTime.now(), // Timestamp for the last modification
    val validatedItem: Boolean = false, // True if the item has been fully saved/validated by the user
    val venue: String? = null, // NEW: Optional venue for the action
    val isDone: Boolean = false, // NEW: Field to mark action as done/completed

    // TIMEZONE CHANGE: Add fields for timezone awareness
    val startDateTimeMillis: Long = 0L,
    val endDateTimeMillis: Long = 0L,
    val creationTimezone: String? = null
)
