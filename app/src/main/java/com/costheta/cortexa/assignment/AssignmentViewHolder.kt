package com.costheta.cortexa.assignment

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action


import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.costheta.cortexa.R
import com.costheta.cortexa.data.db.dao.AttachmentDao
import com.costheta.cortexa.data.models.Assignment
import com.costheta.cortexa.util.ui.CardColor
import kotlinx.coroutines.CoroutineScope
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * A reusable ViewHolder for displaying an Assignment in a CardView (item_assignment.xml).
 * This ViewHolder can be used in a RecyclerView adapter or to populate a standalone view.
 */
class AssignmentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    // --- View References ---
    private val assignmentCardView: CardView = itemView.findViewById(R.id.assignmentCardView)
    private val assignmentCourseNameTextView: TextView = itemView.findViewById(R.id.assignmentCourseNameTextView)
    private val assignmentTopicAndNameTextView: TextView = itemView.findViewById(R.id.assignmentTopicAndNameTextView)
    private val assignmentDueDateTimeTextView: TextView = itemView.findViewById(R.id.assignmentDueDateTimeTextView)
//    private val assignmentEstimatedTimeTextView: TextView = itemView.findViewById(R.id.assignmentEstimatedTimeTextView)
//    private val assignmentCurrentProgressTextView: TextView = itemView.findViewById(R.id.assignmentCurrentProgressTextView)
    private val checkboxDoneAndSubmittedCard: CheckBox = itemView.findViewById(R.id.checkboxDoneAndSubmittedCard)
    private val assignmentHoursLeftTextView: TextView = itemView.findViewById(R.id.assignmentHoursLeftTextView)
    private val deleteButton: ImageButton = itemView.findViewById(R.id.deleteAssignmentButton)
    private val editButton: ImageButton = itemView.findViewById(R.id.editAssignmentButton)
//    private val attachmentIconImageView: ImageView = itemView.findViewById(R.id.attachmentIconImageView)
    private val assignmentNotificationsStatusTextView: TextView = itemView.findViewById(R.id.assignmentNotificationsStatusTextView)

    // --- Formatters ---
    private val dateFormatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
    private val datetimeFormatter = DateTimeFormatter.ofPattern("HH:mm dd-MMM-yyyy", Locale.ENGLISH)

    /**
     * Binds an Assignment object to the views in item_assignment.xml.
     *
     * @param assignment The assignment data to display.
     * @param attachmentDao DAO to check for attachments.
     * @param coroutineScope Scope to launch background tasks for DB access.
     * @param onDeleteClick Lambda to execute when the delete button is clicked.
     * @param onEditClick Lambda to execute when the edit button is clicked.
     * @param onDoneClick Lambda to execute when the 'Done' checkbox state changes.
     * @param showActionButtons If true, the edit and delete buttons are visible.
     */
    fun bind(
        assignment: Assignment,
        attachmentDao: AttachmentDao,
        coroutineScope: CoroutineScope,
        onDeleteClick: (Assignment) -> Unit,
        onEditClick: (Assignment) -> Unit,
        onDoneClick: (Assignment, Boolean) -> Unit,
        showActionButtons: Boolean = true
    ) {
        val systemZoneId = ZoneId.systemDefault()
        val dueDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(assignment.dueDateTimeMillis), systemZoneId)
        val now = LocalDateTime.now(systemZoneId)
        val hoursLeft = ChronoUnit.MINUTES.between(now, dueDateTime).toFloat() / 60.0f
        val timezoneId = systemZoneId.id

        // Determine card background color and text color
        val (cardBackgroundColor, textColor) = CardColor.determineAssignmentCardColors(assignment, itemView.context)
        assignmentCardView.setCardBackgroundColor(cardBackgroundColor)

        // Set text and text color for all TextViews
        assignmentCourseNameTextView.text = assignment.courseName
        assignmentCourseNameTextView.setTextColor(textColor)

        assignmentTopicAndNameTextView.text = "${assignment.assignmentTopic} - ${assignment.assignmentName}"
        assignmentTopicAndNameTextView.setTextColor(textColor)

        val spannableDue = SpannableStringBuilder().apply {
            appendBold(itemView.context.getString(R.string.assignment_due_string))
            append(" ")
            append(dueDateTime.format(dateFormatter))
            appendBold(" at ")
            append(dueDateTime.format(timeFormatter))
            append(" (").append(timezoneId).append(")")
            if (assignment.currentProgress == 100) {
                val displayUntilTime = dueDateTime.plusHours(3).format(datetimeFormatter)
                val displayUntilText = "\n(${itemView.context.getString(R.string.assignment_displayed_till_format, displayUntilTime)})"
                val start = length
                append(displayUntilText)
                setSpan(RelativeSizeSpan(0.8f), start, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        assignmentDueDateTimeTextView.text = spannableDue
        assignmentDueDateTimeTextView.visibility = View.VISIBLE
        assignmentDueDateTimeTextView.setTextColor(textColor)

        val spannableEstimatedHours = SpannableStringBuilder().apply {
            appendBold(itemView.context.getString(R.string.assignment_estimated_hours_format))
            append(" ").append(String.format(Locale.getDefault(), "%d", assignment.estimatedHours))
        }
//        assignmentEstimatedTimeTextView.text = spannableEstimatedHours
//        assignmentEstimatedTimeTextView.setTextColor(textColor)

        val spannableProgress = SpannableStringBuilder().apply {
            appendBold(itemView.context.getString(R.string.assignment_current_progress_format))
            append(" ").append(String.format(Locale.getDefault(), "%d", assignment.currentProgress)).append(" % ")
        }
//        assignmentCurrentProgressTextView.text = spannableProgress
//        assignmentCurrentProgressTextView.setTextColor(textColor)

        checkboxDoneAndSubmittedCard.setOnCheckedChangeListener(null)
        checkboxDoneAndSubmittedCard.isChecked = assignment.currentProgress == 100
        checkboxDoneAndSubmittedCard.setTextColor(textColor)
        checkboxDoneAndSubmittedCard.setOnCheckedChangeListener { _, isChecked -> onDoneClick(assignment, isChecked) }

        if (hoursLeft <= 0) {
            assignmentHoursLeftTextView.text = itemView.context.getString(R.string.assignment_overdue)
            assignmentHoursLeftTextView.setTextColor(ContextCompat.getColor(itemView.context, R.color.white1))
        } else {
            val spannableTimeLeft = SpannableStringBuilder().apply {
                appendBold(itemView.context.getString(R.string.assignment_hours_left_format))
                append(" ").append(String.format(Locale.getDefault(), "%.2f", hoursLeft)).append(" hours")
            }
            assignmentHoursLeftTextView.text = spannableTimeLeft
            assignmentHoursLeftTextView.setTextColor(textColor)
        }

        val notificationsStatus = if (assignment.silenceNotifications) {
            itemView.context.getString(R.string.notifications_silenced)
        } else {
            itemView.context.getString(R.string.notifications_on)
        }
        assignmentNotificationsStatusTextView.setTextColor(if (assignment.silenceNotifications) Color.RED else textColor)

        val spannableNotifications = SpannableStringBuilder().apply {
            val start = length
            appendBold(itemView.context.getString(R.string.assignment_notifications_status_format))
            append(" ")
            if (!assignment.silenceNotifications) {
                append(notificationsStatus)
            } else {
                appendBold(notificationsStatus)
                setSpan(StyleSpan(Typeface.ITALIC), start, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        assignmentNotificationsStatusTextView.text = spannableNotifications

        if (assignment.currentProgress == 100) {
            assignmentHoursLeftTextView.visibility = View.GONE
            assignmentNotificationsStatusTextView.visibility = View.GONE
        } else {
            assignmentHoursLeftTextView.visibility = View.VISIBLE
            assignmentNotificationsStatusTextView.visibility = View.VISIBLE
        }

        // --- Set up actions ---
        deleteButton.visibility = if (showActionButtons) View.VISIBLE else View.GONE
        editButton.visibility = if (showActionButtons) View.VISIBLE else View.GONE
        deleteButton.setOnClickListener { onDeleteClick(assignment) }
        editButton.setOnClickListener { onEditClick(assignment) }

        // Check for attachments
//        assignment.assignmentId?.let { id ->
//            coroutineScope.launch {
//                val attachmentCount = withContext(Dispatchers.IO) {
//                    attachmentDao.getAttachmentCountForEvent("Assignment", id)
//                }
//                withContext(Dispatchers.Main) {
//                    attachmentIconImageView.visibility = if (attachmentCount > 0) View.VISIBLE else View.GONE
//                }
//            }
//        } ?: run {
//            attachmentIconImageView.visibility = View.GONE
//        }
    }

    /**
     * Helper extension function to append bolded text to a SpannableStringBuilder.
     */
    private fun SpannableStringBuilder.appendBold(text: CharSequence): SpannableStringBuilder {
        val start = length
        append(text)
        setSpan(StyleSpan(Typeface.BOLD), start, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        return this
    }
}
