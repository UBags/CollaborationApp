package com.costheta.cortexa.todo

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.costheta.cortexa.R
import com.costheta.cortexa.data.db.dao.AttachmentDao
import kotlinx.coroutines.CoroutineScope

// MODIFIED: Adapter now works with DisplayableTask
class TodoAdapter(
    private val onDeleteClick: (DisplayableTask) -> Unit,
    private val onEditClick: (DisplayableTask) -> Unit,
    private val onToggleComplete: (DisplayableTask, Boolean) -> Unit,
    private val attachmentDao: AttachmentDao,
    private val coroutineScope: CoroutineScope
) : ListAdapter<DisplayableTask, TodoViewHolder>(TodoDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TodoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.todo_item, parent, false)
        return TodoViewHolder(view)
    }

    override fun onBindViewHolder(holder: TodoViewHolder, position: Int) {
        holder.bind(
            // MODIFIED: Pass the DisplayableTask item
            displayableTask = getItem(position),
            attachmentDao = attachmentDao,
            coroutineScope = coroutineScope,
            onDeleteClick = onDeleteClick,
            onEditClick = onEditClick,
            onToggleComplete = onToggleComplete
        )
    }

    // MODIFIED: DiffUtil now compares DisplayableTask objects
    class TodoDiffCallback : DiffUtil.ItemCallback<DisplayableTask>() {
        override fun areItemsTheSame(oldItem: DisplayableTask, newItem: DisplayableTask): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: DisplayableTask, newItem: DisplayableTask): Boolean {
            // Compare content. For data classes, `==` works well.
            return oldItem == newItem
        }
    }
}
