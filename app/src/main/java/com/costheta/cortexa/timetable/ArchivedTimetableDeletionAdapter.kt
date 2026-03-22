package com.costheta.cortexa.timetable

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.costheta.cortexa.R
import com.costheta.cortexa.data.models.Timetable

class ArchivedTimetableDeletionAdapter(private val timetables: List<Timetable>) :
    RecyclerView.Adapter<ArchivedTimetableDeletionAdapter.ViewHolder>() {

    private val selectedTimetables = mutableSetOf<Timetable>()

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val timetableNameTextView: TextView = itemView.findViewById(R.id.timetableNameTextView)
        val timetableTypeTextView: TextView = itemView.findViewById(R.id.timetableTypeTextView) // NEW: TextView for timetable type
        val checkBox: CheckBox = itemView.findViewById(R.id.timetableCheckBox)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.timetable_archived_deletion_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val timetable = timetables[position]
        holder.timetableNameTextView.text = timetable.name
        // NEW: Set the timetable type text, replacing underscores with spaces for readability
        // For Kotlin enums, toString() typically returns the enum entry's name.
        holder.timetableTypeTextView.text = timetable.type.toString().replace("_", " ")
        holder.checkBox.isChecked = selectedTimetables.contains(timetable)

        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedTimetables.add(timetable)
            } else {
                selectedTimetables.remove(timetable)
            }
        }
    }

    override fun getItemCount(): Int {
        return timetables.size
    }

    fun getSelectedTimetables(): List<Timetable> {
        return selectedTimetables.toList()
    }
}
