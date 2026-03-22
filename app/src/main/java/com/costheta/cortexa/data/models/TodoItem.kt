package com.costheta.cortexa.data.models

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Data class representing a single To-Do item in the application.
 * This is a separate entity from Assignments.
 */
@Entity(tableName = "todos")
data class TodoItem(
    @PrimaryKey(autoGenerate = true)
    val todoId: Long? = null, // Unique ID for the To-Do item
    val title: String, // The main title or name of the To-Do item
    val description: String?, // Optional detailed description
    val dueDate: LocalDate?, // Optional due date
    val dueTime: LocalTime?, // Optional due time
    val isCompleted: Boolean = false, // Status of the To-Do item (completed or not)
    val creationDate: LocalDate = LocalDate.now(), // Date when the To-Do item was created
    val lastModified: LocalDateTime = LocalDateTime.now(), // Timestamp of last modification for sorting
    val priority: String? = null, // Optional priority (e.g., "High", "Medium", "Low")
    val validatedItem: Boolean = false, // True if the item has been fully saved/validated by the user
    val completedTimeInMillis: Long? = null, // Timestamp when the To-Do was completed (in milliseconds)
    val silenceNotifications: Boolean = false, // Flag to silence notifications for this To-Do
    val originalZoneId: String?, // The ZoneId string where the To-Do was created
    val dueDateTimeMillis: Long? = null // Due date and time in UTC milliseconds for easy comparison
)
