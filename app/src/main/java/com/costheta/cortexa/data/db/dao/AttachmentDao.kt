// Path: app/src/main/java/com/example/cortexa/data/dao/AttachmentDao.kt
package com.costheta.cortexa.data.db.dao // Confirming package

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update // NEW: Import Update
import kotlinx.coroutines.flow.Flow
import com.costheta.cortexa.data.models.Attachment

@Dao
interface AttachmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachment(attachment: Attachment): Long // Return Long for inserted ID

    @Delete
    suspend fun deleteAttachment(attachment: Attachment)

    @Update // NEW: Add Update method for attachments
    suspend fun updateAttachment(attachment: Attachment): Int

    /**
     * Retrieves all attachments for a specific event type and event ID.
     * For example, all attachments for a particular To-Do item.
     * Ordered by timestamp, so newer attachments appear last by default.
     */
    @Query("SELECT * FROM attachments WHERE eventType = :eventType AND eventRefId = :eventRefId ORDER BY timestamp ASC")
    fun getAttachmentsForEvent(eventType: String, eventRefId: Long): Flow<List<Attachment>>

    /**
     * Retrieves a single attachment by its unique ID.
     */
    @Query("SELECT * FROM attachments WHERE attachmentId = :attachmentId")
    suspend fun getAttachmentById(attachmentId: Long): Attachment?

    /**
     * Deletes all attachments associated with a specific event.
     * Useful when an Assignment or To-Do is deleted.
     */
    @Query("DELETE FROM attachments WHERE eventType = :eventType AND eventRefId = :eventRefId")
    suspend fun deleteAttachmentsForEvent(eventType: String, eventRefId: Long)

    /**
     * NEW: Deletes all unvalidated attachments for a specific event.
     * This is used when a new To-Do/Assignment is discarded.
     */
    @Query("DELETE FROM attachments WHERE eventType = :eventType AND eventRefId = :eventRefId AND validatedItem = 0")
    suspend fun deleteUnvalidatedAttachmentsForEvent(eventType: String, eventRefId: Long): Int

    /**
     * NEW: Updates the validatedItem status for all attachments of a specific event.
     * Called when the parent To-Do/Assignment is successfully saved.
     */
    @Query("UPDATE attachments SET validatedItem = :validatedStatus WHERE eventType = :eventType AND eventRefId = :eventRefId")
    suspend fun updateAttachmentsValidationStatusForEvent(eventType: String, eventRefId: Long, validatedStatus: Boolean): Int

    /**
     * NEW: Deletes all attachments that have not been validated (i.e., temporary/incomplete entries).
     * This is useful for cleaning up after unexpected app closures or user discards.
     */
    @Query("DELETE FROM attachments WHERE validatedItem = 0")
    suspend fun deleteUnvalidatedAttachments(): Int

    /**
     * NEW: Retrieves the count of attachments for a specific event type and event ID.
     * This is used to determine if an attachment icon should be shown.
     */
    @Query("SELECT COUNT(*) FROM attachments WHERE eventType = :eventType AND eventRefId = :eventRefId")
    suspend fun getAttachmentCountForEvent(eventType: String, eventRefId: Long): Int

    /**
     * NEW: Retrieves all unique event types from the attachments table that have validated attachments.
     * Used for the "Copy Attachments" dialog to list available event types.
     */
    @Query("SELECT DISTINCT eventType FROM attachments WHERE validatedItem = 1")
    suspend fun getUniqueAttachmentEventTypes(): List<String>

    /**
     * NEW: Retrieves all validated attachments (eventRefId, displayName, and attachmentType) for a given event type.
     * This is used for the "Copy Attachments" dialog to list specific events to copy from.
     */
    @Query("SELECT attachmentId, eventType, eventRefId, uriString, displayName, attachmentType, timestamp, validatedItem FROM attachments WHERE eventType = :eventType AND validatedItem = 1 GROUP BY eventRefId, displayName, attachmentType")
    suspend fun getValidatedAttachmentsForEventType(eventType: String): List<Attachment> // Changed to return List<Attachment>

    /**
     * NEW: Retrieves all attachments that point to a specific file path (uriString).
     * Used to check if a file is shared before deleting it.
     */
    @Query("SELECT * FROM attachments WHERE uriString = :uriString")
    suspend fun getAttachmentsByUriString(uriString: String): List<Attachment>

    /**
     * NEW: Updates the uriString for all records that match an old path.
     * Used to update all references when a file is moved to the 'common' folder.
     */
    @Query("UPDATE attachments SET uriString = :newUriString WHERE uriString = :oldUriString")
    suspend fun updateUriStringPath(oldUriString: String, newUriString: String)
}
