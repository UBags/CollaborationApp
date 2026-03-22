package com.costheta.cortexa.util.subtask

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action

import com.costheta.cortexa.data.models.Subtask

/**
 * Interface for callbacks from SubtaskManager to the hosting Activity/Fragment.
 * This allows the SubtaskManager to request UI updates, launch activities for results,
 * and display messages without having direct Android framework dependencies.
 */
interface SubtaskManagerCallbacks {
    /**
     * Requests the hosting component to show a Toast message.
     * @param messageResId The string resource ID of the message to show.
     * @param duration The duration of the toast (Toast.LENGTH_SHORT or Toast.LENGTH_LONG).
     */
    fun showToast(messageResId: Int, duration: Int)

    /**
     * Requests the hosting component to show a Toast message with a formatted string.
     * @param message The formatted string to show.
     * @param duration The duration of the toast (Toast.LENGTH_SHORT or Toast.LENGTH_LONG).
     */
    fun showToast(message: String, duration: Int)

    /**
     * Requests the hosting component to update the list of subtasks displayed.
     * This is typically called after any data modification by the manager.
     */
    fun onSubtasksUpdated()

    /**
     * Provides the current eventRefId to the SubtaskManager.
     */
    fun getCurrentEventRefId(): Long

    /**
     * Provides the current eventType to the SubtaskManager.
     */
    fun getCurrentEventType(): String

    // Removed showCopySubtasksDialog and dismissCopySubtasksDialog as they are now handled internally by SubtaskManager

    /**
     * Requests the hosting component to log the current state of the UI (RecyclerView).
     * This is called by SubtaskManager after data changes.
     * @param subtasks The list of subtasks that the RecyclerView should currently be displaying.
     */
    fun requestUiStateLogging(subtasks: List<Subtask>)

    fun onSubtaskCopyRequested() // Callback to notify when copy process starts
}
