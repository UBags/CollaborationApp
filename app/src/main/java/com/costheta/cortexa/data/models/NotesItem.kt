package com.costheta.cortexa.data.models

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

/**
 * Data class representing a single Note item in the application.
 * Notes can be associated with a subject (e.g., a course).
 */
@Entity(tableName = "notes")
data class NotesItem(
    @PrimaryKey(autoGenerate = true)
    val noteId: Long? = null, // Unique ID for the Note item
    val subject: String?, // Optional subject for the note (e.g., "Mathematics I")
    val type: String?, // NEW: Type of the note (e.g., "Lecture", "Lab Notes", "Tutorial")
    val description: String?, // NEW: Optional detailed description for the note
    val keywords: String? = null, // NEW: Optional keywords for the note (e.g., "Algebra, Geometry, Exam Prep")
    val creationDate: LocalDateTime = LocalDateTime.now(), // Date when the Note item was created
    val lastModified: LocalDateTime = LocalDateTime.now(), // Timestamp of last modification
    val validatedItem: Boolean = false, // NEW: True if the item has been fully saved/validated by the user
    val archivedNote: Boolean = false, // NEW: True if the note is archived, false otherwise

    // TIMEZONE CHANGE: Add fields for timezone awareness
    val lastModifiedMillis: Long = 0L,
    val creationTimezone: String? = null
)
