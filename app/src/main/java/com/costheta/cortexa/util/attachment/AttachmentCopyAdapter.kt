package com.costheta.cortexa.util.attachment

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.costheta.cortexa.R
import com.costheta.cortexa.data.models.Attachment
import com.costheta.cortexa.data.models.AttachmentType
import java.time.format.DateTimeFormatter

class AttachmentCopyAdapter : ListAdapter<Attachment, AttachmentCopyAdapter.AttachmentViewHolder>(AttachmentDiffCallback()) {

    private val selectedAttachments = mutableSetOf<Attachment>()

    init {
        setHasStableIds(true)
    }

    override fun onCurrentListChanged(
        previousList: MutableList<Attachment>,
        currentList: MutableList<Attachment>,
    ) {
        // Update selectedAttachments when the list changes, selecting all by default
        selectedAttachments.clear()
        selectedAttachments.addAll(currentList)
        notifyDataSetChanged()
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).attachmentId
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttachmentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.attachment_copy_item, parent, false) // Using a new item layout for copy dialog
        return AttachmentViewHolder(view)
    }

    override fun onBindViewHolder(holder: AttachmentViewHolder, position: Int) {
        val attachment = getItem(position)
        holder.bind(attachment)
    }

    fun getSelectedAttachments(): List<Attachment> {
        return selectedAttachments.toList()
    }

    inner class AttachmentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageViewAttachmentIcon: ImageView = itemView.findViewById(R.id.imageViewAttachmentIcon)
        private val textViewAttachmentName: TextView = itemView.findViewById(R.id.textViewAttachmentName)
        private val textViewAttachmentInfo: TextView = itemView.findViewById(R.id.textViewAttachmentInfo)
        private val checkBox: CheckBox = itemView.findViewById(R.id.checkBoxAttachment)

        private val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")

        fun bind(attachment: Attachment) {
            textViewAttachmentName.text = attachment.displayName ?: attachment.uriString

            val context = itemView.context
            val infoText = "${attachment.attachmentType.name.replace("_", " ")} | ${attachment.timestamp.format(dateTimeFormatter)}"
            textViewAttachmentInfo.text = infoText

            // Set appropriate icon based on attachment type
            val iconResId = when (attachment.attachmentType) {
                AttachmentType.IMAGE_LOCAL -> R.drawable.ic_image
                AttachmentType.DOCUMENT_LOCAL -> R.drawable.ic_document
                AttachmentType.HYPERLINK -> R.drawable.ic_link
                AttachmentType.VIDEO_LOCAL -> R.drawable.ic_video
                AttachmentType.AUDIO_LOCAL -> R.drawable.ic_audio
            }
            imageViewAttachmentIcon.setImageResource(iconResId)
            imageViewAttachmentIcon.contentDescription = when (attachment.attachmentType) {
                AttachmentType.IMAGE_LOCAL -> context.getString(R.string.attachment_string_attachment_image_content_description)
                AttachmentType.DOCUMENT_LOCAL -> context.getString(R.string.attachment_string_attachment_document_content_description)
                AttachmentType.HYPERLINK -> context.getString(R.string.attachment_string_attachment_hyperlink_content_description)
                AttachmentType.VIDEO_LOCAL -> context.getString(R.string.attachment_string_attachment_video_content_description)
                AttachmentType.AUDIO_LOCAL -> context.getString(R.string.attachment_string_attachment_audio_content_description)
            }

            checkBox.isChecked = selectedAttachments.contains(attachment)
            checkBox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedAttachments.add(attachment)
                } else {
                    selectedAttachments.remove(attachment)
                }
            }
        }
    }

    private class AttachmentDiffCallback : DiffUtil.ItemCallback<Attachment>() {
        override fun areItemsTheSame(oldItem: Attachment, newItem: Attachment): Boolean {
            return oldItem.attachmentId == newItem.attachmentId
        }

        override fun areContentsTheSame(oldItem: Attachment, newItem: Attachment): Boolean {
            return oldItem == newItem // Data class equality
        }
    }
}
