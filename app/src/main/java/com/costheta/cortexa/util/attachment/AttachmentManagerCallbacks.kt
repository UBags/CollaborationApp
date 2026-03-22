package com.costheta.cortexa.util.attachment

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action

import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import com.costheta.cortexa.data.models.Attachment

/**
 * Interface for callbacks from AttachmentManager to the hosting Activity/Fragment.
 * This allows the AttachmentManager to request UI updates, launch activities for results,
 * and display messages without having direct Android framework dependencies.
 */
interface AttachmentManagerCallbacks {
    /**
     * Requests the hosting component to launch an activity for a result.
     * @param intent The Intent to launch.
     * @param launcher The ActivityResultLauncher to use for the launch.
     */
    fun launchActivityForResult(intent: Intent, launcher: ActivityResultLauncher<Intent>)

    /**
     * Requests the hosting component to launch an activity for a result with a specific URI.
     * Used for camera capture where the URI is pre-generated.
     * @param uri The URI to pass to the launcher.
     * @param launcher The ActivityResultLauncher to use for the launch.
     */
    fun launchUriActivityForResult(uri: Uri, launcher: ActivityResultLauncher<Uri>)

    /**
     * Requests the hosting component to launch a permission request.
     * @param permissions An array of permissions to request.
     * @param launcher The ActivityResultLauncher to use for the permission request.
     */
    fun requestPermissions(permissions: Array<String>, launcher: ActivityResultLauncher<Array<String>>)

    /**
     * Requests the hosting component to launch a single permission request.
     * @param permission The permission to request.
     * @param launcher The ActivityResultLauncher to use for the permission request.
     */
    fun requestPermission(permission: String, launcher: ActivityResultLauncher<String>)

    /**
     * Requests the hosting component to show a Toast message.
     * @param message The string resource ID of the message to show.
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
     * Requests the hosting component to update the list of attachments displayed.
     */
    fun onAttachmentsUpdated()

    /**
     * Requests the hosting component to finish itself.
     */
    fun finishActivity()

    /**
     * Provides the current eventRefId to the AttachmentManager.
     */
    fun getCurrentEventRefId(): Long

    /**
     * Provides the current eventType to the AttachmentManager.
     */
    fun getCurrentEventType(): String

    /**
     * NEW: Callback to notify when the copy attachments process starts.
     */
    fun onAttachmentCopyRequested()

    /**
     * NEW: Callback to notify when a new attachment is added to the database.
     * This is used to track attachments that might need to be cleaned up if the user cancels.
     * @param attachment The newly created attachment object.
     */
    fun onAttachmentAdded(attachment: Attachment)

    /**
     * NEW: Callback to notify when the copy attachments process completes.
     */
    fun onAttachmentCopyCompleted()

}
