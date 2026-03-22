package com.costheta.cortexa.action

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action


import android.annotation.SuppressLint
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
import com.costheta.cortexa.data.db.dao.AttachmentDao
import com.costheta.cortexa.data.db.dao.SubtaskDao
import com.costheta.cortexa.data.models.Action
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import me.xdrop.fuzzywuzzy.FuzzySearch // Import FuzzySearch library
import com.costheta.cortexa.util.autofill.FuzzyAutoCompleteTextView // NEW: Import FuzzyAutoCompleteTextView
import com.costheta.cortexa.util.uirefresh.PeriodicallyRefreshAppCompatActivity
import androidx.recyclerview.widget.ListAdapter // NEW: Import ListAdapter
import com.costheta.cortexa.NotificationHelper
import com.costheta.cortexa.R
import com.costheta.cortexa.util.keyboard.FuzzyAutoCompleteDisappearingKeyboard
import java.time.Instant
import java.time.LocalDateTime

class ActionListActivity : PeriodicallyRefreshAppCompatActivity() {

    private lateinit var actionDao: com.costheta.cortexa.data.db.dao.ActionDao
    private lateinit var attachmentDao: AttachmentDao
    private lateinit var subtaskDao: SubtaskDao
    private lateinit var actionAdapter: ActionAdapter
    private lateinit var actionRecyclerView: RecyclerView
    private lateinit var noActionsTextView: TextView
    private lateinit var editTextSearchActions: FuzzyAutoCompleteTextView // CHANGED to FuzzyAutoCompleteTextView
    private lateinit var addActionFab: FloatingActionButton
    private lateinit var notificationHelper: NotificationHelper

    private var allDisplayableActionsCache: List<Action> = emptyList()
    private var uniqueSearchSuggestions: List<String> = emptyList()

    // Variables for FAB movement
    private var lastX: Int = 0
    private var lastY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var isClick: Boolean = true // Flag to distinguish between click and drag

    override fun getRecyclerView(): RecyclerView = actionRecyclerView
    override fun getAdapter(): ListAdapter<*, *> = actionAdapter

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.action_activity_list)

        actionDao = AppDatabase.getDatabase(this).actionDao()
        attachmentDao = AppDatabase.getDatabase(this).attachmentDao()
        subtaskDao = AppDatabase.getDatabase(this).subtaskDao()
        notificationHelper = NotificationHelper(this)

        // NEW LOGGING: Fetch all actions and print their details at startup
//        lifecycleScope.launch {
//            val allActionsInDb = withContext(Dispatchers.IO) {
//                actionDao.getAllActivities().firstOrNull() ?: emptyList()
//            }
//            val currentMillis = Instant.now().toEpochMilli()
//            Log.d("ActionListActivity", "--- All Actions in DB at onCreate ---")
//            Log.d("ActionListActivity", "Current Time Millis for filtering: $currentMillis")
//            if (allActionsInDb.isEmpty()) {
//                Log.d("ActionListActivity", "No actions found in the database.")
//            }
//            Log.d("ActionListActivity", "--- End All Actions in DB ---")
//        }

        actionRecyclerView = findViewById(R.id.actionRecyclerView)
        noActionsTextView = findViewById(R.id.noActionsTextView)
        editTextSearchActions = findViewById(R.id.editTextSearchActions) // Cast to FuzzyAutoCompleteTextView
        addActionFab = findViewById(R.id.addActionFab)

        FuzzyAutoCompleteDisappearingKeyboard.getInstance().register(editTextSearchActions)

        // NEW: Initialize search bar with an empty adapter to ensure filter is not null initially
        editTextSearchActions.setAdapter(ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            emptyList<String>()
        ))

        setupRecyclerView()
        setupSearchBar()

        // Set up FAB click listener (this will now be triggered by performClick)
        addActionFab.setOnClickListener {
            val intent = Intent(this, AddActionActivity::class.java)
            startActivity(intent)
        }

        // Set up FAB touch listener for movement
        addActionFab.setOnTouchListener { view, event ->
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
        addActionFab.post {
            val parentView = addActionFab.parent as View
            // Using hardcoded values for margins as dimens.xml is not provided.
            // In a real app, these should be defined in res/values/dimens.xml
            val marginEnd = 48 // dp
            val marginBottom = 48 // dp

            // Convert dp to pixels
            val density = resources.displayMetrics.density
            val marginEndPx = (marginEnd * density).toInt()
            val marginBottomPx = (marginBottom * density).toInt()

            addActionFab.x = (parentView.width - addActionFab.width - marginEndPx).toFloat()
            addActionFab.y = (parentView.height - addActionFab.height - marginBottomPx).toFloat()
        }
    }

    private fun setupRecyclerView() {
        actionAdapter = ActionAdapter(
            onDeleteClick = { action -> showDeleteConfirmationDialog(action) },
            onEditClick = { action -> editAction(action) },
            // onAttachmentClick = { action -> viewAttachmentsForAction(action) },
            onDoneClick = { action, isDone -> handleActionDoneToggle(action, isDone) },
            coroutineScope = lifecycleScope,
            attachmentDao = attachmentDao
        )

        actionRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ActionListActivity)
            adapter = actionAdapter
        }
    }

    /**
     * NEW: Handles the logic when the 'Done' checkbox is toggled for an action.
     */
    private fun handleActionDoneToggle(action: Action, isDone: Boolean) {
        lifecycleScope.launch {
            try {
                val updatedAction = action.copy(isDone = isDone)
                withContext(Dispatchers.IO) {
                    actionDao.updateAction(updatedAction)
                    // If the action is marked as done, cancel its notifications.
                    // If it's marked as not done, reschedule them.
                    if (isDone) {
                        action.actionId?.let { notificationHelper.cancelActionNotifications(it) }
                    } else {
                        notificationHelper.scheduleActionNotifications(updatedAction)
                    }
                }
                // Trigger a reload to re-sort and re-draw the list immediately.
                triggerDataRefresh()
                val status = if(isDone) "marked as done" else "marked as not done"
                Toast.makeText(this@ActionListActivity, "'${action.actionName}' $status.", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                Log.e("ActionListActivity", "Error updating action 'done' status: ${e.message}", e)
                Toast.makeText(this@ActionListActivity, "Failed to update action status.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupSearchBar() {
        // Initialize search bar with an empty adapter
        editTextSearchActions.setAdapter(ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            emptyList<String>()
        ))

        lifecycleScope.launch {
            try {
                val currentTimeMillis = Instant.now().toEpochMilli()
                actionDao.getDisplayableActions(currentTimeMillis).collectLatest { actions ->
                    // Update search suggestions whenever the main list of actions changes
                    uniqueSearchSuggestions = actions
                        .map { action ->
                            "${action.actionName} ${action.description.orEmpty()} ${action.venue.orEmpty()}"
                        }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .sorted()

                    // Update the adapter for the FuzzyAutoCompleteTextView on the main thread
                    withContext(Dispatchers.Main) {
                        editTextSearchActions.setFuzzySuggestions(uniqueSearchSuggestions)
                        editTextSearchActions.threshold = 1 // Start suggesting after 1 character
                    }
                }
            } catch (e: Exception) {
                Log.e("ActionListActivity", "Error loading Action: ${e.message}", e)
                // Toast.makeText(this@ActionListActivity, "Error loading Action: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }

        editTextSearchActions.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // IMPORTANT: Explicitly trigger the FuzzyAutoCompleteTextView's own filter for suggestions
                editTextSearchActions.filter.filter(s) // Correct way to trigger filtering

                // Also filter the main RecyclerView based on the search query
                filterActions(s.toString(), allDisplayableActionsCache)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun loadAndFilterActions() {
        lifecycleScope.launch {
            try {
                // CORRECTED: Use a reliable method to get current UTC milliseconds.
                // This ensures consistency with the stored start/end time milliseconds.
                val currentTimeMillis = Instant.now().toEpochMilli()
                Log.d("ActionListActivity", "Listing all Actions for time $currentTimeMillis")
//                actionDao.getAllActivities().collectLatest { actions ->
//                    actions.forEachIndexed { index, action ->
//                        Log.d("ActionListActivity", "From all Actions #${index + 1}: $action")
//                    }
//                }
                // CORRECTED: Use firstOrNull() for a one-time read for logging.
                // This gets the current list without blocking the coroutine.
//                val allActionsForLogging = withContext(Dispatchers.IO) {
//                    actionDao.getAllActivities().firstOrNull() ?: emptyList()
//                }
//                allActionsForLogging.forEachIndexed { index, action ->
//                    Log.d("ActionListActivity", "From all Actions #${index + 1}: $action")
//                }
                // Now this log will be printed because the previous operation completed.
//                Log.d("ActionListActivity", "About to call getDisplayableActions()")
                actionDao.getDisplayableActions(currentTimeMillis).collectLatest { actions ->
                    allDisplayableActionsCache = actions
//                    allDisplayableActionsCache.forEachIndexed { index, action ->
//                        Log.d("ActionListActivity", "Displayable Action #${index + 1}: $action")
//                    }
                    // Apply filter and update visibility after the new list is received and cached
                    filterActions(editTextSearchActions.text.toString(), allDisplayableActionsCache)
                    updateNoActionsVisibility()
                }
            } catch (e: Exception) {
                Log.e("ActionListActivity", "Error loading Action: ${e.message}", e)
                // Toast.makeText(this@ActionListActivity, "Error loading Action: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume() // Call super.onResume() to trigger the periodic update from base class
//        loadActions()
    }

    override fun triggerDataRefresh() {
        loadAndFilterActions()
    }

    /**
     * Filters the list of actions based on the search query using fuzzy matching.
     * Sorts results by matching score (descending) then by custom action status and date-time.
     */
    @SuppressLint("NotifyDataSetChanged")
    private fun filterActions(query: String, actions : List<Action>) {
        val filteredAndSortedList = if (query.isBlank()) {
            // If query is blank, sort by custom action status and date-time
            sortActionsCustom(actions)
        } else {
            val scoredActions = actions
                .map { action ->
                    val actionName = action.actionName.lowercase(Locale.getDefault()) ?: ""
                    val description = action.description?.lowercase(Locale.getDefault()) ?: ""
                    val location = action.venue?.lowercase(Locale.getDefault()) ?: ""
                    val combinedString = "$actionName $description $location"
                    val score = FuzzySearch.weightedRatio(query.lowercase(Locale.getDefault()), combinedString.lowercase(Locale.getDefault()))

                    Triple(action, score, LocalDateTime.of(action.startDate, action.startTime))
                }

            // Define score brackets for sorting
            val brackets = listOf(100, 90, 80, 70, 60, 50, 40, 30, 20, 10, 0)

            val groupedAndSorted = brackets.flatMap { upperBound ->
                val lowerBound = if (upperBound == 0) 0 else upperBound - 10
                val actionsInBracket = scoredActions
                    .filter { (_, score, _) ->
                        // Ensure score ranges are mutually exclusive to prevent duplicates
                        if (upperBound == 100) {
                            (score >= lowerBound) && (score <= upperBound) // For the highest bracket, include the upper bound (100)
                        } else {
                            (score >= lowerBound) && (score < upperBound) // For all other brackets, include lower bound but exclude upper bound
                        }
                    }
                    .map { it.first } // Extract the original Action object

                // Apply custom sorting within each bracket
                sortActionsCustom(actionsInBracket)
            }
            groupedAndSorted
        }
        actionAdapter.submitList(filteredAndSortedList)
        // CORRECTED: Force a full redraw to update colors and order correctly on refresh.
        // This is necessary because DiffUtil won't detect changes based on the passage of time.
        actionAdapter.notifyDataSetChanged()
        // Removed updateNoActionsVisibility() here, it's now handled in loadActions()
    }

    /**
     * Custom sorting logic for actions based on completion status and start/end dates.
     * a. Incomplete Actions, that have already started, are at the top of the band, ordered in ascending order of start datetime
     * b. Incomplete Actions, that are not yet started, are above these, ordered in ascending order of start datetime
     * c. Completed Actions are at the bottom, ordered in descending order of end datetime
     */
    private fun sortActionsCustom(actions: List<Action>): List<Action> {
        val now = LocalDateTime.now()
        return actions.sortedWith(Comparator { a1, a2 ->
            val a1StartDateTime = LocalDateTime.of(a1.startDate, a1.startTime)
            val a1EndDateTime = LocalDateTime.of(a1.endDate, a1.endTime)
            val a2StartDateTime = LocalDateTime.of(a2.startDate, a2.startTime)
            val a2EndDateTime = LocalDateTime.of(a2.endDate, a2.endTime)

            // Category 1: Incomplete, already started (a. at the top)
            val a1Started = !a1.isDone && a1StartDateTime.isBefore(now)
            val a2Started = !a2.isDone && a2StartDateTime.isBefore(now)

            // Category 2: Incomplete, not yet started (b. above completed)
            val a1NotStarted = !a1.isDone && !a1StartDateTime.isBefore(now)
            val a2NotStarted = !a2.isDone && !a2StartDateTime.isBefore(now)

            // Category 3: Completed Actions (c. at the bottom)
            val a1Completed = a1.isDone
            val a2Completed = a2.isDone

            when {
                // Both are Category 1
                a1Started && a2Started -> a1StartDateTime.compareTo(a2StartDateTime) // Ascending start date

                // a1 is Category 1, a2 is not
                a1Started && !a2Started -> -1
                // a2 is Category 1, a1 is not
                !a1Started && a2Started -> 1

                // Both are Category 2
                a1NotStarted && a2NotStarted -> a1StartDateTime.compareTo(a2StartDateTime) // Ascending start date

                // a1 is Category 2, a2 is Category 3
                a1NotStarted && a2Completed -> -1
                // a2 is Category 2, a1 is Category 3
                a2NotStarted && a1Completed -> 1

                // Both are Category 3
                a1Completed && a2Completed -> a2EndDateTime.compareTo(a1EndDateTime) // Descending end date

                // Fallback (should not be reached if categories cover all cases)
                else -> 0
            }
        })
    }

    /**
     * Updates the visibility of the "no actions found" TextView based on the adapter's item count.
     */
    private fun updateNoActionsVisibility() {
        noActionsTextView.visibility = if (actionAdapter.itemCount == 0) View.VISIBLE else View.GONE
        actionRecyclerView.visibility = if (actionAdapter.itemCount == 0) View.GONE else View.VISIBLE
    }

    /**
     * Shows a confirmation dialog before deleting an action.
     */
    private fun showDeleteConfirmationDialog(action: Action) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_action_confirm_title)
            .setMessage(getString(R.string.delete_action_confirm_message, action.actionName))
            .setPositiveButton(R.string.delete_action_confirm_positive) { dialog, _ ->
                deleteAction(action)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.dialog_cancel_button) { dialog, _ ->
                dialog.cancel()
            }
            .show()
    }

    /**
     * Deletes an action from the database, along with its associated attachments and subtasks,
     * and cancels any scheduled notifications.
     */
    private fun deleteAction(action: Action) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    action.actionId?.let { id ->
                        notificationHelper.cancelActionNotifications(id) // Cancel notifications for this action
                        attachmentDao.deleteAttachmentsForEvent("Action", id)
                        subtaskDao.deleteAllSubtasksForEvent("Action", id)
                        actionDao.deleteAction(id)
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ActionListActivity, getString(R.string.action_deleted_success, action.actionName), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ActionListActivity, getString(R.string.action_deletion_failure, e.localizedMessage), Toast.LENGTH_LONG).show()
                    Log.e("ActionListActivity", "Error deleting action: ${e.message}", e)
                }
            }
        }
    }

    /**
     * Launches the AddActionActivity to edit an existing action.
     */
    private fun editAction(action: Action) {
        val intent = Intent(this, AddActionActivity::class.java).apply {
            putExtra("ACTION_ID", action.actionId)
        }
        startActivity(intent)
    }

    /**
     * Launches the AddActionActivity to view/manage attachments for an action.
     * This reuses the AddActionActivity as it contains the attachment management UI.
     */
    private fun viewAttachmentsForAction(action: Action) {
        val intent = Intent(this, AddActionActivity::class.java).apply {
            putExtra("ACTION_ID", action.actionId)
            // Optionally, you could pass an extra here to indicate that the attachment section
            // should be expanded or highlighted on load if that UI exists.
        }
        startActivity(intent)
    }
}
