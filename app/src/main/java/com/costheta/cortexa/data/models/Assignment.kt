package com.costheta.cortexa.data.models

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalTime

/**
 * Represents an Assignment entity in the database.
 */
@Entity(tableName = "assignments")
data class Assignment(
    @PrimaryKey(autoGenerate = true)
    val assignmentId: Long? = null,
    val courseName: String,
    val assignmentTopic: String,
    val assignmentName: String,
    val dueDate: LocalDate,
    val submissionTime: LocalTime,
    val estimatedHours: Int,
    val currentProgress: Int,
    val silenceNotifications: Boolean = false,
    val creationDate: LocalDate = LocalDate.now(),
    val validatedItem: Boolean = false,
    val dueDateTimeMillis: Long = 0L,
    // NEW: Field to store the ID of the timezone where the assignment was created or last edited.
    val creationTimezone: String? = null
)
