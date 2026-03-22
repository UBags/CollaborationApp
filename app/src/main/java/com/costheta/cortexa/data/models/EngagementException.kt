package com.costheta.cortexa.data.models

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalTime

/**
 * Represents an exception to a recurring Engagement on a specific date.
 * This allows for one-off cancellations or rescheduling of a single engagement instance.
 */
@Entity(
    tableName = "engagement_exceptions",
    foreignKeys = [ForeignKey(
        entity = Engagement::class,
        parentColumns = ["engagementId"],
        childColumns = ["originalEngagementId"],
        onDelete = ForeignKey.CASCADE // If the parent engagement is deleted, its exceptions are also deleted
    )],
    // This index ensures that there can only be one exception for a given engagement on a given date.
    indices = [Index(value = ["originalEngagementId", "originalDate"], unique = true)]
)
data class EngagementException(
    @PrimaryKey(autoGenerate = true)
    val exceptionId: Long? = null,
    val originalEngagementId: Long,   // The ID of the recurring Engagement being modified
    val originalDate: LocalDate,     // The specific date of the engagement instance that is being cancelled or moved

    val engagementExceptionType: EngagementExceptionType, // The type of exception: CANCELLED or MODIFIED

    // These fields are only used if the exceptionType is MODIFIED
    val newDate: LocalDate?,
    val newStartTime: LocalTime?,
    val newDurationMinutes: Int?,
    val newNotes: String?
)

/**
 * Enum to define the type of engagement exception.
 */
enum class EngagementExceptionType {
    CANCELLED, // The engagement instance on the originalDate is cancelled.
    MODIFIED   // The engagement instance is moved to a new date/time or its details are changed for that day.
}
