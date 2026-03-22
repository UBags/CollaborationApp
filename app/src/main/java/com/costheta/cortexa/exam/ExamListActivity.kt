package com.costheta.cortexa.exam

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.costheta.cortexa.data.db.AppDatabase
import com.costheta.cortexa.data.models.Exam
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.collectLatest
import com.costheta.cortexa.data.db.dao.AttachmentDao
import com.costheta.cortexa.data.db.dao.ExamDao
import com.costheta.cortexa.data.db.dao.SubtaskDao
import me.xdrop.fuzzywuzzy.FuzzySearch
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.costheta.cortexa.util.autofill.FuzzyAutoCompleteTextView
import android.annotation.SuppressLint
import java.util.Locale
import com.costheta.cortexa.util.uirefresh.PeriodicallyRefreshAppCompatActivity
import androidx.recyclerview.widget.ListAdapter
import com.costheta.cortexa.NotificationHelper
import com.costheta.cortexa.R
import com.costheta.cortexa.util.keyboard.FuzzyAutoCompleteDisappearingKeyboard
import java.time.Instant

class ExamListActivity : PeriodicallyRefreshAppCompatActivity() {

    private lateinit var examsRecyclerView: RecyclerView
    private lateinit var noExamsTextView: TextView
    private lateinit var examAdapter: ExamAdapter
    private lateinit var examDao: ExamDao
    private lateinit var attachmentDao: AttachmentDao
    private lateinit var subtaskDao: SubtaskDao
    private lateinit var addExamFab: FloatingActionButton
    private lateinit var examListTitle: TextView
    private lateinit var editTextSearchExams: FuzzyAutoCompleteTextView
    private lateinit var notificationHelper: NotificationHelper

    // Variables for FAB movement
    private var lastX: Int = 0
    private var lastY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var isClick: Boolean = true // Flag to distinguish between click and drag

    // Cache to hold all displayable exams fetched from the database for filtering
    private var allDisplayableExamsCache: List<Exam> = emptyList() // Changed to List<Exam>
    private var allUniqueSuggestions: List<String> = emptyList() // Changed to List<String>

    // Change 3: Implement abstract methods from PeriodicallyRefreshAppCompatActivity
    override fun getRecyclerView(): RecyclerView = examsRecyclerView
    override fun getAdapter(): ListAdapter<*, *> = examAdapter

    @SuppressLint("ClickableViewAccessibility") // Suppress warning for setOnTouchListener
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.exam_activity_list)

        examsRecyclerView = findViewById(R.id.examsRecyclerView)
        noExamsTextView = findViewById(R.id.noExamsTextView)
        addExamFab = findViewById(R.id.addExamFab) // Initialize FAB
        examListTitle = findViewById(R.id.examListTitle) // Initialize title
        editTextSearchExams = findViewById(R.id.editTextSearchExams) // Initialize FuzzyAutoCompleteTextView

        FuzzyAutoCompleteDisappearingKeyboard.getInstance().register(editTextSearchExams)

        // Set the activity title
        examListTitle.text = getString(R.string.view_exams_button_text)

        examDao = AppDatabase.getDatabase(this).examDao()
        attachmentDao = AppDatabase.getDatabase(this).attachmentDao() // Initialize AttachmentDao
        subtaskDao = AppDatabase.getDatabase(this).subtaskDao() // Initialize SubtaskDao
        notificationHelper = NotificationHelper(this)

        setupRecyclerView()
        setupSearchBar()

        // Set up FAB click listener (this will now be triggered by performClick)
        addExamFab.setOnClickListener {
            val intent = Intent(this, AddExamActivity::class.java)
            startActivity(intent)
        }

        // Set up FAB touch listener for movement
        addExamFab.setOnTouchListener { view, event ->
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

                    // Boundary checks to keep FAB within parent bounds
                    newX = newX.coerceIn(0f, parentWidth.toFloat())
                    newY = newY.coerceIn(0f, parentHeight.toFloat())

                    view.x = newX
                    view.y = newY

                    lastX = event.rawX.toInt()
                    lastY = event.rawY.toInt()

                    // If moved more than a threshold, it's a drag, not a click
                    val touchSlop = View.getDefaultSize(0, 0) // A small threshold for movement
                    val deltaX = Math.abs(event.rawX - initialTouchX)
                    val deltaY = Math.abs(event.rawY - initialTouchY)
                    if (deltaX > touchSlop || deltaY > touchSlop) {
                        isClick = false
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (isClick) {
                        view.performClick() // Explicitly call performClick for accessibility and default behavior
                    }
                }
                else -> return@setOnTouchListener false
            }
            true // Consume the event
        }

        // Position the FAB at the bottom right after layout is complete
        addExamFab.post {
            val parentView = addExamFab.parent as View
            // Define these dimensions in your dimens.xml file if they don't exist
            val marginEnd = resources.getDimensionPixelSize(R.dimen.fab_margin_end) // Define this in dimens.xml
            val marginBottom = resources.getDimensionPixelSize(R.dimen.fab_margin_bottom) // Define this in dimens.xml

            addExamFab.x = (parentView.width - addExamFab.width - marginEnd).toFloat()
            addExamFab.y = (parentView.height - addExamFab.height - marginBottom).toFloat()
        }
    }

    // Change 4: Remove manual ui update handler calls from onResume (now handled by base class)
    override fun onResume() {
        super.onResume() // Call super.onResume() to trigger the periodic update from base class
//        loadExams() // Refresh exams list when returning to this activity
    }

    override fun triggerDataRefresh() {
        loadAndFilterExams() // Refresh exams list when returning to this activity
    }

    private fun setupRecyclerView() {
        examAdapter = ExamAdapter(
            onDeleteClick = { exam ->
                confirmDeleteExam(exam)
            },
            onEditClick = { exam ->
                editExam(exam)
            },
            attachmentDao = attachmentDao, // Pass AttachmentDao
            coroutineScope = lifecycleScope // Pass CoroutineScope
        )
        examsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ExamListActivity)
            adapter = examAdapter
        }
    }

    private fun setupSearchBar() {
        // Set an empty ArrayAdapter initially to prevent NullPointerException
        editTextSearchExams.setAdapter(ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            emptyList<String>()
        ))

        lifecycleScope.launch {
            try {
                // CORRECTED: Use a reliable method to get current UTC milliseconds.
                // This ensures consistency with the stored start/end time milliseconds.
                val currentTimeMillis = Instant.now().toEpochMilli()

                // Collect only displayable exams from the database using the DAO method
                examDao.getDisplayableExams(currentTimeMillis).collectLatest { exams ->
                    // Create a list of unique concatenated strings for fuzzy search suggestions
                    // Format: "{courseName} {examDetails} {venue}"
                    allUniqueSuggestions = exams
                        .map { exam ->
                            "${exam.courseName} ${exam.examDetails.orEmpty()} ${exam.venue}"
                        }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .sorted()

                    withContext(Dispatchers.Main) {
                        // Set the combined list of suggestions to the FuzzyAutoCompleteTextView
                        editTextSearchExams.setFuzzySuggestions(allUniqueSuggestions)
                        editTextSearchExams.threshold = 1 // Start suggesting after 1 character
                    }
                }
            } catch (e: Exception) {
                Log.e("ExamListActivity", "Error loading exams: ${e.message}", e)
                // Toast.makeText(this@ExamListActivity, "Error loading exams: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }

        editTextSearchExams.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // IMPORTANT: Explicitly trigger the FuzzyAutoCompleteTextView's own filter for suggestions
                editTextSearchExams.filter.filter(s) // Correct way to trigger filtering
                // Also filter the main RecyclerView based on the search query
                filterExams(s.toString(), allDisplayableExamsCache) // Filter based on the cache
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun loadAndFilterExams() {
        lifecycleScope.launch {
            try {
                // TIMEZONE CHANGE: Use current UTC time for query
                val currentTimeMillis = Instant.now().toEpochMilli()

                // Collect only displayable exams from the database using the DAO method
                examDao.getDisplayableExams(currentTimeMillis).collectLatest { exams ->
                    allDisplayableExamsCache = exams // Changed to store Exam objects directly
                    val currentSearchQuery = editTextSearchExams.text.toString().trim()
                    filterExams(currentSearchQuery, allDisplayableExamsCache) // Apply filter after loading
                    updateNoExamsVisibility()
                }
                Log.d("ExamListActivity", "displayableExamsCache is : $allDisplayableExamsCache")
            } catch (e: Exception) {
                Log.e("ExamListActivity", "Error loading exams: ${e.message}", e)
                // Toast.makeText(this@ExamListActivity, "Error loading exams: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Filters the list of exams based on the search query using FuzzySearch.
     * @param query The search query string.
     * @param exams The list of Exam to filter.
     */
    // TIMEZONE CHANGE: This entire method is updated for the sorting logic
    @SuppressLint("NotifyDataSetChanged")
    private fun filterExams(query: String, exams: List<Exam>) {
        val nowMillis = Instant.now().toEpochMilli()

        // Helper function to determine the status of an exam
        fun getExamStatus(exam: Exam): Int {
            return when {
                nowMillis >= exam.startDateTimeMillis && nowMillis < exam.endDateTimeMillis -> 0 // Ongoing
                nowMillis < exam.startDateTimeMillis -> 1 // Future
                else -> 2 // Past
            }
        }

        val filteredAndSortedList = if (query.isBlank()) {
            exams.sortedWith(
                compareBy(
                    { getExamStatus(it) }, // Primary sort: by status
                    { it.startDateTimeMillis }, // Secondary sort: by start time
                    { it.courseName }, // Tertiary sort
                    { it.examDetails }
                )
            )
        } else {
            val scoredExams = exams.map { exam ->
                val targetString = "${exam.courseName} ${exam.examDetails.orEmpty()} ${exam.venue}"
                val score = FuzzySearch.weightedRatio(query.lowercase(Locale.getDefault()), targetString.lowercase(Locale.getDefault()))
                Triple(exam, score, getExamStatus(exam))
            }

            val brackets = listOf(100, 90, 80, 70, 60, 50, 40, 30, 20, 10, 0)

            brackets.flatMap { upperBound ->
                val lowerBound = if (upperBound == 0) 0 else upperBound - 10
                scoredExams
                    .filter { (_, score, _) ->
                        if (upperBound == 100) score in lowerBound..upperBound else score in lowerBound until upperBound
                    }
                    .sortedWith(
                        compareBy(
                            { it.third }, // Sort by status within the score bracket
                            { it.first.startDateTimeMillis }, // Then by start time
                            { it.first.courseName },
                            { it.first.examDetails }
                        )
                    )
                    .map { it.first }
            }
        }

        examAdapter.submitList(filteredAndSortedList)
        // CORRECTED: Force a full redraw to update colors and order correctly on refresh.
        // This is necessary because DiffUtil won't detect changes based on the passage of time.
        examAdapter.notifyDataSetChanged()

        // val isEmpty = filteredAndSortedList.isEmpty()
        // noExamsTextView.visibility = if (isEmpty) View.VISIBLE else View.GONE
        // examsRecyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    /**
     * Updates the visibility of the "no actions found" TextView based on the adapter's item count.
     */
    private fun updateNoExamsVisibility() {
        noExamsTextView.visibility = if (examAdapter.itemCount == 0) View.VISIBLE else View.GONE
        examsRecyclerView.visibility = if (examAdapter.itemCount == 0) View.GONE else View.VISIBLE
    }

    private fun confirmDeleteExam(exam: Exam) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.confirm_delete_title))
            .setMessage(getString(R.string.confirm_delete_exam_message, exam.courseName, exam.examDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))))
            .setPositiveButton(R.string.dialog_ok_button) { dialog, _ ->
                deleteExam(exam)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.dialog_cancel_button) { dialog, _ ->
                dialog.cancel()
            }
            .show()
    }

    private fun deleteExam(exam: Exam) {
        lifecycleScope.launch {
            try {
                val deletedRows = withContext(Dispatchers.IO) {
                    exam.examId?.let { id ->
                        // Delete associated notifications
                        notificationHelper.cancelExamNotifications(id)
                        // Delete associated attachments first
                        attachmentDao.deleteAttachmentsForEvent("Exam", id)
                        subtaskDao.deleteAllSubtasksForEvent("Exam", id) // Delete associated subtasks
                        examDao.deleteExam(id)
                    }
                }
                withContext(Dispatchers.Main) {
                    // Explicitly convert to Int for comparison to avoid "Unresolved reference"
                    val actualDeletedRows = deletedRows ?: 0
                    if (actualDeletedRows > 0) {
                        Toast.makeText(this@ExamListActivity, getString(R.string.exam_deleted_success, exam.courseName), Toast.LENGTH_SHORT).show()
                        loadAndFilterExams() // Refresh list
                    } else {
                        Toast.makeText(this@ExamListActivity, getString(R.string.exam_delete_failure), Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("ExamListActivity", "Error deleting exam: ${e.message}", e)
                Toast.makeText(this@ExamListActivity, getString(R.string.exam_delete_failure_error, e.localizedMessage), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun editExam(exam: Exam) {
        val intent = Intent(this, AddExamActivity::class.java).apply {
            putExtra("EXAM_ID", exam.examId)
        }
        startActivity(intent)
    }
}
