package com.costheta.cortexa.engagement

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.costheta.cortexa.R
import com.costheta.cortexa.data.db.dao.AttachmentDao
import com.costheta.cortexa.data.models.Engagement
import kotlinx.coroutines.CoroutineScope

// FOR CALENDAR: Adapter now takes lambdas for the generic DisplayableItem, but also
// specific lambdas for actions that only apply to Engagements.
class EngagementAdapter(
    private val onDeleteClick: (DisplayableItem) -> Unit,
    private val onEditClick: (DisplayableItem) -> Unit,
    private val onCancelInstanceClick: (Engagement) -> Unit, // This now correctly expects an Engagement
    private val onRescheduleInstanceClick: (Engagement) -> Unit, // This now correctly expects an Engagement
    private val attachmentDao: AttachmentDao,
    private val coroutineScope: CoroutineScope
) : ListAdapter<DisplayableItem, EngagementViewHolder>(DisplayableItemDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EngagementViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.engagement_item, parent, false)
        return EngagementViewHolder(view)
    }

    override fun onBindViewHolder(holder: EngagementViewHolder, position: Int) {
        holder.bind(
            item = getItem(position),
            attachmentDao = attachmentDao,
            coroutineScope = coroutineScope,
            onDeleteClick = onDeleteClick,
            onEditClick = onEditClick,
            onCancelInstanceClick = onCancelInstanceClick,
            onRescheduleInstanceClick = onRescheduleInstanceClick
        )
    }

    class DisplayableItemDiffCallback : DiffUtil.ItemCallback<DisplayableItem>() {
        override fun areItemsTheSame(oldItem: DisplayableItem, newItem: DisplayableItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: DisplayableItem, newItem: DisplayableItem): Boolean {
            return oldItem == newItem
        }
    }
}

