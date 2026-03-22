package com.costheta.cortexa.exam

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.costheta.cortexa.data.db.dao.AttachmentDao
import com.costheta.cortexa.data.models.Exam
import kotlinx.coroutines.CoroutineScope
import com.costheta.cortexa.R

/**
 * RecyclerView Adapter for displaying a list of Exam objects.
 * It uses DiffUtil for efficient list updates.
 */
class ExamAdapter(
    private val onDeleteClick: (Exam) -> Unit,
    private val onEditClick: (Exam) -> Unit,
    private val attachmentDao: AttachmentDao, // Pass AttachmentDao
    private val coroutineScope: CoroutineScope // Pass CoroutineScope
) : ListAdapter<Exam, ExamViewHolder>(ExamDiffCallback()) { // Changed to Exam

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExamViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.exam_item, parent, false)
        return ExamViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExamViewHolder, position: Int) {
        holder.bind(
            exam = getItem(position),
            attachmentDao = attachmentDao,
            coroutineScope = coroutineScope,
            onDeleteClick = onDeleteClick,
            onEditClick = onEditClick
        )
    }

    class ExamDiffCallback : DiffUtil.ItemCallback<Exam>() { // Changed to Exam
        override fun areItemsTheSame(oldItem: Exam, newItem: Exam): Boolean { // Changed to Exam
            return oldItem.examId == newItem.examId
        }

        override fun areContentsTheSame(oldItem: Exam, newItem: Exam): Boolean { // Changed to Exam
            // Compare all relevant fields to determine if content has changed
            return oldItem == newItem // Exam is a data class, so direct comparison works
        }
    }
}
