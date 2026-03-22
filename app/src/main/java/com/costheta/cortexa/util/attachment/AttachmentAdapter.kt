package com.costheta.cortexa.util.attachment

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.costheta.cortexa.R // R file remains in the main package
import com.costheta.cortexa.data.models.Attachment
import com.costheta.cortexa.data.models.AttachmentType
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class AttachmentAdapter(
    private val onDeleteClick: (Attachment) -> Unit,
    private val onItemClick: (Attachment) -> Unit
) : ListAdapter<Attachment, AttachmentAdapter.AttachmentViewHolder>(AttachmentDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttachmentViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.attachment_item, parent, false)
        return AttachmentViewHolder(view)
    }

    override fun onBindViewHolder(holder: AttachmentViewHolder, position: Int) {
        val attachment = getItem(position)
        holder.bind(attachment)
    }

    inner class AttachmentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageViewAttachmentIcon: ImageView = itemView.findViewById(R.id.imageViewAttachmentIcon)
        private val textViewAttachmentName: TextView = itemView.findViewById(R.id.textViewAttachmentName)
        private val textViewAttachmentInfo: TextView = itemView.findViewById(R.id.textViewAttachmentInfo)
        private val buttonDeleteAttachment: ImageButton = itemView.findViewById(R.id.buttonDeleteAttachment)

        // The pattern remains the same, but it will be applied to a ZonedDateTime.
        private val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm z")

        fun bind(attachment: Attachment) {
            textViewAttachmentName.text = attachment.displayName ?: attachment.uriString // Fallback to URI if no display name

            val context = itemView.context

            // Convert the LocalDateTime to a ZonedDateTime in the user's current timezone for display.
            // This assumes the stored LocalDateTime was in the system's default timezone at the time of creation.
            val zonedDateTime = attachment.timestamp.atZone(ZoneId.systemDefault())
            val infoText = "${attachment.attachmentType.name.replace("_", " ")} | ${zonedDateTime.format(dateTimeFormatter)}"
            textViewAttachmentInfo.text = infoText

            // Set appropriate icon based on attachment type
            val iconResId = when (attachment.attachmentType) {
                AttachmentType.IMAGE_LOCAL -> R.drawable.ic_image // Assuming ic_image drawable
                AttachmentType.DOCUMENT_LOCAL -> R.drawable.ic_document // Assuming ic_document drawable
                AttachmentType.HYPERLINK -> R.drawable.ic_link // Assuming ic_link drawable
                AttachmentType.VIDEO_LOCAL -> R.drawable.ic_video // Assuming ic_video drawable
                AttachmentType.AUDIO_LOCAL -> R.drawable.ic_audio // Assuming ic_audio drawable
            }
            imageViewAttachmentIcon.setImageResource(iconResId)
            imageViewAttachmentIcon.contentDescription = when (attachment.attachmentType) {
                AttachmentType.IMAGE_LOCAL -> context.getString(R.string.attachment_string_attachment_image_content_description)
                AttachmentType.DOCUMENT_LOCAL -> context.getString(R.string.attachment_string_attachment_document_content_description)
                AttachmentType.HYPERLINK -> context.getString(R.string.attachment_string_attachment_hyperlink_content_description)
                AttachmentType.VIDEO_LOCAL -> context.getString(R.string.attachment_string_attachment_video_content_description)
                AttachmentType.AUDIO_LOCAL -> context.getString(R.string.attachment_string_attachment_audio_content_description)
            }


            // Set click listeners
            itemView.setOnClickListener { onItemClick(attachment) }
            buttonDeleteAttachment.setOnClickListener { onDeleteClick(attachment) }
        }
    }

    private class AttachmentDiffCallback : DiffUtil.ItemCallback<Attachment>() {
        override fun areItemsTheSame(oldItem: Attachment, newItem: Attachment): Boolean {
            return oldItem.attachmentId == newItem.attachmentId
        }

        override fun areContentsTheSame(oldItem: Attachment, newItem: Attachment): Boolean {
            return oldItem == newItem
        }
    }
}
