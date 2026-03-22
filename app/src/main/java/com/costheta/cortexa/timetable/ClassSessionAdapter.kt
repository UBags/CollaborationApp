package com.costheta.cortexa.timetable

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.costheta.cortexa.R
import com.costheta.cortexa.data.db.dao.TimetableDao
import com.costheta.cortexa.data.models.ClassSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * RecyclerView Adapter for displaying a list of ClassSession objects.
 * It uses DiffUtil for efficient list updates.
 *
 * @param timetableDao The DAO to access timetable data for getting parent timetable info.
 * @param coroutineScope The coroutine scope to use for background database operations.
 * @param onNotification1Click Lambda function for when the first notification time is clicked.
 * @param onNotification2Click Lambda function for when the second notification time is clicked.
 * @param onSilenceToggleClick Lambda function for when the silence checkbox is toggled.
 * Receives the ClassSession and the new checked state.
 */
class ClassSessionAdapter(
    private val timetableDao: TimetableDao,
    private val coroutineScope: LifecycleCoroutineScope,
    private val onNotification1Click: (ClassSession) -> Unit,
    private val onNotification2Click: (ClassSession) -> Unit,
    private val onSilenceToggleClick: (ClassSession, Boolean) -> Unit
) : ListAdapter<ClassSession, ClassSessionViewHolder>(ClassSessionDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClassSessionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.timetable_class_session_item, parent, false)
        // Instantiate the external ClassSessionViewHolder here.
        return ClassSessionViewHolder(
            view,
            parent.context,
            { session, notifNum, offset ->
                if (notifNum == 1) {
                    onNotification1Click(session)
                } else {
                    onNotification2Click(session)
                }
            },
            onSilenceToggleClick
        )
    }

    override fun onBindViewHolder(holder: ClassSessionViewHolder, position: Int) {
        val session = getItem(position)
        coroutineScope.launch {
            val timetable = withContext(Dispatchers.IO) { session.timetableId?.let {
                timetableDao.getTimetableById(
                    it
                )
            } }
            // The holder is already of type ClassSessionViewHolder, so no cast is needed.
            if (timetable != null) {
                holder.bind(session, timetable)
            }
        }
    }

    class ClassSessionDiffCallback : DiffUtil.ItemCallback<ClassSession>() {
        override fun areItemsTheSame(oldItem: ClassSession, newItem: ClassSession): Boolean {
            return oldItem.sessionId == newItem.sessionId
        }

        override fun areContentsTheSame(oldItem: ClassSession, newItem: ClassSession): Boolean {
            return oldItem == newItem
        }
    }
}
