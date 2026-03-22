package com.costheta.cortexa.timetable

import android.content.Context
import android.view.View
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.costheta.cortexa.R
import com.costheta.cortexa.data.models.ClassSession
import com.costheta.cortexa.data.models.Timetable
import java.time.format.DateTimeFormatter
import java.util.Locale
// NEW - Import classes for timezone handling
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class ClassSessionViewHolder(
    private val itemView: View,
    private val context: Context,
    private val onNotif1Click: (ClassSession, Int, Int) -> Unit,
    private val onSilenceToggle: (ClassSession, Boolean) -> Unit,
    private val onEditInstanceClick: ((ClassSession) -> Unit)? = null,
    private val onCancelInstanceClick: ((ClassSession) -> Unit)? = null
) : RecyclerView.ViewHolder(itemView) {

    // UI elements from the layout
    private val classSessionCardView: CardView = itemView.findViewById(R.id.classSessionCardView)
    private val courseNameTextView: TextView = itemView.findViewById(R.id.courseNameTextView)
    private val dayTimeTextView: TextView = itemView.findViewById(R.id.dayTimeTextView)
    private val notification1Label: TextView = itemView.findViewById(R.id.notification1Label)
    private val notification1TimeTextView: TextView = itemView.findViewById(R.id.notification1TimeTextView)
    private val notification2Label: TextView = itemView.findViewById(R.id.notification2Label)
    private val notification2TimeTextView: TextView = itemView.findViewById(R.id.notification2TimeTextView)
    private val silenceNotificationsCheckbox: CheckBox = itemView.findViewById(R.id.silenceNotificationsCheckbox)
    private val notesTextView: TextView = itemView.findViewById(R.id.notesTextView)

    // NEW: UI elements for instance actions
    private val editInstanceButton: ImageButton = itemView.findViewById(R.id.editInstanceButton)
    private val cancelInstanceButton: ImageButton = itemView.findViewById(R.id.cancelInstanceButton)

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /**
     * Binds the data from a ClassSession and its parent Timetable to the view.
     * @param session The ClassSession to display. Can be null for empty cells in the grid.
     * @param timetable The parent Timetable, used for default notification values.
     * @param showInstanceActionIcons NEW: A flag to determine if the edit/cancel icons should be visible.
     */
    fun bind(session: ClassSession?, timetable: Timetable, showInstanceActionIcons: Boolean = false) {
        if (session != null) {
            // --- Standard View Binding ---
            courseNameTextView.text = session.courseName

            // NEW - Get the user's current system timezone
            val systemZoneId = ZoneId.systemDefault()
            // NEW - Convert stored UTC milliseconds to the user's local date and time
            val startDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(session.startDateTimeMillis), systemZoneId)
            val endDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(session.endDateTimeMillis), systemZoneId)

            // NEW - Update the displayed text to be timezone-aware
            dayTimeTextView.text = context.getString(
                R.string.session_details_format_with_timezone, // Assumes a new string resource: "%1$s, %2$s - %3$s (%4$s)"
                session.dayOfWeek.toString().lowercase(Locale.ROOT).replaceFirstChar { it.uppercase() },
                startDateTime.format(timeFormatter),
                endDateTime.format(timeFormatter),
                systemZoneId.id // Display the current timezone ID
            )

            // Set up notification 1
            val offset1 = session.notificationOffsetMinutes1 ?: timetable.defaultNotificationOffsetMinutes1
            // NEW - Calculate notification time based on the timezone-aware start time
            val notifTime1 = startDateTime.minusMinutes(offset1.toLong())
            notification1TimeTextView.text = context.getString(
                R.string.notification_time_format,
                notifTime1.format(timeFormatter),
                offset1
            )
            notification1TimeTextView.setOnClickListener { onNotif1Click(session, 1, offset1) }
            notification1TimeTextView.isClickable = !session.isSilenced
            notification1Label.alpha = if (session.isSilenced) 0.5f else 1.0f
            notification1TimeTextView.alpha = if (session.isSilenced) 0.5f else 1.0f

            // Set up notification 2
            val offset2 = session.notificationOffsetMinutes2 ?: timetable.defaultNotificationOffsetMinutes2
            // NEW - Calculate notification time based on the timezone-aware start time
            val notifTime2 = startDateTime.minusMinutes(offset2.toLong())
            notification2TimeTextView.text = context.getString(
                R.string.notification_time_format,
                notifTime2.format(timeFormatter),
                offset2
            )
            notification2TimeTextView.setOnClickListener { onNotif1Click(session, 2, offset2) }
            notification2TimeTextView.isClickable = !session.isSilenced
            notification2Label.alpha = if (session.isSilenced) 0.5f else 1.0f
            notification2TimeTextView.alpha = if (session.isSilenced) 0.5f else 1.0f

            // Set up silence checkbox
            silenceNotificationsCheckbox.isChecked = session.isSilenced
            silenceNotificationsCheckbox.setOnCheckedChangeListener { _, isChecked ->
                onSilenceToggle(session, isChecked)
            }

            // Set up notes
            notesTextView.text = session.notes?.let { context.getString(R.string.session_notes_format, it) } ?: ""
            notesTextView.visibility = if (session.notes.isNullOrBlank()) View.GONE else View.VISIBLE

            // --- NEW: Instance Action Icon Logic ---
            if (showInstanceActionIcons) {
                editInstanceButton.visibility = View.VISIBLE
                cancelInstanceButton.visibility = View.VISIBLE

                editInstanceButton.setOnClickListener { onEditInstanceClick?.invoke(session) }
                cancelInstanceButton.setOnClickListener { onCancelInstanceClick?.invoke(session) }
            } else {
                editInstanceButton.visibility = View.GONE
                cancelInstanceButton.visibility = View.GONE
            }


            // --- View Visibility Management ---
            courseNameTextView.visibility = View.VISIBLE
            dayTimeTextView.visibility = View.VISIBLE
            notification1Label.visibility = View.VISIBLE
            notification1TimeTextView.visibility = View.VISIBLE
            notification2Label.visibility = View.VISIBLE
            notification2TimeTextView.visibility = View.VISIBLE
            silenceNotificationsCheckbox.visibility = View.VISIBLE
            notesTextView.visibility = if (session.notes.isNullOrBlank()) View.GONE else View.VISIBLE
            classSessionCardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.timetable_cell_background))

        } else {
            // Hide all views for an empty cell
            courseNameTextView.visibility = View.GONE
            dayTimeTextView.visibility = View.GONE
            notification1Label.visibility = View.GONE
            notification1TimeTextView.visibility = View.GONE
            notification2Label.visibility = View.GONE
            notification2TimeTextView.visibility = View.GONE
            silenceNotificationsCheckbox.visibility = View.GONE
            notesTextView.visibility = View.GONE
            editInstanceButton.visibility = View.GONE
            cancelInstanceButton.visibility = View.GONE
        }
    }
}
