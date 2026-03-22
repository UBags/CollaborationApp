package com.costheta.cortexa.util.subtask

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.costheta.cortexa.R
import com.costheta.cortexa.data.models.Subtask

class SubtaskCopyAdapter : ListAdapter<Subtask, SubtaskCopyAdapter.SubtaskViewHolder>(SubtaskDiffCallback()) {

    private val selectedSubtasks = mutableSetOf<Subtask>()

    init {
        // Initialize with all subtasks selected by default
        setHasStableIds(true)
    }

    override fun onCurrentListChanged(
        previousList: MutableList<Subtask>,
        currentList: MutableList<Subtask>,
    ) {
        // Update selectedSubtasks when the list changes, selecting all by default
        selectedSubtasks.clear()
        selectedSubtasks.addAll(currentList)
        notifyDataSetChanged()
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).subtaskId
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubtaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.subtask_copy_item, parent, false)
        return SubtaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: SubtaskViewHolder, position: Int) {
        val subtask = getItem(position)
        holder.bind(subtask)
    }

    fun getSelectedSubtasks(): List<Subtask> {
        return selectedSubtasks.toList()
    }

    inner class SubtaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textViewDescription: TextView = itemView.findViewById(R.id.textViewSubtaskDescription)
        private val checkBox: CheckBox = itemView.findViewById(R.id.checkBoxSubtask)

        fun bind(subtask: Subtask) {
            textViewDescription.text = subtask.description
            checkBox.isChecked = selectedSubtasks.contains(subtask)
            checkBox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedSubtasks.add(subtask)
                } else {
                    selectedSubtasks.remove(subtask)
                }
            }
        }
    }

    class SubtaskDiffCallback : DiffUtil.ItemCallback<Subtask>() {
        override fun areItemsTheSame(oldItem: Subtask, newItem: Subtask): Boolean {
            return oldItem.subtaskId == newItem.subtaskId
        }

        override fun areContentsTheSame(oldItem: Subtask, newItem: Subtask): Boolean {
            return oldItem == newItem
        }
    }
}