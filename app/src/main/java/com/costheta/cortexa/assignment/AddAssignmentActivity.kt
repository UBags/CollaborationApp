package com.costheta.cortexa.assignment

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action


import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.costheta.cortexa.NotificationHelper
import com.costheta.cortexa.R
import com.costheta.cortexa.data.db.AppDatabase
import com.costheta.cortexa.data.db.dao.AssignmentDao
import com.costheta.cortexa.data.db.dao.AttachmentDao
import com.costheta.cortexa.data.db.dao.SubtaskDao
import com.costheta.cortexa.data.db.dao.TimetableDao
import com.costheta.cortexa.data.models.Assignment
import com.costheta.cortexa.data.models.Attachment
import com.costheta.cortexa.data.models.Subtask
import com.costheta.cortexa.util.attachment.AttachmentAdapter
import com.costheta.cortexa.util.attachment.AttachmentManager
import com.costheta.cortexa.util.attachment.AttachmentManagerCallbacks
import com.costheta.cortexa.util.keyboard.AutoCompleteDisappearingKeyboard
import com.costheta.cortexa.util.keyboard.DisappearingKeyboard
import com.costheta.cortexa.util.subtask.ItemMoveCallback
import com.costheta.cortexa.util.subtask.SubtaskAdapter
import com.costheta.cortexa.util.subtask.SubtaskManager
import com.costheta.cortexa.util.subtask.SubtaskManagerCallbacks
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class AddAssignmentActivity : AppCompatActivity(), AttachmentManagerCallbacks, SubtaskManagerCallbacks {

    // UI elements
    private lateinit var assignmentCourseNameAutoCompleteTextView: AutoCompleteTextView
    private lateinit var assignmentTopicAutoCompleteTextView: AutoCompleteTextView
    private lateinit var assignmentNameAutoCompleteTextView: AutoCompleteTextView
    private lateinit var assignmentDueDateEditText: EditText
    private lateinit var assignmentSubmissionTimeEditText: EditText
    private lateinit var estimatedHoursPicker: NumberPicker
    private lateinit var currentProgressPicker: NumberPicker
    private lateinit var checkboxDoneAndSubmitted: CheckBox
    private lateinit var saveAssignmentButton: Button
    private lateinit var addAssignmentTitle: TextView
    private lateinit var silenceAssignmentNotificationsCheckbox: CheckBox

    // TIMEZONE CHANGE: UI Elements for timezone display
    private lateinit var timezoneInfoLayout: LinearLayout
    private lateinit var currentTimezoneTextView: TextView
    private lateinit var originalTimezoneTextView: TextView

    // ATTACHMENT UI ELEMENTS
    private lateinit var buttonPickFile: Button
    private lateinit var buttonTakePhotoVideo: Button
    private lateinit var buttonRecordAudio: Button
    private lateinit var buttonAddHyperlink: Button
    private lateinit var recyclerViewAttachments: RecyclerView
    private lateinit var noAttachmentsFoundTextView: TextView
    private lateinit var attachmentAdapter: AttachmentAdapter
    private lateinit var buttonShareAttachments: Button
    private lateinit var buttonCopyAttachments: Button


    // SUBTASK UI ELEMENTS
    private var itemTouchHelper: ItemTouchHelper? = null
    private lateinit var editTextAddSubtask: TextInputEditText
    private lateinit var buttonAddSubtask: Button
    private lateinit var buttonCopySubtasks: Button
    private lateinit var recyclerViewSubtasks: RecyclerView
    private lateinit var noSubtasksFoundTextView: TextView
    private lateinit var subtaskAdapter: SubtaskAdapter


    // Database DAOs
    private lateinit var assignmentDao: AssignmentDao
    private lateinit var attachmentDao: AttachmentDao
    private lateinit var timetableDao: TimetableDao
    private lateinit var subtaskDao: SubtaskDao

    // Notification Helper
    private lateinit var notificationHelper: NotificationHelper

    // Variables to hold selected date and time
    private var selectedDueDate: LocalDate? = null
    private var selectedSubmissionTime: LocalTime? = null

    // Variable to hold the assignment ID if we are in edit mode
    private var assignmentId: Long = -1L
    private var isNewAssignment: Boolean = false

    // To store the initial state of the assignment for checking unsaved changes
    private var initialAssignment: Assignment? = null
    private var initialAttachmentsLoaded: List<Attachment> = emptyList()
    private var initialSubtasksLoaded: List<Subtask> = emptyList()
    private val newlyAddedAttachments = mutableListOf<Attachment>()

    // Values for the estimatedHoursPicker (2-720 hours, step 2)
    private val estimatedHoursValues = (2..720 step 2).toList().toTypedArray()
    private val progressValues = Array(21) { i -> String.format(Locale.getDefault(), "%d", i * 5) }

    // Managers
    private lateinit var attachmentManager: AttachmentManager
    private lateinit var subtaskManager: SubtaskManager

    // ActivityResultLaunchers for attachments (now managed by AttachmentManager)
    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        attachmentManager.handlePickFileResult(result.resultCode, result.data)
    }

    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        attachmentManager.handleTakePhotoResult(success)
    }

    private val takeVideoLauncher = registerForActivityResult(ActivityResultContracts.CaptureVideo()) { success ->
        attachmentManager.handleTakeVideoResult(success)
    }

    private val recordAudioLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        attachmentManager.handleRecordAudioResult(result.resultCode, result.data)
    }

    // Permission launchers for attachments (now managed by AttachmentManager)
    private val requestCameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        attachmentManager.handleCameraPermissionResult(isGranted)
    }

    private val requestMediaPermissionsLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        attachmentManager.handleMediaPermissionsResult(permissions)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.assignment_activity_add)

        // Initialize UI elements
        addAssignmentTitle = findViewById(R.id.addAssignmentTitle)
        assignmentCourseNameAutoCompleteTextView = findViewById(R.id.assignmentCourseNameAutoCompleteTextView)
        assignmentTopicAutoCompleteTextView = findViewById(R.id.assignmentTopicAutoCompleteTextView)
        assignmentNameAutoCompleteTextView = findViewById(R.id.assignmentNameAutoCompleteTextView)
        assignmentDueDateEditText = findViewById(R.id.assignmentDueDateEditText)
        assignmentSubmissionTimeEditText = findViewById(R.id.assignmentSubmissionTimeEditText)
        estimatedHoursPicker = findViewById(R.id.estimatedHoursPicker)
        currentProgressPicker = findViewById(R.id.currentProgressPicker)
        checkboxDoneAndSubmitted = findViewById(R.id.checkboxDoneAndSubmitted)
        saveAssignmentButton = findViewById(R.id.saveAssignmentButton)
        silenceAssignmentNotificationsCheckbox = findViewById(R.id.silenceAssignmentNotificationsCheckbox)

        // Initialize the DisappearingKeyboard utility
        AutoCompleteDisappearingKeyboard.getInstance().register(assignmentCourseNameAutoCompleteTextView)
        AutoCompleteDisappearingKeyboard.getInstance().register(assignmentTopicAutoCompleteTextView)
        AutoCompleteDisappearingKeyboard.getInstance().register(assignmentNameAutoCompleteTextView)

        // TIMEZONE CHANGE: Initialize timezone UI elements
        timezoneInfoLayout = findViewById(R.id.timezoneInfoLayout)
        currentTimezoneTextView = findViewById(R.id.currentTimezoneTextView)
        originalTimezoneTextView = findViewById(R.id.originalTimezoneTextView)


        // Initialize attachment UI elements from the included layout
        val attachmentLayout = findViewById<View>(R.id.attachmentLayout)
        buttonPickFile = attachmentLayout.findViewById(R.id.buttonPickFile)
        buttonTakePhotoVideo = attachmentLayout.findViewById(R.id.buttonTakePhotoVideo)
        buttonRecordAudio = attachmentLayout.findViewById(R.id.buttonRecordAudio)
        buttonAddHyperlink = attachmentLayout.findViewById(R.id.buttonAddHyperlink)
        recyclerViewAttachments = attachmentLayout.findViewById(R.id.recyclerViewAttachments)
        noAttachmentsFoundTextView = attachmentLayout.findViewById(R.id.noAttachmentsFoundTextView)
        buttonShareAttachments = attachmentLayout.findViewById(R.id.buttonShareAttachments)
        buttonCopyAttachments = attachmentLayout.findViewById(R.id.buttonCopyAttachments)


        // Initialize subtask UI elements from the included layout
        val subtasksLayout = findViewById<View>(R.id.subtasksLayout)
        editTextAddSubtask = subtasksLayout.findViewById(R.id.editTextAddSubtask)
        buttonAddSubtask = subtasksLayout.findViewById(R.id.buttonAddSubtask)
        buttonCopySubtasks = subtasksLayout.findViewById(R.id.buttonCopySubtasks)
        recyclerViewSubtasks = subtasksLayout.findViewById(R.id.recyclerViewSubtasks)
        noSubtasksFoundTextView = subtasksLayout.findViewById(R.id.noSubtasksFoundTextView)
        DisappearingKeyboard.getInstance().register(editTextAddSubtask)


        // Initialize DAOs
        assignmentDao = AppDatabase.getDatabase(applicationContext).assignmentDao()
        timetableDao = AppDatabase.getDatabase(applicationContext).timetableDao()
        attachmentDao = AppDatabase.getDatabase(applicationContext).attachmentDao()
        subtaskDao = AppDatabase.getDatabase(applicationContext).subtaskDao()

        // Initialize NotificationHelper
        notificationHelper = NotificationHelper(applicationContext)

        // Setup Attachment RecyclerView
        attachmentAdapter = AttachmentAdapter(
            onDeleteClick = { attachment -> attachmentManager.showDeleteAttachmentDialog(attachment) },
            onItemClick = { attachment -> attachmentManager.viewAttachment(attachment) }
        )
        recyclerViewAttachments.apply {
            layoutManager = LinearLayoutManager(this@AddAssignmentActivity)
            adapter = attachmentAdapter
        }

        // Setup Subtask RecyclerView
        subtaskAdapter = SubtaskAdapter(
            onToggleComplete = { subtask, isCompleted ->
                subtaskManager.toggleSubtaskCompletion(subtask, isCompleted)
            },
            onDeleteClick = { subtask ->
                subtaskManager.deleteSubtask(subtask)
            },
            onStartDrag = { viewHolder ->
                // This lambda tells the ItemTouchHelper to start dragging the view
                itemTouchHelper?.startDrag(viewHolder)
            },
            onSubtasksReordered = { reorderedList ->
                // This lambda tells the manager to save the new order
                subtaskManager.reorderSubtasks(reorderedList)
            }
        )

        recyclerViewSubtasks.apply {
            layoutManager = LinearLayoutManager(this@AddAssignmentActivity)
            adapter = subtaskAdapter
        }

        val callback = ItemMoveCallback(subtaskAdapter)
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper?.attachToRecyclerView(recyclerViewSubtasks)

        // Initialize Managers
        attachmentManager = AttachmentManager(
            activity = this,
            attachmentDao = attachmentDao,
            lifecycleScope = lifecycleScope,
            callbacks = this,
            pickFileLauncher = pickFileLauncher,
            takePhotoLauncher = takePhotoLauncher,
            takeVideoLauncher = takeVideoLauncher,
            recordAudioLauncher = recordAudioLauncher,
            requestCameraPermissionLauncher = requestCameraPermissionLauncher,
            requestMediaPermissionsLauncher = requestMediaPermissionsLauncher
        )

        // Initialize SubtaskManager
        subtaskManager = SubtaskManager(
            activity = this,
            subtaskDao = subtaskDao,
            lifecycleScope = lifecycleScope,
            callbacks = this
        )


        // Set up NumberPickers for estimated hours
        estimatedHoursPicker.minValue = 0
        estimatedHoursPicker.maxValue = estimatedHoursValues.size - 1
        estimatedHoursPicker.displayedValues = estimatedHoursValues.map { it.toString() }.toTypedArray()
        estimatedHoursPicker.wrapSelectorWheel = false
        estimatedHoursPicker.value = estimatedHoursValues.indexOf(2).coerceAtLeast(0) // Set default to 2 hours

        currentProgressPicker.minValue = 0
        currentProgressPicker.maxValue = progressValues.size - 1
        currentProgressPicker.displayedValues = progressValues
        currentProgressPicker.wrapSelectorWheel = false

        // Set up click listeners for date and time pickers
        assignmentDueDateEditText.setOnClickListener { showDatePicker() }
        assignmentSubmissionTimeEditText.setOnClickListener { showTimePicker() }

        // Set up save button click listener
        saveAssignmentButton.setOnClickListener { saveAssignment() }

        // Set up email attachments button click listener
        buttonShareAttachments.setOnClickListener {
            attachmentManager.shareAttachments(attachmentAdapter.currentList)
        }

        // Set up attachment button listeners (delegating to AttachmentManager)
        buttonPickFile.setOnClickListener { attachmentManager.checkAndRequestMediaPermissionsForPicker() }
        buttonTakePhotoVideo.setOnClickListener { attachmentManager.checkAndRequestCameraPermission() }
        buttonRecordAudio.setOnClickListener { attachmentManager.launchRecordAudioActivity() }
        buttonAddHyperlink.setOnClickListener { attachmentManager.showAddHyperlinkDialog() }
        buttonCopyAttachments.setOnClickListener { attachmentManager.startCopyAttachmentsProcess() }


        // Delegate subtask actions to SubtaskManager
        buttonAddSubtask.setOnClickListener {
            subtaskManager.addSubtasksFromInput(editTextAddSubtask.text.toString())
            editTextAddSubtask.text?.clear()
        }
        buttonCopySubtasks.setOnClickListener { subtaskManager.startCopySubtasksProcess() }
        checkboxDoneAndSubmitted.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val progress100Index = progressValues.indexOf("100")
                if (progress100Index != -1) {
                    currentProgressPicker.value = progress100Index
                }
            }
        }
        currentProgressPicker.setOnValueChangedListener { _, _, newVal ->
            checkboxDoneAndSubmitted.isChecked = progressValues[newVal] == "100"
        }
        // Load unique names for auto-suggestions
        loadUniqueNamesForAutoSuggestions()

        // Check if we are in edit mode
        val intentAssignmentId = intent.getLongExtra("ASSIGNMENT_ID", -1L)
        if (intentAssignmentId != -1L) {
            assignmentId = intentAssignmentId
            isNewAssignment = false
            addAssignmentTitle.text = getString(R.string.edit_assignment_title)
            saveAssignmentButton.text = getString(R.string.assignment_update_button_text)
            lifecycleScope.launch {
                val assignment = assignmentDao.getAssignmentById(assignmentId)
                assignment?.let {
                    initialAssignment = it.copy()
                    loadAssignmentData(it)
                }
                loadAttachmentsForAssignment()
                loadSubtasksForAssignment()
            }
        } else {
            isNewAssignment = true
            addAssignmentTitle.text = getString(R.string.add_assignment_title)
            saveAssignmentButton.text = getString(R.string.assignment_save_button_text)
            selectedDueDate = LocalDate.now().plusDays(2)
            selectedSubmissionTime = LocalTime.of(23, 45)
            updateDueDateDisplay()
            updateSubmissionTimeDisplay()
            updateTimezoneDisplay() // TIMEZONE CHANGE: Update display for new assignment
            lifecycleScope.launch {
                createPlaceholderAssignmentAndLoadRelatedData()
            }
        }

        // Register OnBackPressedCallback for modern back gesture handling
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (hasUnsavedChanges()) {
                    val dialogMessage = if (isNewAssignment) {
                        getString(R.string.exit_without_saving_assignment_message)
                    } else {
                        getString(R.string.exit_without_saving_changes_assignment_message)
                    }

                    AlertDialog.Builder(this@AddAssignmentActivity)
                        .setTitle(getString(R.string.exit_without_saving_title).uppercase(Locale.getDefault()))
                        .setMessage(dialogMessage)
                        .setPositiveButton(getString(R.string.exit_without_saving_confirm).uppercase(Locale.getDefault())) { dialog, _ ->
                            dialog.dismiss()
                            lifecycleScope.launch {
                                withContext(Dispatchers.IO) {
                                    try {
                                        // This handles physical file deletion for newly added attachments.
                                        newlyAddedAttachments.forEach { attachment ->
                                            attachmentManager.deleteAttachment(attachment)
                                        }

                                        if (isNewAssignment) {
                                            // In ADD mode, delete the entire placeholder record and its children.
                                            val currentAssignment = assignmentDao.getAssignmentById(assignmentId)
                                            if (currentAssignment != null && !currentAssignment.validatedItem) {
                                                subtaskDao.deleteUnvalidatedSubtasksForEvent("Assignment", assignmentId)
                                                // Note: Assignment does not have a Location model, so no cleanup needed here.
                                                assignmentDao.deleteAssignment(assignmentId)
                                                Log.d("AddAssignmentActivity", "Deleted placeholder Assignment (ID: $assignmentId) and related data.")
                                            }
                                        } else {
                                            // In EDIT mode, only clean up newly added (unvalidated) children.
                                            attachmentDao.deleteUnvalidatedAttachmentsForEvent("Assignment", assignmentId)
                                            subtaskDao.deleteUnvalidatedSubtasksForEvent("Assignment", assignmentId)
                                            Log.d("AddAssignmentActivity", "Cleaned up unvalidated items for existing Assignment (ID: $assignmentId).")
                                        }
                                    } catch (e: Exception) {
                                        Log.e("AddAssignmentActivity", "Error during cleanup on back press: ${e.message}", e)
                                    }
                                }
                                this@AddAssignmentActivity.finish()
                            }
                        }
                        .setNegativeButton(getString(R.string.dialog_cancel_button).uppercase(Locale.getDefault())) { dialog, _ ->
                            dialog.cancel()
                        }
                        .show()
                } else {
                    this@AddAssignmentActivity.finish()
                }
            }
        })
    }

    /**
     * Loads unique course names, assignment topics, and assignment names from the database
     * and sets them as auto-suggestion adapters for the respective AutoCompleteTextViews.
     */
    private fun loadUniqueNamesForAutoSuggestions() {
        lifecycleScope.launch {
            try {
                // a) Load all courseNames from ClassSessions and Assignment models
                val classSessionCourseNames = withContext(Dispatchers.IO) {
                    timetableDao.getUniqueLectureCourseNames()
                }
                val assignmentCourseNames = withContext(Dispatchers.IO) {
                    assignmentDao.getUniqueAssignmentCourseNames()
                }
                val uniqueCourseNames = (classSessionCourseNames + assignmentCourseNames)
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()

                withContext(Dispatchers.Main) {
                    val courseNameAdapter = ArrayAdapter(
                        this@AddAssignmentActivity,
                        android.R.layout.simple_dropdown_item_1line,
                        uniqueCourseNames
                    )
                    assignmentCourseNameAutoCompleteTextView.setAdapter(courseNameAdapter)
                }

                // b) Load all unique assignmentTopics from Assignment model
                val uniqueAssignmentTopics = withContext(Dispatchers.IO) {
                    assignmentDao.getUniqueAssignmentTopics() // Using the new DAO method
                }
                withContext(Dispatchers.Main) {
                    val assignmentTopicAdapter = ArrayAdapter(
                        this@AddAssignmentActivity,
                        android.R.layout.simple_dropdown_item_1line,
                        uniqueAssignmentTopics
                    )
                    assignmentTopicAutoCompleteTextView.setAdapter(assignmentTopicAdapter)
                }

                // c) Load all unique assignmentNames from Assignment model
                val uniqueAssignmentNames = withContext(Dispatchers.IO) {
                    assignmentDao.getUniqueAssignmentNames() // Using the new DAO method
                }
                withContext(Dispatchers.Main) {
                    val assignmentNameAdapter = ArrayAdapter(
                        this@AddAssignmentActivity,
                        android.R.layout.simple_dropdown_item_1line,
                        uniqueAssignmentNames
                    )
                    assignmentNameAutoCompleteTextView.setAdapter(assignmentNameAdapter)
                }

            } catch (e: Exception) {
                Log.e("AddAssignmentActivity", "Error loading unique names for auto-suggestions: ${e.message}", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        attachmentManager.onDestroy()
        subtaskManager.onDestroy()
    }

    /**
     * Creates a placeholder Assignment item in the database to get a valid ID for attachments and subtasks.
     * This ID will be used for all attachments and subtasks added before the main Assignment is saved.
     * The placeholder is marked as `validatedItem = false`.
     */
    private suspend fun createPlaceholderAssignmentAndLoadRelatedData() {
        withContext(Dispatchers.IO) {
            try {
                // Calculate dueDateTimeMillis for the placeholder
                val placeholderDueDate = selectedDueDate ?: LocalDate.now()
                val placeholderSubmissionTime = selectedSubmissionTime ?: LocalTime.of(23, 45)
                val combinedDateTime = LocalDateTime.of(placeholderDueDate, placeholderSubmissionTime)

                // TIMEZONE CHANGE: Use the system's default timezone to get the correct UTC milliseconds and ID
                val currentZoneId = ZoneId.systemDefault()
                val dueDateTimeMillis = combinedDateTime.atZone(currentZoneId).toInstant().toEpochMilli()

                val placeholderAssignment = Assignment(
                    courseName = "",
                    assignmentTopic = "",
                    assignmentName = "",
                    dueDate = placeholderDueDate,
                    submissionTime = placeholderSubmissionTime,
                    estimatedHours = 2,
                    currentProgress = 0,
                    silenceNotifications = false,
                    validatedItem = false,
                    dueDateTimeMillis = dueDateTimeMillis, // Pass the calculated millis
                    creationTimezone = currentZoneId.id // TIMEZONE CHANGE: Store the creation timezone ID
                )
                assignmentId = assignmentDao.insertAssignment(placeholderAssignment)
                withContext(Dispatchers.Main) {
                    Log.d("AddAssignmentActivity", "Placeholder Assignment created with ID: $assignmentId")
                    initialAssignment = placeholderAssignment.copy(assignmentId = assignmentId)
                    loadAttachmentsForAssignment()
                    loadSubtasksForAssignment()
                }
            } catch (e: Exception) {
                Log.e("AddAssignmentActivity", "Error creating placeholder Assignment: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AddAssignmentActivity, "Error initializing Assignment. Please restart.", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    /**
     * Loads attachments for the current assignmentId and updates the RecyclerView.
     */
    private fun loadAttachmentsForAssignment() {
        if (assignmentId != -1L) {
            lifecycleScope.launch {
                attachmentDao.getAttachmentsForEvent("Assignment", assignmentId).collectLatest { attachments ->
                    val attachmentsToShow = attachments
                    attachmentAdapter.submitList(attachmentsToShow)
                    initialAttachmentsLoaded = attachmentsToShow.toList()
                    noAttachmentsFoundTextView.visibility = if (attachmentsToShow.isEmpty()) View.VISIBLE else View.GONE
                    recyclerViewAttachments.visibility = if (attachmentsToShow.isEmpty()) View.GONE else View.VISIBLE
                    buttonShareAttachments.visibility = if (attachmentsToShow.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        } else {
            attachmentAdapter.submitList(emptyList())
            noAttachmentsFoundTextView.visibility = View.VISIBLE
            recyclerViewAttachments.visibility = View.GONE
            buttonShareAttachments.visibility = View.GONE
        }
    }

    /**
     * Loads subtasks for the current assignmentId and updates the RecyclerView.
     */
    private fun loadSubtasksForAssignment() {
        if (assignmentId != -1L) {
            lifecycleScope.launch {
                subtaskDao.getSubtasksForEvent("Assignment", assignmentId).collectLatest { subtasks ->
                    val subtasksToShow = subtasks
                    subtaskAdapter.submitList(subtasksToShow)
                    initialSubtasksLoaded = subtasksToShow.toList()
                    noSubtasksFoundTextView.visibility = if (subtasksToShow.isEmpty()) View.VISIBLE else View.GONE
                    recyclerViewSubtasks.visibility = if (subtasksToShow.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        } else {
            subtaskAdapter.submitList(emptyList())
            noSubtasksFoundTextView.visibility = View.VISIBLE
            recyclerViewSubtasks.visibility = View.GONE
        }
    }

    /**
     * Loads the data of an existing assignment into the UI fields for editing.
     * @param assignment The Assignment object to load.
     */
    private fun loadAssignmentData(assignment: Assignment) {
        addAssignmentTitle.text = getString(R.string.edit_assignment_title)
        assignmentCourseNameAutoCompleteTextView.setText(assignment.courseName)
        assignmentTopicAutoCompleteTextView.setText(assignment.assignmentTopic)
        assignmentNameAutoCompleteTextView.setText(assignment.assignmentName)

        selectedDueDate = assignment.dueDate
        assignmentDueDateEditText.setText(selectedDueDate?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))

        selectedSubmissionTime = assignment.submissionTime
        assignmentSubmissionTimeEditText.setText(selectedSubmissionTime?.format(DateTimeFormatter.ofPattern("HH:mm")))

        val estimatedHoursIndex = estimatedHoursValues.indexOf(assignment.estimatedHours)
        estimatedHoursPicker.value = estimatedHoursIndex.coerceIn(0, estimatedHoursValues.size - 1)

        val progressIndex = (assignment.currentProgress / 5f).toInt()
        currentProgressPicker.value = progressIndex.coerceIn(0, progressValues.size - 1)
        checkboxDoneAndSubmitted.isChecked = assignment.currentProgress == 100
        silenceAssignmentNotificationsCheckbox.isChecked = assignment.silenceNotifications

        // TIMEZONE CHANGE: Update the timezone display based on the loaded assignment data
        updateTimezoneDisplay(assignment)
    }

    /**
     * Displays a DatePickerDialog to allow the user to select the assignment due date.
     */
    private fun showDatePicker() {
        val initialDate = selectedDueDate ?: LocalDate.now()
        val year = initialDate.year
        val month = initialDate.monthValue - 1
        val day = initialDate.dayOfMonth

        val datePickerDialog = DatePickerDialog(this, { _, y, m, d ->
            val newDate = LocalDate.of(y, m + 1, d)
            selectedDueDate = newDate
            updateDueDateDisplay()
            // TIMEZONE CHANGE: Update the timezone display when the date changes
            updateTimezoneDisplay(initialAssignment)
        }, year, month, day)

        datePickerDialog.show()
    }

    /**
     * Helper to update the due date display in the EditText.
     */
    private fun updateDueDateDisplay() {
        assignmentDueDateEditText.setText(selectedDueDate?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
    }

    /**
     * Displays a TimePickerDialog to allow the user to select the assignment submission time.
     */
    private fun showTimePicker() {
        val initialTime = selectedSubmissionTime ?: LocalTime.of(23, 45)
        val hour = initialTime.hour
        val minute = initialTime.minute

        TimePickerDialog(this, { _, h, m ->
            val newTime = LocalTime.of(h, m)
            selectedSubmissionTime = newTime
            updateSubmissionTimeDisplay()
            // TIMEZONE CHANGE: Update the timezone display when the time changes
            updateTimezoneDisplay(initialAssignment)
        }, hour, minute, true).show()
    }

    /**
     * Helper to update the submission time display in the EditText.
     */
    private fun updateSubmissionTimeDisplay() {
        assignmentSubmissionTimeEditText.setText(selectedSubmissionTime?.format(DateTimeFormatter.ofPattern("HH:mm")))
    }

    // TIMEZONE CHANGE: This entire method is updated to handle the new display format
    /**
     * Updates the timezone TextViews based on the assignment's creation timezone and the device's current timezone.
     * @param assignment The assignment being edited. If null, assumes a new assignment.
     */
    private fun updateTimezoneDisplay(assignment: Assignment? = initialAssignment) {
        val dateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm (dd-MMM-yyyy)", Locale.ENGLISH)
        val currentSystemZone = ZoneId.systemDefault()

        // For new assignments, just show the current timezone
        if (isNewAssignment || assignment == null) {
            val displayDateTime = if (selectedDueDate != null && selectedSubmissionTime != null) {
                LocalDateTime.of(selectedDueDate, selectedSubmissionTime).format(dateTimeFormatter)
            } else {
                "--:-- (-- --- ----)"
            }
            originalTimezoneTextView.text = getString(R.string.timezone_display_format, displayDateTime, currentSystemZone.id)
            originalTimezoneTextView.visibility = View.VISIBLE
            currentTimezoneTextView.visibility = View.GONE
            return
        }

        // For existing assignments, determine the original timezone
        val originalZoneId = try {
            assignment.creationTimezone?.let { ZoneId.of(it) } ?: currentSystemZone
        } catch (e: Exception) {
            // Fallback to current system zone if the stored one is invalid
            currentSystemZone
        }

        // Get the absolute point in time from the stored millis
        val dueDateTimeInstant = Instant.ofEpochMilli(assignment.dueDateTimeMillis)

        // Display the time in the original timezone
        val timeInOriginalZone = LocalDateTime.ofInstant(dueDateTimeInstant, originalZoneId)
        originalTimezoneTextView.text = getString(R.string.original_timezone_display_format, timeInOriginalZone.format(dateTimeFormatter), originalZoneId.id)
        originalTimezoneTextView.visibility = View.VISIBLE

        // If the current timezone is different, also show the time in the current timezone
        if (currentSystemZone.id != originalZoneId.id) {
            val timeInCurrentZone = LocalDateTime.ofInstant(dueDateTimeInstant, currentSystemZone)
            currentTimezoneTextView.text = getString(R.string.current_timezone_display_format, timeInCurrentZone.format(dateTimeFormatter), currentSystemZone.id)
            currentTimezoneTextView.visibility = View.VISIBLE
        } else {
            // Hide the extra row if the timezones are the same
            currentTimezoneTextView.visibility = View.GONE
        }
    }


    /**
     * Checks if there are any unsaved changes in the assignment form.
     * Compares current UI state with the initial state (either default for new or loaded for existing).
     * For new assignments, it checks if any significant field has been filled.
     */
    private fun hasUnsavedChanges(): Boolean {
        val currentCourseName = assignmentCourseNameAutoCompleteTextView.text.toString().trim()
        val currentAssignmentTopic = assignmentTopicAutoCompleteTextView.text.toString().trim()
        val currentAssignmentName = assignmentNameAutoCompleteTextView.text.toString().trim()
        val currentDueDate = selectedDueDate
        val currentSubmissionTime = selectedSubmissionTime
        val currentEstimatedHours = estimatedHoursValues[estimatedHoursPicker.value]
        val currentProgress = progressValues[currentProgressPicker.value].toInt()
        val currentSilenceNotifications = silenceAssignmentNotificationsCheckbox.isChecked
        val currentAttachments = attachmentAdapter.currentList
        val currentSubtasks = subtaskAdapter.currentList

        val initial = initialAssignment

        if (initial == null) {
            Log.e("AddAssignmentActivity", "hasUnsavedChanges: initialAssignment is null. Assuming unsaved changes.")
            return true
        }

        // TIMEZONE CHANGE: Calculate current dueDateTimeMillis for comparison
        val currentDueDateTimeMillis = if (currentDueDate != null && currentSubmissionTime != null) {
            LocalDateTime.of(currentDueDate, currentSubmissionTime).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } else {
            0L
        }

        // Compare core fields
        if (currentCourseName != initial.courseName ||
            currentAssignmentTopic != initial.assignmentTopic ||
            currentAssignmentName != initial.assignmentName ||
            currentDueDate != initial.dueDate ||
            currentSubmissionTime != initial.submissionTime ||
            currentEstimatedHours != initial.estimatedHours ||
            currentProgress != initial.currentProgress ||
            currentSilenceNotifications != initial.silenceNotifications ||
            currentDueDateTimeMillis != initial.dueDateTimeMillis) {
            Log.d("AddAssignmentActivity", "hasUnsavedChanges: TRUE (Core fields changed)")
            return true
        }

        // Check for changes in attachments
        val currentValidatedAttachments = currentAttachments.filter { it.validatedItem }
        val currentUnvalidatedAttachments = currentAttachments.filter { !it.validatedItem }

        if (currentUnvalidatedAttachments.isNotEmpty()) {
            Log.d("AddAssignmentActivity", "hasUnsavedChanges: TRUE (Unvalidated attachments present)")
            return true
        }

        if (initialAttachmentsLoaded.size != currentValidatedAttachments.size) {
            Log.d("AddAssignmentActivity", "hasUnsavedChanges: TRUE (Validated attachment count changed)")
            return true
        }

        val initialAttachmentSet = initialAttachmentsLoaded.map { it.uriString to it.displayName }.toSet()
        val currentValidatedAttachmentSet = currentValidatedAttachments.map { it.uriString to it.displayName }.toSet()

        if (initialAttachmentSet != currentValidatedAttachmentSet) {
            Log.d("AddAssignmentActivity", "hasUnsavedChanges: TRUE (Validated attachment content changed)")
            return true
        }

        // Check for changes in subtasks
        val currentValidatedSubtasks = currentSubtasks.filter { it.validatedItem }
        val currentUnvalidatedSubtasks = currentSubtasks.filter { !it.validatedItem }

        val initialSubtaskSet = initialSubtasksLoaded.map { it.subtaskId to Triple(it.description, it.completionState, it.position) }.toMap()
        val currentSubtaskSet = currentSubtasks.map { it.subtaskId to Triple(it.description, it.completionState, it.position) }.toMap()

        val hasSubtaskChanges = (
                currentUnvalidatedSubtasks.isNotEmpty() ||
                        initialSubtasksLoaded.size != currentValidatedSubtasks.size ||
                        initialSubtaskSet != currentSubtaskSet
                )

        val isOnlySubtaskDeletions = (
                currentUnvalidatedSubtasks.isEmpty() ||
                        currentSubtasks.size <= initialSubtasksLoaded.size ||
                        currentSubtaskSet.all { (id, currentTuple) ->
                            initialSubtaskSet[id]?.let { initialTuple ->
                                currentTuple == initialTuple
                            } ?: false
                        }
                )

        if (hasSubtaskChanges && !isOnlySubtaskDeletions) {
            Log.d("AddAssignmentActivity", "hasUnsavedChanges: TRUE (hasSubtaskChanges is TRUE)")
            return true
        }

        Log.d("AddAssignmentActivity", "hasUnsavedChanges: FALSE (No changes detected)")
        return false
    }

    /**
     * Saves or updates the assignment data to the database.
     */
    private fun saveAssignment() {

        val currentProgress = progressValues[currentProgressPicker.value].toInt()
        val silenceNotifications = silenceAssignmentNotificationsCheckbox.isChecked
        val courseName = assignmentCourseNameAutoCompleteTextView.text.toString().trim()
        val assignmentTopic = assignmentTopicAutoCompleteTextView.text.toString().trim()
        val assignmentName = assignmentNameAutoCompleteTextView.text.toString().trim()
        val estimatedHours = estimatedHoursValues[estimatedHoursPicker.value]

        // --- MANDATORY FIELD VALIDATION ---
        if (courseName.isBlank()) {
            Toast.makeText(this, getString(R.string.assignment_course_name_required), Toast.LENGTH_LONG).show()
            assignmentCourseNameAutoCompleteTextView.error = getString(R.string.assignment_course_name_required)
            assignmentCourseNameAutoCompleteTextView.requestFocus()
            return
        } else {
            assignmentCourseNameAutoCompleteTextView.error = null
        }

        if (assignmentTopic.isBlank()) {
            Toast.makeText(this, getString(R.string.assignment_topic_required), Toast.LENGTH_LONG).show()
            assignmentTopicAutoCompleteTextView.error = getString(R.string.assignment_topic_required)
            assignmentTopicAutoCompleteTextView.requestFocus()
            return
        } else {
            assignmentTopicAutoCompleteTextView.error = null
        }

        if (assignmentName.isBlank()) {
            Toast.makeText(this, getString(R.string.assignment_name_required), Toast.LENGTH_LONG).show()
            assignmentNameAutoCompleteTextView.error = getString(R.string.assignment_name_required)
            assignmentNameAutoCompleteTextView.requestFocus()
            return
        } else {
            assignmentNameAutoCompleteTextView.error = null
        }

        if (selectedDueDate == null) {
            Toast.makeText(this, getString(R.string.assignment_due_date_required), Toast.LENGTH_LONG).show()
            assignmentDueDateEditText.requestFocus()
            return
        }

        if (selectedSubmissionTime == null) {
            Toast.makeText(this, getString(R.string.assignment_submission_time_required), Toast.LENGTH_LONG).show()
            assignmentSubmissionTimeEditText.requestFocus()
            return
        }

        if (estimatedHours <= 0) {
            Toast.makeText(this, getString(R.string.assignment_estimated_time_error), Toast.LENGTH_LONG).show()
            estimatedHoursPicker.requestFocus()
            return
        }

        val combinedDateTime = LocalDateTime.of(selectedDueDate!!, selectedSubmissionTime!!)

        // TIMEZONE CHANGE: Calculate dueDateTimeMillis and determine creation timezone
        val currentZoneId = ZoneId.systemDefault()
        val dueDateTimeMillis = combinedDateTime.atZone(currentZoneId).toInstant().toEpochMilli()
        val creationZone = if (isNewAssignment) currentZoneId.id else initialAssignment?.creationTimezone

        val assignmentToSave = Assignment(
            assignmentId = assignmentId,
            courseName = courseName,
            assignmentTopic = assignmentTopic,
            assignmentName = assignmentName,
            dueDate = selectedDueDate!!,
            submissionTime = selectedSubmissionTime!!,
            estimatedHours = estimatedHours,
            currentProgress = currentProgress,
            silenceNotifications = silenceNotifications,
            validatedItem = true,
            dueDateTimeMillis = dueDateTimeMillis,
            creationTimezone = creationZone // TIMEZONE CHANGE: Set the creation timezone
        )

        lifecycleScope.launch {
            try {
                val savedAssignmentId: Long
                withContext(Dispatchers.IO) {
                    assignmentDao.updateAssignment(assignmentToSave)
                    savedAssignmentId = assignmentId

                    // Mark all attachments for this Assignment as validated and update/insert
                    val currentAttachments = attachmentAdapter.currentList
                    currentAttachments.forEach { attachment ->
                        val updatedAttachment = attachment.copy(
                            eventRefId = savedAssignmentId,
                            validatedItem = true
                        )
                        if (attachment.attachmentId == 0L) {
                            attachmentDao.insertAttachment(updatedAttachment)
                        } else {
                            attachmentDao.updateAttachment(updatedAttachment)
                        }
                    }

                    // Delete any attachments that were removed from the list (if any)
                    val attachmentsToDelete = initialAttachmentsLoaded.filter { initial ->
                        currentAttachments.none { current -> current.attachmentId == initial.attachmentId }
                    }
                    attachmentsToDelete.forEach { attachmentDao.deleteAttachment(it) }

                    // Mark all subtasks for this Assignment as validated and update/insert
                    val currentSubtasks = subtaskAdapter.currentList
                    currentSubtasks.forEach { subtask ->
                        val updatedSubtask = subtask.copy(
                            eventRefId = savedAssignmentId,
                            validatedItem = true
                        )
                        if (subtask.subtaskId == 0L) {
                            subtaskDao.insertSubtask(updatedSubtask)
                        } else {
                            subtaskDao.updateSubtask(updatedSubtask)
                        }
                    }

                    // Delete any subtasks that were removed from the list
                    val subtasksToDelete = initialSubtasksLoaded.filter { initial ->
                        currentSubtasks.none { current -> current.subtaskId == initial.subtaskId }
                    }
                    subtasksToDelete.forEach { subtaskDao.deleteSubtask(it) }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@AddAssignmentActivity,
                        if (isNewAssignment) getString(R.string.assignment_save_success) else getString(
                            R.string.assignment_update_success
                        ),
                        Toast.LENGTH_SHORT
                    ).show()

                    val finalAssignment = assignmentToSave.copy(assignmentId = savedAssignmentId)
                    // MODIFIED: Notification logic
                    if (assignmentToSave.currentProgress == 100 || assignmentToSave.silenceNotifications) {
                        notificationHelper.cancelAssignmentNotifications(assignmentToSave.assignmentId!!)
                    } else {
                        notificationHelper.scheduleAssignmentNotifications(assignmentToSave)
                    }

                    initialAssignment = finalAssignment.copy()
                    initialAttachmentsLoaded = attachmentAdapter.currentList.map { it.copy(validatedItem = true) }.toList()
                    initialSubtasksLoaded = subtaskAdapter.currentList.map { it.copy(validatedItem = true) }.toList()
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@AddAssignmentActivity,
                        if (isNewAssignment) getString(R.string.assignment_save_failure, e.localizedMessage) else getString(
                            R.string.assignment_update_failure, e.localizedMessage),
                        Toast.LENGTH_LONG
                    ).show()
                    Log.e("AddAssignmentActivity", "Error saving/updating assignment: ${e.message}", e)

                    if (isNewAssignment) {
                        val currentAssignment = withContext(Dispatchers.IO) { assignmentDao.getAssignmentById(assignmentId) }
                        if (currentAssignment != null && !currentAssignment.validatedItem) {
                            try {
                                attachmentDao.deleteUnvalidatedAttachmentsForEvent("Assignment", assignmentId)
                                subtaskDao.deleteUnvalidatedSubtasksForEvent("Assignment", assignmentId)
                                assignmentDao.deleteAssignment(assignmentId)
                                Log.d("AddAssignmentActivity", "Deleted unvalidated placeholder Assignment (ID: $assignmentId) and its unvalidated attachments/subtasks due to save failure.")
                            } catch (deleteEx: Exception) {
                                Log.e("AddAssignmentActivity", "Error deleting placeholder Assignment or attachments/subtasks after save failure: ${deleteEx.message}", deleteEx)
                            }
                        }
                    }
                }
            }
        }
    }

    // --- AttachmentManagerCallbacks Implementation ---
    override fun getCurrentEventType(): String {
        return "Assignment"
    }

    override fun getCurrentEventRefId(): Long {
        return assignmentId
    }

    override fun onAttachmentsUpdated() {
        loadAttachmentsForAssignment()
    }

    override fun showToast(messageResId: Int, duration: Int) {
        Toast.makeText(this, messageResId, duration).show()
    }

    override fun showToast(message: String, duration: Int) {
        Toast.makeText(this, message, duration).show()
    }

    override fun requestPermission(permission: String, launcher: ActivityResultLauncher<String>) {
        launcher.launch(permission)
    }

    override fun requestPermissions(permissions: Array<String>, launcher: ActivityResultLauncher<Array<String>>) {
        launcher.launch(permissions)
    }

    override fun launchActivityForResult(intent: Intent, launcher: ActivityResultLauncher<Intent>) {
        launcher.launch(intent)
    }

    override fun launchUriActivityForResult(uri: Uri, launcher: ActivityResultLauncher<Uri>) {
        launcher.launch(uri)
    }

    override fun finishActivity() {
        finish()
    }

    override fun onAttachmentAdded(attachment: Attachment) {
        newlyAddedAttachments.add(attachment)
    }

    override fun onAttachmentCopyRequested() {
        Log.d("AddAssignmentActivity", "Attachment copy process requested by AttachmentManager.")
    }

    override fun onAttachmentCopyCompleted() {
        Log.d("AddAssignmentActivity", "Attachment copy process completed by AttachmentManager.")
    }

    // --- SubtaskManagerCallbacks Implementation --
    override fun onSubtasksUpdated() {
        loadSubtasksForAssignment()
    }

    override fun onSubtaskCopyRequested() {
        Log.d("AddAssignmentActivity", "Subtask copy process requested by SubtaskManager.")
    }

    override fun requestUiStateLogging(subtasks: List<Subtask>) {
        val expectedState = subtaskManager.calculateExpectedState(subtasks)
        Log.d("SubtaskManager_Expected", "Expected UI State (Manager's View):\n$expectedState")

//        recyclerViewSubtasks.post {
//            subtaskAdapter.logCurrentAdapterState("After Data Update")
//        }
    }
}
