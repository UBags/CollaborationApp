package com.costheta.cortexa.note

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.costheta.cortexa.R
import com.costheta.cortexa.data.db.dao.AttachmentDao
import com.costheta.cortexa.data.models.NotesItem
import kotlinx.coroutines.CoroutineScope

class NotesAdapter(
    private val onDeleteClick: (NotesItem) -> Unit,
    private val onEditClick: (NotesItem) -> Unit,
    private val onArchiveClick: (NotesItem, Boolean) -> Unit,
    private val attachmentDao: AttachmentDao,
    private val coroutineScope: CoroutineScope
) : ListAdapter<NotesItem, NoteViewHolder>(NotesDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.note_item, parent, false)
        return NoteViewHolder(view)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        holder.bind(
            noteItem = getItem(position),
            attachmentDao = attachmentDao,
            coroutineScope = coroutineScope,
            onDeleteClick = onDeleteClick,
            onEditClick = onEditClick,
            onArchiveClick = onArchiveClick
        )
    }

    class NotesDiffCallback : DiffUtil.ItemCallback<NotesItem>() {
        override fun areItemsTheSame(oldItem: NotesItem, newItem: NotesItem): Boolean {
            return oldItem.noteId == newItem.noteId
        }

        override fun areContentsTheSame(oldItem: NotesItem, newItem: NotesItem): Boolean {
            return oldItem == newItem
        }
    }
}
