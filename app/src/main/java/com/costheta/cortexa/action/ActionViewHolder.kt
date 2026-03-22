package com.costheta.cortexa.action

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action


import android.text.Html
import android.view.View
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.costheta.cortexa.R
import com.costheta.cortexa.data.db.dao.AttachmentDao
import com.costheta.cortexa.data.models.Action
import com.costheta.cortexa.util.ui.CardColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class ActionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val actionCardView: CardView = itemView.findViewById(R.id.actionCardView)
    private val actionNameTextView: TextView = itemView.findViewById(R.id.actionNameTextView)
    private val descriptionTextView: TextView = itemView.findViewById(R.id.descriptionTextView)
    private val dateTimeInfoTextView: TextView = itemView.findViewById(R.id.dateTimeInfoTextView)
    private val venueTextView: TextView = itemView.findViewById(R.id.venueTextView)
    private val notificationInfoTextView: TextView = itemView.findViewById(R.id.notificationInfoTextView)
    private val doneActionCheckBox: CheckBox = itemView.findViewById(R.id.doneActionCheckBox)
    private val deleteButton: ImageButton = itemView.findViewById(R.id.deleteActionButton)
    private val editButton: ImageButton = itemView.findViewById(R.id.editActionButton)
    private val attachmentIconImageView: ImageView = itemView.findViewById(R.id.attachmentIconImageView)

    private val dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
    private val datetimeFormatter = DateTimeFormatter.ofPattern("HH:mm dd-MMM-yyyy", Locale.ENGLISH)

    fun bind(
        actionItem: Action,
        attachmentDao: AttachmentDao,
        coroutineScope: CoroutineScope,
        onDeleteClick: (Action) -> Unit,
        onEditClick: (Action) -> Unit,
        onDoneClick: (Action, Boolean) -> Unit
    ) {
        actionNameTextView.text = actionItem.actionName

        val descriptionText = actionItem.description ?: itemView.context.getString(R.string.no_description_provided)
        descriptionTextView.text = Html.fromHtml("<b>${itemView.context.getString(R.string.label_description_bold)}</b> $descriptionText", Html.FROM_HTML_MODE_COMPACT)
        descriptionTextView.visibility = View.VISIBLE

        val venueText = actionItem.venue ?: itemView.context.getString(R.string.no_description_provided)
        venueTextView.text = Html.fromHtml("<b>${itemView.context.getString(R.string.label_venue_bold)}</b> $venueText", Html.FROM_HTML_MODE_COMPACT)
        venueTextView.visibility = if (venueText.isBlank() || venueText == itemView.context.getString(R.string.default_location)) View.GONE else View.VISIBLE

        val systemZoneId = ZoneId.systemDefault()
        val startDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(actionItem.startDateTimeMillis), systemZoneId)
        val endDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(actionItem.endDateTimeMillis), systemZoneId)
        val durationText = itemView.context.getString(
            R.string.action_duration_format,
            startDateTime.format(dateFormatter),
            startDateTime.format(timeFormatter),
            endDateTime.format(dateFormatter),
            endDateTime.format(timeFormatter),
            systemZoneId.id
        )

        var cardVisibleTillString = ""
        if (actionItem.isDone) {
            val displayUntilTime = endDateTime.plusHours(1).format(datetimeFormatter)
            cardVisibleTillString = itemView.context.getString(R.string.card_visible_till, displayUntilTime)
            dateTimeInfoTextView.text = Html.fromHtml("<b>${itemView.context.getString(R.string.label_duration_bold)}</b> $durationText<br>$cardVisibleTillString", Html.FROM_HTML_MODE_LEGACY)
        } else {
            dateTimeInfoTextView.text = Html.fromHtml("<b>${itemView.context.getString(R.string.label_duration_bold)}</b> $durationText", Html.FROM_HTML_MODE_LEGACY)
        }

        val notif1 = actionItem.notificationMinutes1?.let { formatMinutesToHoursMinutes(it) } ?: itemView.context.getString(R.string.not_set_short)
        val notif2 = actionItem.notificationMinutes2?.let { formatMinutesToHoursMinutes(it) } ?: itemView.context.getString(R.string.not_set_short)
        val notif3 = actionItem.notificationMinutes3?.let { formatMinutesToHoursMinutes(it) } ?: itemView.context.getString(R.string.not_set_short)

        val notificationText = if (!actionItem.silenceNotifications) {
            "$notif1 | $notif2 | $notif3"
        } else {
            "<font color='red'><b>Off</b></font>"
        }
        notificationInfoTextView.text = Html.fromHtml("<b>${itemView.context.getString(R.string.label_notification_bold)}</b> $notificationText", Html.FROM_HTML_MODE_COMPACT)
        notificationInfoTextView.visibility = if (actionItem.isDone) View.GONE else View.VISIBLE

        deleteButton.setOnClickListener { onDeleteClick(actionItem) }
        editButton.setOnClickListener { onEditClick(actionItem) }
        doneActionCheckBox.setOnCheckedChangeListener(null)
        doneActionCheckBox.isChecked = actionItem.isDone
        doneActionCheckBox.setOnCheckedChangeListener { _, isChecked -> onDoneClick(actionItem, isChecked) }

        actionItem.actionId?.let { id ->
            coroutineScope.launch {
                val attachmentCount = withContext(Dispatchers.IO) {
                    attachmentDao.getAttachmentCountForEvent("Action", id)
                }
                withContext(Dispatchers.Main) {
                    attachmentIconImageView.visibility = if (attachmentCount > 0) View.VISIBLE else View.GONE
                }
            }
        } ?: run {
            attachmentIconImageView.visibility = View.GONE
        }

        val (cardBackgroundColor, textColor) = CardColor.determineActionCardColors(actionItem, itemView.context)
        actionCardView.setCardBackgroundColor(cardBackgroundColor)
        actionNameTextView.setTextColor(textColor)
        descriptionTextView.setTextColor(textColor)
        venueTextView.setTextColor(textColor)
        dateTimeInfoTextView.setTextColor(textColor)
        notificationInfoTextView.setTextColor(textColor)
        doneActionCheckBox.setTextColor(textColor)
    }

    private fun formatMinutesToHoursMinutes(totalMinutes: Int): String {
        if (totalMinutes < 0) return ""
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            minutes > 0 -> "${minutes}m"
            else -> "0m"
        }
    }
}
