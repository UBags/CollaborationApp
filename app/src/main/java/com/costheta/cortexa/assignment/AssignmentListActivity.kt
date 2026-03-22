package com.costheta.cortexa.assignment

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action


import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.costheta.cortexa.data.db.AppDatabase
import com.costheta.cortexa.data.db.dao.AssignmentDao
import com.costheta.cortexa.data.models.Assignment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.CancellationException
import com.costheta.cortexa.data.db.dao.AttachmentDao
import com.costheta.cortexa.data.db.dao.SubtaskDao
import me.xdrop.fuzzywuzzy.FuzzySearch
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.costheta.cortexa.util.autofill.FuzzyAutoCompleteTextView
import android.annotation.SuppressLint
import com.costheta.cortexa.util.uirefresh.PeriodicallyRefreshAppCompatActivity
import androidx.recyclerview.widget.ListAdapter
import com.costheta.cortexa.NotificationHelper
import com.costheta.cortexa.R
import com.costheta.cortexa.util.keyboard.FuzzyAutoCompleteDisappearingKeyboard

class AssignmentListActivity : PeriodicallyRefreshAppCompatActivity() {

    private lateinit var assignmentsRecyclerView: RecyclerView
    private lateinit var noAssignmentsTextView: TextView
    private lateinit var editTextSearchAssignments: FuzzyAutoCompleteTextView
    private lateinit var assignmentAdapter: AssignmentAdapter
    private lateinit var assignmentDao: AssignmentDao
    private lateinit var attachmentDao: AttachmentDao
    private lateinit var subtaskDao: SubtaskDao
    private lateinit var addAssignmentFab: FloatingActionButton
    private lateinit var assignmentListTitle: TextView
    private lateinit var notificationHelper: NotificationHelper

    // Variables for FAB movement
    private var lastX: Int = 0
    private var lastY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var isClick: Boolean = true // Flag to distinguish between click and drag

    // Cache to hold all assignments fetched from the database
    private var allDisplayableAssignmentsCache: List<Assignment> = emptyList() // Changed to Assignment
    private var allUniqueSuggestions: List<String> = emptyList() // Changed to List<String>


    // Change 3: Implement abstract methods from PeriodicallyRefreshAppCompatActivity
    override fun getRecyclerView(): RecyclerView = assignmentsRecyclerView
    override fun getAdapter(): ListAdapter<*, *> = assignmentAdapter

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.assignment_activity_list)

        assignmentsRecyclerView = findViewById(R.id.assignmentsRecyclerView)
        noAssignmentsTextView = findViewById(R.id.noAssignmentsTextView)
        editTextSearchAssignments = findViewById(R.id.editTextSearchAssignments)
        addAssignmentFab = findViewById(R.id.addAssignmentFab)
        assignmentListTitle = findViewById(R.id.assignmentListTitle)

        // Set the activity title
        assignmentListTitle.text = getString(R.string.assignments_title)

        // Initialize the DisappearingKeyboard utility and attach it to the search bar
        FuzzyAutoCompleteDisappearingKeyboard.getInstance().register(editTextSearchAssignments)

        assignmentDao = AppDatabase.getDatabase(applicationContext).assignmentDao()
        attachmentDao = AppDatabase.getDatabase(applicationContext).attachmentDao()
        subtaskDao = AppDatabase.getDatabase(applicationContext).subtaskDao()
        notificationHelper = NotificationHelper(applicationContext)

        setupRecyclerView()
        setupSearchBar()

        // Set up FAB click listener (this will now be triggered by performClick)
        addAssignmentFab.setOnClickListener {
            val intent = Intent(this, AddAssignmentActivity::class.java)
            startActivity(intent)
        }

        // Set up FAB touch listener for movement
        // Suppress the warning because we are explicitly calling performClick()
        addAssignmentFab.setOnTouchListener { view, event ->
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
        addAssignmentFab.post {
            val parentView = addAssignmentFab.parent as View
            val marginEnd = resources.getDimensionPixelSize(R.dimen.fab_margin_end) // Define this in dimens.xml
            val marginBottom = resources.getDimensionPixelSize(R.dimen.fab_margin_bottom) // Define this in dimens.xml

            addAssignmentFab.x = (parentView.width - addAssignmentFab.width - marginEnd).toFloat()
            addAssignmentFab.y = (parentView.height - addAssignmentFab.height - marginBottom).toFloat()
        }
    }

    // Change 4: Remove manual ui update handler calls from onResume (now handled by base class)
    override fun onResume() {
        super.onResume() // Call super.onResume() to trigger the periodic update from base class
//        loadAssignments() // Refresh assignments list when returning to this activity
    }

    override fun triggerDataRefresh() {
        loadAssignments() // Refresh assignments list when returning to this activity
    }

    private fun setupRecyclerView() {
        assignmentAdapter = AssignmentAdapter(
            onDeleteClick = { assignment ->
                confirmDeleteAssignment(assignment)
            },
            onEditClick = { assignment ->
                editAssignment(assignment)
            },
            onDoneClick = { assignment, isDone -> handleAssignmentDoneToggle(assignment, isDone) },
            attachmentDao = attachmentDao,
            coroutineScope = lifecycleScope
        )
        assignmentsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@AssignmentListActivity)
            adapter = assignmentAdapter
        }
    }

    private fun handleAssignmentDoneToggle(assignment: Assignment, isDone: Boolean) {
        lifecycleScope.launch {
            try {
                // If unchecking, set progress to 95, otherwise 100
                val newProgress = if (isDone) 100 else 95
                val updatedAssignment = assignment.copy(currentProgress = newProgress)

                withContext(Dispatchers.IO) {
                    assignmentDao.updateAssignment(updatedAssignment)
                }

                if (isDone) {
                    notificationHelper.cancelAssignmentNotifications(updatedAssignment.assignmentId!!)
                } else if (!updatedAssignment.silenceNotifications) {
                    notificationHelper.scheduleAssignmentNotifications(updatedAssignment)
                }

                Toast.makeText(this@AssignmentListActivity, "Assignment progress updated.", Toast.LENGTH_SHORT).show()
                // The list will refresh automatically due to the Flow from the DAO.
            } catch (e: Exception) {
                Log.e("AssignmentListActivity", "Error toggling assignment completion: ${e.message}", e)
                Toast.makeText(this@AssignmentListActivity, "Failed to update assignment.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupSearchBar() {
        lifecycleScope.launch {
            try {
                // Fetch all displayable assignments to create comprehensive search suggestions
                assignmentDao.getDisplayableAssignments().collectLatest { assignments ->
                    val allDisplayableAssignments = assignments // Store Assignment objects directly
                    // Create a list of unique concatenated strings for fuzzy search suggestions
                    allUniqueSuggestions = allDisplayableAssignments
                        .map { assignment ->
                            "${assignment.courseName} ${assignment.assignmentTopic} ${assignment.assignmentName}"
                        }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .sorted()

                    withContext(Dispatchers.Main) {
                        // Set the combined list of suggestions to the FuzzyAutoCompleteTextView
                        editTextSearchAssignments.setFuzzySuggestions(allUniqueSuggestions)
                    }
                }
            } catch (e: Exception) {
                Log.e("AssignmentListActivity", "Error loading unique names for search: ${e.message}", e)
            }
        }

        editTextSearchAssignments.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                editTextSearchAssignments.filter.filter(s) // Correct way to trigger filtering
                filterAssignments(s.toString(), allDisplayableAssignmentsCache)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun loadAssignments() {
        lifecycleScope.launch {
            try {
                // Collect only displayable assignments from the database using the new DAO method
                assignmentDao.getDisplayableAssignments().collectLatest { assignments ->
                    allDisplayableAssignmentsCache = assignments // Store Assignment objects directly
                    val currentSearchQuery = editTextSearchAssignments.text.toString().trim()
                    filterAssignments(currentSearchQuery, allDisplayableAssignmentsCache)
                    updateNoAssignmentsVisibility()
                }
            } catch (e: CancellationException) {
                Log.d("AssignmentListActivity", "Assignment loading job cancelled: ${e.message}")
            } catch (e: Exception) {
                Log.e("AssignmentListActivity", "Error loading assignments: ${e.message}", e)
                // Toast.makeText(this@AssignmentListActivity, "Error loading assignments: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun filterAssignments(query: String, assignments: List<Assignment>) {
        // MODIFIED: Define the detailed comparator once
        val detailedComparator = compareBy<Assignment> { it.currentProgress == 100 } // false (incomplete) comes before true (complete)
            .thenBy { it.dueDateTimeMillis }
            .thenBy { it.courseName.lowercase(Locale.getDefault()) }
            .thenBy { it.assignmentName.lowercase(Locale.getDefault()) }

        val filteredAndSortedList = if (query.isBlank()) {
            assignments.sortedWith(detailedComparator)
        } else {
            val scoredAssignments = assignments.map { assignment ->
                val targetString = "${assignment.courseName} ${assignment.assignmentTopic} ${assignment.assignmentName}"
                val score = FuzzySearch.weightedRatio(query.lowercase(Locale.getDefault()), targetString.lowercase(Locale.getDefault()))
                Pair(assignment, score)
            }

            val brackets = listOf(100, 90, 80, 70, 60, 50, 40, 30, 20, 10, 0)

            brackets.flatMap { upperBound ->
                val lowerBound = if (upperBound == 0) 0 else upperBound - 10
                scoredAssignments
                    .filter { (_, score) ->
                        if (upperBound == 100) (score >= lowerBound) && (score <= upperBound)
                        else (score >= lowerBound) && (score < upperBound)
                    }
                    .map { it.first } // Extract the assignment object
                    .sortedWith(detailedComparator) // Sort this bucket with the detailed comparator
            }
        }

        assignmentAdapter.submitList(filteredAndSortedList) {
            // After the list is updated, scroll to the top
            if (filteredAndSortedList.isNotEmpty()) {
                assignmentsRecyclerView.scrollToPosition(0)
            }
        }
        assignmentAdapter.notifyDataSetChanged()
    }

    /**
     * Updates the visibility of the "no actions found" TextView based on the adapter's item count.
     */
    private fun updateNoAssignmentsVisibility() {
        noAssignmentsTextView.visibility = if (assignmentAdapter.itemCount == 0) View.VISIBLE else View.GONE
        assignmentsRecyclerView.visibility = if (assignmentAdapter.itemCount == 0) View.GONE else View.VISIBLE
    }



    private fun confirmDeleteAssignment(assignment: Assignment) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.confirm_delete_title))
            .setMessage(getString(R.string.confirm_delete_assignment_message, assignment.assignmentName, assignment.courseName, assignment.dueDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))))
            .setPositiveButton(R.string.dialog_ok_button) { dialog, _ ->
                deleteAssignment(assignment)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.dialog_cancel_button) { dialog, _ ->
                dialog.cancel()
            }
            .show()
    }

    private fun deleteAssignment(assignment: Assignment) {
        lifecycleScope.launch {
            try {
                val deletedRows = withContext(Dispatchers.IO) {
                    assignment.assignmentId?.let { id ->
                        notificationHelper.cancelAssignmentNotifications(id)
                        attachmentDao.deleteAttachmentsForEvent("Assignment", id)
                        subtaskDao.deleteAllSubtasksForEvent("Assignment", id)
                        assignmentDao.deleteAssignment(id)
                    }
                }
                withContext(Dispatchers.Main) {
                    if (deletedRows != null && deletedRows > 0) {
                        Toast.makeText(this@AssignmentListActivity, getString(R.string.assignment_deleted_success, assignment.assignmentName), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@AssignmentListActivity, getString(R.string.assignment_delete_failure), Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("AssignmentListActivity", "Error deleting assignment: ${e.message}", e)
                Toast.makeText(this@AssignmentListActivity, getString(R.string.assignment_delete_failure_error, e.localizedMessage), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun editAssignment(assignment: Assignment) {
        val intent = Intent(this, AddAssignmentActivity::class.java).apply {
            putExtra("ASSIGNMENT_ID", assignment.assignmentId)
        }
        startActivity(intent)
    }
}
