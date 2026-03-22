package com.costheta.cortexa.util.subtask

import android.annotation.SuppressLint
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.costheta.cortexa.R
import com.costheta.cortexa.data.models.Subtask
import java.util.Collections

/**
 * RecyclerView Adapter for displaying a list of Subtask objects.
 * It now handles drag-and-drop reordering via a drag handle.
 *
 * @param onToggleComplete Lambda function called when the checkbox is toggled.
 * @param onDeleteClick Lambda function called when the delete button is clicked.
 * @param onStartDrag Lambda function to notify the ItemTouchHelper to start a drag operation.
 * @param onSubtasksReordered Lambda function to notify the SubtaskManager to persist the new order.
 */
class SubtaskAdapter(
    private val onToggleComplete: (Subtask, Boolean) -> Unit,
    private val onDeleteClick: (Subtask) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
    private val onSubtasksReordered: (List<Subtask>) -> Unit
) : ListAdapter<Subtask, SubtaskAdapter.SubtaskViewHolder>(SubtaskDiffCallback()),
    ItemMoveCallback.ItemTouchHelperContract {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubtaskViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.subtask_item, parent, false)
        return SubtaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: SubtaskViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SubtaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val checkBoxSubtask: CheckBox = itemView.findViewById(R.id.checkBoxSubtask)
        private val textViewSubtaskDescription: TextView = itemView.findViewById(R.id.textViewSubtaskDescription)
        private val buttonDeleteSubtask: ImageButton = itemView.findViewById(R.id.buttonDeleteSubtask)
        private val dragHandle: ImageView = itemView.findViewById(R.id.drag_handle)

        @SuppressLint("ClickableViewAccessibility")
        fun bind(subtask: Subtask) {
            textViewSubtaskDescription.text = subtask.description

            checkBoxSubtask.setOnCheckedChangeListener(null)
            checkBoxSubtask.isChecked = subtask.completionState
            checkBoxSubtask.setOnCheckedChangeListener { _, isChecked ->
                onToggleComplete(subtask, isChecked)
            }

            // Show/hide UI elements based on completion state
            if (subtask.completionState) {
                textViewSubtaskDescription.paintFlags = textViewSubtaskDescription.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                dragHandle.visibility = View.GONE // Can't reorder completed tasks
                buttonDeleteSubtask.visibility = View.GONE
            } else {
                textViewSubtaskDescription.paintFlags = textViewSubtaskDescription.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                dragHandle.visibility = View.VISIBLE
                buttonDeleteSubtask.visibility = View.VISIBLE

                // Start the drag operation when the handle is touched
                dragHandle.setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        onStartDrag(this)
                    }
                    false
                }
            }
            buttonDeleteSubtask.setOnClickListener { onDeleteClick(subtask) }
        }
    }

    override fun onRowMoved(fromPosition: Int, toPosition: Int) {
        val mutableList = currentList.toMutableList()
        Collections.swap(mutableList, fromPosition, toPosition)
        // Update the adapter's internal list to reflect the move.
        // The 'null' payload prevents a full re-bind, allowing for a smooth animation.
        submitList(mutableList, null)
    }

    override fun onRowClear() {
        // When the drag is finished, send the newly ordered list to the manager to save it.
        onSubtasksReordered(currentList)
    }

    private class SubtaskDiffCallback : DiffUtil.ItemCallback<Subtask>() {
        override fun areItemsTheSame(oldItem: Subtask, newItem: Subtask): Boolean {
            return oldItem.subtaskId == newItem.subtaskId
        }

        override fun areContentsTheSame(oldItem: Subtask, newItem: Subtask): Boolean {
            // We only care about content changes, not position, for DiffUtil.
            // This prevents flickering during reordering.
            return oldItem.description == newItem.description &&
                    oldItem.completionState == newItem.completionState
        }
    }
}
