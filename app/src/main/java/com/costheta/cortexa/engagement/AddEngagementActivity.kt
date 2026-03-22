package com.costheta.cortexa.engagement

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.Toast
import android.widget.TextView
import android.widget.CheckBox
import android.widget.GridLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.costheta.cortexa.data.db.AppDatabase
import com.costheta.cortexa.data.db.dao.EngagementDao
import com.costheta.cortexa.data.db.dao.AttachmentDao
import com.costheta.cortexa.data.models.Engagement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collectLatest
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import android.widget.NumberPicker
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher
import androidx.core.view.children
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.costheta.cortexa.NotificationHelper
import com.costheta.cortexa.R
import com.costheta.cortexa.data.models.Attachment
import com.costheta.cortexa.util.attachment.AttachmentAdapter
import com.costheta.cortexa.util.attachment.AttachmentManager
import com.costheta.cortexa.util.attachment.AttachmentManagerCallbacks
import com.costheta.cortexa.data.db.dao.SubtaskDao
import com.costheta.cortexa.data.models.Subtask
import com.costheta.cortexa.util.subtask.SubtaskAdapter
import com.costheta.cortexa.util.subtask.SubtaskManager
import com.costheta.cortexa.util.subtask.SubtaskManagerCallbacks
import com.google.android.material.textfield.TextInputEditText
import java.time.LocalDateTime
// NEW - Import ZoneId for timezone handling
import java.time.ZoneId
import java.util.Calendar

import com.costheta.cortexa.data.db.dao.LocationDao
import com.costheta.cortexa.data.models.Location
import com.costheta.cortexa.util.keyboard.AutoCompleteDisappearingKeyboard
import com.costheta.cortexa.util.keyboard.DisappearingKeyboard
import com.costheta.cortexa.util.location.LocationPickerView
import com.costheta.cortexa.util.location.MapPickerActivity
import com.costheta.cortexa.util.subtask.ItemMoveCallback
import kotlinx.coroutines.flow.firstOrNull


class AddEngagementActivity : AppCompatActivity(), AttachmentManagerCallbacks, SubtaskManagerCallbacks {

    // UI elements
    private lateinit var engagementNameAutoCompleteEditText: AutoCompleteTextView
    private lateinit var notesAutoCompleteEditText: AutoCompleteTextView

    private lateinit var daySelectionRadioGroup: RadioGroup
    private lateinit var layoutSelectDays: LinearLayout
    private lateinit var daysCheckboxGrid: GridLayout
    private lateinit var layoutRecurDays: LinearLayout
    private lateinit var recurrenceNumberPicker: NumberPicker
    private lateinit var startTimeEditText: TextInputEditText
    private lateinit var durationEditText: TextInputEditText
    private lateinit var engagementStartDateEditText: TextInputEditText
    private lateinit var engagementEndDateEditText: TextInputEditText
    private lateinit var silenceNotificationsCheckbox: CheckBox
    private lateinit var saveEngagementButton: Button
    private lateinit var addEngagementTitle: TextView

    private lateinit var notification1EditText: TextInputEditText
    private lateinit var notification2EditText: TextInputEditText
    private lateinit var notification3EditText: TextInputEditText

    private lateinit var engagementLocationPickerView: LocationPickerView

    // NEW - UI Elements for timezone display
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
    private lateinit var editTextAddSubtask: EditText
    private lateinit var buttonAddSubtask: Button
    private lateinit var buttonCopySubtasks: Button
    private lateinit var recyclerViewSubtasks: RecyclerView
    private lateinit var noSubtasksFoundTextView: TextView
    private lateinit var subtaskAdapter: SubtaskAdapter


    private lateinit var engagementDao: EngagementDao
    private lateinit var attachmentDao: AttachmentDao
    private lateinit var subtaskDao: SubtaskDao
    private lateinit var locationDao: LocationDao
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var prefs: SharedPreferences

    // Variables to hold selected values
    private var selectedStartTime: LocalTime? = null
    private var selectedDurationMinutes: Int = 60
    private var selectedStartDate: LocalDate? = null
    private var selectedEndDate: LocalDate? = null
    private var selectedDaysOfWeek: MutableSet<DayOfWeek> = mutableSetOf()
    private var recurrenceIntervalDays: Int? = null
    private var daySelectionType: String = "SPECIFIC_DAYS"

    private var notification1Minutes: Int? = null
    private var notification2Minutes: Int? = null
    private var notification3Minutes: Int? = null

    private var currentEngagementId: Long = -1L
    private var initialEngagement: Engagement? = null
    private var initialAttachmentsLoaded: List<Attachment> = emptyList()
    private var initialSubtasksLoaded: List<Subtask> = emptyList()
    private var initialLocationEntry: Location? = null
    private val newlyAddedAttachments = mutableListOf<Attachment>()

    private var isNewEngagement: Boolean = false


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
                val locationId = intent.getLongExtra(MapPickerActivity.EXTRA_LOCATION_ID, -1L).takeIf { it != -1L }
                val userFriendlyName = intent.getStringExtra(MapPickerActivity.EXTRA_USER_FRIENDLY_NAME)
                val geocodedAddress = intent.getStringExtra(MapPickerActivity.EXTRA_GEOCODED_ADDRESS)
                val latitude = intent.getDoubleExtra(MapPickerActivity.EXTRA_LATITUDE, 0.0)
                val longitude = intent.getDoubleExtra(MapPickerActivity.EXTRA_LONGITUDE, 0.0)
                val eventType = intent.getStringExtra(MapPickerActivity.EXTRA_EVENT_TYPE) ?: ""
                val eventRefId = intent.getLongExtra(MapPickerActivity.EXTRA_EVENT_REF_ID, -1L)

                if (latitude != 0.0 || longitude != 0.0 || !geocodedAddress.isNullOrBlank()) {
                    val selectedLocation = Location(
                        locationId = locationId,
                        userFriendlyName = userFriendlyName ?: "",
                        geocodedAddress = geocodedAddress ?: "",
                        latitude = latitude,
                        longitude = longitude,
                        eventType = eventType,
                        eventRefId = eventRefId
                    )
                    engagementLocationPickerView.handleMapPickerResult(selectedLocation)
                    Log.d("AddEngagementActivity", "MapPicker result: $selectedLocation")
                } else {
                    engagementLocationPickerView.handleMapPickerResult(null)
                    Log.e("AddEngagementActivity", "MapPicker result data incomplete or cleared.")
                    Toast.makeText(this, R.string.map_picker_result_error, Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Log.d("AddEngagementActivity", "MapPicker cancelled or failed.")
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.engagement_activity_add)

        prefs = getSharedPreferences("user_preferences", Context.MODE_PRIVATE)
        val db = AppDatabase.getDatabase(applicationContext)
        engagementDao = db.engagementDao()
        attachmentDao = db.attachmentDao()
        subtaskDao = db.subtaskDao()
        locationDao = db.locationDao()
        notificationHelper = NotificationHelper(applicationContext)

        notification1Minutes = prefs.getInt("pref_engagement_notif1", 60)
        notification2Minutes = prefs.getInt("pref_engagement_notif2", 30)
        notification3Minutes = prefs.getInt("pref_engagement_notif3", 10)


        // Initialize UI elements
        addEngagementTitle = findViewById(R.id.addEngagementTitle)
        engagementNameAutoCompleteEditText = findViewById(R.id.engagementAutoCompleteNameEditText)
        notesAutoCompleteEditText = findViewById(R.id.notesAutoCompleteEditText)

        AutoCompleteDisappearingKeyboard.getInstance().register(engagementNameAutoCompleteEditText)
        AutoCompleteDisappearingKeyboard.getInstance().register(notesAutoCompleteEditText)

        daySelectionRadioGroup = findViewById(R.id.daySelectionRadioGroup)
        layoutSelectDays = findViewById(R.id.layoutSelectDays)
        daysCheckboxGrid = findViewById(R.id.daysCheckboxGrid)
        layoutRecurDays = findViewById(R.id.layoutRecurDays)
        recurrenceNumberPicker = findViewById(R.id.recurrenceNumberPicker)
        startTimeEditText = findViewById(R.id.startTimeEditText)
        durationEditText = findViewById(R.id.durationEditText)
        engagementStartDateEditText = findViewById(R.id.engagementStartDateEditText)
        engagementEndDateEditText = findViewById(R.id.engagementEndDateEditText)
        silenceNotificationsCheckbox = findViewById(R.id.silenceNotificationsCheckbox)
        saveEngagementButton = findViewById(R.id.saveEngagementButton)
        notification1EditText = findViewById(R.id.notification1EditText)
        notification2EditText = findViewById(R.id.notification2EditText)
        notification3EditText = findViewById(R.id.notification3EditText)
        engagementLocationPickerView = findViewById(R.id.engagementLocationPickerView)

        // NEW - Initialize timezone UI elements
        timezoneInfoLayout = findViewById(R.id.timezoneInfoLayout)
        currentTimezoneTextView = findViewById(R.id.currentTimezoneTextView)
        originalTimezoneTextView = findViewById(R.id.originalTimezoneTextView)


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

        // Setup RecyclerViews
        attachmentAdapter = AttachmentAdapter(
            onDeleteClick = { attachment -> attachmentManager.showDeleteAttachmentDialog(attachment) },
            onItemClick = { attachment -> attachmentManager.viewAttachment(attachment) }
        )
        recyclerViewAttachments.apply {
            layoutManager = LinearLayoutManager(this@AddEngagementActivity)
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
            layoutManager = LinearLayoutManager(this@AddEngagementActivity)
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


        // Set up click listeners
        startTimeEditText.setOnClickListener { showTimePickerDialog() }
        durationEditText.setOnClickListener { showDurationPicker() }
        engagementStartDateEditText.setOnClickListener { showDatePicker(true) }
        engagementEndDateEditText.setOnClickListener { showDatePicker(false) }
        saveEngagementButton.setOnClickListener { saveEngagement() }
        notification1EditText.setOnClickListener { showNotificationTimePicker(1) }
        notification2EditText.setOnClickListener { showNotificationTimePicker(2) }
        notification3EditText.setOnClickListener { showNotificationTimePicker(3) }
        buttonPickFile.setOnClickListener { attachmentManager.checkAndRequestMediaPermissionsForPicker() }
        buttonTakePhotoVideo.setOnClickListener { attachmentManager.checkAndRequestCameraPermission() }
        buttonRecordAudio.setOnClickListener { attachmentManager.launchRecordAudioActivity() }
        buttonAddHyperlink.setOnClickListener { attachmentManager.showAddHyperlinkDialog() }
        buttonShareAttachments.setOnClickListener { attachmentManager.shareAttachments(attachmentAdapter.currentList) }
        buttonCopyAttachments.setOnClickListener { attachmentManager.startCopyAttachmentsProcess() }
        buttonAddSubtask.setOnClickListener {
            subtaskManager.addSubtasksFromInput(editTextAddSubtask.text.toString())
            editTextAddSubtask.text?.clear()
        }
        buttonCopySubtasks.setOnClickListener { subtaskManager.startCopySubtasksProcess() }


        setupDaySelectionRadioGroup()
        setupDaysCheckboxes()
        setupRecurrencePicker()
        loadAutofillSuggestions()

        // Check if we are in edit mode
        val intentEngagementId = intent.getLongExtra("ENGAGEMENT_ID", -1L)
        if (intentEngagementId != -1L) {
            currentEngagementId = intentEngagementId
            isNewEngagement = false
            addEngagementTitle.text = getString(R.string.edit_engagement_title)
            saveEngagementButton.text = getString(R.string.update_engagement_button_text)
            lifecycleScope.launch {
                val engagement = engagementDao.getEngagementById(currentEngagementId)
                engagement?.let {
                    initialEngagement = it.copy()
                    loadEngagementData(it)
                }
                engagementLocationPickerView.initialize(
                    eventType = "Engagement",
                    eventRefId = currentEngagementId,
                    lifecycleScope = lifecycleScope,
                    mapPickerLauncher = mapPickerLauncher
                )
                locationDao.getLocationForEvent("Engagement", currentEngagementId).firstOrNull()?.let {
                    initialLocationEntry = it.copy()
                    engagementLocationPickerView.setCurrentSavedLocation(it)
                    engagementLocationPickerView.setUserFriendlyName(engagement?.venue)
                } ?: run {
                    initialLocationEntry = null
                    engagementLocationPickerView.clearLocation()
                    engagementLocationPickerView.setUserFriendlyName(engagement?.venue)
                }
                loadAttachmentsForEngagement()
                loadSubtasksForEngagement()
            }
        } else {
            isNewEngagement = true
            addEngagementTitle.text = getString(R.string.add_engagement_title)
            saveEngagementButton.text = getString(R.string.save_engagement_button_text)

            val now = LocalTime.now()
            val nextHour = now.plusHours(1)

            selectedStartTime = if (nextHour.isBefore(LocalTime.of(6, 0)) || nextHour.isAfter(LocalTime.of(23, 0))) {
                LocalTime.of(6, 0)
            } else {
                LocalTime.of(nextHour.hour, 0)
            }
            startTimeEditText.setText(selectedStartTime?.format(DateTimeFormatter.ofPattern("HH:mm")))

            selectedDurationMinutes = 60
            durationEditText.setText(getString(R.string.duration_minutes_format, selectedDurationMinutes))

            selectedStartDate = LocalDate.now()
            engagementStartDateEditText.setText(selectedStartDate?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
            selectedEndDate = LocalDate.now().plusMonths(1)
            engagementEndDateEditText.setText(selectedEndDate?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))

            notification1EditText.setText(formatMinutes(notification1Minutes))
            notification2EditText.setText(formatMinutes(notification2Minutes))
            notification3EditText.setText(formatMinutes(notification3Minutes))

            selectedDaysOfWeek.add(LocalDate.now().dayOfWeek)
            updateDaysCheckboxes()
            // NEW - Update timezone display for new engagement
            updateTimezoneDisplay()

            lifecycleScope.launch {
                createPlaceholderEngagementAndLoadRelatedData()
            }
        }

        // Handle back press
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (hasUnsavedChanges()) {
                    val dialogMessage = if (isNewEngagement) {
                        getString(R.string.exit_without_saving_engagement_message)
                    } else {
                        getString(R.string.exit_without_saving_changes_engagement_message)
                    }

                    AlertDialog.Builder(this@AddEngagementActivity)
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

                                        if (isNewEngagement) {
                                            // In ADD mode, delete the placeholder and its remaining children.
                                            val currentEngagement = engagementDao.getEngagementById(currentEngagementId)
                                            if (currentEngagement != null && !currentEngagement.validatedItem) {
                                                subtaskDao.deleteUnvalidatedSubtasksForEvent("Engagement", currentEngagementId)
                                                locationDao.deleteLocationsForEvent("Engagement", currentEngagementId)
                                                notificationHelper.cancelEngagementNotifications(currentEngagementId)
                                                engagementDao.deleteEngagement(currentEngagementId)
                                                Log.d("AddEngagementActivity", "Deleted placeholder Engagement (ID: $currentEngagementId) and related data.")
                                            }
                                        } else {
                                            // In EDIT mode, only clean up unvalidated children and revert location.
                                            attachmentDao.deleteUnvalidatedAttachmentsForEvent("Engagement", currentEngagementId)
                                            subtaskDao.deleteUnvalidatedSubtasksForEvent("Engagement", currentEngagementId)

                                            // Simplified and safer location revert logic.
                                            if (initialLocationEntry != null) {
                                                // If a location existed initially, restore it.
                                                locationDao.updateLocation(initialLocationEntry!!)
                                            } else {
                                                // If no location existed initially, delete any that was added.
                                                locationDao.deleteLocationsForEvent("Engagement", currentEngagementId)
                                            }
                                            Log.d("AddEngagementActivity", "Cleaned up unvalidated items for existing Engagement (ID: $currentEngagementId).")
                                        }
                                    } catch (e: Exception) {
                                        Log.e("AddEngagementActivity", "Error during cleanup on back press: ${e.message}", e)
                                    }
                                }
                                this@AddEngagementActivity.finish()
                            }
                        }
                        .setNegativeButton(getString(R.string.dialog_cancel_button).uppercase(Locale.getDefault())) { dialog, _ ->
                            dialog.cancel()
                        }
                        .show()
                } else {
                    this@AddEngagementActivity.finish()
                }
            }
        })
    }

    private fun loadAutofillSuggestions() {
        lifecycleScope.launch {
            try {
                // Fetch all engagements from the database in a background thread
                val allEngagements = withContext(Dispatchers.IO) {
                    engagementDao.getAllEngagements()
                }

                // Create a unique, sorted list of non-blank engagement names
                val uniqueNames = allEngagements
                    .map { it.engagementName }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()

                // Create a unique, sorted list of non-blank notes
                val uniqueNotes = allEngagements
                    .mapNotNull { it.notes } // Use mapNotNull to safely handle nullable notes
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()

                // Switch back to the main thread to update the UI
                withContext(Dispatchers.Main) {
                    // Create an adapter for engagement names and set it
                    val nameAdapter = ArrayAdapter(
                        this@AddEngagementActivity,
                        android.R.layout.simple_dropdown_item_1line,
                        uniqueNames
                    )
                    engagementNameAutoCompleteEditText.setAdapter(nameAdapter)
                    engagementNameAutoCompleteEditText.threshold = 1 // Show suggestions after 1 character

                    // Create an adapter for notes and set it
                    val notesAdapter = ArrayAdapter(
                        this@AddEngagementActivity,
                        android.R.layout.simple_dropdown_item_1line,
                        uniqueNotes
                    )
                    notesAutoCompleteEditText.setAdapter(notesAdapter)
                    notesAutoCompleteEditText.threshold = 1 // Show suggestions after 1 character
                }
            } catch (e: Exception) {
                Log.e("AddEngagementActivity", "Error loading autofill suggestions: ${e.message}", e)
            }
        }
    }

    private fun formatMinutes(minutes: Int?): String {
        return if (minutes != null) {
            getString(R.string.duration_minutes_format, minutes)
        } else {
            ""
        }
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
     * Creates a placeholder Engagement item in the database to get a valid ID for attachments and subtasks.
     */
    private suspend fun createPlaceholderEngagementAndLoadRelatedData() {
        withContext(Dispatchers.IO) {
            try {
                val placeholderEndDateTime = LocalDateTime.of(
                    selectedEndDate ?: LocalDate.now().plusMonths(1),
                    (selectedStartTime ?: LocalTime.of(9,0)).plusMinutes(selectedDurationMinutes.toLong())
                )
                // NEW - Use system default timezone for correct millis calculation
                val placeholderEndDateTimeMillis = placeholderEndDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

                val placeholderEngagement = Engagement(
                    engagementId = null,
                    engagementName = "",
                    startTime = selectedStartTime ?: LocalTime.of(9, 0),
                    durationMinutes = selectedDurationMinutes,
                    validityStartDate = selectedStartDate ?: LocalDate.now(),
                    validityEndDate = selectedEndDate ?: LocalDate.now().plusMonths(1),
                    daySelectionType = "SPECIFIC_DAYS",
                    selectedDaysOfWeek = LocalDate.now().dayOfWeek.name,
                    recurrenceIntervalDays = null,
                    notes = null,
                    isSilenced = false,
                    venue = "",
                    validatedItem = false,
                    endDateTimeInMillis = placeholderEndDateTimeMillis,
                    // NEW - Add creationTimezone to placeholder
                    creationTimezone = ZoneId.systemDefault().id,
                    notification1Minutes = notification1Minutes!!,
                    notification2Minutes = notification2Minutes!!,
                    notification3Minutes = notification3Minutes!!
                )

                currentEngagementId = engagementDao.insertEngagement(placeholderEngagement)
                withContext(Dispatchers.Main) {
                    Log.d("AddEngagementActivity", "Placeholder Engagement created with ID: $currentEngagementId")
                    initialEngagement = placeholderEngagement.copy(engagementId = currentEngagementId)
                    loadAttachmentsForEngagement()
                    loadSubtasksForEngagement()
                    engagementLocationPickerView.initialize(
                        eventType = "Engagement",
                        eventRefId = currentEngagementId,
                        lifecycleScope = lifecycleScope,
                        mapPickerLauncher = mapPickerLauncher
                    )
                    initialLocationEntry = null
                }
            } catch (e: Exception) {
                Log.e("AddEngagementActivity", "Error creating placeholder Engagement: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AddEngagementActivity, "Error initializing Engagement. Please restart.", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    private fun loadEngagementData(engagement: Engagement) {
        engagementNameAutoCompleteEditText.setText(engagement.engagementName)

        if (engagement.daySelectionType == "RECURRENCE") {
            findViewById<RadioButton>(R.id.radioRecur).isChecked = true
            layoutSelectDays.visibility = View.GONE
            layoutRecurDays.visibility = View.VISIBLE
            engagement.recurrenceIntervalDays?.let {
                recurrenceNumberPicker.value = it
            }
        } else {
            findViewById<RadioButton>(R.id.radioSelectDays).isChecked = true
            layoutSelectDays.visibility = View.VISIBLE
            layoutRecurDays.visibility = View.GONE
            engagement.selectedDaysOfWeek?.split(",")?.mapNotNull { dayString ->
                try {
                    DayOfWeek.valueOf(dayString.trim().uppercase(Locale.getDefault()))
                } catch (e: IllegalArgumentException) {
                    null
                }
            }?.let { days ->
                selectedDaysOfWeek.clear()
                selectedDaysOfWeek.addAll(days)
                updateDaysCheckboxes()
            }
        }

        selectedStartTime = engagement.startTime
        startTimeEditText.setText(selectedStartTime?.format(DateTimeFormatter.ofPattern("HH:mm")))

        selectedDurationMinutes = engagement.durationMinutes
        durationEditText.setText(getString(R.string.duration_minutes_format, engagement.durationMinutes))

        selectedStartDate = engagement.validityStartDate
        engagementStartDateEditText.setText(engagement.validityStartDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))

        selectedEndDate = engagement.validityEndDate
        engagementEndDateEditText.setText(engagement.validityEndDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))

        silenceNotificationsCheckbox.isChecked = engagement.isSilenced
        notesAutoCompleteEditText.setText(engagement.notes)

        notification1Minutes = engagement.notification1Minutes
        notification2Minutes = engagement.notification2Minutes
        notification3Minutes = engagement.notification3Minutes
        notification1EditText.setText(formatMinutes(notification1Minutes))
        notification2EditText.setText(formatMinutes(notification2Minutes))
        notification3EditText.setText(formatMinutes(notification3Minutes))

        // NEW - Update the timezone display based on loaded data
        updateTimezoneDisplay(engagement)


        lifecycleScope.launch {
            locationDao.getLocationForEvent("Engagement", engagement.engagementId!!).firstOrNull()?.let {
                initialLocationEntry = it.copy()
                engagementLocationPickerView.setCurrentSavedLocation(it)
                engagementLocationPickerView.setUserFriendlyName(engagement.venue)
            } ?: run {
                initialLocationEntry = null
                engagementLocationPickerView.clearLocation()
                engagementLocationPickerView.setUserFriendlyName(engagement.venue)
            }
        }
    }

    private fun setupDaySelectionRadioGroup() {
        daySelectionRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radioSelectDays -> {
                    daySelectionType = "SPECIFIC_DAYS"
                    layoutSelectDays.visibility = View.VISIBLE
                    layoutRecurDays.visibility = View.GONE
                    if (selectedDaysOfWeek.isEmpty() && isNewEngagement) {
                        selectedDaysOfWeek.add(LocalDate.now().dayOfWeek)
                        updateDaysCheckboxes()
                    } else if (selectedDaysOfWeek.isNotEmpty()) {
                        updateDaysCheckboxes()
                    }
                }
                R.id.radioRecur -> {
                    daySelectionType = "RECURRENCE"
                    layoutSelectDays.visibility = View.GONE
                    layoutRecurDays.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun setupDaysCheckboxes() {
        daysCheckboxGrid.removeAllViews()
        DayOfWeek.entries.forEach { dayOfWeek ->
            val checkBox = CheckBox(this).apply {
                text = dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())
                isChecked = selectedDaysOfWeek.contains(dayOfWeek)
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedDaysOfWeek.add(dayOfWeek)
                    } else {
                        selectedDaysOfWeek.remove(dayOfWeek)
                    }
                }
            }
            daysCheckboxGrid.addView(checkBox)
        }
    }

    private fun updateDaysCheckboxes() {
        daysCheckboxGrid.children.forEach { view ->
            if (view is CheckBox) {
                val dayText = view.text.toString().uppercase(Locale.getDefault())
                try {
                    val dayOfWeek = DayOfWeek.valueOf(dayText)
                    view.isChecked = selectedDaysOfWeek.contains(dayOfWeek)
                } catch (e: IllegalArgumentException) {
                    Log.e("AddEngagementActivity", "Invalid DayOfWeek string: $dayText")
                }
            }
        }
    }

    private fun setupRecurrencePicker() {
        recurrenceNumberPicker.minValue = 1
        recurrenceNumberPicker.maxValue = 30
        recurrenceNumberPicker.value = 1
        recurrenceNumberPicker.setOnValueChangedListener { _, _, newVal ->
            recurrenceIntervalDays = newVal
        }
    }

    private fun showTimePickerDialog() {
        val initialHour = selectedStartTime?.hour ?: 9
        val initialMinute = selectedStartTime?.minute ?: 0

        TimePickerDialog(this, { _, hourOfDay, minute ->
            val newTime = LocalTime.of(hourOfDay, minute)
            val currentDateTime = LocalDateTime.now()

            selectedStartDate?.let { startDate ->
                if (startDate.isEqual(LocalDate.now())) {
                }
            }

            val potentialEndDateTime = LocalDateTime.of(selectedEndDate ?: LocalDate.now(), newTime.plusMinutes(selectedDurationMinutes.toLong()))
            val actualStartDateTime = LocalDateTime.of(selectedStartDate ?: LocalDate.now(), newTime)
            if (potentialEndDateTime.isBefore(actualStartDateTime)) {
                Toast.makeText(this, getString(R.string.engagement_end_time_before_start_time_error), Toast.LENGTH_SHORT).show()
                startTimeEditText.error = getString(R.string.engagement_end_time_before_start_time_error)
                startTimeEditText.requestFocus()
                return@TimePickerDialog
            }


            selectedStartTime = newTime
            startTimeEditText.setText(selectedStartTime?.format(DateTimeFormatter.ofPattern("HH:mm")))
            startTimeEditText.error = null
            // NEW - Update timezone display when time changes
            updateTimezoneDisplay()
        }, initialHour, initialMinute, true).show()
    }

    private fun showDurationPicker() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.engagement_duration_picker_dialog, null)
        val hoursPicker: NumberPicker = dialogView.findViewById(R.id.hourPicker)
        val minutesPicker: NumberPicker = dialogView.findViewById(R.id.minutePicker)

        val currentHours = selectedDurationMinutes / 60
        val currentMinutes = selectedDurationMinutes % 60

        hoursPicker.minValue = 0
        hoursPicker.maxValue = 23
        hoursPicker.value = currentHours

        minutesPicker.minValue = 0
        minutesPicker.maxValue = 59
        minutesPicker.value = currentMinutes

        AlertDialog.Builder(this)
            .setTitle(R.string.select_duration_title)
            .setView(dialogView)
            .setPositiveButton(R.string.dialog_ok_button) { dialog, _ ->
                val newDurationMinutes = hoursPicker.value * 60 + minutesPicker.value
                if (newDurationMinutes <= 0) {
                    Toast.makeText(this, getString(R.string.engagement_duration_zero_error), Toast.LENGTH_SHORT).show()
                    durationEditText.error = getString(R.string.engagement_duration_zero_error)
                    durationEditText.requestFocus()
                    return@setPositiveButton
                }

                selectedDurationMinutes = newDurationMinutes
                durationEditText.setText(getString(R.string.duration_minutes_format, selectedDurationMinutes))
                durationEditText.error = null
                dialog.dismiss()
            }
            .setNegativeButton(R.string.dialog_cancel_button) { dialog, _ ->
                dialog.cancel()
            }
            .show()
    }

    private fun showNotificationTimePicker(notificationNumber: Int) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.engagement_duration_picker_dialog, null)
        val hoursPicker: NumberPicker = dialogView.findViewById(R.id.hourPicker)
        val minutesPicker: NumberPicker = dialogView.findViewById(R.id.minutePicker)

        val currentMinutesValue = when (notificationNumber) {
            1 -> notification1Minutes
            2 -> notification2Minutes
            3 -> notification3Minutes
            else -> 0
        }

        val currentHours = currentMinutesValue?.div(60)
        val currentMinutes = currentMinutesValue?.rem(60)

        hoursPicker.minValue = 0
        hoursPicker.maxValue = 23 * 60 / 60
        if (currentHours != null) {
            hoursPicker.value = currentHours
        }

        minutesPicker.minValue = 0
        minutesPicker.maxValue = 59
        if (currentMinutes != null) {
            minutesPicker.value = currentMinutes
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.select_notification_time_title)
            .setView(dialogView)
            .setPositiveButton(R.string.dialog_ok_button) { dialog, _ ->
                val newNotificationMinutes = hoursPicker.value * 60 + minutesPicker.value

                if (newNotificationMinutes < 0) {
                    Toast.makeText(this, getString(R.string.engagement_notification_time_invalid_error), Toast.LENGTH_SHORT).show()
                    when (notificationNumber) {
                        1 -> notification1EditText.error = getString(R.string.engagement_notification_time_invalid_error)
                        2 -> notification2EditText.error = getString(R.string.engagement_notification_time_invalid_error)
                        3 -> notification3EditText.error = getString(R.string.engagement_notification_time_invalid_error)
                    }
                    when (notificationNumber) {
                        1 -> notification1EditText.requestFocus()
                        2 -> notification2EditText.requestFocus()
                        3 -> notification3EditText.requestFocus()
                    }
                    return@setPositiveButton
                }

                when (notificationNumber) {
                    1 -> {
                        notification1Minutes = newNotificationMinutes
                        notification1EditText.setText(getString(R.string.duration_minutes_format, notification1Minutes))
                        notification1EditText.error = null
                    }
                    2 -> {
                        notification2Minutes = newNotificationMinutes
                        notification2EditText.setText(getString(R.string.duration_minutes_format, notification2Minutes))
                        notification2EditText.error = null
                    }
                    3 -> {
                        notification3Minutes = newNotificationMinutes
                        notification3EditText.setText(getString(R.string.duration_minutes_format, notification3Minutes))
                        notification3EditText.error = null
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.dialog_cancel_button) { dialog, _ ->
                dialog.cancel()
            }
            .show()
    }


    private fun showDatePicker(isStartDate: Boolean) {
        val initialDate = if (isStartDate) selectedStartDate ?: LocalDate.now() else selectedEndDate ?: LocalDate.now().plusMonths(1)
        val year = initialDate.year
        val month = initialDate.monthValue - 1
        val day = initialDate.dayOfMonth

        val datePickerDialog = DatePickerDialog(this, { _, y, m, d ->
            val newDate = LocalDate.of(y, m + 1, d)
            if (isStartDate) {
                selectedStartDate = newDate
                engagementStartDateEditText.setText(selectedStartDate?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                engagementStartDateEditText.error = null

                if (selectedEndDate != null && selectedEndDate!!.isBefore(selectedStartDate)) {
                    selectedEndDate = selectedStartDate
                    engagementEndDateEditText.setText(selectedEndDate?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                }
            } else {
                selectedEndDate = newDate
                engagementEndDateEditText.setText(selectedEndDate?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                engagementEndDateEditText.error = null

                if (selectedStartDate != null && selectedStartDate!!.isAfter(selectedEndDate)) {
                    selectedStartDate = selectedEndDate
                    engagementStartDateEditText.setText(selectedStartDate?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                }
            }
            // NEW - Update timezone display when date changes
            updateTimezoneDisplay()
        }, year, month, day)

        if (isStartDate) {
            val todayCalendar = Calendar.getInstance()
        } else {
            val minDateForEndDate = selectedStartDate ?: LocalDate.now()
            val calendar = Calendar.getInstance()
            calendar.set(minDateForEndDate.year, minDateForEndDate.monthValue - 1, minDateForEndDate.dayOfMonth)
        }
        datePickerDialog.show()
    }

    // NEW - This entire method is new to handle timezone display logic
    /**
     * Updates the timezone TextViews based on the engagement's creation timezone and the device's current timezone.
     * @param engagement The engagement being edited. If null, assumes a new engagement.
     */
    private fun updateTimezoneDisplay(engagement: Engagement? = initialEngagement) {
        val dateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm (dd-MMM-yyyy)", Locale.ENGLISH)
        val currentSystemZone = ZoneId.systemDefault()
        timezoneInfoLayout.visibility = View.VISIBLE

        // For new engagements, just show the current timezone
        if (isNewEngagement || engagement == null) {
            val displayDateTime = if (selectedStartDate != null && selectedStartTime != null) {
                LocalDateTime.of(selectedStartDate, selectedStartTime).format(dateTimeFormatter)
            } else {
                "--:-- (-- --- ----)"
            }
            originalTimezoneTextView.text = getString(R.string.timezone_display_format, displayDateTime, currentSystemZone.id)
            originalTimezoneTextView.visibility = View.VISIBLE
            currentTimezoneTextView.visibility = View.GONE
            return
        }

        // For existing engagements, determine the original timezone
        val originalZoneId = try {
            engagement.creationTimezone?.let { ZoneId.of(it) } ?: currentSystemZone
        } catch (e: Exception) {
            // Fallback to current system zone if the stored one is invalid
            currentSystemZone
        }

        // Get the absolute point in time from the stored start date/time and original timezone
        val startDateTimeInstant = LocalDateTime.of(engagement.validityStartDate, engagement.startTime)
            .atZone(originalZoneId)
            .toInstant()

        // Display the time in the original timezone
        val timeInOriginalZone = LocalDateTime.ofInstant(startDateTimeInstant, originalZoneId)
        originalTimezoneTextView.text = getString(R.string.original_timezone_display_format, timeInOriginalZone.format(dateTimeFormatter), originalZoneId.id)
        originalTimezoneTextView.visibility = View.VISIBLE

        // If the current timezone is different, also show the time in the current timezone
        if (currentSystemZone.id != originalZoneId.id) {
            val timeInCurrentZone = LocalDateTime.ofInstant(startDateTimeInstant, currentSystemZone)
            currentTimezoneTextView.text = getString(R.string.current_timezone_display_format, timeInCurrentZone.format(dateTimeFormatter), currentSystemZone.id)
            currentTimezoneTextView.visibility = View.VISIBLE
        } else {
            // Hide the extra row if the timezones are the same
            currentTimezoneTextView.visibility = View.GONE
        }
    }


    private fun saveEngagement() {

        val engagementName = engagementNameAutoCompleteEditText.text.toString().trim()
        val notes = notesAutoCompleteEditText.text.toString().trim().takeIf { it.isNotBlank() }
        val selectedLocationFromPicker = engagementLocationPickerView.getSavedLocation()
        val userFriendlyLocationName = engagementLocationPickerView.getUserFriendlyName()

        if (engagementName.isBlank()) {
            engagementNameAutoCompleteEditText.error = getString(R.string.engagement_name_mandatory_error)
            engagementNameAutoCompleteEditText.requestFocus()
            Toast.makeText(this, R.string.engagement_name_mandatory_error, Toast.LENGTH_SHORT).show()
            return
        } else {
            engagementNameAutoCompleteEditText.error = null
        }

        if (userFriendlyLocationName.isBlank()) {
            Toast.makeText(this, getString(R.string.engagement_location_name_required), Toast.LENGTH_LONG).show()
            engagementLocationPickerView.findViewById<EditText>(R.id.locationNameEditText).error = getString(
                R.string.engagement_location_name_required
            )
            engagementLocationPickerView.findViewById<EditText>(R.id.locationNameEditText).requestFocus()
            return
        } else {
            engagementLocationPickerView.findViewById<EditText>(R.id.locationNameEditText).error = null
        }

        if (selectedDurationMinutes <= 0) {
            durationEditText.error = getString(R.string.engagement_duration_zero_error)
            durationEditText.requestFocus()
            Toast.makeText(this, R.string.engagement_duration_zero_error, Toast.LENGTH_SHORT).show()
            return
        } else {
            durationEditText.error = null
        }

        if (selectedStartDate!!.isAfter(selectedEndDate)) {
            engagementEndDateEditText.error = getString(R.string.engagement_end_date_before_start_date_error)
            engagementEndDateEditText.requestFocus()
            Toast.makeText(this, R.string.engagement_end_date_before_start_date_error, Toast.LENGTH_SHORT).show()
            return
        } else {
            engagementEndDateEditText.error = null
        }

        val actualEndDateTime = LocalDateTime.of(selectedEndDate!!, selectedStartTime!!.plusMinutes(selectedDurationMinutes.toLong()))
        val currentDateTime = LocalDateTime.now()

        if (actualEndDateTime.isBefore(currentDateTime)) {
            return
        } else {
            durationEditText.error = null
        }

        if (notification1Minutes!! < 0) {
            notification1EditText.error = getString(R.string.engagement_notification_time_invalid_error)
            notification1EditText.requestFocus()
            Toast.makeText(this, R.string.engagement_notification_time_invalid_error, Toast.LENGTH_SHORT).show()
            return
        } else {
            notification1EditText.error = null
        }
        if (notification2Minutes!! < 0) {
            notification2EditText.error = getString(R.string.engagement_notification_time_invalid_error)
            notification2EditText.requestFocus()
            Toast.makeText(this, R.string.engagement_notification_time_invalid_error, Toast.LENGTH_SHORT).show()
            return
        } else {
            notification2EditText.error = null
        }
        if (notification3Minutes!! < 0) {
            notification3EditText.error = getString(R.string.engagement_notification_time_invalid_error)
            notification3EditText.requestFocus()
            Toast.makeText(this, R.string.engagement_notification_time_invalid_error, Toast.LENGTH_SHORT).show()
            return
        } else {
            notification3EditText.error = null
        }


        if (userFriendlyLocationName.isBlank()) {
            Toast.makeText(this, getString(R.string.engagement_location_name_required), Toast.LENGTH_LONG).show()
            engagementLocationPickerView.findViewById<EditText>(R.id.locationNameEditText).error = getString(
                R.string.engagement_location_name_required
            )
            engagementLocationPickerView.findViewById<EditText>(R.id.locationNameEditText).requestFocus()
            return
        } else {
            engagementLocationPickerView.findViewById<EditText>(R.id.locationNameEditText).error = null
        }


        val finalSelectedDaysOfWeek: String? = if (daySelectionType == "SPECIFIC_DAYS") {
            if (selectedDaysOfWeek.isEmpty()) {
                Toast.makeText(this, getString(R.string.engagement_no_days_selected_error), Toast.LENGTH_SHORT).show()
                return
            }
            selectedDaysOfWeek.map { it.name }.sorted().joinToString(",")
        } else {
            null
        }

        val finalRecurrenceIntervalDays: Int? = if (daySelectionType == "RECURRENCE") {
            recurrenceIntervalDays
        } else {
            null
        }

        val endDateTime = LocalDateTime.of(selectedEndDate!!, selectedStartTime!!.plusMinutes(selectedDurationMinutes.toLong()))
        // NEW - Use the system's current timezone to get the correct absolute timestamp
        val currentZoneId = ZoneId.systemDefault()
        val endDateTimeInMillis = endDateTime.atZone(currentZoneId).toInstant().toEpochMilli()
        // NEW - Determine the creation timezone
        val creationZone = if (isNewEngagement) currentZoneId.id else initialEngagement?.creationTimezone


        val finalVenueForEngagement = userFriendlyLocationName


        val engagementToSave = Engagement(
            engagementId = currentEngagementId,
            engagementName = engagementName,
            startTime = selectedStartTime!!,
            durationMinutes = selectedDurationMinutes,
            validityStartDate = selectedStartDate!!,
            validityEndDate = selectedEndDate!!,
            daySelectionType = daySelectionType,
            selectedDaysOfWeek = finalSelectedDaysOfWeek,
            recurrenceIntervalDays = finalRecurrenceIntervalDays,
            notes = notes,
            isSilenced = silenceNotificationsCheckbox.isChecked,
            venue = finalVenueForEngagement,
            validatedItem = true,
            endDateTimeInMillis = endDateTimeInMillis,
            // NEW - Save the creation timezone.
            // NOTE: This requires adding 'val creationTimezone: String? = null' to the Engagement data class.
            creationTimezone = creationZone,
            notification1Minutes = notification1Minutes!!,
            notification2Minutes = notification2Minutes!!,
            notification3Minutes = notification3Minutes!!
        )

        lifecycleScope.launch {
            try {
                val isNewEntry = currentEngagementId == -1L || isNewEngagement
                val savedEngagementId: Long

                withContext(Dispatchers.IO) {
                    if (isNewEngagement) {
                        engagementDao.updateEngagement(engagementToSave)
                        savedEngagementId = currentEngagementId
                    } else {
                        engagementDao.updateEngagement(engagementToSave)
                        savedEngagementId = currentEngagementId
                    }

                    if (userFriendlyLocationName.isNotBlank() || selectedLocationFromPicker != null) {
                        val locationToPersist = Location(
                            // Prioritize ID from map picker result, fallback to initial for edits
                            locationId = selectedLocationFromPicker?.locationId ?: initialLocationEntry?.locationId,
                            userFriendlyName = userFriendlyLocationName,
                            geocodedAddress = selectedLocationFromPicker?.geocodedAddress ?: "",
                            latitude = selectedLocationFromPicker?.latitude ?: 0.0,
                            longitude = selectedLocationFromPicker?.longitude ?: 0.0,
                            eventType = "Engagement",
                            eventRefId = savedEngagementId
                        )

                        // Decide to insert or update based on whether the object has an ID
                        if (locationToPersist.locationId == null) {
                            locationDao.insertLocation(locationToPersist)
                        } else {
                            locationDao.updateLocation(locationToPersist)
                        }
                        Log.d("AddEngagementActivity", "Saved/Updated LocationEntry for Engagement ID: $savedEngagementId: $locationToPersist")
                    } else {
                        if (initialLocationEntry != null) {
                            locationDao.deleteLocationsForEvent("Engagement", savedEngagementId)
                            Log.d("AddEngagementActivity", "Deleted LocationEntry for Engagement ID: $savedEngagementId as user-friendly name was blank.")
                        }
                    }

                    val currentAttachments = attachmentAdapter.currentList
                    currentAttachments.forEach { attachment ->
                        val updatedAttachment = attachment.copy(
                            eventRefId = savedEngagementId,
                            validatedItem = true
                        )
                        if (attachment.attachmentId == 0L) {
                            attachmentDao.insertAttachment(updatedAttachment)
                        } else {
                            attachmentDao.updateAttachment(updatedAttachment)
                        }
                    }

                    val attachmentsToDelete = initialAttachmentsLoaded.filter { initial ->
                        currentAttachments.none { current -> current.attachmentId == initial.attachmentId }
                    }
                    attachmentsToDelete.forEach { attachmentDao.deleteAttachment(it) }

                    val currentSubtasks = subtaskAdapter.currentList
                    currentSubtasks.forEach { subtask ->
                        val updatedSubtask = subtask.copy(
                            eventRefId = savedEngagementId,
                            validatedItem = true
                        )
                        if (subtask.subtaskId == 0L) {
                            subtaskDao.insertSubtask(updatedSubtask)
                        } else {
                            subtaskDao.updateSubtask(updatedSubtask)
                        }
                    }

                    val subtasksToDelete = initialSubtasksLoaded.filter { initial ->
                        currentSubtasks.none { current -> current.subtaskId == initial.subtaskId }
                    }
                    subtasksToDelete.forEach { subtaskDao.deleteSubtask(it) }
                }

                if (!engagementToSave.isSilenced) {
                    notificationHelper.scheduleEngagementNotifications(engagementToSave.copy(engagementId = savedEngagementId))
                } else {
                    notificationHelper.cancelEngagementNotifications(savedEngagementId)
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@AddEngagementActivity,
                        if (isNewEntry) getString(R.string.engagement_save_success) else getString(R.string.engagement_update_success),
                        Toast.LENGTH_SHORT
                    ).show()
                    initialEngagement = engagementToSave.copy(engagementId = savedEngagementId)
                    initialAttachmentsLoaded = attachmentAdapter.currentList.map { it.copy(validatedItem = true) }.toList()
                    initialSubtasksLoaded = subtaskAdapter.currentList.map { it.copy(validatedItem = true) }.toList()
                    initialLocationEntry = engagementLocationPickerView.getSavedLocation()?.copy()
                        ?: if (userFriendlyLocationName.isNotBlank()) {
                            Location(
                                locationId = null,
                                userFriendlyName = userFriendlyLocationName,
                                geocodedAddress = "",
                                latitude = 0.0,
                                longitude = 0.0,
                                eventType = "Engagement",
                                eventRefId = savedEngagementId
                            )
                        } else null
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@AddEngagementActivity,
                        if (isNewEngagement) getString(R.string.engagement_save_failure, e.localizedMessage) else getString(
                            R.string.engagement_update_failure, e.localizedMessage),
                        Toast.LENGTH_LONG
                    ).show()
                    Log.e("AddEngagementActivity", "Error saving/updating ad-hoc engagement: ${e.message}", e)

                    if (isNewEngagement) {
                        val currentEngagement = withContext(Dispatchers.IO) { engagementDao.getEngagementById(currentEngagementId) }
                        if (currentEngagement != null && !currentEngagement.validatedItem) {
                            try {
                                attachmentDao.deleteUnvalidatedAttachmentsForEvent("Engagement", currentEngagementId)
                                subtaskDao.deleteUnvalidatedSubtasksForEvent("Engagement", currentEngagementId)
                                locationDao.deleteLocationsForEvent("Engagement", currentEngagementId)
                                notificationHelper.cancelEngagementNotifications(currentEngagementId)
                                engagementDao.deleteEngagement(currentEngagementId)
                                Log.d("AddEngagementActivity", "Deleted unvalidated placeholder Engagement (ID: $currentEngagementId) and its unvalidated attachments/subtasks/location due to save failure.")
                            } catch (deleteEx: Exception) {
                                Log.e("AddEngagementActivity", "Error deleting placeholder Engagement or related data after save failure: ${deleteEx.message}", deleteEx)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun hasUnsavedChanges(): Boolean {
        val currentName = engagementNameAutoCompleteEditText.text.toString().trim()
        val currentStartTime = selectedStartTime
        val currentDuration = selectedDurationMinutes
        val currentStartDate = selectedStartDate
        val currentEndDate = selectedEndDate
        val currentSilenceStatus = silenceNotificationsCheckbox.isChecked
        val currentNotes = notesAutoCompleteEditText.text.toString().trim().takeIf { it.isNotBlank() }
        val currentAttachments = attachmentAdapter.currentList
        val currentSubtasks = subtaskAdapter.currentList
        val currentNotification1 = notification1Minutes
        val currentNotification2 = notification2Minutes
        val currentNotification3 = notification3Minutes


        val currentMapLocation = engagementLocationPickerView.getSavedLocation()
        val currentUserFriendlyLocationName = engagementLocationPickerView.getUserFriendlyName()


        val initial = initialEngagement

        if (initial == null) {
            Log.e("AddEngagementActivity", "hasUnsavedChanges: initialEngagement is null. Assuming unsaved changes.")
            return true
        }

        Log.d("AddEngagementActivity", "--- hasUnsavedChanges (Engagement) ---")
        Log.d("AddEngagementActivity", "Initial Engagement: $initial")
        Log.d("AddEngagementActivity", "Current UI State:")
        Log.d("AddEngagementActivity", "  Name: '$currentName' (Default: '${initial.engagementName}')")
        Log.d("AddEngagementActivity", "  StartTime: $currentStartTime (Default: ${initial.startTime})")
        Log.d("AddEngagementActivity", "  Duration: $currentDuration (Default: ${initial.durationMinutes})")
        Log.d("AddEngagementActivity", "  StartDate: $currentStartDate (Default: ${initial.validityStartDate})")
        Log.d("AddEngagementActivity", "  EndDate: $currentEndDate (Default: ${initial.validityEndDate})")
        Log.d("AddEngagementActivity", "  Silence: $currentSilenceStatus (Default: ${initial.isSilenced})")
        Log.d("AddEngagementActivity", "  Notes: '$currentNotes' (Default: '${initial.notes ?: ""}')")
        Log.d("AddEngagementActivity", "  Venue: '$currentUserFriendlyLocationName' (Default: '${initial.venue ?: ""}')")
        Log.d("AddEngagementActivity", "  Notification1: $currentNotification1 (Default: ${initial.notification1Minutes})")
        Log.d("AddEngagementActivity", "  Notification2: $currentNotification2 (Default: ${initial.notification2Minutes})")
        Log.d("AddEngagementActivity", "  Notification3: $currentNotification3 (Default: ${initial.notification3Minutes})")
        Log.d("AddEngagementActivity", "------------------------------------------")


        if (currentName != initial.engagementName ||
            currentStartTime != initial.startTime ||
            currentDuration != initial.durationMinutes ||
            currentStartDate != initial.validityStartDate ||
            currentEndDate != initial.validityEndDate ||
            currentSilenceStatus != initial.isSilenced ||
            currentNotes != (initial.notes ?: "") ||
            currentUserFriendlyLocationName != (initial.venue ?: "") ||
            currentNotification1 != initial.notification1Minutes ||
            currentNotification2 != initial.notification2Minutes ||
            currentNotification3 != initial.notification3Minutes) {
            Log.d("AddEngagementActivity", "hasUnsavedChanges: TRUE (Core fields changed)")
            return true
        }

        val currentDaySelectionType = daySelectionType
        val initialDaySelectionType = initial.daySelectionType ?: "SPECIFIC_DAYS"

        if (currentDaySelectionType != initialDaySelectionType) {
            Log.d("AddEngagementActivity", "hasUnsavedChanges: TRUE (Day selection type changed)")
            return true
        }

        if (currentDaySelectionType == "SPECIFIC_DAYS") {
            val initialSelectedDays = initial.selectedDaysOfWeek?.split(",")?.map { DayOfWeek.valueOf(it) }?.toSet() ?: emptySet()
            if (selectedDaysOfWeek != initialSelectedDays) {
                Log.d("AddEngagementActivity", "hasUnsavedChanges: TRUE (Specific days changed)")
                return true
            }
        } else if (currentDaySelectionType == "RECURRENCE") {
            if (recurrenceIntervalDays != initial.recurrenceIntervalDays) {
                Log.d("AddEngagementActivity", "hasUnsavedChanges: TRUE (Recurrence interval changed)")
                return true
            }
        }

        if (engagementLocationPickerView.hasUnsavedChanges(initialLocationEntry?.userFriendlyName, initialLocationEntry)) {
            Log.d("AddEngagementActivity", "hasUnsavedChanges: TRUE (Location picker changes detected)")
            return true
        }

        val currentValidatedAttachments = currentAttachments.filter { it.validatedItem }
        val currentUnvalidatedAttachments = currentAttachments.filter { !it.validatedItem }

        if (currentUnvalidatedAttachments.isNotEmpty()) {
            Log.d("AddEngagementActivity", "hasUnsavedChanges: TRUE (Unvalidated attachments present)")
            return true
        }

        if (initialAttachmentsLoaded.size != currentValidatedAttachments.size) {
            Log.d("AddEngagementActivity", "hasUnsavedChanges: TRUE (Validated attachment count changed)")
            return true
        }

        val initialAttachmentSet = initialAttachmentsLoaded.map { it.uriString to it.displayName }.toSet()
        val currentValidatedAttachmentSet = currentValidatedAttachments.map { it.uriString to it.displayName }.toSet()

        if (initialAttachmentSet != currentValidatedAttachmentSet) {
            Log.d("AddEngagementActivity", "hasUnsavedChanges: TRUE (Validated attachment content changed)")
            return true
        }

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
                currentUnvalidatedSubtasks.isEmpty() &&
                        currentSubtasks.size <= initialSubtasksLoaded.size &&
                        currentSubtaskSet.all { (id, currentTuple) ->
                            initialSubtaskSet[id]?.let { initialTuple ->
                                currentTuple == initialTuple
                            } ?: false
                        }
                )

        if (hasSubtaskChanges and !isOnlySubtaskDeletions) {
            Log.d("AddEngagementActivity", "hasUnsavedChanges: TRUE (hasSubtaskChanges is TRUE)")
            return true
        }

        Log.d("AddEngagementActivity", "hasUnsavedChanges: FALSE (No changes detected)")
        return false
    }

    private fun loadAttachmentsForEngagement() {
        if (currentEngagementId != -1L) {
            lifecycleScope.launch {
                attachmentDao.getAttachmentsForEvent("Engagement", currentEngagementId).collectLatest { attachments ->
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

    private fun loadSubtasksForEngagement() {
        if (currentEngagementId != -1L) {
            lifecycleScope.launch {
                subtaskDao.getSubtasksForEvent("Engagement", currentEngagementId).collectLatest { subtasks ->
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


    // --- AttachmentManagerCallbacks Implementation ---
    override fun getCurrentEventType(): String {
        return "Engagement"
    }

    override fun getCurrentEventRefId(): Long {
        return currentEngagementId
    }

    override fun onAttachmentsUpdated() {
        loadAttachmentsForEngagement()
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
        // This callback can be used by the Activity to show a loading indicator or similar UI feedback
        // when the copy process starts within AttachmentManager.
        Log.d("AddAssignmentActivity", "Attachment copy process requested by AttachmentManager.")
    }

    override fun onAttachmentCopyCompleted() {
        Log.d("AddAssignmentActivity", "Attachment copy process completed by AttachmentManager.")
    }

    // --- SubtaskManagerCallbacks Implementation ---
    override fun onSubtasksUpdated() {
        loadSubtasksForEngagement() // Reload subtasks when notified by SubtaskManager
    }

    // Removed showCopySubtasksDialog and dismissCopySubtasksDialog as they are now handled internally by SubtaskManager

    override fun requestUiStateLogging(subtasks: List<Subtask>) {
        // 1. Log the expected state from SubtaskManager's perspective
        val expectedState = subtaskManager.calculateExpectedState(subtasks)
        Log.d("SubtaskManager_Expected", "Expected UI State (Manager's View):\n$expectedState")

        // 2. Log the actual state as seen by the SubtaskAdapter
        // This should be called after submitList has completed its work and RecyclerView has laid out.
        // Using post ensures it runs on the next UI thread cycle after adapter updates.
        // recyclerViewSubtasks.post {
        //    subtaskAdapter.logCurrentAdapterState("After Data Update")
        // }
    }

    override fun onSubtaskCopyRequested() {
        // This callback can be used by the Activity to show a loading indicator or similar UI feedback
        // when the copy process starts within SubtaskManager.
        Log.d("AddEngagementActivity", "Subtask copy process requested by SubtaskManager.")
    }
}
