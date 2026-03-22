package com.costheta.cortexa.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Data class representing a single subtask for various event types (Engagement, Assignment, Exam, To-Do, Note).
 */
@Entity(tableName = "subtasks")
data class Subtask(
    @PrimaryKey(autoGenerate = true)
    val subtaskId: Long = 0,
    val eventType: String,
    val eventRefId: Long,
    val description: String,
    val completionState: Boolean,
    val position: Int,
    val validatedItem: Boolean,
    val creationDate: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis() // New field to trigger DiffUtil
)
