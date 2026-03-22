package com.costheta.cortexa.todo

import android.graphics.Color
import android.text.Html
import android.util.Log
import android.view.View
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.costheta.cortexa.R
import com.costheta.cortexa.data.db.dao.AttachmentDao
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

class TodoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val todoCardView: CardView = itemView.findViewById(R.id.todoCardView)
    private val todoTitleTextView: TextView = itemView.findViewById(R.id.todoTitleTextView)
    private val todoDescriptionTextView: TextView = itemView.findViewById(R.id.todoDescriptionTextView)
    private val todoDueDateTimeTextView: TextView = itemView.findViewById(R.id.todoDueDateTimeTextView)
    private val todoDueDateTimeTimezoneTextView: TextView = itemView.findViewById(R.id.todoDueDateTimeTimezoneTextView)
    private val todoPriorityTextView: TextView = itemView.findViewById(R.id.todoPriorityTextView)
    private val todoNotificationsStatusTextView: TextView = itemView.findViewById(R.id.todoNotificationsStatusTextView)
    private val todoCompletedCheckbox: CheckBox = itemView.findViewById(R.id.todoCompletedCheckbox)
    private val todoCreationDateTextView: TextView = itemView.findViewById(R.id.todoCreationDateTextView)
    private val deleteButton: ImageButton = itemView.findViewById(R.id.deleteTodoButton)
    private val editButton: ImageButton = itemView.findViewById(R.id.editTodoButton)
    private val attachmentIconImageView: ImageView = itemView.findViewById(R.id.attachmentIconImageView)

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    private val dateFormatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.US)
    private val datetimeFormatter = DateTimeFormatter.ofPattern("HH:mm dd-MMM-yyyy", Locale.ENGLISH)

    // MODIFIED: Bind now takes a DisplayableTask
    fun bind(
        displayableTask: DisplayableTask,
        attachmentDao: AttachmentDao,
        coroutineScope: CoroutineScope,
        onDeleteClick: (DisplayableTask) -> Unit,
        onEditClick: (DisplayableTask) -> Unit,
        onToggleComplete: (DisplayableTask, Boolean) -> Unit
    ) {
        when (displayableTask) {
            is DisplayableTask.AppTodo -> bindAppTodo(displayableTask.todo, attachmentDao, coroutineScope, onDeleteClick, onEditClick, onToggleComplete)
            is DisplayableTask.GoogleTask -> bindGoogleTask(displayableTask.task, attachmentDao, coroutineScope, onEditClick)
        }
    }

    // NEW: Function to bind a native TodoItem
    private fun bindAppTodo(
        todoItem: com.costheta.cortexa.data.models.TodoItem,
        attachmentDao: AttachmentDao,
        coroutineScope: CoroutineScope,
        onDeleteClick: (DisplayableTask) -> Unit,
        onEditClick: (DisplayableTask) -> Unit,
        onToggleComplete: (DisplayableTask, Boolean) -> Unit
    ) {
        val (cardBgColor, textColor) = CardColor.determineTodoCardColors(todoItem, itemView.context)
        val textColorHex = String.format("#%06X", 0xFFFFFF and textColor)

        // --- Set Colors ---
        setCardColors(cardBgColor, textColor)

        // --- Set Text and Visibility ---
        todoTitleTextView.text = todoItem.title
        todoDescriptionTextView.text = todoItem.description.takeIf { !it.isNullOrBlank() }
        todoDescriptionTextView.visibility = if (todoItem.description.isNullOrBlank()) View.GONE else View.VISIBLE

        val priorityText = when (todoItem.priority) {
            "High" -> itemView.context.getString(R.string.priority_high)
            "Medium" -> itemView.context.getString(R.string.priority_medium)
            "Low" -> itemView.context.getString(R.string.priority_low)
            else -> itemView.context.getString(R.string.priority_none)
        }
        todoPriorityTextView.text = Html.fromHtml("<b>${itemView.context.getString(R.string.todo_priority_string)}</b> $priorityText; ", Html.FROM_HTML_MODE_COMPACT)
        todoPriorityTextView.visibility = if (todoItem.priority.isNullOrBlank()) View.GONE else View.VISIBLE

        val notificationStatus = if (todoItem.silenceNotifications) {
            "<font color='red'><b>${itemView.context.getString(R.string.notifications_are_silenced)}</b></font>"
        } else {
            "<font color='$textColorHex'>${itemView.context.getString(R.string.notifications_are_on)}</font>"
        }
        todoNotificationsStatusTextView.text = Html.fromHtml("<b>${itemView.context.getString(R.string.assignment_notifications_status_format)}</b> $notificationStatus", Html.FROM_HTML_MODE_COMPACT)
        todoNotificationsStatusTextView.visibility = if (todoItem.isCompleted) View.GONE else View.VISIBLE

        todoCompletedCheckbox.isChecked = todoItem.isCompleted
        todoCreationDateTextView.text = itemView.context.getString(R.string.todo_creation_date_format, todoItem.creationDate.format(dateFormatter))
        todoCreationDateTextView.visibility = View.VISIBLE

        // --- Set Listeners and Visibility for Actions ---
        deleteButton.visibility = View.VISIBLE
        editButton.visibility = View.VISIBLE
        todoCompletedCheckbox.isEnabled = true
        deleteButton.setOnClickListener { onDeleteClick(DisplayableTask.AppTodo(todoItem)) }
        editButton.setOnClickListener { onEditClick(DisplayableTask.AppTodo(todoItem)) }
        todoCompletedCheckbox.setOnClickListener { onToggleComplete(DisplayableTask.AppTodo(todoItem), todoCompletedCheckbox.isChecked) }

        bindDueDateTime(todoItem.dueDateTimeMillis, todoItem.isCompleted, todoItem.completedTimeInMillis)

        todoItem.todoId?.let { id ->
            bindAttachmentIcon("ToDo", id, attachmentDao, coroutineScope)
        }
    }

    // NEW: Function to bind a SyncedGoogleTask
    private fun bindGoogleTask(
        task: com.costheta.cortexa.data.models.SyncedGoogleTask,
        attachmentDao: AttachmentDao,
        coroutineScope: CoroutineScope,
        onEditClick: (DisplayableTask) -> Unit
    ) {
        // --- Set Colors (use a default color for Google Tasks) ---
        // setCardColors(ContextCompat.getColor(itemView.context, R.color.google_task_blue), Color.BLACK)
        val (cardBgColor, textColor) = CardColor.determineGoogleTaskCardColors(task, itemView.context)
        setCardColors(cardBgColor, textColor)

        // --- Set Text and Visibility ---
        todoTitleTextView.text = task.title
        todoDescriptionTextView.text = task.notes.takeIf { !it.isNullOrBlank() }
        todoDescriptionTextView.visibility = if (task.notes.isNullOrBlank()) View.GONE else View.VISIBLE

        todoPriorityTextView.visibility = View.GONE
        todoNotificationsStatusTextView.visibility = View.GONE
        todoCreationDateTextView.visibility = View.GONE

        todoCompletedCheckbox.isChecked = task.isCompleted

        // --- Set Listeners and Visibility for Actions ---
        deleteButton.visibility = View.GONE // Hide delete button
        editButton.visibility = View.VISIBLE
        todoCompletedCheckbox.isEnabled = false // Disable toggling from here
        editButton.setOnClickListener { onEditClick(DisplayableTask.GoogleTask(task)) }

        bindDueDateTime(task.dueDateTimeMillis, task.isCompleted, null)

        bindAttachmentIcon("SyncedGoogleTask", task.localId, attachmentDao, coroutineScope)
    }

    private fun bindDueDateTime(dueMillis: Long?, isCompleted: Boolean, completedMillis: Long?) {
        try {
            val systemZoneId = ZoneId.systemDefault()
            var cardVisibleTillString = ""

            if (isCompleted && completedMillis != null) {
                val completedTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(completedMillis), systemZoneId)
                val displayUntilTime = completedTime.plusHours(1)
                cardVisibleTillString = itemView.context.getString(R.string.card_visible_till, displayUntilTime.format(datetimeFormatter))
            }

            if (dueMillis != null) {
                val dueTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(dueMillis), systemZoneId)
                val baseDueText = itemView.context.getString(
                    R.string.todo_due_date_html_format,
                    dueTime.format(dateFormatter),
                    dueTime.format(timeFormatter)
                )
                val finalHtmlText = if (cardVisibleTillString.isNotEmpty()) {
                    "$baseDueText<br><small>$cardVisibleTillString</small>"
                } else {
                    baseDueText
                }
                todoDueDateTimeTextView.text = Html.fromHtml(finalHtmlText, Html.FROM_HTML_MODE_COMPACT)
                todoDueDateTimeTimezoneTextView.text = "(${systemZoneId.id})"
                todoDueDateTimeTextView.visibility = View.VISIBLE
                todoDueDateTimeTimezoneTextView.visibility = View.VISIBLE
            } else {
                todoDueDateTimeTextView.visibility = View.GONE
                todoDueDateTimeTimezoneTextView.visibility = View.GONE
            }
        } catch (e: Exception) {
            Log.e("TodoViewHolder", "Error binding due date/time: ${e.message}", e)
            todoDueDateTimeTextView.visibility = View.GONE
            todoDueDateTimeTimezoneTextView.visibility = View.GONE
        }
    }

    private fun bindAttachmentIcon(eventType: String, eventId: Long, attachmentDao: AttachmentDao, coroutineScope: CoroutineScope) {
        coroutineScope.launch {
            val attachmentCount = withContext(Dispatchers.IO) {
                attachmentDao.getAttachmentCountForEvent(eventType, eventId)
            }
            withContext(Dispatchers.Main) {
                attachmentIconImageView.visibility = if (attachmentCount > 0) View.VISIBLE else View.GONE
            }
        }
    }

    private fun setCardColors(bgColor: Int, textColor: Int) {
        todoCardView.setCardBackgroundColor(bgColor)
        todoTitleTextView.setTextColor(textColor)
        todoDescriptionTextView.setTextColor(textColor)
        todoDueDateTimeTextView.setTextColor(textColor)
        todoPriorityTextView.setTextColor(textColor)
        todoCreationDateTextView.setTextColor(textColor)
        todoDueDateTimeTimezoneTextView.setTextColor(textColor)
        todoCompletedCheckbox.setTextColor(textColor)
    }
}
