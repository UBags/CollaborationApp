package com.costheta.cortexa.engagement

import android.text.Html
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.costheta.cortexa.R
import com.costheta.cortexa.data.db.dao.AttachmentDao
import com.costheta.cortexa.data.models.Engagement
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

// FOR CALENDAR: ViewHolder now accepts specific lambdas for Engagement-only actions
class EngagementViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    // UI Views
    private val engagementCardView: CardView = itemView.findViewById(R.id.engagementCardView)
    private val engagementNameTextView: TextView = itemView.findViewById(R.id.engagementNameTextView)
    private val notesTextView: TextView = itemView.findViewById(R.id.notesTextView)
    private val dateTimeInfoTextView: TextView = itemView.findViewById(R.id.dateTimeInfoTextView)
    private val venueTextView: TextView = itemView.findViewById(R.id.venueTextView)
    private val notificationInfoTextView: TextView = itemView.findViewById(R.id.notificationInfoTextView)
    private val attachmentIconImageView: ImageView = itemView.findViewById(R.id.attachmentIconImageView)

    // Action buttons
    private val deleteButton: ImageButton = itemView.findViewById(R.id.deleteEngagementButton)
    private val editButton: ImageButton = itemView.findViewById(R.id.editEngagementButton)
    private val cancelInstanceButton: ImageButton = itemView.findViewById(R.id.cancelInstanceButton)
    private val rescheduleInstanceButton: ImageButton = itemView.findViewById(R.id.rescheduleInstanceButton)

    // Formatters
    private val dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

    fun bind(
        item: DisplayableItem,
        attachmentDao: AttachmentDao,
        coroutineScope: CoroutineScope,
        onDeleteClick: (DisplayableItem) -> Unit,
        onEditClick: (DisplayableItem) -> Unit,
        onCancelInstanceClick: (Engagement) -> Unit,
        onRescheduleInstanceClick: (Engagement) -> Unit
    ) {
        // FOR CALENDAR: Use a 'when' statement to handle each type of DisplayableItem differently
        when (item) {
            is DisplayableItem.AppEngagement -> bindAppEngagement(item, attachmentDao, coroutineScope, onDeleteClick, onEditClick, onCancelInstanceClick, onRescheduleInstanceClick)
            is DisplayableItem.CalendarEvent -> bindCalendarEvent(item, attachmentDao, coroutineScope, onEditClick)
        }
    }

    /**
     * Binds data for a native app Engagement.
     */
    private fun bindAppEngagement(
        engagementItem: DisplayableItem.AppEngagement,
        attachmentDao: AttachmentDao,
        coroutineScope: CoroutineScope,
        onDeleteClick: (DisplayableItem) -> Unit,
        onEditClick: (DisplayableItem) -> Unit,
        onCancelInstanceClick: (Engagement) -> Unit,
        onRescheduleInstanceClick: (Engagement) -> Unit
    ) {
        val engagement = engagementItem.engagement
        val context = itemView.context

        // --- Set Text and Visibility ---
        engagementNameTextView.text = engagement.engagementName

        val notesText = engagement.notes
        notesTextView.visibility = if (notesText.isNullOrBlank()) View.GONE else View.VISIBLE
        notesTextView.text = Html.fromHtml("<b>${context.getString(R.string.label_notes_bold)}</b> $notesText", Html.FROM_HTML_MODE_COMPACT)

        val venueText = engagement.venue
        venueTextView.visibility = if (venueText.isNullOrBlank()) View.GONE else View.VISIBLE
        venueTextView.text = Html.fromHtml("<b>${context.getString(R.string.label_venue_bold)}</b> $venueText", Html.FROM_HTML_MODE_COMPACT)

        val systemZoneId = ZoneId.systemDefault()
        val nextOccurrenceDate = engagement.nextOccurrenceDate
        val endTime = engagement.startTime.plusMinutes(engagement.durationMinutes.toLong())
        val timeText = context.getString(
            R.string.engagement_time_format_with_timezone,
            engagement.startTime.format(timeFormatter),
            endTime.format(timeFormatter),
            systemZoneId.id
        )
        val dateText = nextOccurrenceDate?.format(dateFormatter) ?: "N/A"
        dateTimeInfoTextView.text = Html.fromHtml("<b>${dateText}</b><br/>$timeText", Html.FROM_HTML_MODE_LEGACY)

        val notif1 = formatMinutesToHoursMinutes(engagement.notification1Minutes)
        val notif2 = formatMinutesToHoursMinutes(engagement.notification2Minutes)
        val notif3 = formatMinutesToHoursMinutes(engagement.notification3Minutes)
        val notificationText = if (!engagement.isSilenced) "$notif1 | $notif2 | $notif3" else "<font color='red'><b>Off</b></font>"
        notificationInfoTextView.text = Html.fromHtml("<b>${context.getString(R.string.label_notification_bold)}</b> $notificationText", Html.FROM_HTML_MODE_COMPACT)
        notificationInfoTextView.visibility = View.VISIBLE

        // --- Set Action Buttons and Listeners ---
        // FOR CALENDAR: For engagements, all buttons are visible
        deleteButton.visibility = View.VISIBLE
        editButton.visibility = View.VISIBLE
        cancelInstanceButton.visibility = View.VISIBLE
        rescheduleInstanceButton.visibility = View.VISIBLE

        deleteButton.setOnClickListener { onDeleteClick(engagementItem) }
        editButton.setOnClickListener { onEditClick(engagementItem) }
        cancelInstanceButton.setOnClickListener { onCancelInstanceClick(engagement) }
        rescheduleInstanceButton.setOnClickListener { onRescheduleInstanceClick(engagement) }

        // --- Attachment Icon ---
        engagement.engagementId?.let { id ->
            coroutineScope.launch {
                val attachmentCount = withContext(Dispatchers.IO) {
                    attachmentDao.getAttachmentCountForEvent("Engagement", id)
                }
                withContext(Dispatchers.Main) {
                    attachmentIconImageView.visibility = if (attachmentCount > 0) View.VISIBLE else View.GONE
                }
            }
        } ?: run {
            attachmentIconImageView.visibility = View.GONE
        }

        // --- Card Coloring ---
        val (cardBackgroundColor, textColor) = CardColor.determineEngagementCardColors(engagement, context)
        setCardColors(cardBackgroundColor, textColor)
    }

    /**
     * Binds data for a synced Google Calendar event.
     */
    private fun bindCalendarEvent(
        calendarEventItem: DisplayableItem.CalendarEvent,
        attachmentDao: AttachmentDao,
        coroutineScope: CoroutineScope,
        onEditClick: (DisplayableItem) -> Unit
    ) {
        val event = calendarEventItem.event
        val context = itemView.context
        val systemZoneId = ZoneId.systemDefault()

        // --- Set Text and Visibility ---
        engagementNameTextView.text = event.title

        val descriptionText = event.description
        notesTextView.visibility = if (descriptionText.isNullOrBlank()) View.GONE else View.VISIBLE
        notesTextView.text = Html.fromHtml("<b>${context.getString(R.string.label_description_bold)}</b> $descriptionText", Html.FROM_HTML_MODE_COMPACT)

        val locationText = event.location
        venueTextView.visibility = if (locationText.isNullOrBlank()) View.GONE else View.VISIBLE
        venueTextView.text = Html.fromHtml("<b>${context.getString(R.string.label_location_bold)}</b> $locationText", Html.FROM_HTML_MODE_COMPACT)

        val startDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(event.startDateTimeMillis), systemZoneId)
        val endDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(event.endDateTimeMillis), systemZoneId)
        val timeText = context.getString(
            R.string.engagement_time_format_with_timezone,
            startDateTime.toLocalTime().format(timeFormatter),
            endDateTime.toLocalTime().format(timeFormatter),
            systemZoneId.id
        )
        val dateText = startDateTime.toLocalDate().format(dateFormatter)
        dateTimeInfoTextView.text = Html.fromHtml("<b>${dateText}</b><br/>$timeText", Html.FROM_HTML_MODE_LEGACY)

        // FOR CALENDAR: Calendar events don't have our app's notification settings, so hide this view
        notificationInfoTextView.visibility = View.GONE

        // --- Set Action Buttons and Listeners ---
        // FOR CALENDAR: For calendar events, only the edit button is visible.
        editButton.visibility = View.VISIBLE
        deleteButton.visibility = View.GONE
        cancelInstanceButton.visibility = View.GONE
        rescheduleInstanceButton.visibility = View.GONE

        editButton.setOnClickListener { onEditClick(calendarEventItem) }
        // The other buttons are hidden, so no listeners are needed.

        // --- Attachment Icon ---
        coroutineScope.launch {
            val attachmentCount = withContext(Dispatchers.IO) {
                // We use a new eventType for attachments/subtasks linked to calendar events
                attachmentDao.getAttachmentCountForEvent("SyncedCalendarEvent", event.localId)
            }
            withContext(Dispatchers.Main) {
                attachmentIconImageView.visibility = if (attachmentCount > 0) View.VISIBLE else View.GONE
            }
        }

        // --- Card Coloring ---
        val (cardBackgroundColor, textColor) = CardColor.determineCalendarCardColors(event, context)
        setCardColors(cardBackgroundColor, textColor)
    }

    /**
     * Helper to set colors for all text views in the card.
     */
    private fun setCardColors(backgroundColor: Int, textColor: Int) {
        engagementCardView.setCardBackgroundColor(backgroundColor)
        engagementNameTextView.setTextColor(textColor)
        notesTextView.setTextColor(textColor)
        venueTextView.setTextColor(textColor)
        dateTimeInfoTextView.setTextColor(textColor)
        notificationInfoTextView.setTextColor(textColor)
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

