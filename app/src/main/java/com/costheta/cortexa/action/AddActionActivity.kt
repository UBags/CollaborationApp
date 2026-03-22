package com.costheta.cortexa.action

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.costheta.cortexa.data.db.AppDatabase
import com.costheta.cortexa.data.models.Action
import com.costheta.cortexa.data.models.Attachment
import com.costheta.cortexa.data.models.Subtask
import com.costheta.cortexa.util.attachment.AttachmentAdapter
import com.costheta.cortexa.util.attachment.AttachmentManager
import com.costheta.cortexa.util.attachment.AttachmentManagerCallbacks
import com.costheta.cortexa.util.subtask.SubtaskAdapter
import com.costheta.cortexa.util.subtask.SubtaskManager
import com.costheta.cortexa.util.subtask.SubtaskManagerCallbacks
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull // Import firstOrNull for Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Locale
import android.widget.AutoCompleteTextView // Import AutoCompleteTextView for auto-suggestions
import android.widget.EditText
import android.widget.LinearLayout
import androidx.recyclerview.widget.ItemTouchHelper
import com.costheta.cortexa.NotificationHelper
import com.costheta.cortexa.R

import com.costheta.cortexa.data.models.Location
import com.costheta.cortexa.util.keyboard.AutoCompleteDisappearingKeyboard
import com.costheta.cortexa.util.keyboard.DisappearingKeyboard
import com.costheta.cortexa.util.location.LocationPickerView
import com.costheta.cortexa.util.location.MapPickerActivity
import com.costheta.cortexa.util.subtask.ItemMoveCallback
import java.time.Instant
import java.time.ZoneId


class AddActionActivity : AppCompatActivity(), AttachmentManagerCallbacks, SubtaskManagerCallbacks {

    private lateinit var actionNameAutoCompleteTextView: AutoCompleteTextView // CHANGED to AutoCompleteTextView
    private lateinit var descriptionAutoCompleteTextView: AutoCompleteTextView // CHANGED to AutoCompleteTextView
    private lateinit var startDateEditText: TextInputEditText
    private lateinit var startTimeEditText: TextInputEditText
    private lateinit var endDateEditText: TextInputEditText
    private lateinit var endTimeEditText: TextInputEditText
    private lateinit var notificationMinutes1EditText: TextInputEditText
    private lateinit var notificationMinutes2EditText: TextInputEditText
    private lateinit var notificationMinutes3EditText: TextInputEditText
    private lateinit var silenceNotificationsCheckbox: CheckBox
    private lateinit var doneCheckbox: CheckBox
    private lateinit var saveActionButton: Button
    private lateinit var addActionTitle: TextView

    private lateinit var actionLocationPickerView: LocationPickerView

    // TIMEZONE CHANGE
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

    private var selectedStartDate: LocalDate? = null
    private var selectedStartTime: LocalTime? = null
    private var selectedEndDate: LocalDate? = null
    private var selectedEndTime: LocalTime? = null
    private var selectedNotificationMinutes1: Int? = null
    private var selectedNotificationMinutes2: Int? = null
    private var selectedNotificationMinutes3: Int? = null

    private var actionId: Long = -1L
    private var actionItem: Action? = null
    private var initialActionItem: Action? = null
    private var initialAttachmentsLoaded: List<Attachment> = emptyList()
    private var initialSubtasksLoaded: List<Subtask> = emptyList()
    private var initialLocationEntry: Location? = null
    private val newlyAddedAttachments = mutableListOf<Attachment>()


    private var isNewAction: Boolean = false
    private var initialDurationMinutes: Long = 60L // Default for new actions, updated for existing

    private val actionDao by lazy { AppDatabase.getDatabase(this).actionDao() }
    private val attachmentDao by lazy { AppDatabase.getDatabase(this).attachmentDao() }
    private val subtaskDao by lazy { AppDatabase.getDatabase(this).subtaskDao() }
    private val notificationHelper by lazy { NotificationHelper(this) }
    private val locationDao by lazy { AppDatabase.getDatabase(this).locationDao() }

    // Managers
    private lateinit var attachmentManager: AttachmentManager
    private lateinit var subtaskManager: SubtaskManager

    // Launchers for Activity Results
    private val recordAudioLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        attachmentManager.handleRecordAudioResult(result.resultCode, result.data)
    }

    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        attachmentManager.handlePickFileResult(result.resultCode, result.data)
    }

    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        attachmentManager.handleTakePhotoResult(success)
    }

    private val takeVideoLauncher = registerForActivityResult(ActivityResultContracts.CaptureVideo()) { success ->
        attachmentManager.handleTakeVideoResult(success)
    }

    private val requestCameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        attachmentManager.handleCameraPermissionResult(isGranted)
    }

    private val requestMediaPermissionsLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        attachmentManager.handleMediaPermissionsResult(permissions)
    }

    private val mapPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.let { intent ->
                val locationId = intent.getLongExtra(MapPickerActivity.EXTRA_LOCATION_ID, -1L)
                    .takeIf { it != -1L }
                val userFriendlyName =
                    intent.getStringExtra(MapPickerActivity.EXTRA_USER_FRIENDLY_NAME)
                val geocodedAddress =
                    intent.getStringExtra(MapPickerActivity.EXTRA_GEOCODED_ADDRESS)
                val latitude = intent.getDoubleExtra(MapPickerActivity.EXTRA_LATITUDE, 0.0)
                val longitude = intent.getDoubleExtra(MapPickerActivity.EXTRA_LONGITUDE, 0.0)
                val eventType = intent.getStringExtra(MapPickerActivity.EXTRA_EVENT_TYPE) ?: ""
                val eventRefId = intent.getLongExtra(MapPickerActivity.EXTRA_EVENT_REF_ID, -1L)

                val currentEnteredUserFriendlyName = actionLocationPickerView.getUserFriendlyName()

                if (latitude != 0.0 || longitude != 0.0 || !geocodedAddress.isNullOrBlank()) {
                    val finalUserFriendlyName = userFriendlyName.takeIf { !it.isNullOrBlank() }
                        ?: currentEnteredUserFriendlyName.ifBlank {
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
                    actionLocationPickerView.handleMapPickerResult(selectedLocation)
                    Log.d("AddActionActivity", "MapPicker result: $selectedLocation")
                } else {
                    actionLocationPickerView.handleMapPickerResult(null)
                    Log.e("AddActionActivity", "MapPicker result data incomplete or cleared.")
                }
            }
        } else {
            Log.d("AddActionActivity", "MapPicker cancelled or failed.")
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.action_activity_add)

        // Initialize UI elements
        actionNameAutoCompleteTextView = findViewById(R.id.actionNameEditText) // Cast to AutoCompleteTextView
        descriptionAutoCompleteTextView = findViewById(R.id.descriptionEditText) // Cast to AutoCompleteTextView
        startDateEditText = findViewById(R.id.startDateEditText)
        startTimeEditText = findViewById(R.id.startTimeEditText)
        endDateEditText = findViewById(R.id.endDateEditText)
        endTimeEditText = findViewById(R.id.endTimeEditText)
        notificationMinutes1EditText = findViewById(R.id.notificationMinutes1EditText)
        notificationMinutes2EditText = findViewById(R.id.notificationMinutes2EditText)
        notificationMinutes3EditText = findViewById(R.id.notificationMinutes3EditText)
        silenceNotificationsCheckbox = findViewById(R.id.silenceNotificationsCheckbox)
        doneCheckbox = findViewById(R.id.doneCheckbox)
        saveActionButton = findViewById(R.id.saveActionButton)
        addActionTitle = findViewById(R.id.addActionTitle)

        AutoCompleteDisappearingKeyboard.getInstance().register(actionNameAutoCompleteTextView)
        AutoCompleteDisappearingKeyboard.getInstance().register(descriptionAutoCompleteTextView)

        actionLocationPickerView = findViewById(R.id.actionLocationPickerView)

        // TIMEZONE CHANGE
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

        // Initialize AttachmentManager
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

        // Set up RecyclerViews
        attachmentAdapter = AttachmentAdapter(
            onDeleteClick = { attachment -> attachmentManager.showDeleteAttachmentDialog(attachment) },
            onItemClick = { attachment -> attachmentManager.viewAttachment(attachment) }
        )
        recyclerViewAttachments.apply {
            layoutManager = LinearLayoutManager(this@AddActionActivity)
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
            layoutManager = LinearLayoutManager(this@AddActionActivity)
            adapter = subtaskAdapter
        }

        val callback = ItemMoveCallback(subtaskAdapter)
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper?.attachToRecyclerView(recyclerViewSubtasks)

        // Set up click listeners for date/time pickers
        setupClickListeners()

        // Load unique action names and descriptions for auto-suggestions
        loadUniqueActionNamesAndDescriptions()

        // Check if editing an existing action or adding a new one
        val intentActionId = intent.getLongExtra("ACTION_ID", -1L)
        if (intentActionId != -1L) {
            actionId = intentActionId
            isNewAction = false
            lifecycleScope.launch {
                val action = actionDao.getActionById(actionId)
                action?.let {
                    actionItem = it
                    initialActionItem = it.copy() // Store initial state for change detection
                    loadActionData(it)
                    // Calculate initial duration from loaded data for editing
                    initialDurationMinutes = ChronoUnit.MINUTES.between(
                        LocalDateTime.of(it.startDate, it.startTime),
                        LocalDateTime.of(it.endDate, it.endTime)
                    )
                }
                actionLocationPickerView.initialize(
                    eventType = "Action",
                    eventRefId = actionId,
                    lifecycleScope = lifecycleScope,
                    mapPickerLauncher = mapPickerLauncher
                )
                locationDao.getLocationForEvent("Action", actionId).firstOrNull()?.let {
                    initialLocationEntry = it.copy()
                    actionLocationPickerView.setCurrentSavedLocation(it)
                } ?: run {
                    initialLocationEntry = null
                    actionLocationPickerView.clearLocation()
                    actionLocationPickerView.setUserFriendlyName(action?.venue)
                }
                saveActionButton.text = getString(R.string.update_action_button_text)
                loadAttachmentsForAction()
                loadSubtasksForAction()
            }
        } else {
            isNewAction = true
            // Auto-populate for new action
            setupNewActionDefaults()
            lifecycleScope.launch {
                createPlaceholderActionAndLoadRelatedData()
            }
        }

        // Handle back press to prompt for unsaved changes
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (hasUnsavedChanges()) {
                    AlertDialog.Builder(this@AddActionActivity)
                        .setTitle(getString(R.string.exit_without_saving_title).uppercase(Locale.getDefault()))
                        .setMessage(R.string.exit_without_saving_message)
                        .setPositiveButton(getString(R.string.exit_without_saving_confirm).uppercase(Locale.getDefault())) { dialog, _ ->
                            dialog.dismiss()
                            lifecycleScope.launch {
                                withContext(Dispatchers.IO) {
                                    try {
                                        // This handles physical file deletion for newly added attachments.
                                        newlyAddedAttachments.forEach { attachment ->
                                            attachmentManager.deleteAttachment(attachment)
                                        }

                                        if (isNewAction) {
                                            // In ADD mode, delete the entire placeholder record and its children.
                                            val currentAction = actionDao.getActionById(actionId)
                                            if (currentAction != null && !currentAction.validatedItem) {
                                                subtaskDao.deleteUnvalidatedSubtasksForEvent("Action", actionId)
                                                locationDao.deleteLocationsForEvent("Action", actionId)
                                                notificationHelper.cancelActionNotifications(actionId)
                                                actionDao.deleteAction(actionId)
                                                Log.d("AddActionActivity", "Deleted placeholder Action (ID: $actionId) and related data.")
                                            }
                                        } else {
                                            // In EDIT mode, only clean up newly added (unvalidated) children.
                                            attachmentDao.deleteUnvalidatedAttachmentsForEvent("Action", actionId)
                                            subtaskDao.deleteUnvalidatedSubtasksForEvent("Action", actionId)

                                            // Revert location to its initial state.
                                            if (initialLocationEntry != null) {
                                                locationDao.updateLocation(initialLocationEntry!!)
                                            } else {
                                                locationDao.deleteLocationsForEvent("Action", actionId)
                                            }
                                            Log.d("AddActionActivity", "Cleaned up unvalidated items for existing Action (ID: $actionId).")
                                        }
                                    } catch (e: Exception) {
                                        Log.e("AddActionActivity", "Error during cleanup on back press: ${e.message}", e)
                                    }
                                }
                                this@AddActionActivity.finish()
                            }
                        }
                        .setNegativeButton(getString(R.string.dialog_cancel_button).uppercase(Locale.getDefault())) { dialog, _ ->
                            dialog.cancel()
                        }
                        .show()
                } else {
                    this@AddActionActivity.finish()
                }
            }
        })
    }

    private fun setupClickListeners() {
        startDateEditText.setOnClickListener { showDatePickerDialog(true) }
        startTimeEditText.setOnClickListener { showTimePickerDialog(true) }
        endDateEditText.setOnClickListener { showDatePickerDialog(false) }
        endTimeEditText.setOnClickListener { showTimePickerDialog(false) }
        // Set up click listeners for notification time pickers
        notificationMinutes1EditText.setOnClickListener { showNotificationTimePickerDialog(1) } // Changed to pass int
        notificationMinutes2EditText.setOnClickListener { showNotificationTimePickerDialog(2) } // Changed to pass int
        notificationMinutes3EditText.setOnClickListener { showNotificationTimePickerDialog(3) }

        // Set up save button listener
        saveActionButton.setOnClickListener { saveAction() }

        // Delegate attachment actions to AttachmentManager
        buttonPickFile.setOnClickListener { attachmentManager.checkAndRequestMediaPermissionsForPicker() }
        buttonTakePhotoVideo.setOnClickListener { attachmentManager.checkAndRequestCameraPermission() }
        buttonRecordAudio.setOnClickListener { attachmentManager.launchRecordAudioActivity() }
        buttonAddHyperlink.setOnClickListener { attachmentManager.showAddHyperlinkDialog() }
        buttonShareAttachments.setOnClickListener { attachmentManager.shareAttachments(attachmentAdapter.currentList) }
        buttonCopyAttachments.setOnClickListener { attachmentManager.startCopyAttachmentsProcess() }

        // Delegate subtask actions to SubtaskManager
        buttonAddSubtask.setOnClickListener {
            subtaskManager.addSubtasksFromInput(editTextAddSubtask.text.toString())
            editTextAddSubtask.text?.clear() // Clear input after adding
        }
        buttonCopySubtasks.setOnClickListener { subtaskManager.startCopySubtasksProcess() }    }

    override fun onDestroy() {
        super.onDestroy()
        attachmentManager.onDestroy() // Clean up dialog if open
        subtaskManager.onDestroy() // Clean up dialog if open
    }

    private fun setupNewActionDefaults() {
        val startDateTime = LocalDateTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0)
        selectedStartDate = startDateTime.toLocalDate()
        selectedStartTime = startDateTime.toLocalTime()

        updateEndDateTime(initialDurationMinutes)
        // Get the shared preferences
        val prefs = getSharedPreferences("user_preferences", Context.MODE_PRIVATE)
        // Populate notification times from saved preferences, with original values as defaults
        selectedNotificationMinutes1 = prefs.getInt("pref_action_notif1", 60)
        selectedNotificationMinutes2 = prefs.getInt("pref_action_notif2", 30)
        selectedNotificationMinutes3 = prefs.getInt("pref_action_notif3", 10)
        updateDateTimeFieldsUI()
        actionLocationPickerView.setUserFriendlyName(getString(R.string.default_location))
        saveActionButton.text = getString(R.string.save_action_button_text)
    }

    private fun updateDateTimeFieldsUI() {
        val dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.getDefault())
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
        startDateEditText.setText(selectedStartDate?.format(dateFormatter))
        startTimeEditText.setText(selectedStartTime?.format(timeFormatter))
        endDateEditText.setText(selectedEndDate?.format(dateFormatter))
        endTimeEditText.setText(selectedEndTime?.format(timeFormatter))
        notificationMinutes1EditText.setText(formatMinutesToHoursMinutes(selectedNotificationMinutes1 ?: 0))
        notificationMinutes2EditText.setText(formatMinutesToHoursMinutes(selectedNotificationMinutes2 ?: 0))
        notificationMinutes3EditText.setText(formatMinutesToHoursMinutes(selectedNotificationMinutes3 ?: 0))
    }

    private suspend fun createPlaceholderActionAndLoadRelatedData() {
        withContext(Dispatchers.IO) {
            try {
                // TIMEZONE CHANGE
                val currentZoneId = ZoneId.systemDefault()
                val startDateTime = LocalDateTime.of(selectedStartDate!!, selectedStartTime!!)
                val endDateTime = LocalDateTime.of(selectedEndDate!!, selectedEndTime!!)
                val startMillis = startDateTime.atZone(currentZoneId).toInstant().toEpochMilli()
                val endMillis = endDateTime.atZone(currentZoneId).toInstant().toEpochMilli()

                val placeholderAction = Action(
                    actionName = "", description = null,
                    startDate = selectedStartDate!!, startTime = selectedStartTime!!,
                    endDate = selectedEndDate!!, endTime = selectedEndTime!!,
                    notificationMinutes1 = selectedNotificationMinutes1,
                    notificationMinutes2 = selectedNotificationMinutes2,
                    notificationMinutes3 = selectedNotificationMinutes3,
                    validatedItem = false, venue = "", isDone = false,
                    startDateTimeMillis = startMillis, endDateTimeMillis = endMillis,
                    creationTimezone = currentZoneId.id
                )
                actionId = actionDao.insertAction(placeholderAction)
                withContext(Dispatchers.Main) {
                    initialActionItem = placeholderAction.copy(actionId = actionId)
                    loadAttachmentsForAction()
                    loadSubtasksForAction()
                    actionLocationPickerView.initialize("Action", actionId, lifecycleScope, mapPickerLauncher)
                    initialLocationEntry = null
                    updateTimezoneDisplay(initialActionItem)
                }
            } catch (e: Exception) {
                Log.e("AddActionActivity", "Error creating placeholder: ${e.message}", e)
                withContext(Dispatchers.Main) { Toast.makeText(this@AddActionActivity, "Initialization error.", Toast.LENGTH_LONG).show(); finish() }
            }
        }
    }

    /**
     * Loads existing action data into the UI fields.
     */
    private fun loadActionData(action: Action) {
        actionNameAutoCompleteTextView.setText(action.actionName)
        descriptionAutoCompleteTextView.setText(action.description)
        selectedStartDate = action.startDate
        selectedStartTime = action.startTime
        selectedEndDate = action.endDate
        selectedEndTime = action.endTime

        // *** THE FIX IS HERE ***
        // The member variables must be updated from the loaded action object.
        selectedNotificationMinutes1 = action.notificationMinutes1
        selectedNotificationMinutes2 = action.notificationMinutes2
        selectedNotificationMinutes3 = action.notificationMinutes3

        updateDateTimeFieldsUI() // This will now use the correct, updated member variables
        silenceNotificationsCheckbox.isChecked = action.silenceNotifications
        doneCheckbox.isChecked = action.isDone
        addActionTitle.text = getString(R.string.edit_action_title)
        updateTimezoneDisplay(action) // TIMEZONE CHANGE

        lifecycleScope.launch {
            locationDao.getLocationForEvent("Action", action.actionId!!).firstOrNull()?.let {
                initialLocationEntry = it.copy()
                actionLocationPickerView.setCurrentSavedLocation(it)
                actionLocationPickerView.setUserFriendlyName(it.userFriendlyName)
            } ?: run {
                initialLocationEntry = null
                actionLocationPickerView.clearLocation()
                actionLocationPickerView.setUserFriendlyName(action.venue)
            }
        }
    }

    // TIMEZONE CHANGE: New function
    private fun updateTimezoneDisplay(action: Action?) {
        if (action == null) {
            timezoneInfoLayout.visibility = View.GONE
            return
        }
        timezoneInfoLayout.visibility = View.VISIBLE
        val dateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm (dd-MMM-yyyy)", Locale.ENGLISH)
        val currentSystemZone = ZoneId.systemDefault()
        val originalZoneId = try {
            action.creationTimezone?.let { ZoneId.of(it) } ?: currentSystemZone
        } catch (e: Exception) {
            currentSystemZone
        }

        val endDateTimeInstant = Instant.ofEpochMilli(action.endDateTimeMillis)
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
     * Saves the action to the database.
     */
    private fun saveAction() {
        val name = actionNameAutoCompleteTextView.text.toString().trim()
        val description = descriptionAutoCompleteTextView.text.toString().trim()
        val silenceNotifications = silenceNotificationsCheckbox.isChecked
        val isDone = doneCheckbox.isChecked
        val userFriendlyLocationName = actionLocationPickerView.getUserFriendlyName()
        val mapPickedLocation = actionLocationPickerView.getSavedLocation()

// --- MANDATORY FIELD VALIDATION ---
        if (name.isBlank()) {
            actionNameAutoCompleteTextView.error = getString(R.string.action_name_cannot_be_empty)
            actionNameAutoCompleteTextView.requestFocus()
            Toast.makeText(this, R.string.action_name_cannot_be_empty, Toast.LENGTH_SHORT).show()
            return
        } else {
            actionNameAutoCompleteTextView.error = null
        }

        if (userFriendlyLocationName.isBlank()) {
            Toast.makeText(this, getString(R.string.action_default_location), Toast.LENGTH_LONG).show()
            actionLocationPickerView.setUserFriendlyName(getString(R.string.default_location))
            actionLocationPickerView.findViewById<EditText>(R.id.locationNameEditText).setText(
                getString(R.string.default_location))
            // actionLocationPickerView.findViewById<EditText>(R.id.locationNameEditText).requestFocus()
            // return
        } else {
            actionLocationPickerView.findViewById<EditText>(R.id.locationNameEditText).error = null
        }

        val startDateTime = LocalDateTime.of(selectedStartDate!!, selectedStartTime!!)
        val endDateTime = LocalDateTime.of(selectedEndDate!!, selectedEndTime!!)

        if (endDateTime.isBefore(startDateTime)) {
            Toast.makeText(this, R.string.action_end_before_start_error, Toast.LENGTH_LONG).show()
            // endDateEditText.error = getString(R.string.action_end_before_start_error)
            endTimeEditText.error = getString(R.string.action_end_before_start_error)
            endTimeEditText.requestFocus() // Focus on end date if it's before start
            return
        } else {
            endDateEditText.error = null
            endTimeEditText.error = null
        }

        // TIMEZONE CHANGE
        val currentZoneId = ZoneId.systemDefault()
        val startMillis = startDateTime.atZone(currentZoneId).toInstant().toEpochMilli()
        val endMillis = endDateTime.atZone(currentZoneId).toInstant().toEpochMilli()
        val creationZone = if (isNewAction) currentZoneId.id else initialActionItem?.creationTimezone


        val finalVenueForAction = userFriendlyLocationName.ifBlank {
            getString(R.string.default_location)
        }

        val OZI = try {
            actionItem?.creationTimezone?.let { ZoneId.of(it) } ?: ZoneId.systemDefault()
        } catch (e: Exception) {
            ZoneId.systemDefault()
        }

        val newAction = Action(
            actionId = this.actionId,
            actionName = name,
            description = descriptionAutoCompleteTextView.text.toString().trim().ifEmpty { null },
            startDate = selectedStartDate!!, startTime = selectedStartTime!!,
            endDate = selectedEndDate!!, endTime = selectedEndTime!!,
            silenceNotifications = silenceNotificationsCheckbox.isChecked,
            notificationMinutes1 = selectedNotificationMinutes1,
            notificationMinutes2 = selectedNotificationMinutes2,
            notificationMinutes3 = selectedNotificationMinutes3,
            creationDate = actionItem?.creationDate ?: LocalDateTime.now(),
            lastModified = LocalDateTime.now(OZI),
            validatedItem = true,
            venue = userFriendlyLocationName,
            isDone = doneCheckbox.isChecked,
            startDateTimeMillis = startMillis,
            endDateTimeMillis = endMillis,
            creationTimezone = creationZone
        )

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val rowsUpdated = actionDao.updateAction(newAction)
                    // LOGGING ADDED: Log update result
                    Log.d("AddActionActivity", "Action update result: Rows updated = $rowsUpdated for Action ID: ${newAction.actionId}")
                    if (rowsUpdated == 0) {
                        Log.e("AddActionActivity", "Failed to update Action with ID: $actionId. Rows updated: $rowsUpdated")
                    } else {
                        Log.d("AddActionActivity", "Added row $newAction to database.")
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
                            eventType = "Action",
                            eventRefId = actionId
                        )

                        // Decide to insert or update based on whether the object has an ID
                        if (locationToPersist.locationId == null) {
                            locationDao.insertLocation(locationToPersist)
                        } else {
                            locationDao.updateLocation(locationToPersist)
                        }
                        Log.d("AddActionActivity", "Saved/Updated LocationEntry for Action ID: $actionId: $locationToPersist")
                    }  else {
                        if (initialLocationEntry != null) {
                            locationDao.deleteLocationsForEvent("Action", actionId)
                            Log.d("AddActionActivity", "Deleted LocationEntry for Action ID: $actionId as user-friendly name was blank.")
                        }
                    }


                    // Mark all attachments for this Action as validated and update/insert
                    val currentAttachments = attachmentAdapter.currentList
                    currentAttachments.forEach { attachment ->
                        val updatedAttachment = attachment.copy(
                            eventRefId = actionId,
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

                    // Mark all subtasks for this Action as validated and update/insert
                    val currentSubtasks = subtaskAdapter.currentList
                    currentSubtasks.forEach { subtask ->
                        val updatedSubtask = subtask.copy(
                            eventRefId = actionId,
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

                // MODIFIED: Corrected notification logic
                if (newAction.isDone || newAction.silenceNotifications) {
                    // Cancel notifications if the action is done OR if notifications are silenced
                    notificationHelper.cancelActionNotifications(newAction.actionId!!)
                } else {
                    // Otherwise, schedule them
                    notificationHelper.scheduleActionNotifications(newAction)
                }


                Toast.makeText(this@AddActionActivity, R.string.action_save_success, Toast.LENGTH_SHORT).show()
                // Update initial states after successful save
                initialActionItem = newAction.copy()
                initialAttachmentsLoaded = attachmentAdapter.currentList.map { it.copy(validatedItem = true) }.toList()
                initialSubtasksLoaded = subtaskAdapter.currentList.map { it.copy(validatedItem = true) }.toList()
                initialLocationEntry = actionLocationPickerView.getSavedLocation()?.copy()
                    ?: if (userFriendlyLocationName.isNotBlank()) {
                        // If no map location was picked but userFriendlyName was entered, create a placeholder Location for initial state tracking
                        Location(
                            locationId = null, // Will be assigned by DB if inserted
                            userFriendlyName = userFriendlyLocationName,
                            geocodedAddress = "",
                            latitude = 0.0,
                            longitude = 0.0,
                            eventType = "Action",
                            eventRefId = actionId
                        )
                    } else null

                finish()
            } catch (e: Exception) {
                Log.e("AddActionActivity", "Error saving Action: ${e.message}", e)
                Toast.makeText(this@AddActionActivity, getString(R.string.action_save_failure, e.localizedMessage), Toast.LENGTH_LONG).show()

                if (isNewAction) {
                    val currentAction = withContext(Dispatchers.IO) { actionDao.getActionById(actionId) }
                    if (currentAction != null && !currentAction.validatedItem) {
                        try {
                            attachmentDao.deleteUnvalidatedAttachmentsForEvent("Action", actionId)
                            subtaskDao.deleteUnvalidatedSubtasksForEvent("Action", actionId)
                            locationDao.deleteLocationsForEvent("Action", actionId)
                            notificationHelper.cancelActionNotifications(actionId)
                            actionDao.deleteAction(actionId)
                            Log.d("AddActionActivity", "Deleted unvalidated placeholder Action (ID: $actionId) and its unvalidated attachments/subtasks/location due to save failure.")
                        } catch (deleteEx: Exception) {
                            Log.e("AddActionActivity", "Error deleting placeholder Action or attachments/subtasks/location after save failure: ${deleteEx.message}", deleteEx)
                        }
                    }
                }
            }
        }
    }

    /**
     * Checks if there are any unsaved changes in the action, attachments, or subtasks.
     */
    private fun hasUnsavedChanges(): Boolean {
        val currentName = actionNameAutoCompleteTextView.text.toString().trim()
        val currentDescription = descriptionAutoCompleteTextView.text.toString().trim()
        val currentSilenceNotifications = silenceNotificationsCheckbox.isChecked
        val currentIsDone = doneCheckbox.isChecked
        val currentNotificationMinutes1 = selectedNotificationMinutes1
        val currentNotificationMinutes2 = selectedNotificationMinutes2
        val currentNotificationMinutes3 = selectedNotificationMinutes3
        val currentAttachments = attachmentAdapter.currentList
        val currentSubtasks = subtaskAdapter.currentList

        // Get current state from LocationPickerView
        val currentMapLocation = actionLocationPickerView.getSavedLocation()
        val currentUserFriendlyLocationName = actionLocationPickerView.getUserFriendlyName()


        val initial = initialActionItem

        if (initial == null) {
            Log.e("AddActionActivity", "hasUnsavedChanges: initialActionItem is null. Assuming unsaved changes.")
            return true
        }

//        Log.d("AddActionActivity", "--- hasUnsavedChanges (Action) ---")
//        Log.d("AddActionActivity", "Initial Action: $initial")
//        Log.d("AddActionActivity", "Current UI State:")
//        Log.d("AddActionActivity", "  Name: '$currentName' (Default: '${initial.actionName}')")
//        Log.d("AddActionActivity", "  Description: '$currentDescription' (Default: '${initial.description ?: ""}')")
//        Log.d("AddActionActivity", "  StartDate: $selectedStartDate (Default: ${initial.startDate})")
//        Log.d("AddActionActivity", "  StartTime: $selectedStartTime (Default: ${initial.startTime})")
//        Log.d("AddActionActivity", "  EndDate: $selectedEndDate (Default: ${initial.endDate})")
//        Log.d("AddActionActivity", "  EndTime: $selectedEndTime (Default: ${initial.endTime})")
//        Log.d("AddActionActivity", "  Notif1: $currentNotificationMinutes1 (Default: ${initial.notificationMinutes1})")
//        Log.d("AddActionActivity", "  Notif2: $currentNotificationMinutes2 (Default: ${initial.notificationMinutes2})")
//        Log.d("AddActionActivity", "  Notif3: $currentNotificationMinutes3 (Default: ${initial.notificationMinutes3})")
//        Log.d("AddActionActivity", "  SilenceNotifications: $currentSilenceNotifications (Default: ${initial.silenceNotifications})")
//        Log.d("AddActionActivity", "  IsDone: $currentIsDone (Default: ${initial.isDone})")
//        Log.d("AddActionActivity", "  Venue: '$currentUserFriendlyLocationName' (Default: '${initial.venue ?: ""}')")
//        Log.d("AddActionActivity", "  Attachments Empty: ${currentAttachments.isEmpty()}")
//        Log.d("AddActionActivity", "  Subtasks Empty: ${currentSubtasks.isEmpty()}")
//        Log.d("AddActionActivity", "------------------------------------------")

        val currentDueDateTimeMillis = if (selectedEndDate != null && selectedEndTime != null) {
            // MODIFIED: Use ZoneOffset.UTC for consistency
            LocalDateTime.of(selectedEndDate!!, selectedEndTime!!).atZone(ZoneOffset.UTC).toInstant().toEpochMilli()
        } else {
            0L // Or handle as appropriate if dates/times can be null
        }


        if (isNewAction) {
            // For new actions, compare against the default values set in createPlaceholderActionAndLoadRelatedData
            val defaultName = initial.actionName
            val defaultDescription = initial.description ?: ""
            val defaultStartDate = initial.startDate
            val defaultStartTime = initial.startTime
            val defaultEndDate = initial.endDate
            val defaultEndTime = initial.endTime
            val defaultStartDateTime = LocalDateTime.of(defaultStartDate, defaultStartTime)
            val defaultEndDateTime = LocalDateTime.of(defaultEndDate, defaultEndTime)
            val defaultSilenceNotifications = initial.silenceNotifications
            val defaultIsDone = initial.isDone
            val defaultNotificationMinutes1 = initial.notificationMinutes1
            val defaultNotificationMinutes2 = initial.notificationMinutes2
            val defaultNotificationMinutes3 = initial.notificationMinutes3
            val defaultStartMillis = defaultStartDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val defaultEndMillis = defaultEndDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val defaultVenue = initial.venue ?: ""


            if (currentName != defaultName ||
                currentDescription != defaultDescription ||
                selectedStartDate != defaultStartDate ||
                selectedStartTime != defaultStartTime ||
                selectedEndDate != defaultEndDate ||
                selectedEndTime != defaultEndTime ||
                currentSilenceNotifications != defaultSilenceNotifications ||
                currentIsDone != defaultIsDone ||
                currentNotificationMinutes1 != defaultNotificationMinutes1 ||
                currentNotificationMinutes2 != defaultNotificationMinutes2 ||
                currentNotificationMinutes3 != defaultNotificationMinutes3 ||
                currentUserFriendlyLocationName != defaultVenue ||
                currentAttachments.isNotEmpty() ||
                currentSubtasks.isNotEmpty() || actionLocationPickerView.hasUnsavedChanges(null, null)) {
                Log.d("AddActionActivity", "hasUnsavedChanges: TRUE (New Action - deviation from default or attachments/subtasks/location present)")
                return true
            } else {
                Log.d("AddActionActivity", "hasUnsavedChanges: FALSE (New Action - all fields at default, no attachments/subtasks/location)")
                return false
            }
        } else {
            // For existing actions, compare against the loaded initialActionItem
            if (currentName != initial.actionName ||
                currentDescription != (initial.description ?: "") ||
                selectedStartDate != initial.startDate ||
                selectedStartTime != initial.startTime ||
                selectedEndDate != initial.endDate ||
                selectedEndTime != initial.endTime ||
                currentSilenceNotifications != initial.silenceNotifications ||
                currentIsDone != initial.isDone ||
                currentNotificationMinutes1 != initial.notificationMinutes1 ||
                currentNotificationMinutes2 != initial.notificationMinutes2 ||
                currentNotificationMinutes3 != initial.notificationMinutes3
            ) {
                Log.d("AddActionActivity", "hasUnsavedChanges: TRUE (Existing Action - core fields changed)")
                return true
            }

            if (actionLocationPickerView.hasUnsavedChanges(initialLocationEntry?.userFriendlyName, initialLocationEntry)) {
                Log.d("AddActionActivity", "hasUnsavedChanges: TRUE (Location picker changes detected)")
                return true
            }

            // Check for changes in attachments
            val currentValidatedAttachments = currentAttachments.filter { it.validatedItem }
            val currentUnvalidatedAttachments = currentAttachments.filter { !it.validatedItem }

            if (currentUnvalidatedAttachments.isNotEmpty()) {
                Log.d("AddActionActivity", "hasUnsavedChanges: TRUE (Existing Action - unvalidated attachments present)")
                return true
            }

            if (initialAttachmentsLoaded.size != currentValidatedAttachments.size) {
                Log.d("AddActionActivity", "hasUnsavedChanges: TRUE (Existing Action - validated attachment count changed)")
                return true
            }


            val initialAttachmentSet = initialAttachmentsLoaded.map { it.uriString to it.displayName }.toSet()
            val currentValidatedAttachmentSet = currentValidatedAttachments.map { it.uriString to it.displayName }.toSet()

            if (initialAttachmentSet != currentValidatedAttachmentSet) {
                Log.d("AddActionActivity", "hasUnsavedChanges: TRUE (Existing Action - validated attachment content changed)")
                return true
            }

            // Check for changes in subtasks
            val currentValidatedSubtasks = currentSubtasks.filter { it.validatedItem }
            val currentUnvalidatedSubtasks = currentSubtasks.filter { !it.validatedItem }

            // Create maps for precise subtask comparison
            val initialSubtaskSet = initialSubtasksLoaded.map { it.subtaskId to Triple(it.description, it.completionState, it.position) }.toMap()
            val currentSubtaskSet = currentSubtasks.map { it.subtaskId to Triple(it.description, it.completionState, it.position) }.toMap()

            // Determine if there are any subtask changes
            val hasSubtaskChanges = (
                    currentUnvalidatedSubtasks.isNotEmpty() ||
                            initialSubtasksLoaded.size != currentValidatedSubtasks.size ||
                            initialSubtaskSet != currentSubtaskSet
                    )

            // If there are subtask changes, it's a change
            if (hasSubtaskChanges) {
                Log.d("AddActionActivity", "hasUnsavedChanges: TRUE (Subtask changes detected)")
                return true
            }

            Log.d("AddActionActivity", "hasUnsavedChanges: FALSE (Existing Action - no changes detected)")
            return false
        }
    }

    /**
     * Loads unique action names and descriptions from the database and sets them as
     * auto-suggestion adapters for the respective AutoCompleteTextViews.
     */
    private fun loadUniqueActionNamesAndDescriptions() {
        lifecycleScope.launch {
            try {
                val allActionsFromDb = withContext(Dispatchers.IO) {
                    actionDao.getAllActivities().firstOrNull() ?: emptyList()
                }

                val uniqueActionNames = allActionsFromDb
                    .map { it.actionName }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()

                val uniqueDescriptions = allActionsFromDb
                    .mapNotNull { it.description } // Use mapNotNull to filter out nulls
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()

                withContext(Dispatchers.Main) {
                    val nameAdapter = ArrayAdapter(
                        this@AddActionActivity,
                        android.R.layout.simple_dropdown_item_1line,
                        uniqueActionNames
                    )
                    actionNameAutoCompleteTextView.setAdapter(nameAdapter)
                    actionNameAutoCompleteTextView.threshold = 1 // Start suggesting after 1 character

                    val descriptionAdapter = ArrayAdapter(
                        this@AddActionActivity,
                        android.R.layout.simple_dropdown_item_1line,
                        uniqueDescriptions
                    )
                    descriptionAutoCompleteTextView.setAdapter(descriptionAdapter)
                    descriptionAutoCompleteTextView.threshold = 1 // Start suggesting after 1 character
                }
            } catch (e: Exception) {
                Log.e("AddActionActivity", "Error loading unique action names/descriptions: ${e.message}", e)
            }
        }
    }

    /**
     * Shows a DatePickerDialog for selecting either start or end date.
     * @param isStartDate True if selecting start date, false for end date.
     */
    private fun showDatePickerDialog(isStartDate: Boolean) {
        val calendar = Calendar.getInstance()
        val initialDate = if (isStartDate) selectedStartDate else selectedEndDate
        val year = initialDate?.year ?: calendar.get(Calendar.YEAR)
        val month = initialDate?.monthValue?.minus(1) ?: calendar.get(Calendar.MONTH)
        val day = initialDate?.dayOfMonth ?: calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDayOfMonth ->
            val newDate = LocalDate.of(selectedYear, selectedMonth + 1, selectedDayOfMonth)
            val now = LocalDateTime.now()

            if (isStartDate) {
                // Check if the selected start date is in the past
                if (selectedStartTime != null) { // Only check if time is also selected or default
                    val potentialStartDateTime = LocalDateTime.of(newDate, selectedStartTime!!)
//                    if (potentialStartDateTime.isBefore(now)) {
//                        Toast.makeText(this, R.string.action_start_date_past_error, Toast.LENGTH_SHORT).show()
//                        // startDateEditText.error = getString(R.string.action_start_date_past_error)
//                        startDateEditText.requestFocus()
//                        return@DatePickerDialog // Prevent selection if in the past
//                    } else {
//                        startDateEditText.error = null
//                    }
                }
//                else if (newDate.isBefore(now.toLocalDate())) { // If time is not yet selected, just check date
//                    Toast.makeText(this, R.string.action_start_date_past_error, Toast.LENGTH_SHORT).show()
//                    // startDateEditText.error = getString(R.string.action_start_date_past_error)
//                    startDateEditText.requestFocus()
//                    return@DatePickerDialog
//                } else {
//                    startDateEditText.error = null
//                }

                selectedStartDate = newDate
                startDateEditText.setText(selectedStartDate?.format(DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.getDefault())))

                // When start date changes, update the minimum date for the end date picker
                // and also update the end date/time based on the new start date and maintained duration.
                selectedEndDate = if (selectedEndDate == null || selectedEndDate!!.isBefore(newDate)) newDate else selectedEndDate
                updateEndDateTime(initialDurationMinutes) // Recalculate end date/time
                endDateEditText.setText(selectedEndDate?.format(DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.getDefault())))


            } else { // isEndDate
                // Ensure end date is not before start date
                selectedStartDate?.let { startDate ->
                    if (newDate.isBefore(startDate)) {
                        Toast.makeText(this, R.string.action_end_date_before_start_error, Toast.LENGTH_SHORT).show()
                        // endDateEditText.error = getString(R.string.action_end_date_before_start_error)
                        endDateEditText.requestFocus()
                        return@DatePickerDialog
                    } else {
                        endDateEditText.error = null
                    }
                }

                selectedEndDate = newDate
                endDateEditText.setText(selectedEndDate?.format(DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.getDefault())))
            }
        }, year, month, day)

        // Set the minimum date for the DatePicker
        val todayCalendar = Calendar.getInstance()
//        if (isStartDate) {
//            datePickerDialog.datePicker.minDate = todayCalendar.timeInMillis
//        } else { // For end date, min date is either today or selected start date
//            selectedStartDate?.let {
//                val startDateCalendar = Calendar.getInstance().apply {
//                    set(it.year, it.monthValue - 1, it.dayOfMonth)
//                }
//                datePickerDialog.datePicker.minDate = startDateCalendar.timeInMillis
//            } ?: run {
//                datePickerDialog.datePicker.minDate = todayCalendar.timeInMillis
//            }
//        }

        datePickerDialog.show()
    }

    /**
     * Shows a TimePickerDialog for selecting either start or end time.
     * @param isStartTime True if selecting start time, false for end time.
     */
    private fun showTimePickerDialog(isStartTime: Boolean) {
        val calendar = Calendar.getInstance()
        val initialTime = if (isStartTime) selectedStartTime else selectedEndTime
        val hour = initialTime?.hour ?: calendar.get(Calendar.HOUR_OF_DAY)
        val minute = initialTime?.minute ?: calendar.get(Calendar.MINUTE)

        val timePickerDialog = TimePickerDialog(this, { _, selectedHour, selectedMinute ->
            val newTime = LocalTime.of(selectedHour, selectedMinute)
            val now = LocalDateTime.now()

            if (isStartTime) {
                // Ensure selectedStartDate is not null before creating LocalDateTime
                selectedStartDate?.let { startDate ->
                    val potentialStartDateTime = LocalDateTime.of(startDate, newTime)
//                    if (potentialStartDateTime.isBefore(now)) {
//                        Toast.makeText(this, R.string.action_start_time_past_error, Toast.LENGTH_SHORT).show()
//                        startTimeEditText.error = getString(R.string.action_start_time_past_error)
//                        startTimeEditText.requestFocus()
//                        return@TimePickerDialog // Prevent selection if in the past
//                    } else {
//                        startTimeEditText.error = null
//                    }
                } ?: run {
                    // If selectedStartDate is null, it means date hasn't been picked yet or is invalid.
                    // For simplicity, we'll just let the date picker handle the full past check.
                }

                selectedStartTime = newTime
                startTimeEditText.setText(selectedStartTime?.format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())))

                // Always update end date/time based on the new start time and the maintained duration
                selectedStartDate?.let { startD ->
                    selectedStartTime?.let { startT ->
                        updateEndDateTime(initialDurationMinutes)
                    }
                }
            } else { // isEndTime
                // Ensure selectedEndDate is not null before creating LocalDateTime
                selectedEndDate?.let { endDate ->
                    selectedStartDate?.let { startDate ->
                        selectedStartTime?.let { startTime ->
                            val startDateTime = LocalDateTime.of(startDate, startTime)
                            val potentialEndDateTime = LocalDateTime.of(endDate, newTime)
                            if (potentialEndDateTime.isBefore(startDateTime)) {
                                Toast.makeText(this,
                                    R.string.action_end_time_before_start_error, Toast.LENGTH_SHORT).show()
                                endTimeEditText.error = getString(R.string.action_end_time_before_start_error)
                                endTimeEditText.requestFocus()
                                return@TimePickerDialog
                            } else {
                                endTimeEditText.error = null
                            }
                        }
                    }
                }
                selectedEndTime = newTime
                endTimeEditText.setText(selectedEndTime?.format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())))
            }
        }, hour, minute, true) // true for 24-hour format

        // If the selected date is today, set the minimum time to the current time
        val now = LocalTime.now()
        if (isStartTime) {
            selectedStartDate?.let { startDate ->
                if (startDate.isEqual(LocalDate.now())) {
                    timePickerDialog.updateTime(now.hour, now.minute)
                }
            }
        } else { // For end time
            selectedEndDate?.let { endDate ->
                selectedStartDate?.let { startDate ->
                    selectedStartTime?.let { startTime ->
                        if (endDate.isEqual(startDate)) { // If end date is same as start date
                            timePickerDialog.updateTime(startTime.hour, startTime.minute) // Minimum is start time
                        } else if (endDate.isEqual(LocalDate.now())) { // If end date is today (and not same as start date)
                            timePickerDialog.updateTime(now.hour, now.minute)
                        }
                    }
                }
            }
        }
        timePickerDialog.show()
    }

    /**
     * Shows a TimePickerDialog for selecting notification offset in hours and minutes.
     * @param notificationNumber Indicates which notification field to update (1, 2, or 3).
     */
    private fun showNotificationTimePickerDialog(notificationNumber: Int) {
        val initialMinutes = when (notificationNumber) {
            1 -> selectedNotificationMinutes1
            2 -> selectedNotificationMinutes2
            3 -> selectedNotificationMinutes3
            else -> 0
        } ?: 0
        val initialHours = initialMinutes / 60
        val initialRemainingMinutes = initialMinutes % 60

        TimePickerDialog(this, { _, selectedHour, selectedMinute ->
            val totalMinutes = selectedHour * 60 + selectedMinute
            when (notificationNumber) {
                1 -> {
                    selectedNotificationMinutes1 = totalMinutes
                    notificationMinutes1EditText.setText(formatMinutesToHoursMinutes(totalMinutes))
                }
                2 -> {
                    selectedNotificationMinutes2 = totalMinutes
                    notificationMinutes2EditText.setText(formatMinutesToHoursMinutes(totalMinutes))
                }
                3 -> {
                    selectedNotificationMinutes3 = totalMinutes
                    notificationMinutes3EditText.setText(formatMinutesToHoursMinutes(totalMinutes))
                }
            }
        }, initialHours, initialRemainingMinutes, true).show() // true for 24-hour format
    }

    /**
     * Helper function to format minutes into "Xh Ym" string.
     */
    private fun formatMinutesToHoursMinutes(totalMinutes: Int): String {
        if (totalMinutes < 0) return ""
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            minutes > 0 -> "${minutes}m"
            else -> "0m"
        }
    }

    /**
     * Updates the end date and time based on the start date, start time, and a given duration.
     */
    private fun updateEndDateTime(durationMinutes: Long) {
        selectedStartDate?.let { startDate ->
            selectedStartTime?.let { startTime ->
                val startDateTime = LocalDateTime.of(startDate, startTime)
                val endDateTime = startDateTime.plusMinutes(durationMinutes)
                selectedEndDate = endDateTime.toLocalDate()
                selectedEndTime = endDateTime.toLocalTime()
                endDateEditText.setText(selectedEndDate?.format(DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.getDefault())))
                endTimeEditText.setText(selectedEndTime?.format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())))
            }
        }
    }

    // --- AttachmentManagerCallbacks Implementation ---
    override fun onAttachmentsUpdated() {
        loadAttachmentsForAction()
    }

    override fun getCurrentEventRefId(): Long {
        return actionId
    }

    override fun getCurrentEventType(): String {
        return "Action"
    }

    override fun launchActivityForResult(intent: Intent, launcher: ActivityResultLauncher<Intent>) {
        launcher.launch(intent)
    }

    override fun launchUriActivityForResult(uri: Uri, launcher: ActivityResultLauncher<Uri>) {
        launcher.launch(uri)
    }

    override fun requestPermissions(permissions: Array<String>, launcher: ActivityResultLauncher<Array<String>>) {
        launcher.launch(permissions)
    }

    override fun requestPermission(permission: String, launcher: ActivityResultLauncher<String>) {
        launcher.launch(permission)
    }

    override fun showToast(messageResId: Int, duration: Int) {
        Toast.makeText(this, messageResId, duration).show()
    }

    override fun showToast(message: String, duration: Int) {
        Toast.makeText(this, message, duration).show()
    }

    override fun finishActivity() {
        finish()
    }

    override fun onAttachmentAdded(attachment: Attachment) {
        newlyAddedAttachments.add(attachment)
    }

    override fun onAttachmentCopyRequested() {
        Log.d("AddActionActivity", "Attachment copy process requested by AttachmentManager.")
    }

    override fun onAttachmentCopyCompleted() {
        Log.d("AddActionActivity", "Attachment copy process completed by AttachmentManager.")
    }


    // --- SubtaskManagerCallbacks Implementation ---
    override fun onSubtasksUpdated() {
        loadSubtasksForAction()
    }

    override fun onSubtaskCopyRequested() {
        Log.d("AddActionActivity", "Subtask copy process requested by SubtaskManager.")
    }

    override fun requestUiStateLogging(subtasks: List<Subtask>) {
        // 1. Log the expected state from SubtaskManager's perspective
        val expectedState = subtaskManager.calculateExpectedState(subtasks)
        Log.d("SubtaskManager_Expected", "Expected UI State (Manager's View):\\n$expectedState")

        // 2. Log the actual state as seen by the SubtaskAdapter
        // This should be called after submitList has completed its work and RecyclerView has laid out.
        // Using post ensures it runs on the next UI thread cycle after adapter updates.
        // recyclerViewSubtasks.post {
        //    subtaskAdapter.logCurrentAdapterState("After Data Update")
        // }
    }

    /**
     * Loads attachments for the current action from the database.
     */
    private fun loadAttachmentsForAction() {
        if (actionId != -1L) {
            lifecycleScope.launch {
                attachmentDao.getAttachmentsForEvent("Action", actionId).collectLatest { attachments ->
                    val attachmentsToShow = attachments
                    Log.d("AddActionActivity", "Attachments loaded for actionId $actionId. attachmentsToShow size: ${attachmentsToShow.size}. IsNewAction: $isNewAction")
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
     * Loads subtasks for the current action from the database.
     */
    private fun loadSubtasksForAction() {
        if (actionId != -1L) {
            lifecycleScope.launch {
                subtaskDao.getSubtasksForEvent("Action", actionId).collectLatest { subtasks ->
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
}
