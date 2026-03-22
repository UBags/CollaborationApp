package com.costheta.cortexa.assignment

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.costheta.cortexa.R
import com.costheta.cortexa.data.db.dao.AttachmentDao
import com.costheta.cortexa.data.models.Assignment
import kotlinx.coroutines.CoroutineScope

/**
 * RecyclerView Adapter for displaying a list of Assignment objects.
 * It uses the external AssignmentViewHolder for its views.
 */
class AssignmentAdapter(
    private val onDeleteClick: (Assignment) -> Unit,
    private val onEditClick: (Assignment) -> Unit,
    private val onDoneClick: (Assignment, Boolean) -> Unit,
    private val attachmentDao: AttachmentDao,
    private val coroutineScope: CoroutineScope
) : ListAdapter<Assignment, AssignmentViewHolder>(AssignmentDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AssignmentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.assignment_item, parent, false)
        return AssignmentViewHolder(view)
    }

    override fun onBindViewHolder(holder: AssignmentViewHolder, position: Int) {
        holder.bind(
            assignment = getItem(position),
            attachmentDao = attachmentDao,
            coroutineScope = coroutineScope,
            onDeleteClick = onDeleteClick,
            onEditClick = onEditClick,
            onDoneClick = onDoneClick,
            showActionButtons = true
        )
    }

    class AssignmentDiffCallback : DiffUtil.ItemCallback<Assignment>() {
        override fun areItemsTheSame(oldItem: Assignment, newItem: Assignment): Boolean {
            return oldItem.assignmentId == newItem.assignmentId
        }

        override fun areContentsTheSame(oldItem: Assignment, newItem: Assignment): Boolean {
            return oldItem == newItem
        }
    }
}
