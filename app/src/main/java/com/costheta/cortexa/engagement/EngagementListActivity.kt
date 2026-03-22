package com.costheta.cortexa.engagement

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.costheta.cortexa.NotificationHelper
import com.costheta.cortexa.R
import com.costheta.cortexa.data.db.AppDatabase
import com.costheta.cortexa.data.db.dao.AttachmentDao
import com.costheta.cortexa.data.db.dao.EngagementDao
import com.costheta.cortexa.data.db.dao.SubtaskDao
import com.costheta.cortexa.data.models.Engagement
import com.costheta.cortexa.data.models.EngagementException
import com.costheta.cortexa.data.models.EngagementExceptionType
import com.costheta.cortexa.util.autofill.FuzzyAutoCompleteTextView
import com.costheta.cortexa.util.keyboard.FuzzyAutoCompleteDisappearingKeyboard
import com.costheta.cortexa.util.uirefresh.PeriodicallyRefreshAppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.xdrop.fuzzywuzzy.FuzzySearch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
// FOR CALENDAR: Import required classes for combining flows and handling calendar data
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.ZoneId


class EngagementListActivity : PeriodicallyRefreshAppCompatActivity() {

    private lateinit var engagementDao: EngagementDao
    private lateinit var attachmentDao: AttachmentDao
    private lateinit var subtaskDao: SubtaskDao
    private lateinit var notificationHelper: NotificationHelper

    private lateinit var engagementAdapter: EngagementAdapter
    private lateinit var engagementRecyclerView: RecyclerView
    private lateinit var noEngagementsTextView: TextView
    private lateinit var editTextSearchEngagements: FuzzyAutoCompleteTextView
    private lateinit var addEngagementFab: FloatingActionButton

    private var allDisplayableEngagementsCache: List<DisplayableItem> = emptyList()

    // Formatters
    private val dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

    // Variables for FAB movement
    private var lastX: Int = 0
    private var lastY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var isClick: Boolean = true // Flag to distinguish between click and drag


    override fun getRecyclerView(): RecyclerView = engagementRecyclerView
    override fun getAdapter(): ListAdapter<*, *> = engagementAdapter

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.engagement_activity_list)

        val db = AppDatabase.getDatabase(this)
        engagementDao = db.engagementDao()
        attachmentDao = db.attachmentDao()
        subtaskDao = db.subtaskDao()
        notificationHelper = NotificationHelper(this)

        engagementRecyclerView = findViewById(R.id.engagementRecyclerView)
        noEngagementsTextView = findViewById(R.id.noEngagementsTextView)
        editTextSearchEngagements = findViewById(R.id.editTextSearchEngagements)
        addEngagementFab = findViewById(R.id.addEngagementFab)

        FuzzyAutoCompleteDisappearingKeyboard.getInstance().register(editTextSearchEngagements)

        setupRecyclerView()
        setupSearchBar()

        addEngagementFab.setOnClickListener {
            startActivity(Intent(this, AddEngagementActivity::class.java))
        }

        // Set up FAB touch listener for movement
        addEngagementFab.setOnTouchListener { view, event ->
            val parentWidth = (view.parent as View).width - view.width
            val parentHeight = (view.parent as View).height - view.height

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.rawX.toInt()
                    lastY = event.rawY.toInt()
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isClick = true // Assume it's a click initially
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX.toInt() - lastX
                    val dy = event.rawY.toInt() - lastY

                    var newX = view.x + dx
                    var newY = view.y + dy

                    // Boundary checks
                    newX = newX.coerceIn(0f, parentWidth.toFloat())
                    newY = newY.coerceIn(0f, parentHeight.toFloat())

                    view.x = newX
                    view.y = newY

                    lastX = event.rawX.toInt()
                    lastY = event.rawY.toInt()

                    val touchSlop = View.getDefaultSize(0, 0)
                    val deltaX = Math.abs(event.rawX - initialTouchX)
                    val deltaY = Math.abs(event.rawY - initialTouchY)
                    if (deltaX > touchSlop || deltaY > touchSlop) {
                        isClick = false
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (isClick) {
                        view.performClick()
                    }
                }
                else -> return@setOnTouchListener false
            }
            true
        }

        addEngagementFab.post {
            val parentView = addEngagementFab.parent as View
            val marginEnd = resources.getDimensionPixelSize(R.dimen.fab_margin_end)
            val marginBottom = resources.getDimensionPixelSize(R.dimen.fab_margin_bottom)

            val density = resources.displayMetrics.density
            val marginEndPx = (marginEnd * density).toInt()
            val marginBottomPx = (marginBottom * density).toInt()

            addEngagementFab.x = (parentView.width - addEngagementFab.width - marginEndPx).toFloat()
            addEngagementFab.y = (parentView.height - addEngagementFab.height - marginBottomPx).toFloat()
        }

    }

    override fun onResume() {
        super.onResume()
        triggerDataRefresh()
    }

    override fun triggerDataRefresh() {
        loadAndProcessEngagements()
    }

    private fun setupRecyclerView() {
        // CORRECTED: The adapter constructor now takes specific lambdas where needed.
        // This resolves the type mismatch compilation error.
        engagementAdapter = EngagementAdapter(
            onDeleteClick = { displayableItem -> showDeleteOptionsDialog(displayableItem) },
            onEditClick = { displayableItem -> showEditOptionsDialog(displayableItem) },
            onCancelInstanceClick = { engagement -> showCancelInstanceDialog(engagement) },
            onRescheduleInstanceClick = { engagement -> showRescheduleInstanceDialog(engagement) },
            attachmentDao = attachmentDao,
            coroutineScope = lifecycleScope
        )
        engagementRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@EngagementListActivity)
            adapter = engagementAdapter
        }
    }

    private fun setupSearchBar() {
        editTextSearchEngagements.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                editTextSearchEngagements.filter.filter(s)
                filterEngagements(s.toString(), allDisplayableEngagementsCache)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun loadAndProcessEngagements() {
        lifecycleScope.launch {
            val syncedCalendarEventDao = AppDatabase.getDatabase(this@EngagementListActivity).syncedCalendarEventDao()

            engagementDao.getAllActiveAndValidatedEngagements()
                .combine(syncedCalendarEventDao.getAllFutureEvents(System.currentTimeMillis())) { engagementsFromDb, calendarEventsFromDb ->
                    val today = LocalDate.now()
                    val endDate = engagementsFromDb.maxOfOrNull { it.validityEndDate } ?: today.plusYears(1)
                    val exceptions = withContext(Dispatchers.IO) {
                        engagementDao.getEngagementExceptionsForDateRange(today, endDate)
                    }
                    val exceptionsMap = exceptions.associateBy { it.originalEngagementId to it.originalDate }

                    val processedEngagements = withContext(Dispatchers.Default) {
                        engagementsFromDb.mapNotNull { engagement ->
                            calculateNextOccurrence(engagement, exceptionsMap)
                        }
                    }.map { DisplayableItem.AppEngagement(it) }

                    val processedCalendarEvents = calendarEventsFromDb.map {
                        DisplayableItem.CalendarEvent(it)
                    }

                    processedEngagements + processedCalendarEvents

                }.collectLatest { combinedList ->
                    allDisplayableEngagementsCache = combinedList
                    withContext(Dispatchers.Main) {
                        updateSearchSuggestions(combinedList)
                        filterEngagements(editTextSearchEngagements.text.toString(), combinedList)
                        updateNoEngagementsVisibility()
                    }
                }
        }
    }

    private fun calculateNextOccurrence(
        engagement: Engagement,
        exceptionsMap: Map<Pair<Long, LocalDate>, EngagementException>
    ): Engagement? {
        val today = LocalDate.now()
        var currentDate = if (today.isBefore(engagement.validityStartDate)) engagement.validityStartDate else today

        while (!currentDate.isAfter(engagement.validityEndDate)) {
            val exception = exceptionsMap[engagement.engagementId to currentDate]

            if (exception?.engagementExceptionType == EngagementExceptionType.CANCELLED) {
                currentDate = currentDate.plusDays(1)
                continue
            }

            val isScheduledDay = when (engagement.daySelectionType) {
                "SPECIFIC_DAYS" -> engagement.selectedDaysOfWeek?.split(",")?.map { DayOfWeek.valueOf(it) }?.contains(currentDate.dayOfWeek) ?: false
                "RECURRENCE" -> engagement.recurrenceIntervalDays?.let { ChronoUnit.DAYS.between(engagement.validityStartDate, currentDate) % it == 0L } ?: false
                else -> false
            }

            if (isScheduledDay) {
                val instance = if (exception?.engagementExceptionType == EngagementExceptionType.MODIFIED) {
                    engagement.copy(
                        startTime = exception.newStartTime!!,
                        durationMinutes = exception.newDurationMinutes ?: engagement.durationMinutes,
                        notes = exception.newNotes ?: engagement.notes
                    ).apply {
                        this.nextOccurrenceDate = exception.newDate
                        this.startDateTime = LocalDateTime.of(exception.newDate, exception.newStartTime)
                    }
                } else {
                    engagement.copy().apply {
                        this.nextOccurrenceDate = currentDate
                        this.startDateTime = LocalDateTime.of(currentDate, engagement.startTime)
                    }
                }

                val endDateTime = instance.startDateTime?.plusMinutes(instance.durationMinutes.toLong())
                if (endDateTime?.isAfter(LocalDateTime.now()) == true) {
                    return instance
                }
            }
            currentDate = currentDate.plusDays(1)
        }
        return null
    }

    private fun showCancelInstanceDialog(engagement: Engagement) {
        AlertDialog.Builder(this)
            .setTitle(R.string.cancel_instance_title)
            .setMessage(getString(R.string.cancel_instance_confirm_message, engagement.engagementName))
            .setPositiveButton(R.string.dialog_ok_button) { _, _ ->
                cancelEngagementInstance(engagement)
            }
            .setNegativeButton(R.string.dialog_cancel_button, null)
            .show()
    }

    private fun showRescheduleInstanceDialog(engagement: Engagement) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.engagement_reschedule_dialog, null)
        val dateEditText = dialogView.findViewById<TextInputEditText>(R.id.rescheduleDateEditText)
        val startTimeEditText = dialogView.findViewById<TextInputEditText>(R.id.rescheduleStartTimeEditText)
        val endTimeEditText = dialogView.findViewById<TextInputEditText>(R.id.rescheduleEndTimeEditText)

        var selectedDate = engagement.nextOccurrenceDate ?: LocalDate.now()
        var selectedStartTime = engagement.startTime
        val duration = engagement.durationMinutes.toLong()

        dateEditText.setText(selectedDate.format(dateFormatter))
        startTimeEditText.setText(selectedStartTime.format(timeFormatter))
        endTimeEditText.setText(selectedStartTime.plusMinutes(duration).format(timeFormatter))

        dateEditText.setOnClickListener {
            DatePickerDialog(this, { _, year, month, dayOfMonth ->
                selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
                dateEditText.setText(selectedDate.format(dateFormatter))
            }, selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth).show()
        }

        startTimeEditText.setOnClickListener {
            TimePickerDialog(this, { _, hourOfDay, minute ->
                selectedStartTime = LocalTime.of(hourOfDay, minute)
                startTimeEditText.setText(selectedStartTime.format(timeFormatter))
                endTimeEditText.setText(selectedStartTime.plusMinutes(duration).format(timeFormatter))
            }, selectedStartTime.hour, selectedStartTime.minute, true).show()
        }

        endTimeEditText.setOnClickListener {
            TimePickerDialog(this, { _, hourOfDay, minute ->
                val selectedEndTime = LocalTime.of(hourOfDay, minute)
                endTimeEditText.setText(selectedEndTime.format(timeFormatter))
            }, selectedStartTime.plusMinutes(duration).hour, selectedStartTime.plusMinutes(duration).minute, true).show()
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.reschedule_instance_title)
            .setView(dialogView)
            .setPositiveButton(R.string.reschedule) { _, _ ->
                rescheduleEngagementInstance(engagement, selectedDate, selectedStartTime)
            }
            .setNegativeButton(R.string.dialog_cancel_button, null)
            .show()
    }

    private fun showDeleteOptionsDialog(item: DisplayableItem) {
        // CORRECTED: This check ensures we only show the dialog for AppEngagements.
        // For CalendarEvents, nothing will happen, effectively disabling the delete action.
        if (item is DisplayableItem.AppEngagement) {
            val engagement = item.engagement
            val options = arrayOf("This instance only", "Entire engagement series")
            AlertDialog.Builder(this)
                .setTitle("Delete Engagement")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> showCancelInstanceDialog(engagement)
                        1 -> showConfirmDeleteSeriesDialog(engagement)
                    }
                }
                .setNegativeButton(R.string.dialog_cancel_button, null)
                .show()
        }
    }

    private fun showConfirmDeleteSeriesDialog(engagement: Engagement) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_engagement_confirm_title)
            .setMessage(getString(R.string.delete_engagement_confirm_message, engagement.engagementName))
            .setPositiveButton(R.string.delete_action_confirm_positive) { _, _ ->
                deleteEngagementSeries(engagement)
            }
            .setNegativeButton(R.string.dialog_cancel_button, null)
            .show()
    }

    private fun showEditOptionsDialog(item: DisplayableItem) {
        when (item) {
            is DisplayableItem.AppEngagement -> {
                val engagement = item.engagement
                val options = arrayOf("Edit this instance", "Edit all subsequent occurrences")
                AlertDialog.Builder(this)
                    .setTitle("Edit Engagement")
                    .setItems(options) { _, which ->
                        when (which) {
                            0 -> showRescheduleInstanceDialog(engagement)
                            1 -> editEngagementSeries(engagement)
                        }
                    }
                    .setNegativeButton(R.string.dialog_cancel_button, null)
                    .show()
            }
            is DisplayableItem.CalendarEvent -> {
                val intent = Intent(this, com.costheta.cortexa.external.calendar.ViewCalendarEventActivity::class.java).apply {
                    putExtra("CALENDAR_EVENT_LOCAL_ID", item.event.localId)
                }
                startActivity(intent)
            }
        }
    }

    private fun cancelEngagementInstance(engagement: Engagement) {
        lifecycleScope.launch {
            val exception = EngagementException(
                originalEngagementId = engagement.engagementId!!,
                originalDate = engagement.nextOccurrenceDate!!,
                engagementExceptionType = EngagementExceptionType.CANCELLED,
                newDate = null,
                newStartTime = null,
                newDurationMinutes = null,
                newNotes = null
            )
            withContext(Dispatchers.IO) {
                engagementDao.upsertEngagementException(exception)
            }
            Toast.makeText(this@EngagementListActivity, "Instance cancelled.", Toast.LENGTH_SHORT).show()
            triggerDataRefresh()
        }
    }

    private fun rescheduleEngagementInstance(engagement: Engagement, newDate: LocalDate, newStartTime: LocalTime) {
        lifecycleScope.launch {
            val exception = EngagementException(
                originalEngagementId = engagement.engagementId!!,
                originalDate = engagement.nextOccurrenceDate!!,
                engagementExceptionType = EngagementExceptionType.MODIFIED,
                newDate = newDate,
                newStartTime = newStartTime,
                newDurationMinutes = engagement.durationMinutes,
                newNotes = engagement.notes
            )
            withContext(Dispatchers.IO) {
                engagementDao.upsertEngagementException(exception)
            }
            Toast.makeText(this@EngagementListActivity, "Instance rescheduled.", Toast.LENGTH_SHORT).show()
            triggerDataRefresh()
        }
    }

    private fun deleteEngagementSeries(engagement: Engagement) {
        lifecycleScope.launch {
            try {
                engagement.engagementId?.let { id ->
                    withContext(Dispatchers.IO) {
                        notificationHelper.cancelEngagementNotifications(id)
                        attachmentDao.deleteAttachmentsForEvent("Engagement", id)
                        subtaskDao.deleteAllSubtasksForEvent("Engagement", id)
                        engagementDao.deleteEngagement(id)
                    }
                    Toast.makeText(this@EngagementListActivity, getString(R.string.engagement_deleted_success, engagement.engagementName), Toast.LENGTH_SHORT).show()
                    triggerDataRefresh()
                }
            } catch (e: Exception) {
                Toast.makeText(this@EngagementListActivity, getString(R.string.engagement_deletion_failure, e.localizedMessage), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun editEngagementSeries(engagement: Engagement) {
        val intent = Intent(this, AddEngagementActivity::class.java).apply {
            putExtra("ENGAGEMENT_ID", engagement.engagementId)
        }
        startActivity(intent)
    }

    private fun updateSearchSuggestions(items: List<DisplayableItem>) {
        val suggestions = items.map {
            when (it) {
                is DisplayableItem.AppEngagement -> "${it.engagement.engagementName} ${it.engagement.notes.orEmpty()} ${it.engagement.venue.orEmpty()}"
                is DisplayableItem.CalendarEvent -> "${it.event.title} ${it.event.description.orEmpty()} ${it.event.location.orEmpty()}"
            }
        }.filter { it.isNotBlank() }.distinct().sorted()

        editTextSearchEngagements.setFuzzySuggestions(suggestions)
        editTextSearchEngagements.threshold = 1
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun filterEngagements(query: String, items: List<DisplayableItem>) {
        val filteredAndSortedList = if (query.isBlank()) {
            sortDisplayableItemsCustom(items)
        } else {
            val scoredItems = items.map { item ->
                val combinedString = when (item) {
                    is DisplayableItem.AppEngagement -> "${item.engagement.engagementName} ${item.engagement.notes.orEmpty()} ${item.engagement.venue.orEmpty()}"
                    is DisplayableItem.CalendarEvent -> "${item.event.title} ${item.event.description.orEmpty()} ${item.event.location.orEmpty()}"
                }
                val score = FuzzySearch.weightedRatio(query.lowercase(Locale.getDefault()), combinedString.lowercase(Locale.getDefault()))
                Pair(item, score)
            }

            val brackets = listOf(100, 90, 80, 70, 60, 50, 40, 30, 20, 10, 0)
            brackets.flatMap { upperBound ->
                val lowerBound = if (upperBound == 0) 0 else upperBound - 10
                val inBracket = scoredItems.filter { (_, score) ->
                    if (upperBound == 100) score in lowerBound..upperBound
                    else score in lowerBound..<upperBound
                }.map { it.first }
                sortDisplayableItemsCustom(inBracket)
            }
        }
        engagementAdapter.submitList(filteredAndSortedList)
        engagementAdapter.notifyDataSetChanged()
    }

    private fun sortDisplayableItemsCustom(items: List<DisplayableItem>): List<DisplayableItem> {
        return items.sortedWith(compareBy {
            when (it) {
                is DisplayableItem.AppEngagement -> it.engagement.startDateTime
                is DisplayableItem.CalendarEvent -> {
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(it.event.startDateTimeMillis), ZoneId.systemDefault())
                }
            }
        })
    }

    private fun updateNoEngagementsVisibility() {
        noEngagementsTextView.visibility = if (engagementAdapter.itemCount == 0) View.VISIBLE else View.GONE
        engagementRecyclerView.visibility = if (engagementAdapter.itemCount == 0) View.GONE else View.VISIBLE
    }
}

