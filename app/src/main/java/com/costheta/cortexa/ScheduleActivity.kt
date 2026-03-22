package com.costheta.cortexa

import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.costheta.cortexa.data.db.AppDatabase
import com.costheta.cortexa.data.db.dao.TimetableDao
import com.costheta.cortexa.data.db.dao.EngagementDao
import com.costheta.cortexa.data.db.dao.SyncedCalendarEventDao
import com.costheta.cortexa.data.db.dao.TodoDao
import com.costheta.cortexa.data.db.dao.SyncedGoogleTaskDao
import com.costheta.cortexa.data.models.TimetableType
import com.costheta.cortexa.data.models.ScheduleEvent // Corrected import for ScheduleEvent
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull // Import firstOrNull for Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import android.widget.ScrollView
import androidx.core.content.ContextCompat
import android.widget.LinearLayout
import com.costheta.cortexa.data.db.dao.ActionDao
import com.costheta.cortexa.data.db.dao.AssignmentDao
import com.costheta.cortexa.data.db.dao.AttachmentDao
import com.costheta.cortexa.data.db.dao.ExamDao
import com.costheta.cortexa.data.models.ClassSessionExceptionType
import com.costheta.cortexa.data.models.EngagementExceptionType
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class ScheduleActivity : AppCompatActivity() {

    private lateinit var scheduleView: ScheduleView
    private lateinit var timetableDao: TimetableDao
    private lateinit var engagementDao: EngagementDao
    // NEW: DAO for SyncedCalendarEvents
    private lateinit var syncedCalendarEventDao: SyncedCalendarEventDao
    private lateinit var syncedGoogleTaskDao: SyncedGoogleTaskDao
    private lateinit var examDao: ExamDao
    private lateinit var assignmentDao: AssignmentDao
    private lateinit var todoDao: TodoDao
    private lateinit var actionDao: ActionDao
    private lateinit var attachmentDao: AttachmentDao
    private lateinit var scheduleViewTitle: TextView
    private lateinit var scheduleScrollView: ScrollView
    private lateinit var columnHeadersLayout: LinearLayout // Reference to the column headers LinearLayout

    private var currentDayOffset: Int = 0 // Overall offset from today (0 = today, 1 = tomorrow, etc.) is currently selected
    private var currentWeekOffset: Int = 0 // The current 7-day page (0-3)
    private lateinit var goLeftButton: MaterialButton
    private lateinit var goRightButton: MaterialButton

    private val dayButtons = mutableListOf<MaterialButton>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.schedule_activity)

        scheduleView = findViewById(R.id.scheduleView)
        val db = AppDatabase.getDatabase(applicationContext)
        timetableDao = db.timetableDao()
        engagementDao = db.engagementDao()
        // NEW: Initialize SyncedCalendarEventDao
        syncedCalendarEventDao = db.syncedCalendarEventDao()
        syncedGoogleTaskDao = db.syncedGoogleTaskDao()
        examDao = db.examDao()
        assignmentDao = db.assignmentDao()
        todoDao = db.todoDao()
        actionDao = db.actionDao()
        attachmentDao = db.attachmentDao()
        scheduleViewTitle = findViewById(R.id.scheduleViewTitle)
        scheduleScrollView = findViewById(R.id.scheduleScrollView)
        columnHeadersLayout = findViewById(R.id.columnHeadersLayout)

        goLeftButton = findViewById(R.id.goLeft)
        goRightButton = findViewById(R.id.goRight)

        // MODIFIED: Pass the new SyncedGoogleTaskDao to the ScheduleView
        scheduleView.setDaos(
            timetableDao = timetableDao,
            assignmentDao = assignmentDao,
            examDao = examDao,
            engagementDao = engagementDao,
            syncedCalendarEventDao = syncedCalendarEventDao,
            syncedGoogleTaskDao = syncedGoogleTaskDao, // Pass the new DAO
            todoDao = todoDao,
            actionDao = actionDao,
            attachmentDao = attachmentDao
        ) {
            updateDaySelection(currentDayOffset)
        }

        // Initialize day buttons as MaterialButtons
        dayButtons.add(findViewById(R.id.schedule_btnDay1))
        dayButtons.add(findViewById(R.id.schedule_btnDay2))
        dayButtons.add(findViewById(R.id.schedule_btnDay3))
        dayButtons.add(findViewById(R.id.schedule_btnDay4))
        dayButtons.add(findViewById(R.id.schedule_btnDay5))
        dayButtons.add(findViewById(R.id.schedule_btnDay6))
        dayButtons.add(findViewById(R.id.schedule_btnDay7))

        setupDayButtons()
        updateDaySelection(0) // Select today by default and load its events
    }

    override fun onResume() {
        super.onResume()
        // This is crucial for edits: When returning from Add...Activity, reload the schedule.
        // It will re-fetch data for the currently selected day.
        updateDaySelection(currentDayOffset)
        Log.d("ScheduleActivity", "ScheduleActivity onResume: Reloading schedule for current day offset $currentDayOffset.")
    }

    private fun setupDayButtons() {
        for (i in 0 until dayButtons.size) {
            val button = dayButtons[i]
            button.setOnClickListener {
                val newDayOffset = (currentWeekOffset * 7) + i
                updateDaySelection(newDayOffset)
                // Scroll to 05:30 when a day button is clicked
                columnHeadersLayout.post {
                    val scrollTargetYInScheduleView = scheduleView.getScrollYForTime(5, 30)
                    scheduleScrollView.post {
                        val adjustedScrollY = scrollTargetYInScheduleView.coerceAtLeast(0)
                        scheduleScrollView.scrollTo(0, adjustedScrollY)
                    }
                }
            }
        }

        goLeftButton.setOnClickListener {
            if (currentWeekOffset > 0) {
                currentWeekOffset--
                updateDaySelection(currentWeekOffset * 7) // Select the first day of the new week
            }
        }

        goRightButton.setOnClickListener {
            if (currentWeekOffset < 3) {
                currentWeekOffset++
                updateDaySelection(currentWeekOffset * 7) // Select the first day of the new week
            }
        }
    }

    private fun updateDaySelection(dayOffset: Int) {
        currentDayOffset = dayOffset
        currentWeekOffset = dayOffset / 7 // Determine the week based on the absolute day offset

        val selectedDate = LocalDate.now().plusDays(dayOffset.toLong())

        // Update the 7 day buttons' text and selection state
        for (i in 0 until dayButtons.size) {
            val button = dayButtons[i]
            // Calculate the date for this button based on the current week
            val dateForButton = LocalDate.now().plusDays((currentWeekOffset * 7 + i).toLong())
            button.text = dateForButton.format(DateTimeFormatter.ofPattern("EEE\ndd", Locale.getDefault()))

            // Check if this button represents the currently selected day
            button.isSelected = (currentWeekOffset * 7 + i == dayOffset)

            // Programmatically set background tint and text color based on selection state
            if (button.isSelected) {
                button.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, android.R.color.black))
                button.setTextColor(ContextCompat.getColor(this, R.color.light_gray))
            } else {
                button.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.purple_500))
                button.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            }
        }

        // Update navigation button states
        goLeftButton.isEnabled = currentWeekOffset > 0
        goRightButton.isEnabled = currentWeekOffset < 3

        // Update title and load events for the selected day
        scheduleViewTitle.text = getString(R.string.schedule_view_title_for_date, selectedDate.format(DateTimeFormatter.ofPattern("EEE, MMM dd", Locale.getDefault())))
        loadEventsForDate(selectedDate)
    }

    private fun loadEventsForDate(date: LocalDate) {
        lifecycleScope.launch {
            try {
                val allEventsForDate = mutableListOf<ScheduleEvent>()

                // --- 1. Process Class Sessions and their Exceptions---
                val regularSessionsOnDay = withContext(Dispatchers.IO) {
                    timetableDao.getAllRegularClassSessions().filter { it.dayOfWeek == date.dayOfWeek }
                }

                val exceptionsForDate = withContext(Dispatchers.IO) {
                    timetableDao.getSessionExceptionsForDate(date)
                }

                // Create a map for quick lookup of exceptions for original session instances on the current date
                val exceptionsMap = exceptionsForDate
                    .filter { it.originalDate == date }
                    .associateBy { it.originalSessionId }

                // Process regular sessions, filtering out any that are cancelled or moved
                regularSessionsOnDay.forEach { session ->
                    val exception = exceptionsMap[session.sessionId]
                    if (exception == null) { // No exception for this session on this date
                        val parentTimetable = withContext(Dispatchers.IO) { session.timetableId?.let { timetableDao.getTimetableById(it) } }
                        if (parentTimetable != null && parentTimetable.isActive && !date.isBefore(parentTimetable.validityStartDate) && !date.isAfter(parentTimetable.validityEndDate)) {
                            allEventsForDate.add(ScheduleEvent.ClassSessionEvent(
                                id = session.sessionId, name = session.courseName,
                                type = if (parentTimetable.type == TimetableType.PERSONAL) ScheduleEvent.ScheduleEventType.PERSONAL else ScheduleEvent.ScheduleEventType.LECTURE,
                                date = date, startTime = session.startTime, durationMinutes = session.durationMinutes,
                                location = session.location, notes = session.notes, originalItem = session,
                                isModified = false // This is a regular, unmodified session
                            ))
                        }
                    }
                    // If an exception exists (CANCELLED or MODIFIED), we simply don't add the regular session.
                }

                // Process sessions that were rescheduled TO this date
                exceptionsForDate
                    .filter { it.classSessionExceptionType == ClassSessionExceptionType.MODIFIED && it.newDate == date }
                    .forEach { exception ->
                        val originalSession = withContext(Dispatchers.IO) { timetableDao.getClassSessionById(exception.originalSessionId) }
                        if (originalSession != null) {
                            val parentTimetable = withContext(Dispatchers.IO) { originalSession.timetableId?.let { timetableDao.getTimetableById(it) } }
                            if (parentTimetable != null && parentTimetable.isActive) {
                                // Create a temporary ClassSession object with the modified details for this single instance
                                val modifiedInstance = originalSession.copy(
                                    startTime = exception.newStartTime!!,
                                    durationMinutes = exception.newDurationMinutes!!,
                                    notes = exception.newNotes ?: originalSession.notes
                                )
                                allEventsForDate.add(ScheduleEvent.ClassSessionEvent(
                                    id = modifiedInstance.sessionId, name = modifiedInstance.courseName,
                                    type = if (parentTimetable.type == TimetableType.PERSONAL) ScheduleEvent.ScheduleEventType.PERSONAL else ScheduleEvent.ScheduleEventType.LECTURE,
                                    date = date, startTime = modifiedInstance.startTime, durationMinutes = modifiedInstance.durationMinutes,
                                    location = modifiedInstance.location, notes = modifiedInstance.notes, originalItem = modifiedInstance,
                                    isModified = true // Flag this event as a modified instance
                                ))
                            }
                        }
                    }


                // 2. Get Engagements (Ad-hoc)
                val allEngagements = withContext(Dispatchers.IO) { engagementDao.getAllEngagements() }
                val engagementExceptionsForDate = withContext(Dispatchers.IO) { engagementDao.getEngagementExceptionsForDateRange(date, date) }

                val engagementExceptionsMap = engagementExceptionsForDate
                    .filter { it.originalDate == date }
                    .associateBy { it.originalEngagementId }

                allEngagements.forEach { engagement ->
                    if (engagement.engagementId != null && engagement.isActive && !date.isBefore(engagement.validityStartDate) && !date.isAfter(engagement.validityEndDate)) {
                        val exception = engagementExceptionsMap[engagement.engagementId]
                        if (exception == null) {
                            val isScheduledDay = when (engagement.daySelectionType) {
                                "SPECIFIC_DAYS" -> engagement.selectedDaysOfWeek?.split(",")?.map { DayOfWeek.valueOf(it) }?.contains(date.dayOfWeek) ?: false
                                "RECURRENCE" -> engagement.recurrenceIntervalDays?.let { ChronoUnit.DAYS.between(engagement.validityStartDate, date) % it == 0L } ?: false
                                else -> false
                            }

                            if (isScheduledDay) {
                                // Create a copy of the engagement and set its nextOccurrenceDate for this instance
                                val instance = engagement.copy().apply {
                                    this.nextOccurrenceDate = date
                                    this.startDateTime = LocalDateTime.of(date, this.startTime) // Add this line
                                }

                                allEventsForDate.add(ScheduleEvent.EngagementEvent(
                                    id = instance.engagementId, name = instance.engagementName,
                                    type = ScheduleEvent.ScheduleEventType.ENGAGEMENT,
                                    date = date, startTime = instance.startTime, durationMinutes = instance.durationMinutes,
                                    location = instance.venue, notes = instance.notes, originalItem = instance,
                                    isModified = false
                                ))
                            }
                        }
                    }
                }

                engagementExceptionsForDate
                    .filter { it.engagementExceptionType == EngagementExceptionType.MODIFIED && it.newDate == date }
                    .forEach { exception ->
                        val originalEngagement = withContext(Dispatchers.IO) { engagementDao.getEngagementById(exception.originalEngagementId) }
                        if (originalEngagement != null && originalEngagement.isActive) {
                            val modifiedInstance = originalEngagement.copy(
                                startTime = exception.newStartTime!!,
                                durationMinutes = exception.newDurationMinutes ?: originalEngagement.durationMinutes,
                                notes = exception.newNotes ?: originalEngagement.notes
                            ).apply {
                                this.nextOccurrenceDate = date
                                this.startDateTime = LocalDateTime.of(date, this.startTime) // Add this line
                            }

                            allEventsForDate.add(ScheduleEvent.EngagementEvent(
                                id = modifiedInstance.engagementId, name = modifiedInstance.engagementName,
                                type = ScheduleEvent.ScheduleEventType.ENGAGEMENT,
                                date = date, startTime = modifiedInstance.startTime, durationMinutes = modifiedInstance.durationMinutes,
                                location = modifiedInstance.venue, notes = modifiedInstance.notes, originalItem = modifiedInstance,
                                isModified = true
                            ))
                        }
                    }

                // NEW: 3. Get Synced Calendar Events
                val startOfDayMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val endOfDayMillis = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val calendarEvents = withContext(Dispatchers.IO) {
                    syncedCalendarEventDao.getAllFutureEvents(startOfDayMillis).firstOrNull()?.filter {
                        it.startDateTimeMillis < endOfDayMillis
                    } ?: emptyList()
                }
                calendarEvents.forEach { event ->
                    val startDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(event.startDateTimeMillis), ZoneId.systemDefault())
                    val endDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(event.endDateTimeMillis), ZoneId.systemDefault())
                    val duration = ChronoUnit.MINUTES.between(startDateTime, endDateTime).toInt()

                    allEventsForDate.add(ScheduleEvent.CalendarEvent(
                        id = event.localId,
                        name = event.title,
                        type = ScheduleEvent.ScheduleEventType.CALENDAR,
                        date = date,
                        startTime = startDateTime.toLocalTime(),
                        durationMinutes = duration,
                        location = event.location,
                        notes = event.description,
                        originalItem = event
                    ))
                }


                // 4. Get Exams
                val exams = examDao.getExamsForSpecificDate(date)
                exams.forEach { exam ->
                    allEventsForDate.add(
                        ScheduleEvent.ExamEvent(
                            id = exam.examId,
                            name = exam.courseName,
                            type = ScheduleEvent.ScheduleEventType.EXAM,
                            date = exam.examDate,
                            startTime = exam.startTime,
                            durationMinutes = ChronoUnit.MINUTES.between(exam.startTime, exam.endTime).toInt(),
                            location = exam.venue,
                            notes = null,
                            originalItem = exam
                        )
                    )
                }

                // 5. Get Assignments
                val assignments = assignmentDao.getAssignmentsForSpecificDate(date)
                assignments.forEach { assignment ->
                    allEventsForDate.add(
                        ScheduleEvent.AssignmentEvent(
                            id = assignment.assignmentId,
                            name = assignment.assignmentName, // This 'name' is used for general event display, not the dialog
                            type = ScheduleEvent.ScheduleEventType.ASSIGNMENT,
                            date = assignment.dueDate,
                            startTime = assignment.submissionTime,
                            durationMinutes = 0, // Assignments are point events
                            location = null,
                            notes = null,
                            originalItem = assignment // Pass the full Assignment object
                        )
                    )
                }

                // 6. Get To-Do's (High and Medium Priority)
                val allTodos = withContext(Dispatchers.IO) {
                    // Collect the flow and get the latest list of todos
                    todoDao.getAllTodos().firstOrNull() ?: emptyList()
                }
                val relevantTodos = allTodos.filter { todo ->
                    // Filter for high/medium priority and due on the selected date
                    (todo.priority == "High" || todo.priority == "Medium") && todo.dueDate == date
                }
                relevantTodos.forEach { todo ->
                    // Use a default time if dueTime is null for consistent display
                    val todoTime = todo.dueTime ?: LocalTime.of(23, 59) // End of day if no specific time
                    allEventsForDate.add(
                        ScheduleEvent.TodoEvent(
                            id = todo.todoId,
                            name = todo.title,
                            type = ScheduleEvent.ScheduleEventType.TODO,
                            date = todo.dueDate ?: date, // Use actual due date or current date
                            startTime = todoTime,
                            durationMinutes = 0, // To-Do's are point events
                            location = null,
                            notes = todo.description,
                            priority = todo.priority,
                            originalItem = todo
                        )
                    )
                }

                // NEW: 7. Get Synced Google Tasks
                val googleTasks = withContext(Dispatchers.IO) {
                    syncedGoogleTaskDao.getAllDisplayableTasks(startOfDayMillis).firstOrNull()?.filter { task ->
                        task.dueDateTimeMillis != null &&
                                task.dueDateTimeMillis >= startOfDayMillis &&
                                task.dueDateTimeMillis < endOfDayMillis
                    } ?: emptyList()
                }
                googleTasks.forEach { task ->
                    val dueDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(task.dueDateTimeMillis!!), ZoneId.systemDefault())
                    allEventsForDate.add(ScheduleEvent.TaskEvent(
                        id = task.localId,
                        name = task.title,
                        date = date,
                        startTime = dueDateTime.toLocalTime(),
                        notes = task.notes,
                        originalItem = task
                        // Priority is hardcoded to "Medium" in the data class
                    ))
                }

                val actions = withContext(Dispatchers.IO) {
                    actionDao.getAllActivities().firstOrNull() ?: emptyList()
                }
                actions.forEach { action ->
                    if (action.actionId != null && !date.isBefore(action.startDate) && !date.isAfter(action.endDate)) {
                        val eventStartTime = if (date.isEqual(action.startDate)) action.startTime else LocalTime.MIN
                        val eventEndTime = if (date.isEqual(action.endDate)) action.endTime else LocalTime.of(23, 59)

                        if (!eventStartTime.isAfter(eventEndTime)) {
                            val duration = ChronoUnit.MINUTES.between(eventStartTime, eventEndTime).toInt()
                            allEventsForDate.add(ScheduleEvent.ActionEvent(
                                id = action.actionId,
                                name = action.actionName,
                                type = ScheduleEvent.ScheduleEventType.ACTION,
                                date = date,
                                startTime = eventStartTime,
                                durationMinutes = duration,
                                location = action.venue,
                                notes = action.description,
                                originalItem = action
                            ))
                        }
                    }
                }

                // Sort all events by start time
                val sortedEvents = allEventsForDate.sortedBy { it.startTime }

                withContext(Dispatchers.Main) {
                    if (sortedEvents.isEmpty()) {
                        Toast.makeText(this@ScheduleActivity, getString(R.string.no_events_found_for_day), Toast.LENGTH_SHORT).show()
                    }
                    scheduleView.events = sortedEvents

                    // Scroll to 05:30 on load or day selection
                    columnHeadersLayout.post {
                        val scrollTargetYInScheduleView = scheduleView.getScrollYForTime(5, 30)
                        val finalScrollY = scrollTargetYInScheduleView

                        scheduleScrollView.post {
                            val adjustedScrollY = finalScrollY.coerceAtLeast(0)
                            scheduleScrollView.scrollTo(0, adjustedScrollY)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ScheduleActivity, getString(R.string.schedule_view_error, e.localizedMessage), Toast.LENGTH_LONG).show()
                    Log.e("ScheduleActivity", "Error loading events for schedule view: ${e.message}", e)
                }
            }
        }
    }
}
