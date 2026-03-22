// Path: app/src/main/java/com/example/cortexa/data/model/Attachment.kt
package com.costheta.cortexa.data.models // Confirming package

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "attachments")
data class Attachment(
    @PrimaryKey(autoGenerate = true)
    val attachmentId: Long = 0,
    val eventType: String, // e.g., "Assignment", "ToDo", "Engagement", "Exam"
    val eventRefId: Long,   // The ID of the specific event (e.g., TodoItem.id, Assignment.id)
    val attachmentType: AttachmentType, // Enum: IMAGE_LOCAL, DOCUMENT_LOCAL, HYPERLINK
    val uriString: String, // Stores Content URI (for local files) or URL string (for hyperlinks)
    val displayName: String?, // User-friendly name for the attachment (e.g., "My Notes.pdf", "Project Requirements")
    val timestamp: LocalDateTime = LocalDateTime.now(), // When this attachment record was created
    val validatedItem: Boolean = false // NEW: True if the attachment has been fully saved/validated by the user
)

enum class AttachmentType {
    IMAGE_LOCAL,       // Images captured by the app or picked from local storage/gallery. Stored as Content URI.
    DOCUMENT_LOCAL,    // Documents (PDF, DOCX, TXT etc.) picked from local storage. Stored as Content URI.
    HYPERLINK,         // Web URLs. Stored as the URL string itself.
    VIDEO_LOCAL,       // Locally stored videos.
    AUDIO_LOCAL,       // Locally stored audio files.
    // Future types could include other...
}