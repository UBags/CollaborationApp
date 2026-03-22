package com.costheta.cortexa

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.toColorInt
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.costheta.cortexa.action.ActionViewHolder
import com.costheta.cortexa.action.AddActionActivity
import com.costheta.cortexa.assignment.AddAssignmentActivity
import com.costheta.cortexa.assignment.AssignmentViewHolder
import com.costheta.cortexa.data.db.dao.ActionDao
import com.costheta.cortexa.data.db.dao.AssignmentDao
import com.costheta.cortexa.data.db.dao.AttachmentDao
import com.costheta.cortexa.data.db.dao.EngagementDao
import com.costheta.cortexa.data.db.dao.ExamDao
import com.costheta.cortexa.data.db.dao.TimetableDao
import com.costheta.cortexa.data.db.dao.TodoDao
// NEW: Import Synced DAOs and Models
import com.costheta.cortexa.data.db.dao.SyncedCalendarEventDao
import com.costheta.cortexa.data.db.dao.SyncedGoogleTaskDao
import com.costheta.cortexa.data.models.SyncedCalendarEvent
import com.costheta.cortexa.data.models.Action
import com.costheta.cortexa.data.models.Assignment
import com.costheta.cortexa.data.models.ClassSession
import com.costheta.cortexa.data.models.Engagement
import com.costheta.cortexa.data.models.Exam
import com.costheta.cortexa.data.models.ClassSessionExceptionType
import com.costheta.cortexa.data.models.EngagementException
import com.costheta.cortexa.data.models.EngagementExceptionType
import com.costheta.cortexa.data.models.ScheduleEvent
import com.costheta.cortexa.data.models.SessionException
import com.costheta.cortexa.data.models.SyncedGoogleTask
import com.costheta.cortexa.data.models.TodoItem
import com.costheta.cortexa.engagement.AddEngagementActivity
import com.costheta.cortexa.engagement.DisplayableItem
import com.costheta.cortexa.engagement.EngagementViewHolder
import com.costheta.cortexa.exam.AddExamActivity
import com.costheta.cortexa.exam.ExamViewHolder
import com.costheta.cortexa.external.calendar.ViewCalendarEventActivity
import com.costheta.cortexa.external.tasks.ViewGoogleTaskActivity
import com.costheta.cortexa.timetable.ClassSessionViewHolder
import com.costheta.cortexa.todo.AddTodoActivity
import com.costheta.cortexa.todo.DisplayableTask
import com.costheta.cortexa.todo.TodoViewHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt


// Extension function to append bold text to SpannableStringBuilder
// Moved outside the class to be a top-level function, making it accessible.
fun SpannableStringBuilder.appendBold(text: CharSequence): SpannableStringBuilder {
    append(text, StyleSpan(Typeface.BOLD), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    return this // Explicitly return this to allow chaining
}

// Data class to hold event data along with rendering information for overlap resolution
// This is now correctly placed at the top level to be accessible.
private data class EventRenderData(
    val event: ScheduleEvent,
    var horizontalTrack: Int = 0, // Which horizontal slot (0, 1, 2...) it occupies
    var totalTracksInOverlap: Int = 1, // Total number of concurrent events in its most crowded period
    val rect: RectF = RectF(), // Pre-allocated RectF for drawing and hit testing
    val isModified: Boolean = false // Flag to indicate if this event is a rescheduled instance
)

/**
 * Custom View for displaying a daily schedule with time slots and events.
 * It supports displaying Class Sessions, Exams, Assignments, Engagements, and To-Do items.
 */
class ScheduleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // DAOs will be initialized later via a setter or directly if passed from Activity
    private var timetableDao: TimetableDao? = null
    private var engagementDao: EngagementDao? = null
    // NEW: Add SyncedCalendarEventDao
    private var syncedCalendarEventDao: SyncedCalendarEventDao? = null
    private var syncedGoogleTaskDao: SyncedGoogleTaskDao? = null
    private var examDao: ExamDao? = null
    private var assignmentDao: AssignmentDao? = null
    private var todoDao: TodoDao? = null
    private var actionDao: ActionDao? = null
    private var attachmentDao: AttachmentDao? = null
    private var reloadScheduleCallback: (() -> Unit)? = null

    // Helpers
    private val notificationHelper: NotificationHelper by lazy { NotificationHelper(context.applicationContext) }
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd") // Added for reschedule dialog

    // MODIFIED: Updated setter to include SyncedCalendarEventDao and SyncedGoogleTaskDao
    fun setDaos(
        timetableDao: TimetableDao,
        assignmentDao: AssignmentDao,
        examDao: ExamDao,
        engagementDao: EngagementDao,
        syncedCalendarEventDao: SyncedCalendarEventDao, // New parameter
        syncedGoogleTaskDao: SyncedGoogleTaskDao, // New parameter
        todoDao: TodoDao,
        actionDao: ActionDao,
        attachmentDao: AttachmentDao,
        reloadSchedule: () -> Unit
    ) {
        this.timetableDao = timetableDao
        this.engagementDao = engagementDao
        this.syncedCalendarEventDao = syncedCalendarEventDao // Assign new DAO
        this.syncedGoogleTaskDao = syncedGoogleTaskDao // Assign new DAO
        this.examDao = examDao
        this.assignmentDao = assignmentDao
        this.todoDao = todoDao
        this.actionDao = actionDao
        this.attachmentDao = attachmentDao
        this.reloadScheduleCallback = reloadSchedule
    }


    var events: List<ScheduleEvent> = emptyList()
        set(value) {
            field = value
            // Ensure eventRenderDataList is updated when events change
            updateEventRenderDataList() // This now updates all event types' render data
            invalidate() // Request a redraw
        }

    private val timeLabels = (0..23).map { String.format(Locale.getDefault(), "%02d:00", it) } // Hourly labels 00:00 to 23:00

    private var columnWidth: Float // Width of each column (Lecture, Personal, Engagement, Exam)
    private var rowHeight: Float // Height of each hour slot

    private var headerHeight: Float = 0f // This will be 0 as headers are external now
    private var timeLabelColumnWidth: Float = dpToPx(60f) // Fixed width to match XML Space element

    // Variable to extend the view at top and bottom by half a cell height
    private var verticalExtensionHeight: Float = 0f

    // Corner radius for rounded rectangles, converted from dp to pixels
    private val cornerRadiusPx = dpToPx(8f)

    // Vertical padding for all boxes (top and bottom)
    private val verticalBoxPaddingPx = dpToPx(1f)

    // Horizontal padding for event boxes (each side)
    private val horizontalBoxPaddingPx = dpToPx(2f)

    // Paints for drawing
    private val linePaint = Paint().apply {
        color = Color.DKGRAY
        strokeWidth = 3f
    }
    private val timeTextPaint = Paint().apply {
        color = Color.BLACK
        textSize = dpToPx(16f)
        isAntiAlias = true
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.DEFAULT_BOLD
    }

    // Paint for event text to ensure it's black and centered and BOLD
    private val eventTextPaint = Paint().apply {
        color = Color.BLACK
        textSize = dpToPx(12f)
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    // Event fill paints (updated colors as per request)
    private val lecturePaint = Paint().apply { color = "#EAEAFB".toColorInt() } // Light Lavender
    private val personalPaint = Paint().apply { color = "#DCEFDD".toColorInt() } // Lighter Green
    private val engagementPaint = Paint().apply { color = "#EFEADC".toColorInt() } // Light Brown
    private val examPaint = Paint().apply { color = "#E6FFFF".toColorInt() } // Lighter Pale Turquoise
    private val actionPaint = Paint().apply { color = "#F0E68C".toColorInt() } // Khaki
    // NEW: Paint for Calendar Events
    private val calendarPaint = Paint().apply { color = "#FFDDC1".toColorInt() } // Light Peach


    // Assignment/To-Do specific drawing parameters and paints
    private val dotCircleRadius = 6f
    private val dotPointRadius = dpToPx(dotCircleRadius) // Smaller radius for all dots
    private val dotOuterRadius1 = dotPointRadius + dpToPx(1f) // Smaller radiating circle
    private val dotOuterRadius2 = dotPointRadius + dpToPx(2f) // Smaller radiating circle
    private val dotGap = dpToPx(dotCircleRadius + 1f) // Gap between dots
    private val dotTextSize = dpToPx(dotCircleRadius + 4f) // Smaller 'A'/'T' text size

    // Common paint for the text ('A' or 'T') on the dots for calculating text position
    private val dotTextPaint = Paint().apply {
        color = Color.BLACK // This color will be overridden by specific paints during drawText
        textSize = dotTextSize
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    // Assignment paints
    private val assignmentCirclePaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
    }
    private val assignmentTextPaint = Paint().apply {
        color = Color.WHITE // 'A' text color
        textSize = dotTextSize // Use the smaller text size
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val assignmentRadiatingCirclePaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1f)
        alpha = 128 // Semi-transparent
    }

    // To-Do High Priority paints (Pink)
    private val todoHighPriorityCirclePaint = Paint().apply {
        color = "#00FF33".toColorInt() // Pink
        style = Paint.Style.FILL
    }
    private val todoHighPriorityTextPaint = Paint().apply {
        color = Color.BLACK // 'T' text color
        textSize = dotTextSize
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val todoHighPriorityRadiatingCirclePaint = Paint().apply {
        color = "#00FF33".toColorInt() // Pink
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1f)
        alpha = 128
    }

    // To-Do Medium Priority paints (Yellow)
    private val todoMediumPriorityCirclePaint = Paint().apply {
        color = "#0033FF".toColorInt()
        style = Paint.Style.FILL
    }
    private val todoMediumPriorityTextPaint = Paint().apply {
        color = Color.WHITE // 'T' text color
        textSize = dotTextSize
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val todoMediumPriorityRadiatingCirclePaint = Paint().apply {
        color = "#0033FF".toColorInt()
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1f)
        alpha = 128
    }

    // NEW: Google Task paints (Google Blue)
    private val taskCirclePaint = Paint().apply {
        color = "#4285F4".toColorInt() // Google Blue
        style = Paint.Style.FILL
    }
    private val taskTextPaint = Paint().apply {
        color = Color.WHITE // 'T' text color
        textSize = dotTextSize
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val taskRadiatingCirclePaint = Paint().apply {
        color = "#4285F4".toColorInt() // Google Blue
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1f)
        alpha = 128
    }

    // Constants for assignment/todo positioning
    private val MAX_DOT_EVENTS_PER_LINE = 3 // Max assignments/todos per horizontal line
    private val DOT_EVENT_VERTICAL_LINE_OFFSET = dpToPx(15f) // CHANGED: Increased from 12f to 15f for more vertical gap

    // Event border paints (updated colors as per request)
    private val lectureBorderPaint = Paint().apply {
        color = "#9370DB".toColorInt() // Dark Lavender
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(2f)
    }
    private val personalBorderPaint = Paint().apply {
        color = "#388E3C".toColorInt() // Lighter Dark Green
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(2f)
    }
    private val engagementBorderPaint = Paint().apply {
        color = "#7A3402".toColorInt() // Dark Brown
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(2f)
    }
    private val examBorderPaint = Paint().apply {
        color = "#1C97A8".toColorInt() // Lighter Teal
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(2f)
    }
    private val actionBorderPaint = Paint().apply {
        color = "#BDB76B".toColorInt() // DarkKhaki
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(2f)
    }
    // NEW: Border paint for Calendar Events
    private val calendarBorderPaint = Paint().apply {
        color = "#E57373".toColorInt() // Darker Peach
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(2f)
    }

    // Paint for the border of a modified (rescheduled) class session
    private val modifiedSessionBorderPaint = Paint().apply {
        color = "#FF8C00".toColorInt() // DarkOrange color to indicate a change
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(2.5f)
        pathEffect = DashPathEffect(floatArrayOf(dpToPx(4f), dpToPx(4f)), 0f) // Dashed line effect
    }

    // List to store the drawn event rectangles and their associated events for click detection
    // This list will now store EventRenderData objects for ALL event types
    private val eventRenderDataList = mutableListOf<EventRenderData>()

    init {
        columnWidth = 0f
        rowHeight = 0f
        isClickable = true
        isFocusable = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        headerHeight = 0f // No internal header space needed

        val baseHourHeightPx = dpToPx(80f)
        rowHeight = baseHourHeightPx
        verticalExtensionHeight = rowHeight / 2f

        // Adjust total drawable height to include half a cell height at top and bottom
        // Total height = 24 hours * rowHeight + top_extension + bottom_extension
        val totalDrawableHeight = (24 * rowHeight) + (2 * verticalExtensionHeight)
        setMeasuredDimension(w, totalDrawableHeight.roundToInt())

        val eventColumnsTotalWidth = w - timeLabelColumnWidth
        columnWidth = eventColumnsTotalWidth / 4f // Changed to 4 columns for Lecture, Personal, Engagement, Exam

        // Recalculate event render data when view size changes
        updateEventRenderDataList()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val baseHourHeightPx = dpToPx(80f)
        rowHeight = baseHourHeightPx // Ensure rowHeight is set consistently
        verticalExtensionHeight = rowHeight / 2f // Ensure verticalExtensionHeight is set consistently

        // Calculate the total height needed: 24 hours * rowHeight + top_extension + bottom_extension
        val totalContentHeight = (24 * rowHeight) + (2 * verticalExtensionHeight)

        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = resolveSize(totalContentHeight.roundToInt(), heightMeasureSpec)

        setMeasuredDimension(width, height)
    }

    private fun findEventClusters(events: List<ScheduleEvent>): List<List<ScheduleEvent>> {
        if (events.isEmpty()) return emptyList()

        val clusters = mutableListOf<List<ScheduleEvent>>()
        val visited = mutableSetOf<ScheduleEvent>()

        fun doEventsOverlap(event1: ScheduleEvent, event2: ScheduleEvent): Boolean {
            return event1.getStartDateTime().isBefore(event2.getEndDateTime()) &&
                    event2.getStartDateTime().isBefore(event1.getEndDateTime())
        }

        for (event in events) {
            if (event in visited) continue

            val currentCluster = mutableListOf<ScheduleEvent>()
            val toVisit = ArrayDeque<ScheduleEvent>()

            toVisit.add(event)
            visited.add(event)

            while (toVisit.isNotEmpty()) {
                val current = toVisit.removeFirst()
                currentCluster.add(current)

                events.forEach { other ->
                    if (other !in visited && doEventsOverlap(current, other)) {
                        visited.add(other)
                        toVisit.add(other)
                    }
                }
            }
            clusters.add(currentCluster)
        }
        return clusters
    }

    /**
     * Calculates and updates the `eventRenderDataList` with horizontal track and total tracks
     * information for overlap resolution. This method implements a "slot packing" algorithm
     * for regular events and positions assignments/todos based on the stacking logic.
     */
    private fun updateEventRenderDataList() {
        eventRenderDataList.clear() // Clear previous data

        // Separate dot events from block events
        val blockEvents = mutableListOf<ScheduleEvent>()
        val dotEvents = mutableListOf<ScheduleEvent>()

        events.forEach { event ->
            when (event.type) {
                ScheduleEvent.ScheduleEventType.ASSIGNMENT,
                ScheduleEvent.ScheduleEventType.TODO,
                ScheduleEvent.ScheduleEventType.TASK -> dotEvents.add(event)
                else -> blockEvents.add(event)
            }
        }

        // MODIFIED: Group by a calculated column index for overlap resolution, clubbing Calendar with Engagement
        val blockEventsByColumn = blockEvents.groupBy { event ->
            when (event.type) {
                ScheduleEvent.ScheduleEventType.LECTURE,
                ScheduleEvent.ScheduleEventType.EXAM -> 0
                ScheduleEvent.ScheduleEventType.PERSONAL -> 1
                ScheduleEvent.ScheduleEventType.ENGAGEMENT,
                ScheduleEvent.ScheduleEventType.CALENDAR -> 2 // CALENDAR events go into the ENGAGEMENT column
                ScheduleEvent.ScheduleEventType.ACTION -> 3
                else -> 3 // Fallback column
            }
        }

        // 1. Process Block Events (Lectures, Personal, Engagements, Exams, Calendar)
        blockEventsByColumn.forEach { (columnIndex, columnEvents) ->
            val eventClusters = findEventClusters(columnEvents)

            eventClusters.forEach { cluster ->
                val sortedClusterEvents = cluster.sortedBy { it.getStartDateTime() }
                val slots = mutableListOf<MutableList<ScheduleEvent>>()
                val eventToSlotMap = mutableMapOf<ScheduleEvent, Int>()

                sortedClusterEvents.forEach { event ->
                    var assignedSlotIndex = -1
                    for (i in 0 until slots.size) {
                        val slot = slots[i]
                        val canPlaceInSlot = slot.all { existingEventInSlot ->
                            !existingEventInSlot.getStartDateTime().isBefore(event.getEndDateTime()) ||
                                    !event.getStartDateTime().isBefore(existingEventInSlot.getEndDateTime())
                        }
                        if (canPlaceInSlot) {
                            slot.add(event)
                            assignedSlotIndex = i
                            break
                        }
                    }
                    if (assignedSlotIndex == -1) {
                        assignedSlotIndex = slots.size
                        slots.add(mutableListOf(event))
                    }
                    eventToSlotMap[event] = assignedSlotIndex
                }

                val maxSlotsUsedInCluster = slots.size.coerceAtLeast(1)

                sortedClusterEvents.forEach { event ->
                    val assignedSlot = eventToSlotMap[event] ?: 0
                    val top = verticalExtensionHeight + (event.startTime.hour + event.startTime.minute / 60f) * rowHeight + verticalBoxPaddingPx
                    val bottom = verticalExtensionHeight + (event.endTime.hour + event.endTime.minute / 60f) * rowHeight - verticalBoxPaddingPx

                    val baseLeft = timeLabelColumnWidth + (columnIndex * columnWidth)
                    val subColumnWidth = columnWidth / maxSlotsUsedInCluster
                    val left = baseLeft + (assignedSlot * subColumnWidth) + horizontalBoxPaddingPx
                    val right = left + subColumnWidth - (2 * horizontalBoxPaddingPx)

                    val rect = RectF(left, top, right, bottom)
                    val isModified = when(event) {
                        is ScheduleEvent.ClassSessionEvent -> event.isModified
                        is ScheduleEvent.EngagementEvent -> event.isModified
                        else -> false
                    }
                    eventRenderDataList.add(EventRenderData(event, assignedSlot, maxSlotsUsedInCluster, rect, isModified))
                }
            }
        }

        // 2. Process Dot Events (Assignments and To-Do's)
        val combinedDotEventsGroupedByTime = dotEvents
            .groupBy { Pair(it.date, it.startTime) }

        combinedDotEventsGroupedByTime.forEach { (timeSlot, eventsInGroup) ->
            val sortedDotEventsInGroup = eventsInGroup.sortedWith(compareBy<ScheduleEvent> {
                when (it.type) {
                    ScheduleEvent.ScheduleEventType.TODO -> 0
                    ScheduleEvent.ScheduleEventType.ASSIGNMENT -> 1
                    ScheduleEvent.ScheduleEventType.TASK -> 2 // Add Task to sorting
                    else -> 3
                }
            }.thenBy { it.name })

            val baseCenterY = verticalExtensionHeight + (timeSlot.second.hour * rowHeight) + (timeSlot.second.minute * rowHeight / 60f)

            sortedDotEventsInGroup.forEachIndexed { indexInGroup, dotEvent ->
                val currentLine = indexInGroup / MAX_DOT_EVENTS_PER_LINE
                val positionInLine = indexInGroup % MAX_DOT_EVENTS_PER_LINE
                val basePointX = timeLabelColumnWidth - dpToPx(15f)
                val horizontalOffsetPerDot = (2 * dotPointRadius) + dotGap
                val adjustedPointX = basePointX - (positionInLine * horizontalOffsetPerDot)
                val adjustedCenterY = baseCenterY + (currentLine * DOT_EVENT_VERTICAL_LINE_OFFSET)
                val textY = adjustedCenterY - ((dotTextPaint.descent() + dotTextPaint.ascent()) / 2)
                val hitTestRect = RectF(
                    adjustedPointX - dotOuterRadius2,
                    adjustedCenterY - dotOuterRadius2,
                    adjustedPointX + dotOuterRadius2,
                    adjustedCenterY + dotOuterRadius2
                )
                eventRenderDataList.add(EventRenderData(dotEvent, horizontalTrack = indexInGroup, rect = hitTestRect))
            }
        }
    }


    /**
     * Returns the Y-coordinate for the top of a given time (hour and minute), suitable for scrolling.
     * This value is relative to the top of the ScheduleView's drawable area.
     * @param hour The hour (0-23).
     * @param minute The minute (0-59).
     * @return The Y-coordinate in pixels.
     */
    fun getScrollYForTime(hour: Int, minute: Int = 0): Int {
        val totalMinutes = hour * 60 + minute
        val pixelsPerMinute = rowHeight / 60f
        // Adjust scroll target by verticalExtensionHeight
        return (totalMinutes * pixelsPerMinute + verticalExtensionHeight).roundToInt().coerceAtLeast(0)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event?.action == MotionEvent.ACTION_UP) {
            val x = event.x
            val y = event.y
            // Adjust touch coordinates by scroll position if the view is scrollable
            val adjustedX = x + scrollX
            val adjustedY = y + scrollY

            for (renderData in eventRenderDataList.asReversed()) { // Iterate reversed to catch topmost event first
                if (renderData.rect.contains(adjustedX, adjustedY)) {
                    performClick()
                    showEventDetailsDialog(renderData.event)
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    /**
     * Shows an AlertDialog with the details of the clicked schedule event,
     * using specific layouts for each event type.
     * @param event The ScheduleEvent that was clicked.
     */
    @SuppressLint("InflateParams")
    private fun showEventDetailsDialog(event: ScheduleEvent) {
        val dialogBuilder = AlertDialog.Builder(context)
        val alertDialog = dialogBuilder.create()

        // Custom title view (black bar with white font)
        val titleView = TextView(context).apply {
            text = when (event.type) {
                ScheduleEvent.ScheduleEventType.LECTURE -> context.getString(R.string.schedule_dialog_lecture_title)
                ScheduleEvent.ScheduleEventType.PERSONAL -> context.getString(R.string.schedule_dialog_personal_title)
                ScheduleEvent.ScheduleEventType.ENGAGEMENT -> context.getString(R.string.schedule_dialog_engagement_title)
                ScheduleEvent.ScheduleEventType.EXAM -> context.getString(R.string.schedule_dialog_exam_title)
                ScheduleEvent.ScheduleEventType.ASSIGNMENT -> context.getString(R.string.schedule_dialog_assignment_title)
                ScheduleEvent.ScheduleEventType.TODO -> context.getString(R.string.schedule_dialog_todo_title)
                ScheduleEvent.ScheduleEventType.ACTION -> context.getString(R.string.schedule_dialog_action_title)
                ScheduleEvent.ScheduleEventType.CALENDAR -> context.getString(R.string.schedule_dialog_calendar_event_title)
                ScheduleEvent.ScheduleEventType.TASK -> context.getString(R.string.schedule_dialog_task_title)
            }
            setBackgroundColor(Color.BLACK)
            setTextColor(Color.WHITE)
            textSize = 20f
            setPadding(dpToPx(16f).toInt(), dpToPx(16f).toInt(), dpToPx(16f).toInt(), dpToPx(16f).toInt())
            gravity = Gravity.CENTER
        }
        alertDialog.setCustomTitle(titleView)

        val fullDialogContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Add event-specific details and action buttons
        when (event.type) {
            ScheduleEvent.ScheduleEventType.LECTURE, ScheduleEvent.ScheduleEventType.PERSONAL -> {
                val classSession = event.originalItem as ClassSession
                val cardView = LayoutInflater.from(context).inflate(R.layout.timetable_class_session_item, fullDialogContent, false)

                // Define lambdas for the edit and cancel instance actions
                val onEdit = { session: ClassSession ->
                    alertDialog.dismiss()
                    showRescheduleDialog(session, event.date)
                }
                val onCancel = { session: ClassSession ->
                    alertDialog.dismiss()
                    showCancelConfirmationDialog(session, event.date)
                }

                // Pass the lambdas to the ViewHolder
                val viewHolder = ClassSessionViewHolder(
                    cardView,
                    context,
                    { s, n, o -> showNotificationTimePickerDialog(s, n, o) },
                    { s, b -> updateSessionSilenceStatus(s, b) },
                    onEdit,
                    onCancel
                )

                findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
                    val timetable = withContext(Dispatchers.IO) { classSession.timetableId?.let {
                        timetableDao?.getTimetableById(it)
                    } }
                    if (timetable != null) {
                        // Pass true to show the edit/cancel icons for this specific instance
                        viewHolder.bind(classSession, timetable, showInstanceActionIcons = true)
                        val dayTimeTextView = cardView.findViewById<TextView>(R.id.dayTimeTextView)
                        dayTimeTextView.visibility = View.GONE
                    }
                }

                val layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                val verticalMargin = dpToPx(8f).toInt()
                val horizontalMargin = dpToPx(10f).toInt()
                layoutParams.setMargins(horizontalMargin, verticalMargin, horizontalMargin, verticalMargin)
                cardView.layoutParams = layoutParams

                fullDialogContent.addView(cardView)
            }
            ScheduleEvent.ScheduleEventType.ENGAGEMENT, ScheduleEvent.ScheduleEventType.CALENDAR -> { // MODIFIED: Handle Calendar events here too
                val cardView = LayoutInflater.from(context).inflate(R.layout.engagement_item, fullDialogContent, false)
                val viewHolder = EngagementViewHolder(cardView)

                // This wrapper allows us to reuse the EngagementViewHolder's logic
                val displayableItem = if (event.type == ScheduleEvent.ScheduleEventType.CALENDAR) {
                    DisplayableItem.CalendarEvent(event.originalItem as SyncedCalendarEvent)
                } else {
                    DisplayableItem.AppEngagement(event.originalItem as Engagement)
                }

                // Define click handlers
                val onEditClick: (DisplayableItem) -> Unit = { item ->
                    alertDialog.dismiss()
                    if (item is DisplayableItem.CalendarEvent) {
                        val intent = Intent(context, ViewCalendarEventActivity::class.java).apply {
                            putExtra("EVENT_LOCAL_ID", item.event.localId)
                        }
                        (context as? Activity)?.startActivity(intent)
                    } else if (item is DisplayableItem.AppEngagement) {
                        item.engagement.engagementId?.let { id ->
                            val intent = Intent(context, AddEngagementActivity::class.java).apply {
                                putExtra("ENGAGEMENT_ID", id)
                            }
                            (context as? Activity)?.startActivity(intent)
                        }
                    }
                }

                val onCancelInstanceClick: (Engagement) -> Unit = { engagement ->
                    alertDialog.dismiss()
                    showCancelEngagementConfirmationDialog(engagement, event.date)
                }

                val onRescheduleInstanceClick: (Engagement) -> Unit = { engagement ->
                    alertDialog.dismiss()
                    showRescheduleEngagementDialog(engagement, event.date)
                }

                val onDeleteSeriesClick: (DisplayableItem) -> Unit = { item ->
                    alertDialog.dismiss()
                    if(item is DisplayableItem.AppEngagement) {
                        showDeleteConfirmationDialog(item.engagement)
                    }
                }


                viewHolder.bind(
                    item = displayableItem,
                    attachmentDao = this.attachmentDao!!,
                    coroutineScope = findViewTreeLifecycleOwner()!!.lifecycleScope,
                    onDeleteClick = onDeleteSeriesClick,
                    onEditClick = onEditClick,
                    onCancelInstanceClick = onCancelInstanceClick,
                    onRescheduleInstanceClick = onRescheduleInstanceClick
                )
                fullDialogContent.addView(cardView)
            }


            ScheduleEvent.ScheduleEventType.EXAM -> {
                val exam = event.originalItem as Exam
                val cardView = LayoutInflater.from(context).inflate(R.layout.exam_item, null)
                val viewHolder = ExamViewHolder(cardView)
                val onEditClick = { ex: Exam ->
                    ex.examId?.let { id ->
                        val intent = Intent(context, AddExamActivity::class.java).apply {
                            putExtra("EXAM_ID", id)
                        }
                        (context as? Activity)?.startActivity(intent)
                    }
                    alertDialog.dismiss()
                }
                val onDeleteClick = { ex: Exam ->
                    showDeleteConfirmationDialog(ex)
                    alertDialog.dismiss()
                }
                viewHolder.bind(
                    exam = exam,
                    attachmentDao = this.attachmentDao!!,
                    coroutineScope = findViewTreeLifecycleOwner()!!.lifecycleScope,
                    onDeleteClick = onDeleteClick,
                    onEditClick = onEditClick
                )
                val layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                val verticalMargin = dpToPx(8f).toInt()
                val horizontalMargin = dpToPx(10f).toInt()
                layoutParams.setMargins(horizontalMargin, verticalMargin, horizontalMargin, verticalMargin)
                cardView.layoutParams = layoutParams
                fullDialogContent.addView(cardView)
            }
            ScheduleEvent.ScheduleEventType.ASSIGNMENT -> {
                val assignment = event.originalItem as Assignment
                val cardView = LayoutInflater.from(context).inflate(R.layout.assignment_item, null)
                val viewHolder = AssignmentViewHolder(cardView)

                val onEditClick = { ass: Assignment ->
                    ass.assignmentId?.let { id ->
                        val intent = Intent(context, AddAssignmentActivity::class.java).apply {
                            putExtra("ASSIGNMENT_ID", id)
                        }
                        (context as? Activity)?.startActivity(intent)
                    }
                    alertDialog.dismiss()
                }

                val onDeleteClick = { ass: Assignment ->
                    showDeleteConfirmationDialog(ass)
                    alertDialog.dismiss()
                }

                val onDoneClick: (Assignment, Boolean) -> Unit = { ass, isDone ->
                    handleAssignmentDoneToggle(ass, isDone)
                    alertDialog.dismiss()
                    reloadScheduleCallback?.invoke()
                }

                viewHolder.bind(
                    assignment = assignment,
                    attachmentDao = this.attachmentDao!!,
                    coroutineScope = findViewTreeLifecycleOwner()!!.lifecycleScope,
                    onDeleteClick = onDeleteClick,
                    onEditClick = onEditClick,
                    onDoneClick = onDoneClick,
                    showActionButtons = true
                )

                val layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                val verticalMargin = dpToPx(8f).toInt()
                val horizontalMargin = dpToPx(10f).toInt()
                layoutParams.setMargins(horizontalMargin, verticalMargin, horizontalMargin, verticalMargin)
                cardView.layoutParams = layoutParams

                fullDialogContent.addView(cardView)
            }
            ScheduleEvent.ScheduleEventType.TODO, ScheduleEvent.ScheduleEventType.TASK -> {
                val cardView = LayoutInflater.from(context).inflate(R.layout.todo_item, null)
                val viewHolder = TodoViewHolder(cardView)

                val displayableTask = if (event.type == ScheduleEvent.ScheduleEventType.TASK) {
                    DisplayableTask.GoogleTask(event.originalItem as SyncedGoogleTask)
                } else {
                    DisplayableTask.AppTodo(event.originalItem as TodoItem)
                }

                val onEditClick: (DisplayableTask) -> Unit = { item ->
                    alertDialog.dismiss()
                    if (item is DisplayableTask.GoogleTask) {
                        val intent = Intent(context, ViewGoogleTaskActivity::class.java).apply {
                            putExtra("TASK_LOCAL_ID", item.task.localId)
                        }
                        (context as? Activity)?.startActivity(intent)
                    } else if (item is DisplayableTask.AppTodo) {
                        item.todo.todoId?.let { id ->
                            val intent = Intent(context, AddTodoActivity::class.java).apply {
                                putExtra("TODO_ID", id)
                            }
                            (context as? Activity)?.startActivity(intent)
                        }
                    }
                }

                val onDeleteClick = { item: DisplayableTask ->
                    if (item is DisplayableTask.AppTodo) {
                        showDeleteConfirmationDialog(item.todo)
                    }
                    alertDialog.dismiss()
                }

                val onToggleComplete : (item: DisplayableTask, isCompleted: Boolean) -> Unit = { item, isCompleted ->
                    if (item is DisplayableTask.AppTodo) {
                        handleTodoCompletionToggle(item.todo, isCompleted)
                    }
                    alertDialog.dismiss()
                    reloadScheduleCallback?.invoke()
                }

                viewHolder.bind(
                    displayableTask = displayableTask,
                    attachmentDao = this.attachmentDao!!,
                    coroutineScope = findViewTreeLifecycleOwner()!!.lifecycleScope,
                    onDeleteClick = onDeleteClick,
                    onEditClick = onEditClick,
                    onToggleComplete = onToggleComplete
                )

                val layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                val verticalMargin = dpToPx(8f).toInt()
                val horizontalMargin = dpToPx(10f).toInt()
                layoutParams.setMargins(horizontalMargin, verticalMargin, horizontalMargin, verticalMargin)
                cardView.layoutParams = layoutParams

                fullDialogContent.addView(cardView)
            }
            ScheduleEvent.ScheduleEventType.ACTION -> {
                val action = event.originalItem as Action
                val cardView = LayoutInflater.from(context).inflate(R.layout.action_item, null)
                val viewHolder = ActionViewHolder(cardView)

                val onEditClick = { act: Action ->
                    act.actionId?.let { id ->
                        val intent = Intent(context, AddActionActivity::class.java).apply {
                            putExtra("ACTION_ID", id)
                        }
                        (context as? Activity)?.startActivity(intent)
                    }
                    alertDialog.dismiss()
                }

                val onDeleteClick = { act: Action ->
                    showDeleteConfirmationDialog(act)
                    alertDialog.dismiss()
                }

                val onDoneClick: (act: Action, isDone: Boolean) -> Unit = { act, isDone ->
                    handleActionDoneToggle(act, isDone)
                    alertDialog.dismiss()
                    reloadScheduleCallback?.invoke()
                }

                viewHolder.bind(
                    actionItem = action,
                    attachmentDao = this.attachmentDao!!,
                    coroutineScope = findViewTreeLifecycleOwner()!!.lifecycleScope,
                    onDeleteClick = onDeleteClick,
                    onEditClick = onEditClick,
                    onDoneClick = onDoneClick
                )

                val layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                val verticalMargin = dpToPx(8f).toInt()
                val horizontalMargin = dpToPx(10f).toInt()
                layoutParams.setMargins(horizontalMargin, verticalMargin, horizontalMargin, verticalMargin)
                cardView.layoutParams = layoutParams

                fullDialogContent.addView(cardView)
            }
        }

        alertDialog.setView(fullDialogContent)
        alertDialog.window?.decorView?.setBackgroundResource(R.drawable.rounded_dialog_background)

        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, context.getString(android.R.string.ok)) { dialog, _ ->
            dialog.dismiss()
        }
        alertDialog.show()
    }

    // Function to show a confirmation dialog before cancelling a single session instance
    private fun showCancelConfirmationDialog(session: ClassSession, date: LocalDate) {
        AlertDialog.Builder(context)
            .setTitle("Cancel Session Instance")
            .setMessage("Are you sure you want to cancel the '${session.courseName}' session for $date? This cannot be undone.")
            .setPositiveButton("Confirm") { dialog, _ ->
                findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
                    try {
                        val exception = SessionException(
                            originalSessionId = session.sessionId!!,
                            originalDate = date,
                            classSessionExceptionType = ClassSessionExceptionType.CANCELLED,
                            newDate = null,
                            newStartTime = null,
                            newDurationMinutes = null,
                            newNotes = null
                        )
                        // Fetch the parent timetable to access notification settings
                        val timetable = withContext(Dispatchers.IO) {
                            session.timetableId?.let { timetableDao?.getTimetableById(it) }
                        }
                        withContext(Dispatchers.IO) {
                            timetableDao?.upsertSessionException(exception)
                        }

                        // Cancel the notifications for the original instance
                        if (timetable != null) {
                            notificationHelper.cancelSingleInstanceNotifications(session, timetable, date)
                        }

                        Toast.makeText(context, "Session instance cancelled.", Toast.LENGTH_SHORT).show()
                        reloadScheduleCallback?.invoke()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Failed to cancel session: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Back", null)
            .show()
    }

    // Function to show a dialog for rescheduling a single session instance
    private fun showRescheduleDialog(session: ClassSession, originalDate: LocalDate) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.timetable_class_session_reschedule_dialog, null)
        val dateEditText: EditText = dialogView.findViewById(R.id.rescheduleDateEditText)
        val timeEditText: EditText = dialogView.findViewById(R.id.rescheduleTimeEditText)
        val endTimeEditText: EditText = dialogView.findViewById(R.id.rescheduleEndTimeEditText)
        val notesEditText: EditText = dialogView.findViewById(R.id.rescheduleNotesEditText)

        var selectedDate: LocalDate = originalDate
        var selectedTime: LocalTime = session.startTime
        var selectedEndTime: LocalTime = session.startTime.plusMinutes(session.durationMinutes.toLong())
        // Store the original duration
        val originalDurationMinutes = session.durationMinutes.toLong()

        dateEditText.setText(selectedDate.format(dateFormatter))
        timeEditText.setText(selectedTime.format(timeFormatter))
        endTimeEditText.setText(selectedEndTime.format(timeFormatter))

        dateEditText.setOnClickListener {
            DatePickerDialog(context, { _, year, month, dayOfMonth ->
                selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
                dateEditText.setText(selectedDate.format(dateFormatter))
            }, selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth).show()
        }

        // The start time picker now updates the end time automatically.
        timeEditText.setOnClickListener {
            TimePickerDialog(context, { _, hourOfDay, minute ->
                selectedTime = LocalTime.of(hourOfDay, minute)
                timeEditText.setText(selectedTime.format(timeFormatter))

                // Automatically update end time based on original duration
                selectedEndTime = selectedTime.plusMinutes(originalDurationMinutes)
                endTimeEditText.setText(selectedEndTime.format(timeFormatter))

            }, selectedTime.hour, selectedTime.minute, true).show()
        }

        endTimeEditText.setOnClickListener {
            TimePickerDialog(context, { _, hourOfDay, minute ->
                selectedEndTime = LocalTime.of(hourOfDay, minute)
                endTimeEditText.setText(selectedEndTime.format(timeFormatter))
            }, selectedEndTime.hour, selectedEndTime.minute, true).show()
        }

        AlertDialog.Builder(context)
            .setTitle("Reschedule Session Instance")
            .setView(dialogView)
            .setPositiveButton("Save") { dialog, _ ->
                if (selectedEndTime.isBefore(selectedTime) || selectedEndTime == selectedTime) {
                    Toast.makeText(context, "End time must be after start time.", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }

                val newNotes = notesEditText.text.toString().trim()
                val newDuration = Duration.between(selectedTime, selectedEndTime).toMinutes().toInt()

                findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
                    try {
                        val exception = SessionException(
                            originalSessionId = session.sessionId!!,
                            originalDate = originalDate,
                            classSessionExceptionType = ClassSessionExceptionType.MODIFIED,
                            newDate = selectedDate,
                            newStartTime = selectedTime,
                            newDurationMinutes = newDuration,
                            newNotes = newNotes.ifBlank { null }
                        )
                        // Fetch the parent timetable to access notification settings
                        val timetable = withContext(Dispatchers.IO) {
                            session.timetableId?.let { timetableDao?.getTimetableById(it) }
                        }
                        withContext(Dispatchers.IO) {
                            timetableDao?.upsertSessionException(exception)
                        }

                        // Cancel old notifications and schedule new ones
                        if (timetable != null) {
                            // 1. Cancel notifications for the original date
                            notificationHelper.cancelSingleInstanceNotifications(session, timetable, originalDate)
                            // 2. Schedule notifications for the new date and time
                            notificationHelper.scheduleSingleInstanceNotifications(session, timetable, selectedDate, selectedTime)
                        }

                        Toast.makeText(context, "Session instance rescheduled.", Toast.LENGTH_SHORT).show()
                        reloadScheduleCallback?.invoke()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Failed to reschedule session: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCancelEngagementConfirmationDialog(engagement: Engagement, date: LocalDate) {
        AlertDialog.Builder(context)
            .setTitle("Cancel Engagement Instance")
            .setMessage("Are you sure you want to cancel the '${engagement.engagementName}' for $date? This cannot be undone.")
            .setPositiveButton("Confirm") { dialog, _ ->
                findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
                    try {
                        val exception = EngagementException(
                            originalEngagementId = engagement.engagementId!!,
                            originalDate = date,
                            engagementExceptionType = EngagementExceptionType.CANCELLED,
                            newDate = null, newStartTime = null, newDurationMinutes = null, newNotes = null
                        )
                        withContext(Dispatchers.IO) {
                            engagementDao?.upsertEngagementException(exception)
                        }
                        // TODO: Cancel notifications for this specific instance if needed
                        Toast.makeText(context, "Engagement instance cancelled.", Toast.LENGTH_SHORT).show()
                        reloadScheduleCallback?.invoke()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Failed to cancel engagement: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Back", null)
            .show()
    }

    private fun showRescheduleEngagementDialog(engagement: Engagement, originalDate: LocalDate) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.engagement_reschedule_dialog, null)
        val dateEditText: EditText = dialogView.findViewById(R.id.rescheduleDateEditText)
        val timeEditText: EditText = dialogView.findViewById(R.id.rescheduleStartTimeEditText)
        val endTimeEditText: EditText = dialogView.findViewById(R.id.rescheduleEndTimeEditText)

        var selectedDate: LocalDate = originalDate
        var selectedTime: LocalTime = engagement.startTime
        var selectedEndTime: LocalTime = engagement.startTime.plusMinutes(engagement.durationMinutes.toLong())
        val originalDurationMinutes = engagement.durationMinutes.toLong()

        dateEditText.setText(selectedDate.format(dateFormatter))
        timeEditText.setText(selectedTime.format(timeFormatter))
        endTimeEditText.setText(selectedEndTime.format(timeFormatter))

        dateEditText.setOnClickListener {
            DatePickerDialog(context, { _, year, month, dayOfMonth ->
                selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
                dateEditText.setText(selectedDate.format(dateFormatter))
            }, selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth).show()
        }

        timeEditText.setOnClickListener {
            TimePickerDialog(context, { _, hourOfDay, minute ->
                selectedTime = LocalTime.of(hourOfDay, minute)
                timeEditText.setText(selectedTime.format(timeFormatter))
                selectedEndTime = selectedTime.plusMinutes(originalDurationMinutes)
                endTimeEditText.setText(selectedEndTime.format(timeFormatter))
            }, selectedTime.hour, selectedTime.minute, true).show()
        }

        endTimeEditText.setOnClickListener {
            TimePickerDialog(context, { _, hourOfDay, minute ->
                selectedEndTime = LocalTime.of(hourOfDay, minute)
                endTimeEditText.setText(selectedEndTime.format(timeFormatter))
            }, selectedEndTime.hour, selectedEndTime.minute, true).show()
        }

        AlertDialog.Builder(context)
            .setTitle("Reschedule Engagement Instance")
            .setView(dialogView)
            .setPositiveButton("Save") { dialog, _ ->
                if (selectedEndTime.isBefore(selectedTime) || selectedEndTime == selectedTime) {
                    Toast.makeText(context, "End time must be after start time.", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }

                val newDuration = Duration.between(selectedTime, selectedEndTime).toMinutes().toInt()

                findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
                    try {
                        val exception = EngagementException(
                            originalEngagementId = engagement.engagementId!!,
                            originalDate = originalDate,
                            engagementExceptionType = EngagementExceptionType.MODIFIED,
                            newDate = selectedDate,
                            newStartTime = selectedTime,
                            newDurationMinutes = newDuration,
                            newNotes = engagement.notes // Assuming notes don't change in this dialog
                        )
                        withContext(Dispatchers.IO) {
                            engagementDao?.upsertEngagementException(exception)
                        }
                        // TODO: Cancel old and schedule new notifications for this instance
                        Toast.makeText(context, "Engagement instance rescheduled.", Toast.LENGTH_SHORT).show()
                        reloadScheduleCallback?.invoke()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Failed to reschedule engagement: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showNotificationTimePickerDialog(session: ClassSession, notificationNumber: Int, currentOffset: Int) {
        val initialTime = session.startTime.minusMinutes(currentOffset.toLong())
        val hour = initialTime.hour
        val minute = initialTime.minute

        TimePickerDialog(context, { _, h, m ->
            val selectedNotifTime = LocalTime.of(h, m)
            val newOffsetMinutes = abs(Duration.between(session.startTime, selectedNotifTime).toMinutes().toInt())
            updateNotificationOffset(session, notificationNumber, newOffsetMinutes)
        }, hour, minute, true).show()
    }

    private fun updateNotificationOffset(session: ClassSession, notificationNumber: Int, newOffsetMinutes: Int) {
        findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
            try {
                val updatedSession = if (notificationNumber == 1) {
                    session.copy(notificationOffsetMinutes1 = newOffsetMinutes)
                } else {
                    session.copy(notificationOffsetMinutes2 = newOffsetMinutes)
                }
                withContext(Dispatchers.IO) {
                    timetableDao?.updateClassSession(updatedSession)
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Notification ${notificationNumber} offset updated.", Toast.LENGTH_SHORT).show()
                    reloadScheduleCallback?.invoke()
                }
                val timetable = withContext(Dispatchers.IO) { session.timetableId?.let {
                    timetableDao?.getTimetableById(
                        it
                    )
                } }
                if (timetable != null && !updatedSession.isSilenced) {
                    notificationHelper.scheduleAllNotificationsForClassSession(updatedSession, timetable)
                }
            } catch (e: Exception) {
                Log.e("ScheduleView", "Error updating notification offset: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to update notification offset: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun updateSessionSilenceStatus(session: ClassSession, isSilenced: Boolean) {
        findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
            try {
                val updatedSession = session.copy(isSilenced = isSilenced)
                withContext(Dispatchers.IO) {
                    timetableDao?.updateClassSession(updatedSession)
                }
                val endTime = session.startTime.plusMinutes(session.durationMinutes.toLong())
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        if (isSilenced) context.getString(R.string.notifications_silenced_for_session, updatedSession.courseName, updatedSession.startTime.format(timeFormatter), endTime.format(timeFormatter))
                        else context.getString(R.string.notifications_unsilenced_for_session, updatedSession.courseName, updatedSession.startTime.format(timeFormatter), endTime.format(timeFormatter)),
                        Toast.LENGTH_SHORT
                    ).show()
                    reloadScheduleCallback?.invoke()
                }

                val timetable = withContext(Dispatchers.IO) { session.timetableId?.let {
                    timetableDao?.getTimetableById(
                        it
                    )
                } }
                if (timetable != null) {
                    if (isSilenced) {
                        updatedSession.sessionId?.let { sessionId ->
                            val offset1 = updatedSession.notificationOffsetMinutes1 ?: timetable.defaultNotificationOffsetMinutes1 ?: notificationHelper.DEFAULT_NOTIFICATION_OFFSET_MINUTES_1
                            val offset2 = updatedSession.notificationOffsetMinutes2 ?: timetable.defaultNotificationOffsetMinutes2 ?: notificationHelper.DEFAULT_NOTIFICATION_OFFSET_MINUTES_2
                            notificationHelper.cancelNotificationsForClassSession(sessionId, offset1)
                            notificationHelper.cancelNotificationsForClassSession(sessionId, offset2)
                        }
                    } else {
                        notificationHelper.scheduleAllNotificationsForClassSession(updatedSession, timetable)
                    }
                }
            } catch (e: Exception) {
                Log.e("ScheduleView", "Error updating session silence status: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to update silence status: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun handleAssignmentDoneToggle(assignment: Assignment, isDone: Boolean) {
        findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
            try {
                val newProgress = if (isDone) 100 else 95
                val updatedAssignment = assignment.copy(currentProgress = newProgress)

                withContext(Dispatchers.IO) {
                    assignmentDao?.updateAssignment(updatedAssignment)
                }

                updatedAssignment.assignmentId?.let {
                    if (isDone || updatedAssignment.silenceNotifications) {
                        notificationHelper.cancelAssignmentNotifications(it)
                    } else {
                        notificationHelper.scheduleAssignmentNotifications(updatedAssignment)
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Assignment progress updated.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("ScheduleView", "Error toggling assignment completion: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to update assignment.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun handleTodoCompletionToggle(todoItem: TodoItem, isCompleted: Boolean) {
        findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
            try {
                // Fetch the latest version of the item to avoid conflicts
                val latestTodo = withContext(Dispatchers.IO) {
                    todoItem.todoId?.let { todoDao?.getTodoById(it) }
                } ?: return@launch // Exit if item not found

                val updatedTodo = latestTodo.copy(
                    isCompleted = isCompleted,
                    completedTimeInMillis = if (isCompleted) Instant.now().toEpochMilli() else null,
                    lastModified = LocalDateTime.now(ZoneId.systemDefault())
                )

                withContext(Dispatchers.IO) {
                    todoDao?.updateTodo(updatedTodo)
                }

                // Update notifications based on the new status
                updatedTodo.todoId?.let {
                    if (isCompleted || updatedTodo.silenceNotifications) {
                        notificationHelper.cancelTodoNotifications(it)
                    } else {
                        notificationHelper.scheduleTodoNotifications(updatedTodo)
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "To-Do status updated.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("ScheduleView", "Error toggling To-Do completion: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to update To-Do status.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // a) Add this function to handle the completion toggle
    private fun handleActionDoneToggle(action: Action, isDone: Boolean) {
        findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
            try {
                val updatedAction = action.copy(isDone = isDone)
                withContext(Dispatchers.IO) {
                    actionDao?.updateAction(updatedAction)
                    if (isDone || updatedAction.silenceNotifications) {
                        updatedAction.actionId?.let { notificationHelper.cancelActionNotifications(it) }
                    } else {
                        notificationHelper.scheduleActionNotifications(updatedAction)
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Action status updated.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("ScheduleView", "Error updating action 'done' status: ${e.message}", e)
            }
        }
    }

    /**
     * Shows a confirmation dialog before deleting an item (Engagement, Exam, Assignment, or TodoItem).
     * @param item The item to be deleted (Engagement, Exam, Assignment, or TodoItem object).
     */
    private fun showDeleteConfirmationDialog(item: Any) {
        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.dialog_delete_confirmation_title))
            .setMessage(context.getString(R.string.dialog_delete_confirmation_message,
                when (item) {
                    is Engagement -> item.engagementName
                    is Exam -> item.courseName
                    is Assignment -> "${item.assignmentTopic} - ${item.assignmentName}" // Updated for Assignment
                    is TodoItem -> item.title
                    is Action -> item.actionName
                    else -> "item"
                }
            ))
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                val lifecycleScope = findViewTreeLifecycleOwner()?.lifecycleScope // Get lifecycleScope from view tree
                lifecycleScope?.launch {
                    try {
                        withContext(Dispatchers.IO) { // Ensure database operations are on IO thread
                            when (item) {
                                is Engagement -> {
                                    item.engagementId?.let { engagementDao?.deleteEngagement(it) }
                                }
                                is Exam -> {
                                    item.examId?.let { examDao?.deleteExam(it) }
                                }
                                is Assignment -> {
                                    item.assignmentId?.let { assignmentDao?.deleteAssignment(it) }
                                }
                                is TodoItem -> { // Handle TodoItem deletion
                                    item.todoId?.let { todoDao?.deleteTodo(it) }
                                }
                                is Action -> {
                                    item.actionId?.let { actionDao?.deleteAction(it) }
                                }
                                else -> {
                                    // Should not happen for this dialog
                                }
                            }
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, context.getString(R.string.delete_success), Toast.LENGTH_SHORT).show()
                            reloadScheduleCallback?.invoke() // Trigger reload in ScheduleActivity AFTER deletion
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, context.getString(R.string.delete_failure, e.localizedMessage), Toast.LENGTH_LONG).show()
                            Log.e("ScheduleView", "Error deleting item: ${e.message}", e)
                        }
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    // Helper to convert dp to pixels
    private fun dpToPx(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (width == 0 || height == 0) return

        // 1. Draw grid lines and time labels
        for (i in 0..23) {
            val y = verticalExtensionHeight + i * rowHeight
            canvas.drawText(timeLabels[i], timeLabelColumnWidth - dpToPx(4f), y + timeTextPaint.textSize / 2, timeTextPaint)
            canvas.drawLine(timeLabelColumnWidth, y, width.toFloat(), y, linePaint)
        }
        val y24 = verticalExtensionHeight + 24 * rowHeight
        canvas.drawLine(timeLabelColumnWidth, y24, width.toFloat(), y24, linePaint)

        for (i in 0..4) {
            val x = timeLabelColumnWidth + i * columnWidth
            canvas.drawLine(x, verticalExtensionHeight, x, height.toFloat() - verticalExtensionHeight, linePaint)
        }

        // 2. Draw all events using their pre-calculated render data
        eventRenderDataList.forEach { renderData ->
            val event = renderData.event

            when (event.type) {
                ScheduleEvent.ScheduleEventType.LECTURE,
                ScheduleEvent.ScheduleEventType.PERSONAL,
                ScheduleEvent.ScheduleEventType.ENGAGEMENT,
                ScheduleEvent.ScheduleEventType.EXAM,
                ScheduleEvent.ScheduleEventType.ACTION,
                ScheduleEvent.ScheduleEventType.CALENDAR -> { // MODIFIED: Add Calendar to this block
                    var fillPaint: Paint = lecturePaint
                    var borderPaint: Paint = lectureBorderPaint

                    when (event.type) {
                        ScheduleEvent.ScheduleEventType.LECTURE -> {
                            fillPaint = lecturePaint
                            borderPaint = if (renderData.isModified) modifiedSessionBorderPaint else lectureBorderPaint
                        }
                        ScheduleEvent.ScheduleEventType.PERSONAL -> {
                            fillPaint = personalPaint
                            borderPaint = if (renderData.isModified) modifiedSessionBorderPaint else personalBorderPaint
                        }
                        ScheduleEvent.ScheduleEventType.ENGAGEMENT -> {
                            fillPaint = engagementPaint
                            borderPaint = if (renderData.isModified) modifiedSessionBorderPaint else engagementBorderPaint
                        }
                        ScheduleEvent.ScheduleEventType.EXAM -> {
                            fillPaint = examPaint
                            borderPaint = examBorderPaint
                        }
                        ScheduleEvent.ScheduleEventType.ACTION -> {
                            fillPaint = actionPaint
                            borderPaint = actionBorderPaint
                        }
                        // NEW: Define drawing paints for Calendar events
                        ScheduleEvent.ScheduleEventType.CALENDAR -> {
                            fillPaint = calendarPaint
                            borderPaint = calendarBorderPaint
                        }
                        else -> {

                        }
                    }

                    // Draw the event box using the pre-calculated rectangle
                    canvas.drawRoundRect(renderData.rect, cornerRadiusPx, cornerRadiusPx, fillPaint)
                    canvas.drawRoundRect(renderData.rect, cornerRadiusPx, cornerRadiusPx, borderPaint)

                    // Draw the event name, truncated if necessary
                    val eventName = event.name
                    val subColumnWidth = renderData.rect.width()
                    val maxCharsPerSubColumn = (subColumnWidth / eventTextPaint.textSize * 1.5).roundToInt().coerceAtLeast(3)
                    val displayedText = if (eventName.length > maxCharsPerSubColumn) {
                        eventName.substring(0, maxCharsPerSubColumn - 3) + "..."
                    } else {
                        eventName
                    }
                    val textX = renderData.rect.centerX()
                    val textY = renderData.rect.centerY() - ((eventTextPaint.descent() + eventTextPaint.ascent()) / 2)
                    canvas.drawText(displayedText, textX, textY, eventTextPaint)
                }
                ScheduleEvent.ScheduleEventType.ASSIGNMENT -> {
                    val baseCenterY = verticalExtensionHeight + (event.startTime.hour * rowHeight) + (event.startTime.minute * rowHeight / 60f)
                    val currentLine = renderData.horizontalTrack / MAX_DOT_EVENTS_PER_LINE
                    val positionInLine = renderData.horizontalTrack % MAX_DOT_EVENTS_PER_LINE

                    val basePointX = timeLabelColumnWidth - dpToPx(15f)
                    val horizontalOffsetPerDot = (2 * dotPointRadius) + dotGap
                    val adjustedPointX = basePointX - (positionInLine * horizontalOffsetPerDot)

                    val adjustedCenterY = baseCenterY + (currentLine * DOT_EVENT_VERTICAL_LINE_OFFSET)
                    val textY = adjustedCenterY - ((dotTextPaint.descent() + dotTextPaint.ascent()) / 2)

                    canvas.drawCircle(adjustedPointX, adjustedCenterY, dotPointRadius, assignmentCirclePaint)
                    canvas.drawCircle(adjustedPointX, adjustedCenterY, dotOuterRadius1, assignmentRadiatingCirclePaint)
                    canvas.drawCircle(adjustedPointX, adjustedCenterY, dotOuterRadius2, assignmentRadiatingCirclePaint)
                    canvas.drawText("A", adjustedPointX, textY, assignmentTextPaint)
                }
                ScheduleEvent.ScheduleEventType.TODO -> {
                    val todoEvent = event as ScheduleEvent.TodoEvent
                    val baseCenterY = verticalExtensionHeight + (todoEvent.startTime.hour * rowHeight) + (todoEvent.startTime.minute * rowHeight / 60f)
                    val currentLine = renderData.horizontalTrack / MAX_DOT_EVENTS_PER_LINE
                    val positionInLine = renderData.horizontalTrack % MAX_DOT_EVENTS_PER_LINE

                    val basePointX = timeLabelColumnWidth - dpToPx(15f)
                    val horizontalOffsetPerDot = (2 * dotPointRadius) + dotGap
                    val adjustedPointX = basePointX - (positionInLine * horizontalOffsetPerDot)

                    val adjustedCenterY = baseCenterY + (currentLine * DOT_EVENT_VERTICAL_LINE_OFFSET)
                    val textY = adjustedCenterY - ((dotTextPaint.descent() + dotTextPaint.ascent()) / 2)

                    when (todoEvent.priority) {
                        "High" -> {
                            canvas.drawCircle(adjustedPointX, adjustedCenterY, dotPointRadius, todoHighPriorityCirclePaint)
                            canvas.drawCircle(adjustedPointX, adjustedCenterY, dotOuterRadius1, todoHighPriorityRadiatingCirclePaint)
                            canvas.drawCircle(adjustedPointX, adjustedCenterY, dotOuterRadius2, todoHighPriorityRadiatingCirclePaint)
                            canvas.drawText("T", adjustedPointX, textY, todoHighPriorityTextPaint)
                        }
                        "Medium" -> {
                            canvas.drawCircle(adjustedPointX, adjustedCenterY, dotPointRadius, todoMediumPriorityCirclePaint)
                            canvas.drawCircle(adjustedPointX, adjustedCenterY, dotOuterRadius1, todoMediumPriorityRadiatingCirclePaint)
                            canvas.drawCircle(adjustedPointX, adjustedCenterY, dotOuterRadius2, todoMediumPriorityRadiatingCirclePaint)
                            canvas.drawText("T", adjustedPointX, textY, todoMediumPriorityTextPaint)
                        }
                        else -> {
                            canvas.drawCircle(adjustedPointX, adjustedCenterY, dotPointRadius, assignmentCirclePaint)
                            canvas.drawCircle(adjustedPointX, adjustedCenterY, dotOuterRadius1, assignmentRadiatingCirclePaint)
                            canvas.drawCircle(adjustedPointX, adjustedCenterY, dotOuterRadius2, assignmentRadiatingCirclePaint)
                            canvas.drawText("T", adjustedPointX, textY, assignmentTextPaint)
                        }
                    }
                }
                ScheduleEvent.ScheduleEventType.TASK -> {
                    val baseCenterY = verticalExtensionHeight + (event.startTime.hour * rowHeight) + (event.startTime.minute * rowHeight / 60f)
                    val currentLine = renderData.horizontalTrack / MAX_DOT_EVENTS_PER_LINE
                    val positionInLine = renderData.horizontalTrack % MAX_DOT_EVENTS_PER_LINE

                    val basePointX = timeLabelColumnWidth - dpToPx(15f)
                    val horizontalOffsetPerDot = (2 * dotPointRadius) + dotGap
                    val adjustedPointX = basePointX - (positionInLine * horizontalOffsetPerDot)

                    val adjustedCenterY = baseCenterY + (currentLine * DOT_EVENT_VERTICAL_LINE_OFFSET)
                    val textY = adjustedCenterY - ((dotTextPaint.descent() + dotTextPaint.ascent()) / 2)

                    canvas.drawCircle(adjustedPointX, adjustedCenterY, dotPointRadius, taskCirclePaint)
                    canvas.drawCircle(adjustedPointX, adjustedCenterY, dotOuterRadius1, taskRadiatingCirclePaint)
                    canvas.drawCircle(adjustedPointX, adjustedCenterY, dotOuterRadius2, taskRadiatingCirclePaint)
                    canvas.drawText("T", adjustedPointX, textY, taskTextPaint)
                }
            }
        }
    }

}

