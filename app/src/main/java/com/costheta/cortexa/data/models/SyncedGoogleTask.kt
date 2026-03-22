package com.costheta.cortexa.data.models

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a single task synced from the Google Tasks API.
 * This acts as a local, offline cache of a user's Google Tasks.
 * The index on googleTaskId ensures we can efficiently update or delete tasks
 * without creating duplicates.
 */
@Entity(
    tableName = "synced_google_tasks",
    indices = [Index(value = ["googleTaskId"], unique = true)]
)
data class SyncedGoogleTask(
    @PrimaryKey(autoGenerate = true)
    val localId: Long = 0,
    val googleTaskId: String, // The original ID from the Google Tasks API
    val googleTaskListId: String, // The ID of the task list it belongs to
    val title: String,
    val notes: String?,
    val dueDateTimeMillis: Long?,
    val isCompleted: Boolean,
    val parentTaskId: String?, // For handling subtasks
    val lastSyncedTimestamp: Long = System.currentTimeMillis()
)
