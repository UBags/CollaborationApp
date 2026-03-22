package com.costheta.cortexa.exam

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
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.costheta.cortexa.data.db.AppDatabase
import com.costheta.cortexa.data.db.dao.TimetableDao
import com.costheta.cortexa.data.models.Exam
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collectLatest
import java.time.Instant // TIMEZONE CHANGE: Import Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId // TIMEZONE CHANGE: Import ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import androidx.activity.OnBackPressedCallback // Import for handling back press
import androidx.appcompat.app.AlertDialog // Import for AlertDialog
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.costheta.cortexa.data.db.dao.AttachmentDao
import com.costheta.cortexa.data.db.dao.ExamDao
import com.costheta.cortexa.data.models.Attachment
import com.costheta.cortexa.util.attachment.AttachmentAdapter
import com.costheta.cortexa.util.attachment.AttachmentManager
import com.costheta.cortexa.util.attachment.AttachmentManagerCallbacks
import com.google.android.material.textfield.TextInputEditText
import com.costheta.cortexa.data.db.dao.SubtaskDao
import com.costheta.cortexa.data.models.Subtask
import com.costheta.cortexa.util.subtask.SubtaskAdapter
import com.costheta.cortexa.util.subtask.SubtaskManager
import com.costheta.cortexa.util.subtask.SubtaskManagerCallbacks
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.flow.firstOrNull
import android.widget.CheckBox
import android.widget.LinearLayout
import androidx.recyclerview.widget.ItemTouchHelper
import com.costheta.cortexa.NotificationHelper
import com.costheta.cortexa.R

import com.costheta.cortexa.data.db.dao.LocationDao
import com.costheta.cortexa.data.models.Location
import com.costheta.cortexa.util.keyboard.AutoCompleteDisappearingKeyboard
import com.costheta.cortexa.util.keyboard.DisappearingKeyboard
import com.costheta.cortexa.util.location.LocationPickerView
import com.costheta.cortexa.util.location.MapPickerActivity
import com.costheta.cortexa.util.subtask.ItemMoveCallback

class AddExamActivity : AppCompatActivity(), AttachmentManagerCallbacks, SubtaskManagerCallbacks {

    // UI elements
    private lateinit var examCourseNameAutoCompleteTextView: AutoCompleteTextView
    private lateinit var examDetailsEditText: EditText
    private lateinit var examDateEditText: EditText
    private lateinit var examStartTimeEditText: EditText
    private lateinit var examEndTimeEditText: EditText
    private lateinit var saveExamButton: Button
    private lateinit var addExamTitle: TextView
    private lateinit var silenceNotificationsCheckBox: CheckBox

    private lateinit var examLocationPickerView: LocationPickerView

    // TIMEZONE CHANGE: UI Elements for timezone display
    private lateinit var timezoneInfoLayout: LinearLayout
    private lateinit var currentTimezoneTextView: TextView
    private lateinit var originalTimezoneTextView: TextView

    // ATTACHMENT UI ELEMENTS (now part of included layout)
    private lateinit var buttonPickFile: Button
    private lateinit var buttonTakePhotoVideo: Button
    private lateinit var buttonRecordAudio: Button
    private lateinit var buttonAddHyperlink: Button
    private lateinit var recyclerViewAttachments: RecyclerView
    private lateinit var noAttachmentsFoundTextView: TextView
    private lateinit var buttonShareAttachments: Button
    private lateinit var buttonCopyAttachments: Button
    private lateinit var attachmentAdapter: AttachmentAdapter

    // SUBTASK UI ELEMENTS
    private var itemTouchHelper: ItemTouchHelper? = null
    private lateinit var editTextAddSubtask: TextInputEditText
    private lateinit var buttonAddSubtask: Button
    private lateinit var buttonCopySubtasks: Button
    private lateinit var recyclerViewSubtasks: RecyclerView
    private lateinit var noSubtasksFoundTextView: TextView
    private lateinit var subtaskAdapter: SubtaskAdapter


    // Database DAO
    private lateinit var timetableDao: TimetableDao
    private lateinit var examDao: ExamDao
    private lateinit var attachmentDao: AttachmentDao
    private lateinit var subtaskDao: SubtaskDao
    private lateinit var locationDao: LocationDao

    // Notification Helper
    private lateinit var notificationHelper: NotificationHelper

    // Variables to hold selected date and time
    private var selectedExamDate: LocalDate? = null
    private var selectedStartTime: LocalTime? = null
    private var selectedEndTime: LocalTime? = null

    // Variable to hold the exam ID if we are in edit mode
    private var examId: Long = -1L
    private var isNewExam: Boolean = false // Flag to determine if it's a new Exam or an existing one

    // Stores the initial duration of the exam when in edit mode
    private var initialDurationMinutes: Long? = null

    private var initialExam: Exam? = null
    private var initialAttachmentsLoaded: List<Attachment> = emptyList() // Stores original attachments for comparison
    private var initialSubtasksLoaded: List<Subtask> = emptyList()
    private var initialLocationEntry: Location? = null
    private val newlyAddedAttachments = mutableListOf<Attachment>()

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

    private val mapPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.let { intent ->
                val locationId = intent.getLongExtra(MapPickerActivity.EXTRA_LOCATION_ID, -1L).takeIf { it != -1L }
                val userFriendlyName = intent.getStringExtra(MapPickerActivity.EXTRA_USER_FRIENDLY_NAME)
                val geocodedAddress = intent.getStringExtra(MapPickerActivity.EXTRA_GEOCODED_ADDRESS)
                val latitude = intent.getDoubleExtra(MapPickerActivity.EXTRA_LATITUDE, 0.0)
                val longitude = intent.getDoubleExtra(MapPickerActivity.EXTRA_LONGITUDE, 0.0)
                val eventType = intent.getStringExtra(MapPickerActivity.EXTRA_EVENT_TYPE) ?: ""
                val eventRefId = intent.getLongExtra(MapPickerActivity.EXTRA_EVENT_REF_ID, -1L)

                val currentEnteredUserFriendlyName = examLocationPickerView.getUserFriendlyName()

                if (latitude != 0.0 || longitude != 0.0 || !geocodedAddress.isNullOrBlank()) {
                    val finalUserFriendlyName = userFriendlyName.takeIf { !it.isNullOrBlank() } ?: currentEnteredUserFriendlyName.ifBlank {
                        geocodedAddress ?: ""
                    }

                    val selectedLocation = Location(
                        locationId = locationId,
                        userFriendlyName = finalUserFriendlyName,
                        geocodedAddress = geocodedAddress ?: "",
                        latitude = latitude,
                        longitude = longitude,
                        eventType = eventType,
                        eventRefId = eventRefId
                    )
                    examLocationPickerView.handleMapPickerResult(selectedLocation)
                    Log.d("AddExamActivity", "MapPicker result: $selectedLocation")
                } else {
                    examLocationPickerView.handleMapPickerResult(null)
                    Log.e("AddExamActivity", "MapPicker result data incomplete or cleared.")
                }
            }
        } else {
            Log.d("AddExamActivity", "MapPicker cancelled or failed.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.exam_activity_add)

        // Initialize UI elements
        addExamTitle = findViewById(R.id.addExamTitle)
        examCourseNameAutoCompleteTextView = findViewById(R.id.examCourseNameAutoCompleteTextView)
        examDetailsEditText = findViewById(R.id.examDetailsEditText)
        examDateEditText = findViewById(R.id.examDateEditText)
        examStartTimeEditText = findViewById(R.id.examStartTimeEditText)
        examEndTimeEditText = findViewById(R.id.examEndTimeEditText)
        saveExamButton = findViewById(R.id.saveExamButton)
        silenceNotificationsCheckBox = findViewById(R.id.silenceNotificationsCheckBox)

        AutoCompleteDisappearingKeyboard.getInstance().register(examCourseNameAutoCompleteTextView)
        DisappearingKeyboard.getInstance().register(examDetailsEditText)

        examLocationPickerView = findViewById(R.id.examLocationPickerView)

        // TIMEZONE CHANGE: Initialize timezone UI elements
        timezoneInfoLayout = findViewById(R.id.timezoneInfoLayout)
        currentTimezoneTextView = findViewById(R.id.currentTimezoneTextView)
        originalTimezoneTextView = findViewById(R.id.originalTimezoneTextView)

        // Initialize NEW attachment UI elements from the included layout
        val attachmentLayout = findViewById<View>(R.id.attachmentLayout)
        buttonPickFile = attachmentLayout.findViewById(R.id.buttonPickFile)
        buttonTakePhotoVideo = attachmentLayout.findViewById(R.id.buttonTakePhotoVideo)
        buttonRecordAudio = attachmentLayout.findViewById(R.id.buttonRecordAudio)
        buttonAddHyperlink = attachmentLayout.findViewById(R.id.buttonAddHyperlink)
        recyclerViewAttachments = attachmentLayout.findViewById(R.id.recyclerViewAttachments)
        noAttachmentsFoundTextView = attachmentLayout.findViewById(R.id.noAttachmentsFoundTextView)
        buttonShareAttachments = attachmentLayout.findViewById(R.id.buttonShareAttachments)
        buttonCopyAttachments = attachmentLayout.findViewById(R.id.buttonCopyAttachments)

        val subtasksLayout = findViewById<View>(R.id.subtasksLayout)
        editTextAddSubtask = subtasksLayout.findViewById(R.id.editTextAddSubtask)
        buttonAddSubtask = subtasksLayout.findViewById(R.id.buttonAddSubtask)
        buttonCopySubtasks = subtasksLayout.findViewById(R.id.buttonCopySubtasks)
        recyclerViewSubtasks = subtasksLayout.findViewById(R.id.recyclerViewSubtasks)
        noSubtasksFoundTextView = subtasksLayout.findViewById(R.id.noSubtasksFoundTextView)

        DisappearingKeyboard.getInstance().register(editTextAddSubtask)

        // Initialize database DAO
        timetableDao = AppDatabase.getDatabase(applicationContext).timetableDao()
        examDao = AppDatabase.getDatabase(applicationContext).examDao()
        attachmentDao = AppDatabase.getDatabase(applicationContext).attachmentDao()
        subtaskDao = AppDatabase.getDatabase(applicationContext).subtaskDao()
        locationDao = AppDatabase.getDatabase(applicationContext).locationDao()

        // Initialize NotificationHelper
        notificationHelper = NotificationHelper(applicationContext)

        // Setup Attachment RecyclerView
        attachmentAdapter = AttachmentAdapter(
            onDeleteClick = { attachment -> attachmentManager.showDeleteAttachmentDialog(attachment) },
            onItemClick = { attachment -> attachmentManager.viewAttachment(attachment) }
        )
        recyclerViewAttachments.apply {
            layoutManager = LinearLayoutManager(this@AddExamActivity)
            adapter = attachmentAdapter
        }

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
            layoutManager = LinearLayoutManager(this@AddExamActivity)
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

        subtaskManager = SubtaskManager(
            activity = this,
            subtaskDao = subtaskDao,
            lifecycleScope = lifecycleScope,
            callbacks = this
        )


        // Set up click listeners for date and time pickers
        examDateEditText.setOnClickListener { showDatePicker() }
        examStartTimeEditText.setOnClickListener { showTimePicker(true) }
        examEndTimeEditText.setOnClickListener { showTimePicker(false) }

        // Set up save button click listener
        saveExamButton.setOnClickListener { saveExam() }

        // Set up email attachments button click listener
        buttonShareAttachments.setOnClickListener { attachmentManager.shareAttachments(attachmentAdapter.currentList) }
        buttonCopyAttachments.setOnClickListener { attachmentManager.startCopyAttachmentsProcess() }

        // Set up attachment button listeners (delegating to AttachmentManager)
        buttonPickFile.setOnClickListener { attachmentManager.checkAndRequestMediaPermissionsForPicker() }
        buttonTakePhotoVideo.setOnClickListener { attachmentManager.checkAndRequestCameraPermission() }
        buttonRecordAudio.setOnClickListener { attachmentManager.launchRecordAudioActivity() }
        buttonAddHyperlink.setOnClickListener { attachmentManager.showAddHyperlinkDialog() }

        buttonAddSubtask.setOnClickListener {
            subtaskManager.addSubtasksFromInput(editTextAddSubtask.text.toString())
            editTextAddSubtask.text?.clear()
        }
        buttonCopySubtasks.setOnClickListener { subtaskManager.startCopySubtasksProcess() }

        // Load course names for AutoCompleteTextView (Venue is now handled by LocationPickerView)
        loadCourseNames() // RENAMED: from loadCourseAndVenueNames

        // Check if we are in edit mode
        val intentExamId = intent.getLongExtra("EXAM_ID", -1L)
        if (intentExamId != -1L) {
            examId = intentExamId
            isNewExam = false
            addExamTitle.text = getString(R.string.edit_exam_title)
            saveExamButton.text = getString(R.string.exam_update_button_text)
            lifecycleScope.launch {
                val exam = examDao.getExamById(examId)
                exam?.let {
                    initialExam = it.copy()
                    loadExamData(it)
                }
                examLocationPickerView.initialize(
                    eventType = "Exam",
                    eventRefId = examId,
                    lifecycleScope = lifecycleScope,
                    mapPickerLauncher = mapPickerLauncher
                )
                locationDao.getLocationForEvent("Exam", examId).firstOrNull()?.let {
                    initialLocationEntry = it.copy()
                    examLocationPickerView.setCurrentSavedLocation(it)
                    // examLocationPickerView.setUserFriendlyName(it.userFriendlyName)
                } ?: run {
                    initialLocationEntry = null
                    examLocationPickerView.clearLocation()
                    examLocationPickerView.setUserFriendlyName(exam?.venue)
                }

                loadAttachmentsForExam()
                loadSubtasksForExam()
            }
        } else {
            // This is a new Exam
            isNewExam = true
            addExamTitle.text = getString(R.string.add_exam_title)
            saveExamButton.text = getString(R.string.exam_save_button_text)
            // Set default exam date to today + 1
            selectedExamDate = LocalDate.now().plusDays(1) // MODIFIED: Auto-populate with current date + 1
            examDateEditText.setText(selectedExamDate?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
            // Set default start time to 09:00 and end time to 11:00
            selectedStartTime = LocalTime.of(9, 0)
            examStartTimeEditText.setText(selectedStartTime?.format(DateTimeFormatter.ofPattern("HH:mm")))
            selectedEndTime = LocalTime.of(11, 0)
            examEndTimeEditText.setText(selectedEndTime?.format(DateTimeFormatter.ofPattern("HH:mm")))
            silenceNotificationsCheckBox.isChecked = false
            updateTimezoneDisplay() // TIMEZONE CHANGE: Update display for new exam
            lifecycleScope.launch {
                createPlaceholderExamAndLoadRelatedData()
            }
        }

        // Register OnBackPressedCallback for modern back gesture handling
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (hasUnsavedChanges()) {
                    val dialogMessage = if (isNewExam) {
                        getString(R.string.exit_without_saving_exam_message)
                    } else {
                        getString(R.string.exit_without_saving_changes_exam_message)
                    }

                    AlertDialog.Builder(this@AddExamActivity)
                        .setTitle(getString(R.string.exit_without_saving_title).uppercase(Locale.getDefault()))
                        .setMessage(dialogMessage)
                        .setPositiveButton(getString(R.string.exit_without_saving_confirm).uppercase(Locale.getDefault())) { dialog, _ ->
                            dialog.dismiss()
                            lifecycleScope.launch {
                                withContext(Dispatchers.IO) {
                                    try {
                                        // This loop handles physical file deletion and their DB records. It's essential.
                                        newlyAddedAttachments.forEach { attachment ->
                                            attachmentManager.deleteAttachment(attachment)
                                        }

                                        if (isNewExam) {
                                            // In ADD mode, delete the placeholder and its remaining children.
                                            val currentExam = examDao.getExamById(examId)
                                            if (currentExam != null && !currentExam.validatedItem) {
                                                // No need to delete attachments again, the loop did it.
                                                subtaskDao.deleteUnvalidatedSubtasksForEvent("Exam", examId)
                                                locationDao.deleteLocationsForEvent("Exam", examId)
                                                examDao.deleteExam(examId)
                                                Log.d("AddExamActivity", "Deleted placeholder Exam (ID: $examId) and related data.")
                                            }
                                        } else {
                                            // In EDIT mode, only clean up unvalidated children and revert location.
                                            attachmentDao.deleteUnvalidatedAttachmentsForEvent("Exam", examId)
                                            subtaskDao.deleteUnvalidatedSubtasksForEvent("Exam", examId)

                                            // Simplified and safer location revert logic.
                                            if (initialLocationEntry != null) {
                                                // If a location existed initially, restore it.
                                                locationDao.updateLocation(initialLocationEntry!!)
                                            } else {
                                                // If no location existed initially, delete any that was added.
                                                locationDao.deleteLocationsForEvent("Exam", examId)
                                            }
                                            Log.d("AddExamActivity", "Cleaned up unvalidated items for existing Exam (ID: $examId).")
                                        }
                                    } catch (e: Exception) {
                                        Log.e("AddExamActivity", "Error during cleanup on back press: ${e.message}", e)
                                    }
                                }
                                this@AddExamActivity.finish()
                            }                        }
                        .setNegativeButton(getString(R.string.dialog_cancel_button).uppercase(Locale.getDefault())) { dialog, _ ->
                            dialog.cancel()
                        }
                        .show()
                } else {
                    this@AddExamActivity.finish()
                }
            }
        })
    }

    override fun onStop() {
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        attachmentManager.onDestroy()
        subtaskManager.onDestroy()
    }

    /**
     * Creates a placeholder Exam item in the database to get a valid examId for attachments and subtasks.
     * This ID will be used for all attachments and subtasks added before the main Exam is saved.
     * The placeholder is marked as `validatedItem = false`.
     */
    private suspend fun createPlaceholderExamAndLoadRelatedData() {
        withContext(Dispatchers.IO) {
            try {
                // Use selectedExamDate (which is now current date + 1 for new exams)
                val placeholderExamDate = selectedExamDate ?: LocalDate.now().plusDays(1)
                val placeholderStartTime = selectedStartTime ?: LocalTime.of(9, 0)
                val placeholderEndTime = selectedEndTime ?: LocalTime.of(11, 0)

                /// TIMEZONE CHANGE: Calculate timestamps and get current zone ID
                val currentZoneId = ZoneId.systemDefault()
                val startDateTime = LocalDateTime.of(placeholderExamDate, placeholderStartTime)
                val endDateTime = if (placeholderEndTime.isBefore(placeholderStartTime)) {
                    LocalDateTime.of(placeholderExamDate.plusDays(1), placeholderEndTime)
                } else {
                    LocalDateTime.of(placeholderExamDate, placeholderEndTime)
                }
                val startDateTimeMillis = startDateTime.atZone(currentZoneId).toInstant().toEpochMilli()
                val endDateTimeMillis = endDateTime.atZone(currentZoneId).toInstant().toEpochMilli()

                val placeholderExam = Exam(
                    examId = null,
                    courseName = "",
                    examDetails = null,
                    examDate = placeholderExamDate,
                    startTime = selectedStartTime ?: LocalTime.of(9, 0),
                    endTime = placeholderEndTime,
                    venue = "", // Venue is now managed by LocationPickerView
                    validatedItem = false,
                    startDateTimeMillis = startDateTimeMillis, // TIMEZONE CHANGE
                    endDateTimeMillis = endDateTimeMillis,   // TIMEZONE CHANGE
                    silenceNotifications = false,
                    creationTimezone = currentZoneId.id // TIMEZONE CHANGE
                )
                examId = examDao.insertExam(placeholderExam)
                withContext(Dispatchers.Main) {
                    Log.d("AddExamActivity", "Placeholder Exam created with ID: $examId")
                    // Now that examId is available, load attachments (will be empty for a new exam)
                    loadAttachmentsForExam()
                    loadSubtasksForExam()
                    examLocationPickerView.initialize(
                        eventType = "Exam",
                        eventRefId = examId,
                        lifecycleScope = lifecycleScope,
                        mapPickerLauncher = mapPickerLauncher
                    )
                    initialExam = placeholderExam.copy(examId = examId)
                    initialLocationEntry = null // For a new exam, no initial location
                }
            } catch (e: Exception) {
                Log.e("AddExamActivity", "Error creating placeholder Exam: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AddExamActivity, "Error initializing Exam. Please restart.", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    /**
     * Loads attachments for the current examId and updates the RecyclerView.
     */
    private fun loadAttachmentsForExam() {
        if (examId != -1L) {
            lifecycleScope.launch {
                attachmentDao.getAttachmentsForEvent("Exam", examId).collectLatest { attachments ->
                    val attachmentsToShow = attachments
                    attachmentAdapter.submitList(attachmentsToShow)
                    initialAttachmentsLoaded = attachmentsToShow.toList()
                    val hasAttachments = attachments.isNotEmpty()
                    noAttachmentsFoundTextView.visibility = if (hasAttachments) View.GONE else View.VISIBLE
                    recyclerViewAttachments.visibility = if (hasAttachments) View.VISIBLE else View.GONE
                    buttonShareAttachments.visibility = if (hasAttachments) View.VISIBLE else View.GONE
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
     * NEW: Loads subtasks for the current examId and updates the RecyclerView.
     */
    private fun loadSubtasksForExam() {
        if (examId != -1L) {
            lifecycleScope.launch {
                subtaskDao.getSubtasksForEvent("Exam", examId).collectLatest { subtasks ->
                    val subtasksToShow = subtasks
                    subtaskAdapter.submitList(subtasksToShow)
                    initialSubtasksLoaded = subtasksToShow.toList()
                    val hasSubtasks = subtasks.isNotEmpty()
                    noSubtasksFoundTextView.visibility = if (hasSubtasks) View.GONE else View.VISIBLE
                    recyclerViewSubtasks.visibility = if (hasSubtasks) View.VISIBLE else View.GONE
                }
            }
        } else {
            subtaskAdapter.submitList(emptyList())
            noSubtasksFoundTextView.visibility = View.VISIBLE
            recyclerViewSubtasks.visibility = View.GONE
        }
    }

    /**
     * Loads the data of an existing exam into the UI fields for editing.
     * @param exam The Exam object to load.
     */
    private fun loadExamData(exam: Exam) {
        addExamTitle.text = getString(R.string.edit_exam_title)
        examCourseNameAutoCompleteTextView.setText(exam.courseName)
        examDetailsEditText.setText(exam.examDetails)

        selectedExamDate = exam.examDate
        examDateEditText.setText(exam.examDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))

        selectedStartTime = exam.startTime
        examStartTimeEditText.setText(selectedStartTime?.format(DateTimeFormatter.ofPattern("HH:mm")))

        selectedEndTime = exam.endTime
        examEndTimeEditText.setText(selectedEndTime?.format(DateTimeFormatter.ofPattern("HH:mm")))

        silenceNotificationsCheckBox.isChecked = exam.silenceNotifications

        // Calculate and store the initial duration for edit mode
        initialDurationMinutes = ChronoUnit.MINUTES.between(selectedStartTime, selectedEndTime)

        // Store the initial exam object for unsaved changes comparison
        initialExam = exam.copy()

//        lifecycleScope.launch {
//            locationDao.getLocationForEvent("Exam", exam.examId!!).firstOrNull()?.let {
//                initialLocationEntry = it.copy()
//                examLocationPickerView.setCurrentSavedLocation(it)
//                // Ensure the user-friendly name from the database is set
//                examLocationPickerView.setUserFriendlyName(it.userFriendlyName)
//            } ?: run {
//                // If no Location object exists in the database for this eventRefId,
//                // we still want to populate the LocationPickerView's EditText with the Exam's venue.
//                initialLocationEntry = null // No initial location
//                examLocationPickerView.clearLocation()
//                examLocationPickerView.setUserFriendlyName(exam.venue) // Populate text field from Exam.venue
//            }
//        }
        // TIMEZONE CHANGE: Update the timezone display based on loaded data
        updateTimezoneDisplay(exam)
    }

    /**
     * Loads unique lecture course names from the database to populate the AutoCompleteTextView.
     */
    private fun loadCourseNames() { // RENAMED from loadCourseAndVenueNames
        lifecycleScope.launch {
            try {
                val classSessionCourseNames = withContext(Dispatchers.IO) {
                    timetableDao.getUniqueLectureCourseNames()
                }
                val examCourseNames = withContext(Dispatchers.IO) {
                    examDao.getUniqueExamCourseNames()
                }
                val uniqueCourseNames = (classSessionCourseNames + examCourseNames)
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()

                withContext(Dispatchers.Main) {
                    val courseNameAdapter = ArrayAdapter(
                        this@AddExamActivity,
                        android.R.layout.simple_dropdown_item_1line,
                        uniqueCourseNames
                    )
                    examCourseNameAutoCompleteTextView.setAdapter(courseNameAdapter)
                }
            } catch (e: Exception) {
                Log.e("AddExamActivity", "Error loading course names for autocomplete: ${e.message}", e)
            }
        }
    }

    /**
     * Displays a DatePickerDialog to allow the user to select the exam date.
     */
    private fun showDatePicker() {
        val initialDate = selectedExamDate ?: LocalDate.now()
        val datePickerDialog = DatePickerDialog(this, { _, y, m, d ->
            selectedExamDate = LocalDate.of(y, m + 1, d)
            examDateEditText.setText(selectedExamDate?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
            updateEndTimeBasedOnStartTimeAndDuration()
            updateTimezoneDisplay(initialExam) // TIMEZONE CHANGE
        }, initialDate.year, initialDate.monthValue - 1, initialDate.dayOfMonth)
        datePickerDialog.show()
    }

    /**
     * Displays a TimePickerDialog to allow the user to select the exam start or end time.
     * @param isStartTime True if selecting start time, false if selecting end time.
     */
    private fun showTimePicker(isStartTime: Boolean) {
        val initialTime = if (isStartTime) selectedStartTime ?: LocalTime.now() else selectedEndTime ?: LocalTime.now().plusHours(1)
        TimePickerDialog(this, { _, h, m ->
            val newTime = LocalTime.of(h, m)
            if (isStartTime) {
                selectedStartTime = newTime
                examStartTimeEditText.setText(selectedStartTime?.format(DateTimeFormatter.ofPattern("HH:mm")))
                updateEndTimeBasedOnStartTimeAndDuration()
            } else {
                selectedEndTime = newTime
                examEndTimeEditText.setText(selectedEndTime?.format(DateTimeFormatter.ofPattern("HH:mm")))
            }
            updateTimezoneDisplay(initialExam) // TIMEZONE CHANGE
        }, initialTime.hour, initialTime.minute, true).show()
    }

    /**
     * Helper function to update the end time based on the start time and stored duration.
     * This is called when start time or date is changed.
     */
    private fun updateEndTimeBasedOnStartTimeAndDuration() {
        if (selectedStartTime != null) {
            if (isNewExam) {
                selectedEndTime = selectedStartTime?.plusHours(1)
            } else {
                selectedEndTime = selectedStartTime?.plusMinutes(initialDurationMinutes ?: 60L)
            }
            examEndTimeEditText.setText(selectedEndTime?.format(DateTimeFormatter.ofPattern("HH:mm")))
        }
    }

    // TIMEZONE CHANGE: This entire method is new or heavily modified
    private fun updateTimezoneDisplay(exam: Exam? = initialExam) {
        val dateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm (dd-MMM-yyyy)", Locale.ENGLISH)
        val currentSystemZone = ZoneId.systemDefault()

        if (isNewExam || exam == null) {
            val displayDateTime = if (selectedExamDate != null && selectedEndTime != null) {
                val endTime = selectedEndTime!!
                val date = if (selectedStartTime != null && endTime.isBefore(selectedStartTime)) {
                    selectedExamDate!!.plusDays(1)
                } else {
                    selectedExamDate!!
                }
                LocalDateTime.of(date, endTime).format(dateTimeFormatter)
            } else {
                "--:-- (-- --- ----)"
            }
            originalTimezoneTextView.text = getString(R.string.timezone_display_format, displayDateTime, currentSystemZone.id)
            originalTimezoneTextView.visibility = View.VISIBLE
            currentTimezoneTextView.visibility = View.GONE
            return
        }

        val originalZoneId = try {
            exam.creationTimezone?.let { ZoneId.of(it) } ?: currentSystemZone
        } catch (e: Exception) {
            currentSystemZone
        }

        val endDateTimeInstant = Instant.ofEpochMilli(exam.endDateTimeMillis)
        val timeInOriginalZone = LocalDateTime.ofInstant(endDateTimeInstant, originalZoneId)
        originalTimezoneTextView.text = getString(R.string.original_timezone_display_format, timeInOriginalZone.format(dateTimeFormatter), originalZoneId.id)
        originalTimezoneTextView.visibility = View.VISIBLE

        if (currentSystemZone.id != originalZoneId.id) {
            val timeInCurrentZone = LocalDateTime.ofInstant(endDateTimeInstant, currentSystemZone)
            currentTimezoneTextView.text = getString(R.string.current_timezone_display_format, timeInCurrentZone.format(dateTimeFormatter), currentSystemZone.id)
            currentTimezoneTextView.visibility = View.VISIBLE
        } else {
            currentTimezoneTextView.visibility = View.GONE
        }
    }

    /**
     * Checks if there are any unsaved changes in the exam form.
     * Compares current UI state with the initial state (either default for new or loaded for existing).
     */
    private fun hasUnsavedChanges(): Boolean {
        val currentCourseName = examCourseNameAutoCompleteTextView.text.toString().trim()
        val currentExamDetails = examDetailsEditText.text.toString().trim()
        val currentExamDate = selectedExamDate
        val currentStartTime = selectedStartTime
        val currentEndTime = selectedEndTime
        val currentSilenceNotifications = silenceNotificationsCheckBox.isChecked
        val currentAttachments = attachmentAdapter.currentList
        val currentSubtasks = subtaskAdapter.currentList

        val initial = initialExam

        if (initial == null) return true

        // Calculate current dueDateTimeMillis considering potential date rollover for midnight
        val currentDueDateTimeMillis = if (currentExamDate != null && currentEndTime != null) {
            val endDateTimeForMillis = if (currentEndTime == LocalTime.MIDNIGHT && (currentStartTime ?: LocalTime.MIN).isAfter(currentEndTime)) {
                LocalDateTime.of(currentExamDate.plusDays(1), currentEndTime)
            } else {
                LocalDateTime.of(currentExamDate, currentEndTime)
            }
            endDateTimeForMillis.plusHours(1).atZone(ZoneOffset.UTC).toInstant().toEpochMilli()
        } else {
            0L
        }

        // Get current state from LocationPickerView
        val currentMapLocation = examLocationPickerView.getSavedLocation()
        val currentUserFriendlyLocationName = examLocationPickerView.getUserFriendlyName()

        // Compare core exam fields (excluding venue, as it's handled by location picker)

        if (currentCourseName != initial.courseName ||
            currentExamDetails != (initial.examDetails ?: "") ||
            selectedExamDate != initial.examDate ||
            selectedStartTime != initial.startTime ||
            selectedEndTime != initial.endTime ||
            currentSilenceNotifications != initial.silenceNotifications) {
            return true
        }

        if (examLocationPickerView.hasUnsavedChanges(initialLocationEntry?.userFriendlyName, initialLocationEntry)) {
            Log.d("AddExamActivity", "hasUnsavedChanges: TRUE (Location picker changes detected)")
            return true
        }

        // Check attachments
        val currentValidatedAttachments = currentAttachments.filter { it.validatedItem }
        val currentUnvalidatedAttachments = currentAttachments.filter { !it.validatedItem }

        if (currentUnvalidatedAttachments.isNotEmpty()) {
            Log.d("AddExamActivity", "hasUnsavedChanges: TRUE (Unvalidated attachments present)")
            return true
        }

        if (initialAttachmentsLoaded.size != currentValidatedAttachments.size) {
            Log.d("AddExamActivity", "hasUnsavedChanges: TRUE (Validated attachment count changed)")
            return true
        }

        val initialAttachmentSet = initialAttachmentsLoaded.map { it.uriString to it.displayName }.toSet()
        val currentValidatedAttachmentSet = currentValidatedAttachments.map { it.uriString to it.displayName }.toSet()

        if (initialAttachmentSet != currentValidatedAttachmentSet) {
            Log.d("AddExamActivity", "hasUnsavedChanges: TRUE (Validated attachment content changed)")
            return true
        }

        // Check for changes in subtasks
        val currentValidatedSubtasks = currentSubtasks.filter { it.validatedItem }
        val currentUnvalidatedSubtasks = currentSubtasks.filter { !it.validatedItem }

        val initialSubtaskSet = initialSubtasksLoaded.associate {
            it.subtaskId to Triple(
                it.description,
                it.completionState,
                it.position
            )
        }
        val currentSubtaskSet = currentSubtasks.associate {
            it.subtaskId to Triple(
                it.description,
                it.completionState,
                it.position
            )
        }

        val hasSubtaskChanges = (
                currentUnvalidatedSubtasks.isNotEmpty() ||
                        initialSubtasksLoaded.size != currentValidatedSubtasks.size ||
                        initialSubtaskSet != currentSubtaskSet
                )

        val isOnlySubtaskDeletions = (
                currentUnvalidatedSubtasks.isEmpty() &&
                        currentSubtasks.size <= initialSubtasksLoaded.size &&
                        currentSubtaskSet.all { (id, currentTuple) ->
                            initialSubtaskSet[id]?.let { initialTuple ->
                                currentTuple == initialTuple
                            } ?: false
                        }
                )

        if (hasSubtaskChanges && !isOnlySubtaskDeletions) {
            Log.d("AddExamActivity", "hasUnsavedChanges: TRUE (hasSubtaskChanges is TRUE)")
            return true
        }

        Log.d("AddExamActivity", "hasUnsavedChanges: FALSE (No changes detected)")
        return false
    }

    /**
     * Saves or updates the exam data to the database.
     */
    private fun saveExam() {
        val isNewEntry = examId == -1L || isNewExam // Determine if it's a new entry for toast
        val courseName = examCourseNameAutoCompleteTextView.text.toString().trim()
        val examDetails = examDetailsEditText.text.toString().trim().takeIf { it.isNotBlank() }
        val silenceNotifications = silenceNotificationsCheckBox.isChecked
        val userFriendlyLocationName = examLocationPickerView.getUserFriendlyName()
        val mapPickedLocation = examLocationPickerView.getSavedLocation()

        // --- MANDATORY FIELD VALIDATION ---
        if (courseName.isBlank()) {
            Toast.makeText(this, getString(R.string.exam_course_name_required), Toast.LENGTH_LONG).show()
            examCourseNameAutoCompleteTextView.error = getString(R.string.exam_course_name_required)
            examCourseNameAutoCompleteTextView.requestFocus()
            return
        } else {
            examCourseNameAutoCompleteTextView.error = null
        }

        if (userFriendlyLocationName.isBlank()) {
            Toast.makeText(this, getString(R.string.exam_location_name_required), Toast.LENGTH_LONG).show()
            examLocationPickerView.findViewById<EditText>(R.id.locationNameEditText).error = getString(R.string.exam_location_name_required)
            examLocationPickerView.findViewById<EditText>(R.id.locationNameEditText).requestFocus()
            return
        } else {
            examLocationPickerView.findViewById<EditText>(R.id.locationNameEditText).error = null
        }

        // Validate time order, considering midnight rollover
        val combinedStartDateTime = LocalDateTime.of(selectedExamDate!!, selectedStartTime!!)
        val combinedEndDateTime = if (selectedEndTime == LocalTime.MIDNIGHT && selectedStartTime!!.isAfter(selectedEndTime)) {
            // If end time is midnight (00:00) and start time is later in the day (e.g., 23:00),
            // then 00:00 refers to the next day.
            LocalDateTime.of(selectedExamDate!!.plusDays(1), selectedEndTime!!)
        } else {
            LocalDateTime.of(selectedExamDate!!, selectedEndTime!!)
        }

        if (combinedStartDateTime.isAfter(combinedEndDateTime) || combinedStartDateTime == combinedEndDateTime) {
            Toast.makeText(this, getString(R.string.exam_time_order_error), Toast.LENGTH_LONG).show()
            examEndTimeEditText.error = getString(R.string.exam_time_order_error) // Set error
            examEndTimeEditText.requestFocus()
            return
        } else {
            examEndTimeEditText.error = null // Clear error
        }

        val currentZoneId = ZoneId.systemDefault()
        val startDateTimeMillis = combinedStartDateTime.atZone(currentZoneId).toInstant().toEpochMilli()
        val endDateTimeMillis = combinedEndDateTime.atZone(currentZoneId).toInstant().toEpochMilli()
        val creationZone = if (isNewExam) currentZoneId.id else initialExam?.creationTimezone

        // Determine the final venue string to save in the Exam object.
        // This will be the user-friendly name if a map location is selected,
        // otherwise it will be the textfield data.
        val finalVenueForExam = userFriendlyLocationName // Always use the text field's content for the Exam's venue


        // Check for empty exam (adjusted for venue being optional if map not picked)
        // This check is now less critical as mandatory fields are handled above,
        // but can serve as a final check for a truly empty entry if all optionals are also blank.
        if (courseName.isEmpty() && (examDetails == null || examDetails.isEmpty()) && finalVenueForExam.isEmpty() && attachmentAdapter.currentList.isEmpty() && subtaskAdapter.currentList.isEmpty()) {
            Toast.makeText(this, R.string.empty_exam_warning, Toast.LENGTH_SHORT).show()
            return
        }

        val examToSave = Exam(
            examId = examId,
            courseName = courseName,
            examDetails = examDetails,
            examDate = selectedExamDate!!,
            startTime = selectedStartTime!!,
            endTime = selectedEndTime!!,
            venue = userFriendlyLocationName,
            validatedItem = true,
            startDateTimeMillis = startDateTimeMillis,
            endDateTimeMillis = endDateTimeMillis,
            silenceNotifications = silenceNotifications,
            creationTimezone = creationZone
        )

        lifecycleScope.launch {
            try {
                val savedExamId: Long
                withContext(Dispatchers.IO) {
                    if (isNewExam) {
                        examDao.updateExam(examToSave)
                        savedExamId = examId
                    } else {
                        examDao.updateExam(examToSave)
                        savedExamId = examId
                    }

                    // MODIFIED: Handle saving/updating the LocationEntry based on userFriendlyLocationName
                    if (userFriendlyLocationName.isNotBlank() || mapPickedLocation != null) {
                        val locationToPersist = Location(
                            // Prioritize ID from map picker result, fallback to initial for edits
                            locationId = mapPickedLocation?.locationId ?: initialLocationEntry?.locationId,
                            userFriendlyName = userFriendlyLocationName,
                            geocodedAddress = mapPickedLocation?.geocodedAddress ?: "",
                            latitude = mapPickedLocation?.latitude ?: 0.0,
                            longitude = mapPickedLocation?.longitude ?: 0.0,
                            eventType = "Exam",
                            eventRefId = savedExamId
                        )

                        // Decide to insert or update based on whether the object has an ID
                        if (locationToPersist.locationId == null) {
                            locationDao.insertLocation(locationToPersist)
                        } else {
                            locationDao.updateLocation(locationToPersist)
                        }
                        Log.d("AddExamActivity", "Saved/Updated LocationEntry for Exam ID: $savedExamId: $locationToPersist")
                    } else {
                        // If userFriendlyLocationName is blank, delete any existing Location entry for this event.
                        if (initialLocationEntry != null) {
                            locationDao.deleteLocationsForEvent("Exam", savedExamId)
                            Log.d("AddExamActivity", "Deleted LocationEntry for Exam ID: $savedExamId as user-friendly name was blank.")
                        }
                    }

                    val currentAttachments = attachmentAdapter.currentList
                    currentAttachments.forEach { attachment ->
                        val updatedAttachment = attachment.copy(
                            eventRefId = savedExamId,
                            validatedItem = true
                        )
                        if (attachment.attachmentId == 0L) {
                            attachmentDao.insertAttachment(updatedAttachment)
                        } else { // Existing attachment
                            attachmentDao.updateAttachment(updatedAttachment)
                        }
                    }

                    // Delete any attachments that were removed from the list (if any)
                    val attachmentsToDelete = initialAttachmentsLoaded.filter { initial ->
                        currentAttachments.none { current -> current.attachmentId == initial.attachmentId }
                    }
                    attachmentsToDelete.forEach { attachmentDao.deleteAttachment(it) }

                    val currentSubtasks = subtaskAdapter.currentList
                    currentSubtasks.forEach { subtask ->
                        val updatedSubtask = subtask.copy(
                            eventRefId = savedExamId,
                            validatedItem = true
                        )
                        if (subtask.subtaskId == 0L) {
                            subtaskDao.insertSubtask(updatedSubtask)
                        } else { // Existing subtask
                            subtaskDao.updateSubtask(updatedSubtask)
                        }
                    }

                    val subtasksToDelete = initialSubtasksLoaded.filter { initial ->
                        currentSubtasks.none { current -> current.subtaskId == initial.subtaskId }
                    }
                    subtasksToDelete.forEach { subtaskDao.deleteSubtask(it) }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@AddExamActivity,
                        if (isNewEntry) getString(R.string.exam_save_success) else getString(R.string.exam_update_success),
                        Toast.LENGTH_SHORT
                    ).show()

                    val finalExam = examToSave.copy(examId = savedExamId)
                    if (!finalExam.silenceNotifications) {
                        notificationHelper.scheduleExamNotifications(finalExam)
                    } else {
                        notificationHelper.cancelExamNotifications(finalExam.examId!!) // Cancel if previously scheduled and now silenced
                    }

                    initialExam = finalExam.copy()
                    initialAttachmentsLoaded = attachmentAdapter.currentList.map { it.copy(validatedItem = true) }.toList()
                    initialSubtasksLoaded = subtaskAdapter.currentList.map { it.copy(validatedItem = true) }.toList()
                    initialLocationEntry = examLocationPickerView.getSavedLocation()?.copy()
                        ?: if (userFriendlyLocationName.isNotBlank()) {
                            // If no map location was picked but userFriendlyName was entered, create a placeholder Location for initial state tracking
                            Location(
                                locationId = null, // Will be assigned by DB if inserted
                                userFriendlyName = userFriendlyLocationName,
                                geocodedAddress = "",
                                latitude = 0.0,
                                longitude = 0.0,
                                eventType = "Exam",
                                eventRefId = savedExamId
                            )
                        } else null

                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@AddExamActivity,
                        if (isNewExam) getString(R.string.exam_save_failure, e.localizedMessage) else getString(
                            R.string.exam_update_failure, e.localizedMessage),
                        Toast.LENGTH_LONG
                    ).show()
                    Log.e("AddExamActivity", "Error saving/updating exam: ${e.message}", e)

                    if (isNewExam) {
                        val currentExam = withContext(Dispatchers.IO) { examDao.getExamById(examId) }
                        if (currentExam != null && !currentExam.validatedItem) {
                            try {
                                attachmentDao.deleteUnvalidatedAttachmentsForEvent("Exam", examId)
                                subtaskDao.deleteUnvalidatedSubtasksForEvent("Exam", examId)
                                locationDao.deleteLocationsForEvent("Exam", examId)
                                notificationHelper.cancelExamNotifications(examId)
                                examDao.deleteExam(examId)
                                Log.d("AddExamActivity", "Deleted unvalidated placeholder Exam (ID: $examId) and its unvalidated attachments/subtasks/location due to save failure.")
                            } catch (deleteEx: Exception) {
                                Log.e("AddExamActivity", "Error deleting placeholder Exam or related data after save failure: ${deleteEx.message}", deleteEx)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun getCurrentEventType(): String = "Exam"
    override fun getCurrentEventRefId(): Long = examId
    override fun onAttachmentsUpdated() { loadAttachmentsForExam() }
    override fun showToast(messageResId: Int, duration: Int) { Toast.makeText(this, messageResId, duration).show() }
    override fun showToast(message: String, duration: Int) { Toast.makeText(this, message, duration).show() }
    override fun requestPermission(permission: String, launcher: ActivityResultLauncher<String>) { launcher.launch(permission) }
    override fun requestPermissions(permissions: Array<String>, launcher: ActivityResultLauncher<Array<String>>) { launcher.launch(permissions) }
    override fun launchActivityForResult(intent: Intent, launcher: ActivityResultLauncher<Intent>) { launcher.launch(intent) }
    override fun launchUriActivityForResult(uri: Uri, launcher: ActivityResultLauncher<Uri>) { launcher.launch(uri) }
    override fun finishActivity() { finish() }
    override fun onAttachmentAdded(attachment: Attachment) { newlyAddedAttachments.add(attachment) }
    override fun onAttachmentCopyRequested() { Log.d("AddAssignmentActivity", "Attachment copy process requested by AttachmentManager.") }
    override fun onAttachmentCopyCompleted() { Log.d("AddAssignmentActivity", "Attachment copy process completed by AttachmentManager.") }
    override fun onSubtasksUpdated() { loadSubtasksForExam() }
    override fun requestUiStateLogging(subtasks: List<Subtask>) {
        val expectedState = subtaskManager.calculateExpectedState(subtasks)
        Log.d("SubtaskManager_Expected", "Expected UI State (Manager's View):\\n$expectedState")
    }
    override fun onSubtaskCopyRequested() { Log.d("AddExamActivity", "Subtask copy process requested by SubtaskManager.") }
}
