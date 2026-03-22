package com.costheta.cortexa.timetable

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.costheta.cortexa.data.models.Timetable
import com.costheta.cortexa.data.models.TimetableType
import java.time.format.DateTimeFormatter
import java.util.Locale
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import com.costheta.cortexa.R

/**
 * RecyclerView Adapter for displaying a combined list of Timetable and Engagement objects.
 * It uses DiffUtil for efficient list updates and supports multiple view types.
 *
 * @param onDeleteClick Lambda function to be called when the delete button is clicked.
 * Receives the item (Timetable or Engagement) to be deleted.
 * @param onEditClick Lambda function to be called when the edit button is clicked.
 * Receives the item (Timetable or Engagement).
 * @param onTimetableClick Lambda function to be called when a Timetable item's main area is clicked.
 * Receives the Timetable object.
 */
class TimetableAdapter(
    private val onDeleteClick: (Any) -> Unit,
    private val onEditClick: (Any) -> Unit,
    private val onTimetableClick: (Timetable) -> Unit // New lambda for timetable item clicks
) : ListAdapter<Any, RecyclerView.ViewHolder>(DiffCallback()) {

    // Constants for view types
    private val TYPE_TIMETABLE = 0
    private val TYPE_ENGAGEMENT = 4

    // Date and Time formatters
    private val dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.getDefault())
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

    /**
     * Base ViewHolder for all item types.
     * Subclasses will implement specific binding logic.
     */
    abstract class BaseViewHolder<T>(itemView: View) : RecyclerView.ViewHolder(itemView) {
        abstract fun bind(item: T)
    }

    /**
     * ViewHolder for Timetable items.
     * Uses item_timetable_entry.xml
     */
    inner class TimetableItemViewHolder(itemView: View) : BaseViewHolder<Timetable>(itemView) {
        private val timetableHeaderBar: LinearLayout = itemView.findViewById(R.id.timetableHeaderBar)
        private val timetableHeaderTextView: TextView = itemView.findViewById(R.id.timetableHeaderTextView)
        private val notificationTimesTextView: TextView = itemView.findViewById(R.id.notificationTimesTextView)

        // Get a reference to the contentLayout first, as many views are nested inside it
        private val contentLayout: ConstraintLayout = itemView.findViewById(R.id.contentLayout)

        // Views that are inside contentLayout and specific to Timetable
        private val entryTitleTextView: TextView = contentLayout.findViewById(R.id.entryTitleTextView)
        private val entryTypeTextView: TextView = contentLayout.findViewById(R.id.entryTypeTextView)
        private val validityDatesTextView: TextView = contentLayout.findViewById(R.id.validityDatesTextView)
        private val deleteButton: ImageButton = contentLayout.findViewById(R.id.deleteButton)
        private val editButton: ImageButton = contentLayout.findViewById(R.id.editButton)


        override fun bind(item: Timetable) {
            // Ensure only relevant views are visible for Timetable type
            timetableHeaderBar.visibility = View.VISIBLE
            timetableHeaderTextView.visibility = View.VISIBLE
            entryTitleTextView.visibility = View.VISIBLE
            entryTypeTextView.visibility = View.VISIBLE
            validityDatesTextView.visibility = View.VISIBLE
            deleteButton.visibility = View.VISIBLE // Timetables can be archived/deleted
            editButton.visibility = View.VISIBLE // Make edit button visible for Timetable items

            timetableHeaderTextView.text = if (item.type == TimetableType.LECTURE) {
                itemView.context.getString(R.string.timetable_header_text)
            } else {
                itemView.context.getString(R.string.personal_timetable_header_text)
            }
            entryTitleTextView.text = item.name.replaceFirst(Regex("_\\d{4}_\\d{2}_\\d{2}_\\d{2}_\\d{2}_\\d{2}"), "")
            entryTypeTextView.text = item.type.toString().replace("_", " ")
            validityDatesTextView.text = itemView.context.getString(
                R.string.validity_dates_format,
                item.validityStartDate.format(dateFormatter),
                item.validityEndDate.format(dateFormatter)
            )
            notificationTimesTextView.text = itemView.context.getString(
                R.string.notification_times_format,
                item.defaultNotificationOffsetMinutes1,
                item.defaultNotificationOffsetMinutes2
            )

            editButton.setOnClickListener { onEditClick(item) }
            deleteButton.setOnClickListener { onDeleteClick(item) }
            itemView.setOnClickListener { onTimetableClick(item) }
        }
    }

    /**
     * Determines the view type for a given position.
     * This is crucial for the RecyclerView to know which layout to inflate.
     */
    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is Timetable -> TYPE_TIMETABLE
            else -> throw IllegalArgumentException("Unknown item type at position $position")
        }
    }

    /**
     * Creates and returns the appropriate ViewHolder based on the viewType.
     */
    // Suppress unchecked cast warning for BaseViewHolder<Any>
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_TIMETABLE -> TimetableItemViewHolder(layoutInflater.inflate(R.layout.timetable_entry_item, parent, false))
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    /**
     * Binds the data to the ViewHolder.
     */
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is TimetableItemViewHolder -> holder.bind(item as Timetable)
            else -> throw IllegalArgumentException("Invalid ViewHolder type for item at position $position")
        }
    }

    /**
     * DiffCallback for efficiently updating the RecyclerView when the list changes.
     */
    class DiffCallback : DiffUtil.ItemCallback<Any>() {
        override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {
            return when {
                oldItem is Timetable && newItem is Timetable -> oldItem.id == newItem.id
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
            return when {
                oldItem is Timetable && newItem is Timetable -> oldItem == newItem
                else -> false
            }
        }
    }
}
