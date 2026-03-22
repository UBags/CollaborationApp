package com.costheta.cortexa.util.subtask

import android.app.Activity
import android.app.AlertDialog
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.costheta.cortexa.R
import com.costheta.cortexa.data.db.AppDatabase
import com.costheta.cortexa.data.db.dao.ActionDao
import com.costheta.cortexa.data.db.dao.AssignmentDao
import com.costheta.cortexa.data.db.dao.EngagementDao
import com.costheta.cortexa.data.db.dao.ExamDao
import com.costheta.cortexa.data.db.dao.NotesDao
import com.costheta.cortexa.data.db.dao.SubtaskDao
import com.costheta.cortexa.data.db.dao.TodoDao
import com.costheta.cortexa.data.models.Subtask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.firstOrNull
import java.util.Locale

/**
 * Manages all subtask-related operations, including adding, deleting, reordering,
 * and copying subtasks. It interacts with the SubtaskDao and communicates with
 * the hosting Activity/Fragment via SubtaskManagerCallbacks.
 *
 * @param activity The hosting Activity context. (CHANGED: Context to Activity for dialogs)
 * @param subtaskDao The DAO for Subtask operations.
 * @param lifecycleScope The CoroutineScope for launching suspend functions.
 * @param callbacks The callback interface to interact with the hosting Activity/Fragment.
 */
class SubtaskManager(
    private val activity: Activity, // CHANGED: Context to Activity
    private val subtaskDao: SubtaskDao,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val callbacks: SubtaskManagerCallbacks
) {

    private val assignmentDao: AssignmentDao = AppDatabase.getDatabase(activity).assignmentDao()
    private val examDao: ExamDao = AppDatabase.getDatabase(activity).examDao()
    private val todoDao: TodoDao = AppDatabase.getDatabase(activity).todoDao()
    private val notesDao: NotesDao = AppDatabase.getDatabase(activity).notesDao()
    private val engagementDao: EngagementDao = AppDatabase.getDatabase(activity).engagementDao()
    // NEW: Added ActionDao to fetch Action details for formatting
    private val actionDao: ActionDao = AppDatabase.getDatabase(activity).actionDao()


    private val TAG = "SubtaskManager"

    // To hold the list of subtasks for the current event
    private var currentSubtasks: MutableList<Subtask> = mutableListOf()

    // Dialog reference for the copy subtasks dialog
    private var copyDialog: AlertDialog? = null

    init {
        lifecycleScope.launch {
            callbacks.getCurrentEventRefId().let { eventRefId ->
                if (eventRefId != -1L) {
                    subtaskDao.getSubtasksForEvent(callbacks.getCurrentEventType(), eventRefId)
                        .collectLatest { subtasks ->
                            currentSubtasks = subtasks.toMutableList()
                            callbacks.onSubtasksUpdated()
                            callbacks.requestUiStateLogging(currentSubtasks)
                        }
                }
            }
        }
    }

    /**
     * Helper function to calculate expected state based on a list of subtasks.
     * Mirrors the arrow visibility logic in SubtaskAdapter.
     */
    fun calculateExpectedState(subtasks: List<Subtask>): String {
        return subtasks.joinToString("\n") { subtask ->
            "Subtask(id=${subtask.subtaskId}, description=${subtask.description}, completed=${subtask.completionState}, position=${subtask.position})"
        }
    }

    /**
     * Adds new subtasks from a raw input string, splitting by sentences.
     * Ensures N-1 is redrawn by updating lastModified.
     */
    fun addSubtasksFromInput(rawInput: String) {
        if (rawInput.isBlank()) {
            callbacks.showToast(R.string.subtask_empty_input_warning, Toast.LENGTH_SHORT)
            return
        }

        val sentences = rawInput.split(Regex("[.;]"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (sentences.isEmpty()) {
            callbacks.showToast(R.string.subtask_empty_input_warning, Toast.LENGTH_SHORT)
            return
        }

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val eventRefId = callbacks.getCurrentEventRefId()
                val eventType = callbacks.getCurrentEventType()
                if (eventRefId == -1L) {
                    Log.e(TAG, "Cannot add subtask: eventRefId is -1L.")
                    withContext(Dispatchers.Main) { // Switch to Main for Toast
                        callbacks.showToast(R.string.subtask_error_no_parent_event, Toast.LENGTH_LONG)
                    }
                    return@withContext
                }

                val newSubtasks = mutableListOf<Subtask>()
                val existingSubtasks = subtaskDao.getSubtasksForEvent(eventType, eventRefId).firstOrNull() ?: emptyList()
                val existingIncompleteSubtasks = existingSubtasks.filter { !it.completionState }.sortedBy { it.position }
                val lastIncompleteSubtaskBeforeAdd = existingIncompleteSubtasks.lastOrNull()
                var nextPositionForNewIncomplete = existingIncompleteSubtasks.size

                for (sentence in sentences) {
                    val formattedDescription = formatSubtaskDescription(sentence)
                    if (existingSubtasks.none { it.description.equals(formattedDescription, ignoreCase = true) }) {
                        val newSubtask = Subtask(
                            eventType = eventType,
                            eventRefId = eventRefId,
                            description = formattedDescription,
                            completionState = false,
                            position = nextPositionForNewIncomplete,
                            validatedItem = false,
                            lastModified = System.currentTimeMillis()
                        )
                        newSubtasks.add(newSubtask)
                        nextPositionForNewIncomplete++
                    } else {
                        Log.d(TAG, "Skipping duplicate subtask: $formattedDescription")
                        withContext(Dispatchers.Main) {
                            callbacks.showToast(activity.getString(R.string.subtask_duplicate_skipped, formattedDescription), Toast.LENGTH_SHORT)
                        }
                    }
                }

                if (newSubtasks.isNotEmpty()) {
                    subtaskDao.insertAllSubtasks(newSubtasks)
                    if (lastIncompleteSubtaskBeforeAdd != null) {
                        val updatedLastSubtask = lastIncompleteSubtaskBeforeAdd.copy(
                            lastModified = System.currentTimeMillis()
                        )
                        subtaskDao.updateSubtask(updatedLastSubtask)
                        Log.d(TAG, "Forced update on N-1 subtask (ID: ${lastIncompleteSubtaskBeforeAdd.subtaskId}, Description: ${lastIncompleteSubtaskBeforeAdd.description}) to trigger DiffUtil re-bind.")
                    }
                    reassignPositionsForGroup(eventType, eventRefId, false)
                    withContext(Dispatchers.Main) {
                        callbacks.showToast(R.string.subtask_added_success, Toast.LENGTH_SHORT)
                        Log.d(TAG, "Added: ${newSubtasks.joinToString { it.description }}")
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        callbacks.showToast(R.string.subtask_no_new_subtasks_added, Toast.LENGTH_SHORT)
                    }
                }
            }
        }
    }

    /**
     * Toggles the completion state of a subtask and updates its position.
     * Ensures N-1 is redrawn by updating lastModified.
     */
    fun toggleSubtaskCompletion(subtask: Subtask, isCompleted: Boolean) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val eventType = callbacks.getCurrentEventType()
                val eventRefId = callbacks.getCurrentEventRefId()

                // 1. Get the list of subtasks *before* any changes to find the original neighbors
                val subtasksBeforeToggle = subtaskDao.getSubtasksForEvent(eventType, eventRefId).firstOrNull()
                    ?.sortedWith(compareBy({ it.completionState }, { it.position })) ?: emptyList()

                val originalIndex = subtasksBeforeToggle.indexOfFirst { it.subtaskId == subtask.subtaskId }

                // Determine the original neighbors (N-1, N+1)
                val originalNeighborsToUpdate = mutableListOf<Subtask>()
                if (originalIndex != -1) {
                    // Original N-1
                    subtasksBeforeToggle.getOrNull(originalIndex - 1)?.let {
                        // Only consider incomplete tasks for arrow visibility re-evaluation
                        if (!it.completionState) originalNeighborsToUpdate.add(it)
                    }
                    // Original N+1
                    subtasksBeforeToggle.getOrNull(originalIndex + 1)?.let {
                        // Only consider incomplete tasks for arrow visibility re-evaluation
                        if (!it.completionState) originalNeighborsToUpdate.add(it)
                    }
                }

                // Calculate max position for the target completion group
                val targetSubtasksForNewPosition = subtasksBeforeToggle.filter { it.completionState == isCompleted }
                val maxPositionInTargetGroup = targetSubtasksForNewPosition.maxOfOrNull { it.position } ?: -1
                val newPosition = maxPositionInTargetGroup + 1

                // 2. Update the subtask being toggled
                val updatedSubtask = subtask.copy(
                    completionState = isCompleted,
                    position = newPosition,
                    lastModified = System.currentTimeMillis() // Always update the toggled subtask
                )
                subtaskDao.updateSubtask(updatedSubtask)

                // 3. Re-fetch the list *after* the update to find the new neighbors
                // This is critical because the list order and positions have changed in the database.
                val subtasksAfterToggle = subtaskDao.getSubtasksForEvent(eventType, eventRefId).firstOrNull()
                    ?.sortedWith(compareBy({ it.completionState }, { it.position })) ?: emptyList()

                val newIndex = subtasksAfterToggle.indexOfFirst { it.subtaskId == updatedSubtask.subtaskId }

                // Determine the new neighbors (M-1, M+1)
                val newNeighborsToUpdate = mutableListOf<Subtask>()
                if (newIndex != -1) {
                    // New M-1
                    subtasksAfterToggle.getOrNull(newIndex - 1)?.let {
                        // Only consider incomplete tasks for arrow visibility re-evaluation
                        if (!it.completionState) newNeighborsToUpdate.add(it)
                    }
                    // New M+1
                    subtasksAfterToggle.getOrNull(newIndex + 1)?.let {
                        // Only consider incomplete tasks for arrow visibility re-evaluation
                        if (!it.completionState) newNeighborsToUpdate.add(it)
                    }
                }

                // Combine all unique neighbors that need re-binding, excluding the toggled subtask itself
                val allNeighborsToRebind = (originalNeighborsToUpdate + newNeighborsToUpdate)
                    .distinctBy { it.subtaskId } // Ensure uniqueness by ID
                    .filter { it.subtaskId != updatedSubtask.subtaskId } // Exclude the toggled subtask itself

                // 4. Update lastModified for all identified neighbors
                for (neighbor in allNeighborsToRebind) {
                    val updatedNeighbor = neighbor.copy(lastModified = System.currentTimeMillis())
                    subtaskDao.updateSubtask(updatedNeighbor)
                    Log.d(TAG, "Forced update on neighbor subtask (ID: ${updatedNeighbor.subtaskId}, Description: ${updatedNeighbor.description}) for DiffUtil re-bind.")
                }

                withContext(Dispatchers.Main) {
                    Log.d(TAG, "Checkbox clicked: ${subtask.description} : ${if (isCompleted) "Checked" else "Unchecked"}")
                }
            }
        }
    }

    /**
     * Updates the positions of subtasks in the database after a drag-and-drop reorder.
     * It compares the new list with the database state and only updates items whose
     * positions have changed.
     *
     * @param reorderedSubtasks The full list of subtasks in their new order from the adapter.
     */
    fun reorderSubtasks(reorderedSubtasks: List<Subtask>) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                // We only care about reordering incomplete tasks, as completed ones are not draggable.
                val incompleteTasks = reorderedSubtasks.filter { !it.completionState }

                // Fetch the original state from the DB to compare against.
                val originalSubtasks = subtaskDao.getSubtasksForEvent(callbacks.getCurrentEventType(), callbacks.getCurrentEventRefId())
                    .firstOrNull()?.filter { !it.completionState }?.associateBy { it.subtaskId } ?: emptyMap()

                val updatesToPerform = mutableListOf<Subtask>()

                incompleteTasks.forEachIndexed { newPosition, subtask ->
                    val originalSubtask = originalSubtasks[subtask.subtaskId]
                    // Only update the subtask if its position has actually changed.
                    if (originalSubtask != null && originalSubtask.position != newPosition) {
                        updatesToPerform.add(
                            subtask.copy(
                                position = newPosition,
                                lastModified = System.currentTimeMillis() // Update timestamp for DiffUtil
                            )
                        )
                    }
                }

                if (updatesToPerform.isNotEmpty()) {
                    // Update all changed subtasks in the database.
                    // If you have an `updateAll` DAO method, that would be more efficient.
                    updatesToPerform.forEach { subtaskDao.updateSubtask(it) }
                    Log.d("SubtaskManager", "Persisted new order for ${updatesToPerform.size} subtasks.")
                }
            }
        }
    }

    /**
     * Deletes a subtask after user confirmation.
     * Ensures N-1 is redrawn by updating lastModified.
     */
    fun deleteSubtask(subtask: Subtask) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.subtask_delete_confirm_title)
            .setMessage(activity.getString(R.string.subtask_delete_confirm_message, subtask.description))
            .setPositiveButton(R.string.subtask_delete_confirm_positive) { dialog, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        val eventType = callbacks.getCurrentEventType()
                        val eventRefId = callbacks.getCurrentEventRefId()
                        val currentSubtasks = subtaskDao.getSubtasksForEvent(eventType, eventRefId).firstOrNull()
                            ?.sortedWith(compareBy({ it.completionState }, { it.position })) ?: emptyList()
                        val subtaskIndex = currentSubtasks.indexOfFirst { it.subtaskId == subtask.subtaskId }
                        val subtaskAtNMinus1 = if (subtaskIndex > 0) currentSubtasks.getOrNull(subtaskIndex - 1) else null

                        subtaskDao.deleteSubtask(subtask.subtaskId)

                        if (subtaskAtNMinus1 != null && !subtaskAtNMinus1.completionState) {
                            val updatedNMinus1 = subtaskAtNMinus1.copy(
                                lastModified = System.currentTimeMillis()
                            )
                            subtaskDao.updateSubtask(updatedNMinus1)
                            Log.d(TAG, "Forced update on N-1 subtask (ID: ${subtaskAtNMinus1.subtaskId}, Description: ${subtaskAtNMinus1.description}) to trigger DiffUtil re-bind.")
                        }

                        reassignPositionsForGroup(eventType, eventRefId, subtask.completionState)
                        withContext(Dispatchers.Main) {
                            callbacks.showToast(R.string.subtask_deleted_success, Toast.LENGTH_SHORT)
                            Log.d(TAG, "Deleted: ${subtask.description}")
                        }
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.dialog_cancel_button) { dialog, _ ->
                dialog.cancel()
            }
            .show()
    }

    /**
     * Reassigns positions for subtasks within a specific completion group.
     * Updates lastModified for affected subtasks.
     */
    private suspend fun reassignPositionsForGroup(eventType: String, eventRefId: Long, completionState: Boolean) {
        val subtasksInGroup = subtaskDao.getSubtasksForEvent(eventType, eventRefId).firstOrNull()
            ?.filter { it.completionState == completionState }
            ?.sortedBy { it.position }
            ?: emptyList()

        subtasksInGroup.forEachIndexed { index, subtask ->
            if (subtask.position != index) {
                subtaskDao.updateSubtask(subtask.copy(
                    position = index,
                    lastModified = System.currentTimeMillis()
                ))
            }
        }
        Log.d(TAG, "Reassigned positions for ${subtasksInGroup.size} ${if (completionState) "completed" else "incomplete"} subtasks.")
    }

    /**
     * Initiates the process of copying subtasks from another event.
     */
    fun startCopySubtasksProcess() {
        lifecycleScope.launch {
            callbacks.onSubtaskCopyRequested() // Notify activity that copy process is starting
            val eventTypes = withContext(Dispatchers.IO) {
                subtaskDao.getUniqueEventTypes()
            }
            if (eventTypes.isEmpty()) {
                withContext(Dispatchers.Main) {
                    callbacks.showToast(R.string.subtask_no_other_events_to_copy_from, Toast.LENGTH_LONG)
                }
            } else {
                showCopySubtasksDialog(eventTypes) // Show dialog from SubtaskManager
            }
        }
    }


    /**
     * Displays the dialog for selecting event type, event, and subtasks to copy.
     * This method is now entirely within SubtaskManager.
     */
    private fun showCopySubtasksDialog(eventTypes: List<String>) {
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.subtask_copy_subtasks_selection_dialog, null)
        val spinnerEventType: Spinner = dialogView.findViewById(R.id.spinnerEventType)
        val spinnerEvents: Spinner = dialogView.findViewById(R.id.spinnerEvents)
        val textViewSelectEventPrompt: TextView = dialogView.findViewById(R.id.textViewSelectEventPrompt)
        val textViewSelectSubtasksPrompt: TextView = dialogView.findViewById(R.id.textViewSelectSubtasksPrompt)
        val recyclerViewSubtasks: RecyclerView = dialogView.findViewById(R.id.recyclerViewSubtasks)
        val noSubtasksFoundTextView: TextView = dialogView.findViewById(R.id.noSubtasksFoundTextView)
        val buttonCopySubtasks: Button = dialogView.findViewById(R.id.buttonCopySubtasks)

        val eventTypeAdapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, eventTypes)
        eventTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerEventType.adapter = eventTypeAdapter

        val subtaskCopyAdapter = SubtaskCopyAdapter()
        recyclerViewSubtasks.apply {
            layoutManager = LinearLayoutManager(activity)
            adapter = subtaskCopyAdapter
        }

        // Initially hide event selection and subtask selection UI
        textViewSelectEventPrompt.visibility = View.GONE
        spinnerEvents.visibility = View.GONE
        textViewSelectSubtasksPrompt.visibility = View.GONE
        recyclerViewSubtasks.visibility = View.GONE
        noSubtasksFoundTextView.visibility = View.VISIBLE // Show "No subtasks found" by default
        buttonCopySubtasks.visibility = View.GONE


        spinnerEventType.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedType = eventTypes[position]
                textViewSelectEventPrompt.visibility = View.VISIBLE
                spinnerEvents.visibility = View.VISIBLE

                lifecycleScope.launch {
                    val eventsForType = withContext(Dispatchers.IO) {
                        getEventsForType(selectedType)
                    }
                    withContext(Dispatchers.Main) {
                        if (eventsForType.isEmpty()) {
                            // No events found for this type
                            val emptyEventAdapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, listOf(activity.getString(R.string.no_events_found)))
                            emptyEventAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                            spinnerEvents.adapter = emptyEventAdapter
                            spinnerEvents.setSelection(0)
                            spinnerEvents.isEnabled = false // Disable spinner if no events

                            textViewSelectSubtasksPrompt.visibility = View.GONE
                            recyclerViewSubtasks.visibility = View.GONE
                            noSubtasksFoundTextView.visibility = View.VISIBLE
                            buttonCopySubtasks.visibility = View.GONE
                            subtaskCopyAdapter.submitList(emptyList()) // Clear previous subtasks
                            Log.d(TAG, "No events found for type: $selectedType")

                        } else {
                            spinnerEvents.isEnabled = true // Enable spinner if events are found
                            val eventNames = eventsForType.map { it.second }
                            val eventNameToIdMap = eventsForType.associate { it.second to it.first }

                            // MODIFIED: Use a custom ArrayAdapter to handle text truncation
                            val eventAdapter = object : ArrayAdapter<String>(activity, android.R.layout.simple_spinner_item, eventNames) {
                                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                                    val view = super.getView(position, convertView, parent) as TextView
                                    view.ellipsize = TextUtils.TruncateAt.END
                                    view.isSingleLine = true
                                    return view
                                }

                                override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                                    val view = super.getDropDownView(position, convertView, parent) as TextView
                                    view.ellipsize = TextUtils.TruncateAt.END
                                    view.isSingleLine = true
                                    return view
                                }
                            }
                            eventAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                            spinnerEvents.adapter = eventAdapter

                            // Define the onItemSelectedListener for spinnerEvents BEFORE setting selection
                            spinnerEvents.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                                    val selectedEventDisplayName = eventNames[position]
                                    val selectedEventRefId = eventNameToIdMap[selectedEventDisplayName]
                                    if (selectedEventRefId != null) {
                                        lifecycleScope.launch {
                                            val subtasks = withContext(Dispatchers.IO) {
                                                subtaskDao.getSubtasksForEvent(selectedType, selectedEventRefId).firstOrNull() ?: emptyList()
                                            }
                                            subtaskCopyAdapter.submitList(subtasks)
                                            textViewSelectSubtasksPrompt.visibility = if (subtasks.isEmpty()) View.GONE else View.VISIBLE
                                            recyclerViewSubtasks.visibility = if (subtasks.isEmpty()) View.GONE else View.VISIBLE
                                            noSubtasksFoundTextView.visibility = if (subtasks.isEmpty()) View.VISIBLE else View.GONE
                                            buttonCopySubtasks.visibility = if (subtasks.isEmpty()) View.GONE else View.VISIBLE
                                            Log.d(TAG, "Loaded ${subtasks.size} subtasks for event: $selectedEventDisplayName (ID: $selectedEventRefId)")
                                        }
                                    } else {
                                        subtaskCopyAdapter.submitList(emptyList())
                                        textViewSelectSubtasksPrompt.visibility = View.GONE
                                        recyclerViewSubtasks.visibility = View.GONE
                                        noSubtasksFoundTextView.visibility = View.VISIBLE
                                        buttonCopySubtasks.visibility = View.GONE
                                        Log.w(TAG, "Selected eventRefId is null for: $selectedEventDisplayName")
                                    }
                                }

                                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {
                                    textViewSelectSubtasksPrompt.visibility = View.GONE
                                    recyclerViewSubtasks.visibility = View.GONE
                                    noSubtasksFoundTextView.visibility = View.VISIBLE
                                    buttonCopySubtasks.visibility = View.GONE
                                    subtaskCopyAdapter.submitList(emptyList())
                                }
                            }
                            // Programmatically select the first item to trigger its onItemSelectedListener
                            // This ensures subtasks are loaded for the default event when the dialog first appears.
                            spinnerEvents.setSelection(0, true)
                        }
                    }
                }
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {
                textViewSelectEventPrompt.visibility = View.GONE
                spinnerEvents.visibility = View.GONE
                textViewSelectSubtasksPrompt.visibility = View.GONE
                recyclerViewSubtasks.visibility = View.GONE
                noSubtasksFoundTextView.visibility = View.VISIBLE
                buttonCopySubtasks.visibility = View.GONE
                subtaskCopyAdapter.submitList(emptyList())
            }
        }

        buttonCopySubtasks.setOnClickListener {
            val selectedSubtasks = subtaskCopyAdapter.getSelectedSubtasks()
            copySelectedSubtasks(selectedSubtasks)
            copyDialog?.dismiss()
        }

        copyDialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .setNegativeButton(R.string.dialog_cancel_button) { dialog, _ -> dialog.dismiss() }
            .create()
        copyDialog?.show()
    }


    private fun copySelectedSubtasks(subtasks: List<Subtask>) {
        if (subtasks.isEmpty()) {
            callbacks.showToast(activity.getString(R.string.subtask_no_subtasks_selected), Toast.LENGTH_SHORT)
            return
        }

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val currentEventType = callbacks.getCurrentEventType()
                val currentEventRefId = callbacks.getCurrentEventRefId()
                val currentSubtasksInDb = subtaskDao.getSubtasksForEvent(currentEventType, currentEventRefId).firstOrNull() ?: emptyList()
                val existingDescriptions = currentSubtasksInDb.map { it.description.lowercase(Locale.getDefault()) }.toSet()
                var maxPosition = currentSubtasksInDb.filter { !it.completionState }.maxOfOrNull { it.position } ?: -1

                val newSubtasks = subtasks.filter { subtask ->
                    // Only copy if it's not from the current event AND it's not a duplicate description
                    subtask.eventRefId != currentEventRefId || !existingDescriptions.contains(subtask.description.lowercase(Locale.getDefault()))
                }.map { subtask ->
                    subtask.copy(
                        subtaskId = 0L, // Reset ID for new insertion
                        eventType = currentEventType,
                        eventRefId = currentEventRefId,
                        completionState = false, // Copied subtasks start as incomplete
                        position = ++maxPosition, // Assign new position
                        validatedItem = false, // Copied subtasks are initially unvalidated
                        lastModified = System.currentTimeMillis() // Update timestamp for DiffUtil
                    )
                }

                if (newSubtasks.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        callbacks.showToast(activity.getString(R.string.subtask_no_new_subtasks_copied), Toast.LENGTH_SHORT)
                    }
                    return@withContext
                }

                // Use insertAllSubtasks for efficiency
                subtaskDao.insertAllSubtasks(newSubtasks)
                Log.d(TAG, "Copied ${newSubtasks.size} subtasks: ${newSubtasks.joinToString { it.description }}")

                // Force update on the last incomplete subtask before copy if it exists, to re-evaluate its down arrow
                val lastIncompleteBeforeCopy =
                    currentSubtasksInDb.filter { !it.completionState }.maxByOrNull { it.position }
                if (lastIncompleteBeforeCopy != null) {
                    val updatedLast = lastIncompleteBeforeCopy.copy(lastModified = System.currentTimeMillis())
                    subtaskDao.updateSubtask(updatedLast)
                    Log.d(TAG, "Forced update on last incomplete subtask before copy (ID: ${updatedLast.subtaskId}) to trigger DiffUtil re-bind.")
                }

                // Reassign positions for the incomplete group to ensure contiguous ordering
                reassignPositionsForGroup(currentEventType, currentEventRefId, false)
            }
            withContext(Dispatchers.Main) {
                callbacks.showToast(activity.getString(R.string.subtask_copied_success, subtasks.size), Toast.LENGTH_SHORT)
            }
            callbacks.onSubtasksUpdated() // Notify UI to refresh
            callbacks.requestUiStateLogging(currentSubtasks) // Request logging after update
        }
    }

    /**
     * MODIFIED: This function now formats the event display names according to the user's specific requirements.
     * It fetches the corresponding event object and constructs a string from its properties.
     */
    private suspend fun getEventsForType(eventType: String): List<Pair<Long, String>> {
        return try {
            withContext(Dispatchers.IO) {
                val subtasksForType = subtaskDao.getValidatedSubtasksForEventType(eventType)

                subtasksForType.distinctBy { it.eventRefId }
                    .mapNotNull { subtask ->
                        val eventRefId = subtask.eventRefId
                        val eventName = when (eventType) {
                            "Assignment" -> {
                                val assignment = assignmentDao.getAssignmentById(eventRefId)
                                // Format: <courseName>-<assignmentTopic>-<assignmentName>
                                assignment?.let { "${it.courseName} - ${it.assignmentTopic} - ${it.assignmentName}" }
                            }
                            "Action" -> {
                                val action = actionDao.getActionById(eventRefId)
                                // Format: <actionName>-<venue>-<actionDescription> (filters out blank parts)
                                action?.let {
                                    listOfNotNull(it.actionName, it.venue, it.description)
                                        .filter { part -> part.isNotBlank() }
                                        .joinToString(" - ")
                                }
                            }
                            "Engagement" -> {
                                val engagement = engagementDao.getEngagementById(eventRefId)
                                // Format: <engagementName>-<venue>-<notes> (filters out blank parts)
                                engagement?.let {
                                    listOfNotNull(it.engagementName, it.venue, it.notes)
                                        .filter { part -> part.isNotBlank() }
                                        .joinToString(" - ")
                                }
                            }
                            "Exam" -> {
                                val exam = examDao.getExamById(eventRefId)
                                // Format: <examName>-<examDetails>-<examDate>
                                exam?.let {
                                    listOfNotNull(it.courseName, it.examDetails, it.examDate.toString())
                                        .filter { part -> part.isNotBlank() }
                                        .joinToString(" - ")
                                }
                            }
                            "Notes" -> {
                                val note = notesDao.getNoteById(eventRefId)
                                // Format: <subject>-<type>-<description> (filters out blank parts)
                                note?.let {
                                    listOfNotNull(it.subject, it.type, it.description)
                                        .filter { part -> part.isNotBlank() }
                                        .joinToString(" - ")
                                } ?: activity.getString(R.string.untitled_note)
                            }
                            "Todo" -> {
                                val todo = todoDao.getTodoById(eventRefId)
                                // Format: <title>-<description> (filters out blank parts)
                                todo?.let {
                                    listOfNotNull(it.title, it.description)
                                        .filter { part -> part.isNotBlank() }
                                        .joinToString(" - ")
                                }
                            }
                            else -> null
                        }
                        // Only return if an event name could be constructed
                        eventName?.takeIf { it.isNotBlank() }?.let { eventRefId to it }
                    }
            }
        } catch (e: Exception) {
            Log.e("SubtaskManager", "Error fetching events for type $eventType: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Formats a raw subtask description.
     */
    private fun formatSubtaskDescription(description: String): String {
        return description.trim()
            .replace(Regex("\\s+"), " ")
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    /**
     * Cleans up unvalidated subtasks when an event is discarded.
     */
    suspend fun cleanUpUnvalidatedSubtasks(isNewEvent: Boolean) {
        withContext(Dispatchers.IO) {
            val eventRefId = callbacks.getCurrentEventRefId()
            val eventType = callbacks.getCurrentEventType()
            if (eventRefId != -1L) {
                val deletedCount = subtaskDao.deleteUnvalidatedSubtasksForEvent(eventType, eventRefId)
                Log.d(TAG, "Cleaned up $deletedCount unvalidated subtasks for event $eventType:$eventRefId (isNewEvent: $isNewEvent).")
            } else {
                Log.w(TAG, "Attempted to clean up subtasks for eventRefId -1L.")
            }
        }
    }

    /**
     * Marks all subtasks for the current event as validated.
     */
    suspend fun markSubtasksAsValidated() {
        withContext(Dispatchers.IO) {
            val eventRefId = callbacks.getCurrentEventRefId()
            val eventType = callbacks.getCurrentEventType()
            if (eventRefId != -1L) {
                val updatedCount = subtaskDao.updateSubtasksValidationStatusForEvent(eventType, eventRefId, true)
                Log.d(TAG, "Marked $updatedCount subtasks as validated for event $eventType:$eventRefId.")
            }
        }
    }

    /**
     * Retrieves the current list of subtasks.
     */
    fun getCurrentSubtasks(): List<Subtask> {
        return currentSubtasks
    }

    // NEW: Clean up dialog on activity destruction
    fun onDestroy() {
        copyDialog?.dismiss()
        copyDialog = null
    }

}
