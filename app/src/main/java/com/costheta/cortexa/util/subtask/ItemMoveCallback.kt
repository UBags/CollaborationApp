package com.costheta.cortexa.util.subtask

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

/**
 * Manages drag-and-drop gestures for the RecyclerView.
 * It communicates with the adapter via the ItemTouchHelperContract interface.
 *
 * @param adapter The adapter that implements the contract to respond to move events.
 */
class ItemMoveCallback(private val adapter: ItemTouchHelperContract) : ItemTouchHelper.Callback() {

    /**
     * Interface to notify an adapter of drag-and-drop events.
     */
    interface ItemTouchHelperContract {
        fun onRowMoved(fromPosition: Int, toPosition: Int)
        fun onRowClear()
    }

    override fun isLongPressDragEnabled(): Boolean {
        // We use a custom drag handle, so we disable the default long-press-to-drag.
        return false
    }

    override fun isItemViewSwipeEnabled(): Boolean {
        // Swiping is not needed for this feature.
        return false
    }

    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        val adapter = recyclerView.adapter as SubtaskAdapter
        val subtask = adapter.currentList.getOrNull(viewHolder.absoluteAdapterPosition)

        // Only allow dragging for incomplete subtasks. Completed tasks are locked.
        val dragFlags = if (subtask != null && !subtask.completionState) {
            ItemTouchHelper.UP or ItemTouchHelper.DOWN
        } else {
            0
        }
        return makeMovementFlags(dragFlags, 0)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        // Notify the adapter that an item has been moved.
        adapter.onRowMoved(viewHolder.absoluteAdapterPosition, target.absoluteAdapterPosition)
        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, i: Int) {
        // Not implemented.
    }

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        // Provide visual feedback when an item is being dragged.
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
            viewHolder?.itemView?.alpha = 0.7f
        }
        super.onSelectedChanged(viewHolder, actionState)
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        // Restore the item's appearance and notify the adapter that the drag is complete.
        viewHolder.itemView.alpha = 1.0f
        adapter.onRowClear()
    }
}
