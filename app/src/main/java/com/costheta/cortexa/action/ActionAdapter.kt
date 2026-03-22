package com.costheta.cortexa.action

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.costheta.cortexa.R
import com.costheta.cortexa.data.db.dao.AttachmentDao
import com.costheta.cortexa.data.models.Action
import kotlinx.coroutines.CoroutineScope

class ActionAdapter(
    private val onDeleteClick: (Action) -> Unit,
    private val onEditClick: (Action) -> Unit,
    private val onDoneClick: (Action, Boolean) -> Unit,
    private val attachmentDao: AttachmentDao,
    private val coroutineScope: CoroutineScope
) : ListAdapter<Action, ActionViewHolder>(ActionDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.action_item, parent, false)
        return ActionViewHolder(view)
    }

    override fun onBindViewHolder(holder: ActionViewHolder, position: Int) {
        holder.bind(
            actionItem = getItem(position),
            attachmentDao = attachmentDao,
            coroutineScope = coroutineScope,
            onDeleteClick = onDeleteClick,
            onEditClick = onEditClick,
            onDoneClick = onDoneClick
        )
    }

    class ActionDiffCallback : DiffUtil.ItemCallback<Action>() {
        override fun areItemsTheSame(oldItem: Action, newItem: Action): Boolean {
            return oldItem.actionId == newItem.actionId
        }

        override fun areContentsTheSame(oldItem: Action, newItem: Action): Boolean {
            return oldItem == newItem
        }
    }
}
