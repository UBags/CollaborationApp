package com.costheta.cortexa.exam

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action

import android.graphics.Color
import android.text.Html
import android.text.SpannableStringBuilder
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.costheta.cortexa.R
import com.costheta.cortexa.data.db.dao.AttachmentDao
import com.costheta.cortexa.data.models.Exam
import com.costheta.cortexa.util.ui.CardColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

class ExamViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val examCardView: CardView = itemView.findViewById(R.id.examCardView)
    private val examCourseNameTextView: TextView = itemView.findViewById(R.id.examCourseNameTextView)
    private val examDetailsContentTextView: TextView = itemView.findViewById(R.id.examDetailsContentTextView)
    private val examDateTimeVenueTextView: TextView = itemView.findViewById(R.id.examDateTimeVenueTextView)
    private val examHoursLeftTextView: TextView = itemView.findViewById(R.id.examHoursLeftTextView)
    private val examNotificationsStatusTextView: TextView = itemView.findViewById(R.id.examNotificationsStatusTextView)
    private val deleteButton: ImageButton = itemView.findViewById(R.id.deleteExamButton)
    private val editButton: ImageButton = itemView.findViewById(R.id.editExamButton)
    private val attachmentIconImageView: ImageView = itemView.findViewById(R.id.attachmentIconImageView)

    private val dateFormatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val datetimeFormatter = DateTimeFormatter.ofPattern("HH:mm dd-MMM-yyyy", Locale.ENGLISH)

    fun bind(
        exam: Exam,
        attachmentDao: AttachmentDao,
        coroutineScope: CoroutineScope,
        onDeleteClick: (Exam) -> Unit,
        onEditClick: (Exam) -> Unit
    ) {
        val systemZoneId = ZoneId.systemDefault()
        val examStartDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(exam.startDateTimeMillis), systemZoneId)
        val examEndDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(exam.endDateTimeMillis), systemZoneId)
        val now = LocalDateTime.now(systemZoneId)
        val hoursLeft = ChronoUnit.MINUTES.between(now, examStartDateTime).toFloat() / 60.0f

        val (cardBackgroundColor, textColor) = CardColor.determineExamCardColors(exam, itemView.context)

        examCardView.setCardBackgroundColor(cardBackgroundColor)
        examCourseNameTextView.text = exam.courseName
        examCourseNameTextView.setTextColor(textColor)

        if (!exam.examDetails.isNullOrBlank()) {
            examDetailsContentTextView.text = Html.fromHtml("<b>${itemView.context.getString(R.string.exam_details_label)}:</b> ${exam.examDetails}", Html.FROM_HTML_MODE_COMPACT)
            examDetailsContentTextView.visibility = View.VISIBLE
            examDetailsContentTextView.setTextColor(textColor)
        } else {
            examDetailsContentTextView.visibility = View.GONE
        }

        val timezoneId = systemZoneId.id
        var cardVisibleTillString = ""
        if (now.isAfter(examEndDateTime)) {
            val displayUntilTime = examEndDateTime.plusHours(1).format(datetimeFormatter)
            cardVisibleTillString = itemView.context.getString(R.string.card_visible_till, displayUntilTime)
        }

        examDateTimeVenueTextView.text = Html.fromHtml(
            itemView.context.getString(
                R.string.exam_date_time_venue_format,
                examStartDateTime.format(dateFormatter),
                examStartDateTime.format(timeFormatter),
                examEndDateTime.format(timeFormatter),
                timezoneId,
                exam.venue,
                cardVisibleTillString
            ), Html.FROM_HTML_MODE_COMPACT
        )
        examDateTimeVenueTextView.setTextColor(textColor)

        if (now.isAfter(examStartDateTime) && now.isBefore(examEndDateTime)) {
            examHoursLeftTextView.text = itemView.context.getString(R.string.exam_ongoing)
            examHoursLeftTextView.setTextColor(ContextCompat.getColor(itemView.context, R.color.exam_ongoing_text_green))
        } else if (now.isAfter(examEndDateTime)) {
            examHoursLeftTextView.text = itemView.context.getString(R.string.exam_over)
            examHoursLeftTextView.setTextColor(ContextCompat.getColor(itemView.context, R.color.exam_overdue_text_red))
        } else {
            examHoursLeftTextView.text = itemView.context.getString(R.string.exam_hours_left_format, hoursLeft)
            examHoursLeftTextView.setTextColor(textColor)
        }

        val notificationStatus = if (exam.silenceNotifications) {
            itemView.context.getString(R.string.notifications_are_silenced)
        } else {
            itemView.context.getString(R.string.notifications_are_on)
        }

        if (!exam.silenceNotifications) {
            examNotificationsStatusTextView.setTextColor(textColor)
        } else {
            examNotificationsStatusTextView.setTextColor(Color.RED)
        }

        val spannableNotifications = SpannableStringBuilder().apply {
            append("<b>${itemView.context.getString(R.string.assignment_notifications_status_format)}</b> ")
            if (!exam.silenceNotifications) {
                append(notificationStatus)
            } else {
                append("<b>$notificationStatus</b>")
            }
        }
        examNotificationsStatusTextView.text = Html.fromHtml(spannableNotifications.toString(), Html.FROM_HTML_MODE_COMPACT)

        if (now.isAfter(examStartDateTime)) {
            examNotificationsStatusTextView.visibility = View.GONE
        } else {
            examNotificationsStatusTextView.visibility = View.VISIBLE
        }

        deleteButton.setOnClickListener { onDeleteClick(exam) }
        editButton.setOnClickListener { onEditClick(exam) }

        exam.examId?.let { id ->
            coroutineScope.launch {
                val attachmentCount = withContext(Dispatchers.IO) {
                    attachmentDao.getAttachmentCountForEvent("Exam", id)
                }
                withContext(Dispatchers.Main) {
                    attachmentIconImageView.visibility = if (attachmentCount > 0) View.VISIBLE else View.GONE
                }
            }
        } ?: run {
            attachmentIconImageView.visibility = View.GONE
        }
    }
}
