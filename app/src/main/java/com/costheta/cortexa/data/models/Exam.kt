package com.costheta.cortexa.data.models

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalTime

/**
 * Represents an Exam entity in the database.
 */
@Entity(tableName = "exams")
data class Exam(
    @PrimaryKey(autoGenerate = true)
    val examId: Long? = null,
    val courseName: String,
    val examDetails: String? = null,
    val examDate: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val venue: String,
    val creationDate: LocalDate = LocalDate.now(),
    val validatedItem: Boolean = false,
    // TIMEZONE CHANGE: Renamed for clarity and added start/end specific millis
    val startDateTimeMillis: Long = 0L,
    val endDateTimeMillis: Long = 0L,
    val silenceNotifications: Boolean = false,
    // TIMEZONE CHANGE: Added field to store the original timezone ID
    val creationTimezone: String? = null
)
