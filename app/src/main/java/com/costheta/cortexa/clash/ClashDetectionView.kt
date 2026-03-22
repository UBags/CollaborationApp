package com.costheta.cortexa.clash

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action

import android.content.Context
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import java.time.LocalDate
import java.time.LocalTime

// Enum to define the type of event for clash detection
enum class ClashEventType {
    LECTURE,
    PERSONAL, // Although not explicitly used as a distinct type in current data, good to keep
    ENGAGEMENT, // NEW: Type for ad-hoc engagements
    EXAM
}

// Data class to represent an event for clash detection
data class ClashEvent(
    val id: Long?, // Nullable for events without a direct DB ID, or if not needed
    val name: String,
    val type: ClashEventType,
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val location: String?, // For ClassSession and Exam
    val notes: String? // For ClassSession and Engagement
)

// NEW Data class to store pre-calculated drawing information for clash events
data class DrawnClashEvent(
    val rect: RectF,
    val text: String,
    val paint: Paint,
    val originalEvents: List<ClashEvent> // Store original events for dialog
)

class ClashDetectionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
//
//    // Store the actual event data
//    var events: List<ClashEvent> = emptyList()
//        set(value) {
//            field = value
//            updateDrawnClashEvents() // Recalculate drawn events when data changes
//            invalidate() // Request a redraw
//        }
//
//    private val timeLabels = mutableListOf<String>()
//    // This list now stores the pre-calculated drawing information
//    private val drawnClashEvents = mutableListOf<DrawnClashEvent>()
//
//    // Preallocate a RectF object to be reused in getEventRect to avoid allocations in a loop
//    private val reusableRectF = RectF()
//
//    // Paints
//    private val hourLinePaint = Paint().apply {
//        color = "#CCCCCC".toColorInt() // Light gray
//        strokeWidth = 1f
//    }
//    private val halfHourLinePaint = Paint().apply {
//        color = "#E0E0E0".toColorInt() // Lighter gray
//        strokeWidth = 0.5f
//        pathEffect = DashPathEffect(floatArrayOf(5f, 5f), 0f) // Dashed line
//    }
//    private val timeTextPaint = Paint().apply {
//        color = Color.BLACK
//        textSize = dpToPx(12f)
//        textAlign = Paint.Align.RIGHT
//        isAntiAlias = true
//    }
//    private val eventPaint = Paint().apply {
//        color = ContextCompat.getColor(context, R.color.purple_200) // Default event color
//        isAntiAlias = true
//    }
//    private val clashPaint = Paint().apply {
//        color = ContextCompat.getColor(context, R.color.overdue_assignment_red) // Red for clashes
//        isAntiAlias = true
//    }
//    private val eventTextPaint = Paint().apply {
//        color = Color.BLACK
//        textSize = dpToPx(12f)
//        isAntiAlias = true
//    }
//
//    // Dimensions
//    private val hourHeight = dpToPx(60f) // Height for one hour
//    private val timeLabelWidth = dpToPx(40f) // Width for the time labels column
//    private val eventPadding = dpToPx(4f) // Padding around events
//    private val minEventHeight = dpToPx(20f) // Minimum height for an event rectangle
//
//    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault())
//    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
//
//    init {
//        // Generate time labels (e.g., 00:00, 01:00, ..., 23:00)
//        for (i in 0..23) {
//            timeLabels.add(String.format(Locale.getDefault(), "%02d:00", i))
//        }
//    }
//
////    /**
////     * Sets the events to be displayed on the clash detection view.
////     * This method now triggers the recalculation of drawn events.
////     * @param events The list of ClashEvent objects.
////     */
////    fun setEvents(events: List<ClashEvent>) {
////        this.events = events // Assign to the backing property
////    }
//
//    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
//        val desiredWidth = MeasureSpec.getSize(widthMeasureSpec)
//        // Calculate total height needed for 24 hours
//        val desiredHeight = (24 * hourHeight + paddingTop + paddingBottom).roundToInt()
//        setMeasuredDimension(desiredWidth, desiredHeight)
//    }
//
//    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
//        super.onSizeChanged(w, h, oldw, oldh)
//        // Recalculate drawn events when the view's size changes
//        updateDrawnClashEvents()
//    }
//
//    /**
//     * Calculates the RectF for each event, detects clashes, and populates the drawnClashEvents list.
//     * This method should be called whenever the event data changes or the view's size changes.
//     * This avoids allocations during onDraw.
//     */
//    private fun updateDrawnClashEvents() {
//        drawnClashEvents.clear() // Clear previous drawn events
//        if (width == 0 || height == 0) return // Avoid calculation if view not laid out yet
//
//        val eventWidth = width - timeLabelWidth - eventPadding * 2
//        val eventLeft = timeLabelWidth + eventPadding
//
//        val processedEvents = mutableSetOf<ClashEvent>()
//
//        // Sort events by start time to simplify clash detection
//        val sortedEvents = events.sortedBy { it.startTime }
//
//        for (i in sortedEvents.indices) {
//            val event1 = sortedEvents[i]
//            if (event1 in processedEvents) continue // Skip if already processed as part of a clash
//
//            val eventRect1 = RectF() // Use a temporary RectF for initial calculation
//            if (!getEventRect(event1, eventLeft, eventWidth, eventRect1)) continue
//
//            var isClashing = false
//            val clashingEvents = mutableListOf(event1)
//
//            // Check for clashes with subsequent events
//            for (j in (i + 1) until sortedEvents.size) {
//                val event2 = sortedEvents[j]
//                val eventRect2 = RectF() // Use a temporary RectF for initial calculation
//                if (!getEventRect(event2, eventLeft, eventWidth, eventRect2)) continue
//
//                // Check for intersection
//                if (eventRect1.intersects(eventRect2.left, eventRect2.top, eventRect2.right, eventRect2.bottom)) {
//                    isClashing = true
//                    clashingEvents.add(event2)
//                    processedEvents.add(event2) // Mark as processed to avoid re-checking
//                }
//            }
//
//            // Determine paint and text based on clash status
//            val paintToUse = if (isClashing) clashPaint else eventPaint
//            val textToDraw = if (isClashing) {
//                context.getString(R.string.clash_text_summary, clashingEvents.size)
//            } else {
//                event1.name
//            }
//
//            // Create the final RectF for drawing
//            val finalEventRect = if (isClashing) {
//                // If clashing, calculate a single combined rectangle for all clashing events
//                val minTop = clashingEvents.minOf {
//                    val tempRect = RectF()
//                    getEventRect(it, eventLeft, eventWidth, tempRect)
//                    tempRect.top
//                }
//                val maxBottom = clashingEvents.maxOf {
//                    val tempRect = RectF()
//                    getEventRect(it, eventLeft, eventWidth, tempRect)
//                    tempRect.bottom
//                }
//                RectF(eventLeft, minTop, eventLeft + eventWidth, maxBottom)
//            } else {
//                RectF(eventRect1) // Copy the single event rect
//            }
//            // Add the pre-calculated drawing information to the list
//            drawnClashEvents.add(DrawnClashEvent(finalEventRect, textToDraw, paintToUse, clashingEvents))
//        }
//    }
//
//    override fun onDraw(canvas: Canvas) {
//        super.onDraw(canvas)
//
//        // Draw horizontal hour lines and time labels
//        for (i in 0..24) {
//            val y = i * hourHeight + paddingTop
//            canvas.drawLine(timeLabelWidth, y, width.toFloat(), y, hourLinePaint)
//            if (i < 24) {
//                canvas.drawLine(timeLabelWidth, y + hourHeight / 2, width.toFloat(), y + hourHeight / 2, halfHourLinePaint)
//            }
//            if (i < timeLabels.size) {
//                canvas.drawText(timeLabels[i], timeLabelWidth - dpToPx(8f), y + timeTextPaint.textSize / 2, timeTextPaint)
//            }
//        }
//
//        // Now, iterate over the pre-calculated drawnClashEvents and draw them.
//        // No new objects are allocated in this loop.
//        for (drawnEvent in drawnClashEvents) {
//            canvas.drawRoundRect(drawnEvent.rect, dpToPx(8f), dpToPx(8f), drawnEvent.paint)
//            // Draw text for the event
//            drawEventText(canvas, drawnEvent.text, drawnEvent.rect)
//        }
//    }
//
//    /**
//     * Calculates the RectF for an event and updates the provided outRect.
//     * Returns true if a valid RectF was calculated, false otherwise.
//     * This method avoids creating new RectF objects.
//     */
//    private fun getEventRect(event: ClashEvent, left: Float, width: Float, outRect: RectF): Boolean {
//        val startMinuteOfDay = event.startTime.hour * 60 + event.startTime.minute
//        val endMinuteOfDay = event.endTime.hour * 60 + event.endTime.minute
//
//        // Handle overnight events by capping at 24:00 for display purposes
//        val effectiveEndMinuteOfDay = if (event.endTime.isBefore(event.startTime)) 24 * 60 else endMinuteOfDay
//
//        val top = (startMinuteOfDay / 60f) * hourHeight + paddingTop
//        val bottom = (effectiveEndMinuteOfDay / 60f) * hourHeight + paddingTop
//
//        if (bottom - top < minEventHeight) {
//            // If the event is too short, make it at least minEventHeight
//            outRect.set(left, top, left + width, top + minEventHeight)
//        } else {
//            outRect.set(left, top, left + width, bottom)
//        }
//        return true // Always return true if we set the rect
//    }
//
//    private fun drawEventText(canvas: Canvas, text: String, rect: RectF) {
//        val textHeight = eventTextPaint.descent() - eventTextPaint.ascent()
//        val textY = rect.centerY() + textHeight / 2 - eventTextPaint.descent()
//        val textX = rect.left + eventPadding
//
//        // Check if text fits, if not, truncate with ellipsis
//        val availableWidth = rect.width() - eventPadding * 2
//        val measuredWidth = eventTextPaint.measureText(text)
//
//        if (measuredWidth > availableWidth) {
//            val ellipsis = "..."
//            val ellipsisWidth = eventTextPaint.measureText(ellipsis)
//            val charWidth = eventTextPaint.measureText("W") // Estimate average char width
//            val maxChars = ((availableWidth - ellipsisWidth) / charWidth).toInt().coerceAtLeast(0)
//
//            val truncatedText = if (maxChars > 0 && text.length > maxChars) {
//                text.substring(0, maxChars) + ellipsis
//            } else {
//                "" // Text too short to even show ellipsis
//            }
//            canvas.drawText(truncatedText, textX, textY, eventTextPaint)
//        } else {
//            canvas.drawText(text, textX, textY, eventTextPaint)
//        }
//    }
//
//    override fun onTouchEvent(event: MotionEvent?): Boolean {
//        var handled = false
//        when (event?.action) {
//            MotionEvent.ACTION_UP -> {
//                val x = event.x
//                val y = event.y
//
//                for (drawnEvent in drawnClashEvents) {
//                    if (drawnEvent.rect.contains(x, y)) {
//                        performClick()
//                        // Pass the original events associated with this drawn block
//                        showEventDetailsDialog(drawnEvent.originalEvents)
//                        handled = true
//                        break
//                    }
//                }
//            }
//        }
//        return handled || super.onTouchEvent(event)
//    }
//
//    override fun performClick(): Boolean {
//        super.performClick()
//        return true
//    }
//
//    private fun dpToPx(dp: Float): Float {
//        return TypedValue.applyDimension(
//            TypedValue.COMPLEX_UNIT_DIP,
//            dp,
//            resources.displayMetrics
//        )
//    }
//
//    /**
//     * Calculates the Y scroll position for a given time.
//     * @param hour The hour (0-23).
//     * @param minute The minute (0-59).
//     * @return The Y coordinate to scroll to.
//     */
//    fun getScrollYForTime(hour: Int, minute: Int): Int {
//        val totalMinutes = hour * 60 + minute
//        val pixelsPerMinute = hourHeight / 60f
//        // Adjust by a small offset to center the time in the view, if possible
//        val scrollPosition = (totalMinutes * pixelsPerMinute - height / 4).roundToInt()
//        return scrollPosition.coerceIn(0, (24 * hourHeight - height).roundToInt().coerceAtLeast(0))
//    }
//
//    // MODIFIED: showEventDetailsDialog now accepts a List<ClashEvent>
//    private fun showEventDetailsDialog(events: List<ClashEvent>) {
//        val dialogMessageBuilder = SpannableStringBuilder()
//
//        if (events.size == 1) {
//            val event = events.first()
//            // Name (Bold)
//            dialogMessageBuilder.appendBold(event.name)
//            dialogMessageBuilder.append("\n\n")
//
//            // Type
//            dialogMessageBuilder.appendBold(context.getString(R.string.clash_format_type))
//            dialogMessageBuilder.append(" : ")
//            dialogMessageBuilder.append(
//                when (event.type) {
//                    ClashEventType.LECTURE -> context.getString(R.string.clash_type_lecture)
//                    ClashEventType.PERSONAL -> context.getString(R.string.clash_type_personal)
//                    ClashEventType.ENGAGEMENT -> context.getString(R.string.clash_type_engagement)
//                    ClashEventType.EXAM -> context.getString(R.string.clash_type_exam)
//                }
//            )
//            dialogMessageBuilder.append("\n")
//
//            // Date
//            dialogMessageBuilder.appendBold(context.getString(R.string.clash_format_date))
//            dialogMessageBuilder.append(" : ")
//            dialogMessageBuilder.append(event.date.format(dateFormatter))
//            dialogMessageBuilder.append("\n")
//
//            // Time
//            dialogMessageBuilder.appendBold(context.getString(R.string.clash_format_time))
//            dialogMessageBuilder.append(" : ")
//            dialogMessageBuilder.append(event.startTime.format(timeFormatter))
//            dialogMessageBuilder.append(" - ")
//            dialogMessageBuilder.append(event.endTime.format(timeFormatter))
//            dialogMessageBuilder.append("\n")
//
//            // Location (if applicable)
//            if (!event.location.isNullOrBlank()) {
//                dialogMessageBuilder.appendBold(context.getString(R.string.clash_format_location))
//                dialogMessageBuilder.append(" : ")
//                dialogMessageBuilder.append(event.location)
//                dialogMessageBuilder.append("\n")
//            }
//
//            // Notes (if applicable)
//            if (!event.notes.isNullOrBlank()) {
//                dialogMessageBuilder.appendBold(context.getString(R.string.clash_format_notes))
//                dialogMessageBuilder.append(" : ")
//                dialogMessageBuilder.append(event.notes)
//                dialogMessageBuilder.append("\n")
//            }
//        } else {
//            // Handle multiple clashing events
//            dialogMessageBuilder.appendBold(context.getString(R.string.clash_details_title))
//            dialogMessageBuilder.append("\n\n")
//            events.forEachIndexed { index, event ->
//                dialogMessageBuilder.appendBold("Event ${index + 1}: ${event.name}")
//                dialogMessageBuilder.append("\n")
//                dialogMessageBuilder.append("Type: ")
//                dialogMessageBuilder.append(
//                    when (event.type) {
//                        ClashEventType.LECTURE -> context.getString(R.string.clash_type_lecture)
//                        ClashEventType.PERSONAL -> context.getString(R.string.clash_type_personal)
//                        ClashEventType.ENGAGEMENT -> context.getString(R.string.clash_type_engagement)
//                        ClashEventType.EXAM -> context.getString(R.string.clash_type_exam)
//                    }
//                )
//                dialogMessageBuilder.append("\n")
//                dialogMessageBuilder.append("Time: ${event.startTime.format(timeFormatter)} - ${event.endTime.format(timeFormatter)}")
//                dialogMessageBuilder.append("\n")
//                if (!event.location.isNullOrBlank()) {
//                    dialogMessageBuilder.append("Location: ${event.location}")
//                    dialogMessageBuilder.append("\n")
//                }
//                if (!event.notes.isNullOrBlank()) {
//                    dialogMessageBuilder.append("Notes: ${event.notes}")
//                    dialogMessageBuilder.append("\n")
//                }
//                dialogMessageBuilder.append("\n")
//            }
//        }
//
//        val dialogBuilder = AlertDialog.Builder(context)
//        val alertDialog = dialogBuilder.create()
//
//        val titleView = TextView(context).apply {
//            text = context.getString(R.string.clash_details_title)
//            setBackgroundColor(Color.BLACK)
//            setTextColor(Color.WHITE)
//            textSize = 20f
//            setPadding(dpToPx(16f).toInt(), dpToPx(16f).toInt(), dpToPx(16f).toInt(), dpToPx(16f).toInt())
//            gravity = android.view.Gravity.CENTER
//        }
//        alertDialog.setCustomTitle(titleView)
//
//        alertDialog.setMessage(dialogMessageBuilder)
//        alertDialog.window?.decorView?.setBackgroundResource(R.drawable.rounded_dialog_background_clash)
//
//        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, context.getString(android.R.string.ok)) { dialog, _ ->
//            dialog.dismiss()
//        }
//        alertDialog.show()
//    }
//
//    // Extension function to append bold text to SpannableStringBuilder
//    private fun SpannableStringBuilder.appendBold(text: CharSequence) {
//        append(text, StyleSpan(Typeface.BOLD), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
//    }
}
