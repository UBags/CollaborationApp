package com.costheta.cortexa.todo

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.costheta.cortexa.data.db.AppDatabase
import com.costheta.cortexa.data.models.Attachment
import com.costheta.cortexa.data.models.TodoItem
import com.costheta.cortexa.util.attachment.AttachmentAdapter
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Calendar
import androidx.activity.result.ActivityResultLauncher
import com.costheta.cortexa.util.attachment.AttachmentManager
import com.costheta.cortexa.util.attachment.AttachmentManagerCallbacks
import com.costheta.cortexa.data.models.Subtask
import com.costheta.cortexa.util.subtask.SubtaskAdapter
import com.costheta.cortexa.util.subtask.SubtaskManager
import com.costheta.cortexa.util.subtask.SubtaskManagerCallbacks
import java.time.LocalDateTime
import java.time.ZoneId
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import kotlinx.coroutines.flow.firstOrNull
import android.widget.CheckBox
import android.widget.LinearLayout
import androidx.recyclerview.widget.ItemTouchHelper
import com.costheta.cortexa.NotificationHelper
import com.costheta.cortexa.R
import com.costheta.cortexa.util.keyboard.AutoCompleteDisappearingKeyboard
import com.costheta.cortexa.util.keyboard.DisappearingKeyboard
import com.costheta.cortexa.util.subtask.ItemMoveCallback
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale


// Implement SubtaskManagerCallbacks interface
class AddTodoActivity : AppCompatActivity(), AttachmentManagerCallbacks, SubtaskManagerCallbacks {

    // CHANGED: From TextInputEditText to AutoCompleteTextView
    private lateinit var editTextTodoName: AutoCompleteTextView
    private lateinit var editTextTodoDescription: TextInputEditText
    private lateinit var editTextTodoDueDate: TextInputEditText
    private lateinit var editTextTodoDueTime: TextInputEditText
    private lateinit var radioGroupPriority: RadioGroup
    private lateinit var checkboxIsCompleted: CheckBox // NEW: Checkbox for completion status
    private lateinit var checkboxSilenceNotifications: CheckBox // NEW: Checkbox for silencing notifications
    private lateinit var buttonSaveTodo: Button
    // NEW: TextViews for timezone display
    private lateinit var timezoneInfoLayout: LinearLayout
    private lateinit var textViewCurrentTimezoneInfo: TextView
    private lateinit var textViewOriginalTimezoneInfo: TextView


    // ATTACHMENT UI ELEMENTS (now part of included layout, but still need references)
    private lateinit var buttonPickFile: Button
    private lateinit var buttonTakePhotoVideo: Button
    private lateinit var buttonRecordAudio: Button
    private lateinit var buttonAddHyperlink: Button
    private lateinit var recyclerViewAttachments: RecyclerView
    private lateinit var noAttachmentsFoundTextView: TextView
    private lateinit var buttonShareAttachments: Button
    private lateinit var buttonCopyAttachments: Button
    private lateinit var attachmentAdapter: AttachmentAdapter

    // SUBTASK UI ELEMENTS - Added for Subtask Integration
    private var itemTouchHelper: ItemTouchHelper? = null
    private lateinit var editTextAddSubtask: TextInputEditText // Changed from EditText to TextInputEditText
    private lateinit var buttonAddSubtask: Button
    private lateinit var buttonCopySubtasks: Button
    private lateinit var recyclerViewSubtasks: RecyclerView
    private lateinit var noSubtasksFoundTextViewSubtasks: TextView
    private lateinit var subtaskAdapter: SubtaskAdapter


    private var selectedDueDate: LocalDate? = null
    private var selectedDueTime: LocalTime? = null

    private var todoId: Long = -1L
    private var todoItem: TodoItem? = null
    private var initialTodoItem: TodoItem? = null
    private var initialAttachmentsLoaded: List<Attachment> = emptyList()
    private var initialSubtasksLoaded: List<Subtask> = emptyList()
    private val newlyAddedAttachments = mutableListOf<Attachment>()

    private var isNewTodo: Boolean = false

    private val todoDao by lazy { AppDatabase.getDatabase(this).todoDao() }
    private val attachmentDao by lazy { AppDatabase.getDatabase(this).attachmentDao() }
    private val subtaskDao by lazy { AppDatabase.getDatabase(this).subtaskDao() }
    private val notificationHelper by lazy { NotificationHelper(this) }

    // Managers
    private lateinit var attachmentManager: AttachmentManager
    private lateinit var subtaskManager: SubtaskManager


    // Moved from AttachmentActivity: Launchers for Activity Results
    // These launchers are now passed to the AttachmentManager
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


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.todo_activity_add)

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

        // Initialize SubtaskManager - Pass 'this' as Activity context
        subtaskManager = SubtaskManager(
            activity = this, // Pass Activity context
            subtaskDao = subtaskDao,
            lifecycleScope = lifecycleScope,
            callbacks = this
        )


        // CHANGED: From TextInputEditText to AutoCompleteTextView
        editTextTodoName = findViewById(R.id.editTextTodoName)
        editTextTodoDescription = findViewById(R.id.editTextTodoDescription)
        editTextTodoDueDate = findViewById(R.id.editTextTodoDueDate)
        editTextTodoDueTime = findViewById(R.id.editTextTodoDueTime)
        radioGroupPriority = findViewById(R.id.radioGroupPriority)
        checkboxIsCompleted = findViewById(R.id.checkboxIsCompleted)
        checkboxSilenceNotifications = findViewById(R.id.checkboxSilenceNotifications)
        buttonSaveTodo = findViewById(R.id.buttonSaveTodo)
        // NEW: Initialize timezone TextViews
        timezoneInfoLayout = findViewById(R.id.timezoneInfoLayout)
        textViewCurrentTimezoneInfo = findViewById(R.id.textViewCurrentTimezoneInfo)
        textViewOriginalTimezoneInfo = findViewById(R.id.textViewOriginalTimezoneInfo)

        AutoCompleteDisappearingKeyboard.getInstance().register(editTextTodoName)
        DisappearingKeyboard.getInstance().register(editTextTodoDescription)

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
        noSubtasksFoundTextViewSubtasks = subtasksLayout.findViewById(R.id.noSubtasksFoundTextView)

        DisappearingKeyboard.getInstance().register(editTextAddSubtask)

        attachmentAdapter = AttachmentAdapter(
            onDeleteClick = { attachment -> attachmentManager.showDeleteAttachmentDialog(attachment) },
            onItemClick = { attachment -> attachmentManager.viewAttachment(attachment) }
        )
        recyclerViewAttachments.apply {
            layoutManager = LinearLayoutManager(this@AddTodoActivity)
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
            layoutManager = LinearLayoutManager(this@AddTodoActivity)
            adapter = subtaskAdapter
        }

        val callback = ItemMoveCallback(subtaskAdapter)
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper?.attachToRecyclerView(recyclerViewSubtasks)
        findViewById<RadioButton>(R.id.radioMedium)?.isChecked = true

        editTextTodoDueDate.setOnClickListener { showDatePickerDialog() }
        editTextTodoDueTime.setOnClickListener { showTimePickerDialog() }

        buttonSaveTodo.setOnClickListener {
            saveTodo()
        }

        // Delegate attachment actions to AttachmentManager
        buttonPickFile.setOnClickListener { attachmentManager.checkAndRequestMediaPermissionsForPicker() }
        buttonTakePhotoVideo.setOnClickListener { attachmentManager.checkAndRequestCameraPermission() }
        buttonRecordAudio.setOnClickListener { attachmentManager.launchRecordAudioActivity() }
        buttonAddHyperlink.setOnClickListener { attachmentManager.showAddHyperlinkDialog() }
        buttonShareAttachments.setOnClickListener {
            attachmentManager.shareAttachments(attachmentAdapter.currentList)
        }
        buttonCopyAttachments.setOnClickListener { attachmentManager.startCopyAttachmentsProcess() }

        // Delegate subtask actions to SubtaskManager
        buttonAddSubtask.setOnClickListener {
            subtaskManager.addSubtasksFromInput(editTextAddSubtask.text.toString())
            editTextAddSubtask.text?.clear() // Clear input after adding
        }
        buttonCopySubtasks.setOnClickListener { subtaskManager.startCopySubtasksProcess() }


        val intentTodoId = intent.getLongExtra("TODO_ID", -1L)
        if (intentTodoId != -1L) {
            todoId = intentTodoId
            isNewTodo = false
            lifecycleScope.launch {
                val todo = todoDao.getTodoById(todoId)
                todo?.let {
                    todoItem = it
                    initialTodoItem = it.copy()
                    loadTodoData(it)
                }
                loadAttachmentsForTodo()
                loadSubtasksForTodo() // Load subtasks for existing todo
            }
        } else {
            isNewTodo = true
            // Set default UI values for new todo
            selectedDueDate = LocalDate.now().plusDays(7) // CHANGED: Default to 7 days from now
            editTextTodoDueDate.setText(selectedDueDate?.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")))
            selectedDueTime = LocalTime.of(23, 45) // CHANGED: Default to 23:45
            editTextTodoDueTime.setText(selectedDueTime?.format(DateTimeFormatter.ofPattern("HH:mm")))

            lifecycleScope.launch {
                // Pass the default UI values to the placeholder creation
                createPlaceholderTodoAndLoadRelatedData(selectedDueDate, selectedDueTime)
            }
            // NEW: Update timezone display for new To-Do with default times
            updateTimezoneDisplay()
        }

        // NEW: Load unique Todo titles for autofill suggestions
        loadTodoTitlesForAutocomplete()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (hasUnsavedChanges()) {
                    AlertDialog.Builder(this@AddTodoActivity)
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

                                        if (isNewTodo) {
                                            // In ADD mode, delete the entire placeholder record and its children.
                                            val currentTodo = todoDao.getTodoById(todoId)
                                            if (currentTodo != null && !currentTodo.validatedItem) {
                                                subtaskDao.deleteUnvalidatedSubtasksForEvent("ToDo", todoId)
                                                notificationHelper.cancelTodoNotifications(todoId)
                                                todoDao.deleteTodo(todoId)
                                                Log.d("AddTodoActivity", "Deleted placeholder To-Do (ID: $todoId) and related data.")
                                            }
                                        } else {
                                            // In EDIT mode, only clean up newly added (unvalidated) children.
                                            attachmentDao.deleteUnvalidatedAttachmentsForEvent("ToDo", todoId)
                                            subtaskDao.deleteUnvalidatedSubtasksForEvent("ToDo", todoId)
                                            Log.d("AddTodoActivity", "Cleaned up unvalidated items for existing To-Do (ID: $todoId).")
                                        }
                                    } catch (e: Exception) {
                                        Log.e("AddTodoActivity", "Error during cleanup on back press: ${e.message}", e)
                                    }
                                }
                                this@AddTodoActivity.finish()
                            }
                        }
                        .setNegativeButton(getString(R.string.dialog_cancel_button).uppercase(Locale.getDefault())) { dialog, _ ->
                            dialog.cancel()
                        }
                        .show()
                } else {
                    this@AddTodoActivity.finish()
                }
            }
        })
    }

    override fun onStop() {
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up SubtaskManager dialog if it's open
        subtaskManager.onDestroy()
        attachmentManager.onDestroy()
    }

    // Modified: Added parameters for defaultDueDate and defaultDueTime
    private suspend fun createPlaceholderTodoAndLoadRelatedData(defaultDueDate: LocalDate?, defaultDueTime: LocalTime?) {
        withContext(Dispatchers.IO) {
            try {
                // Use the default UI values for the placeholder
                val placeholderTodo = TodoItem(
                    title = "",
                    description = null,
                    dueDate = defaultDueDate,
                    dueTime = defaultDueTime,
                    priority = null,
                    validatedItem = false,
                    completedTimeInMillis = null, // NEW: Set completedTimeInMillis to null for new placeholder
                    lastModified = LocalDateTime.now(ZoneId.systemDefault()), // NEW: Set lastModified for placeholder
                    silenceNotifications = false, // NEW: Default to false for silenceNotifications
                    originalZoneId = ZoneId.systemDefault().id, // Store current zone ID
                    dueDateTimeMillis = null // Null initially
                )
                todoId = todoDao.insertTodo(placeholderTodo)
                withContext(Dispatchers.Main) {
                    Log.d("AddTodoActivity", "Placeholder To-Do created with ID: $todoId")
                    // Set initialTodoItem to the placeholder, ensuring it matches the UI's default state
                    initialTodoItem = placeholderTodo.copy(todoId = todoId)
                    todoItem = initialTodoItem // Also set the main todoItem reference
                    loadAttachmentsForTodo()
                    loadSubtasksForTodo() // Load subtasks for the new placeholder todo
                }
            } catch (e: Exception) {
                Log.e("AddTodoActivity", "Error creating placeholder To-Do: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AddTodoActivity, "Error initializing To-Do. Please restart.", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    // NEW: Function to load unique Todo titles for autocomplete suggestions
    private fun loadTodoTitlesForAutocomplete() {
        lifecycleScope.launch {
            try {
                val allTodoTitles = withContext(Dispatchers.IO) {
                    // Assuming todoDao.getAllTodoTitles() exists and returns Flow<List<String>>
                    // or a suspend fun returning List<String>
                    todoDao.getAllTodoTitles().firstOrNull() ?: emptyList()
                }
                val uniqueTitles = allTodoTitles.filter { it.isNotBlank() }.distinct().sorted()

                withContext(Dispatchers.Main) {
                    val adapter = ArrayAdapter(
                        this@AddTodoActivity,
                        android.R.layout.simple_dropdown_item_1line,
                        uniqueTitles
                    )
                    editTextTodoName.setAdapter(adapter)
                }
            } catch (e: Exception) {
                Log.e("AddTodoActivity", "Error loading todo titles for autocomplete: ${e.message}", e)
            }
        }
    }

    override fun onAttachmentsUpdated() {
        loadAttachmentsForTodo()
    }

    override fun getCurrentEventRefId(): Long = todoId
    override fun getCurrentEventType(): String = "ToDo"
    override fun launchActivityForResult(intent: Intent, launcher: ActivityResultLauncher<Intent>) = launcher.launch(intent)
    override fun launchUriActivityForResult(uri: Uri, launcher: ActivityResultLauncher<Uri>) = launcher.launch(uri)
    override fun requestPermissions(permissions: Array<String>, launcher: ActivityResultLauncher<Array<String>>) = launcher.launch(permissions)
    override fun requestPermission(permission: String, launcher: ActivityResultLauncher<String>) = launcher.launch(permission)
    override fun showToast(messageResId: Int, duration: Int) = Toast.makeText(this, messageResId, duration).show()
    override fun showToast(message: String, duration: Int) = Toast.makeText(this, message, duration).show()
    override fun finishActivity() = finish()

    override fun onAttachmentAdded(attachment: Attachment) {
        newlyAddedAttachments.add(attachment)
    }

    override fun onAttachmentCopyRequested() {
        Log.d("AddTodoActivity", "Attachment copy process requested by AttachmentManager.")
    }
    override fun onAttachmentCopyCompleted() {
        Log.d("AddTodoActivity", "Attachment copy process completed by AttachmentManager.")
    }
    override fun onSubtasksUpdated() = loadSubtasksForTodo()
    override fun onSubtaskCopyRequested() {
        Log.d("AddTodoActivity", "Subtask copy process requested by SubtaskManager.")
    }

    private fun loadAttachmentsForTodo() {
        if (todoId != -1L) {
            lifecycleScope.launch {
                attachmentDao.getAttachmentsForEvent("ToDo", todoId).collectLatest { attachments ->
                    attachmentAdapter.submitList(attachments)
                    initialAttachmentsLoaded = if (isNewTodo) emptyList() else attachments.toList()
                    noAttachmentsFoundTextView.visibility = if (attachments.isEmpty()) View.VISIBLE else View.GONE
                    recyclerViewAttachments.visibility = if (attachments.isEmpty()) View.GONE else View.VISIBLE
                    buttonShareAttachments.visibility = if (attachments.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        } else {
            attachmentAdapter.submitList(emptyList())
            noAttachmentsFoundTextView.visibility = View.VISIBLE
            recyclerViewAttachments.visibility = View.GONE
            buttonShareAttachments.visibility = View.GONE
        }
    }

    private fun loadSubtasksForTodo() {
        if (todoId != -1L) {
            lifecycleScope.launch {
                subtaskDao.getSubtasksForEvent("ToDo", todoId).collectLatest { subtasks ->
                    subtaskAdapter.submitList(subtasks)
                    initialSubtasksLoaded = if (isNewTodo) emptyList() else subtasks.toList()
                    noSubtasksFoundTextViewSubtasks.visibility = if (subtasks.isEmpty()) View.GONE else View.VISIBLE
                    recyclerViewSubtasks.visibility = if (subtasks.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        } else {
            subtaskAdapter.submitList(emptyList())
            noSubtasksFoundTextViewSubtasks.visibility = View.VISIBLE
            recyclerViewSubtasks.visibility = View.GONE
        }
    }

    private fun loadTodoData(todo: TodoItem) {
        editTextTodoName.setText(todo.title)
        editTextTodoDescription.setText(todo.description)

        todo.dueDate?.let {
            selectedDueDate = it
            editTextTodoDueDate.setText(it.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")))
        }
        todo.dueTime?.let {
            selectedDueTime = it
            editTextTodoDueTime.setText(it.format(DateTimeFormatter.ofPattern("HH:mm")))
        }

        when (todo.priority) {
            "High" -> findViewById<RadioButton>(R.id.radioHigh).isChecked = true
            "Medium" -> findViewById<RadioButton>(R.id.radioMedium).isChecked = true
            "Low" -> findViewById<RadioButton>(R.id.radioLow).isChecked = true
            "None" -> findViewById<RadioButton>(R.id.radioNone).isChecked = true
            else -> findViewById<RadioButton>(R.id.radioMedium).isChecked = true
        }
        checkboxIsCompleted.isChecked = todo.isCompleted
        checkboxSilenceNotifications.isChecked = todo.silenceNotifications
        findViewById<TextView>(R.id.add_todo_title).text = getString(R.string.edit_todo_title)
        // NEW: Update timezone display when loading data
        updateTimezoneDisplay(todo)
    }

    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        val year = selectedDueDate?.year ?: calendar.get(Calendar.YEAR)
        val month = selectedDueDate?.monthValue?.minus(1) ?: calendar.get(Calendar.MONTH)
        val day = selectedDueDate?.dayOfMonth ?: calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDayOfMonth ->
            selectedDueDate = LocalDate.of(selectedYear, selectedMonth + 1, selectedDayOfMonth)
            editTextTodoDueDate.setText(selectedDueDate?.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")))
            // NEW: Update timezone display after picking a date
            updateTimezoneDisplay()
        }, year, month, day)
        datePickerDialog.show()
    }

    private fun showTimePickerDialog() {
        val now = LocalTime.now()
        val initialHour = selectedDueTime?.hour ?: now.hour
        val initialMinute = selectedDueTime?.minute ?: now.minute

        val timePickerDialog = TimePickerDialog(this, { _, selectedHour, selectedMinute ->
            selectedDueTime = LocalTime.of(selectedHour, selectedMinute)
            editTextTodoDueTime.setText(selectedDueTime?.format(DateTimeFormatter.ofPattern("HH:mm")))
            // NEW: Update timezone display after picking a time
            updateTimezoneDisplay()
        }, initialHour, initialMinute, true)
        timePickerDialog.show()
    }

    private fun updateTimezoneDisplay(todo: TodoItem? = initialTodoItem) {
        val dateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm (dd-MMM-yyyy)", Locale.ENGLISH)
        val currentSystemZone = ZoneId.systemDefault()

        if (isNewTodo || todo == null || todo.dueDateTimeMillis == null) {
            val displayDateTime = if (selectedDueDate != null && selectedDueTime != null) {
                LocalDateTime.of(selectedDueDate, selectedDueTime).format(dateTimeFormatter)
            } else {
                "--:-- (-- --- ----)"
            }
            textViewOriginalTimezoneInfo.text = getString(R.string.timezone_display_format, displayDateTime, currentSystemZone.id)
            textViewOriginalTimezoneInfo.visibility = View.VISIBLE
            textViewCurrentTimezoneInfo.visibility = View.GONE
            timezoneInfoLayout.visibility = if (selectedDueDate != null && selectedDueTime != null) View.VISIBLE else View.GONE
            return
        }

        val originalZoneId = try {
            todo.originalZoneId?.let { ZoneId.of(it) } ?: currentSystemZone
        } catch (e: Exception) {
            currentSystemZone
        }

        val dueDateTimeInstant = Instant.ofEpochMilli(todo.dueDateTimeMillis)
        val timeInOriginalZone = LocalDateTime.ofInstant(dueDateTimeInstant, originalZoneId)
        textViewOriginalTimezoneInfo.text = getString(R.string.original_timezone_display_format, timeInOriginalZone.format(dateTimeFormatter), originalZoneId.id)
        textViewOriginalTimezoneInfo.visibility = View.VISIBLE

        if (currentSystemZone.id != originalZoneId.id) {
            val timeInCurrentZone = LocalDateTime.ofInstant(dueDateTimeInstant, currentSystemZone)
            textViewCurrentTimezoneInfo.text = getString(R.string.current_timezone_display_format, timeInCurrentZone.format(dateTimeFormatter), currentSystemZone.id)
            textViewCurrentTimezoneInfo.visibility = View.VISIBLE
        } else {
            textViewCurrentTimezoneInfo.visibility = View.GONE
        }
        timezoneInfoLayout.visibility = View.VISIBLE
    }

    private fun saveTodo() {
        val title = editTextTodoName.text.toString().trim()
        val description = editTextTodoDescription.text.toString().trim()
        val priorityRadioButtonId = radioGroupPriority.checkedRadioButtonId
        val priority = if (priorityRadioButtonId != -1) {
            findViewById<RadioButton>(priorityRadioButtonId).text.toString()
        } else {
            "None"
        }
        val isCompleted = checkboxIsCompleted.isChecked
        val silenceNotifications = checkboxSilenceNotifications.isChecked

        // --- MANDATORY FIELD VALIDATION ---
        if (title.isBlank()) {
            Toast.makeText(this, getString(R.string.todo_title_required), Toast.LENGTH_LONG).show()
            editTextTodoName.error = getString(R.string.todo_title_required)
            editTextTodoName.requestFocus()
            return
        } else {
            editTextTodoName.error = null
        }

        // NEW: Determine completedTimeInMillis
        val newCompletedTimeInMillis = if (isCompleted) {
            todoItem?.completedTimeInMillis ?: Instant.now().toEpochMilli()
        } else {
            null
        }

//        if (selectedDueDate == null) {
//            Toast.makeText(this, getString(R.string.todo_due_date_required), Toast.LENGTH_LONG).show()
////            editTextTodoDueDate.error = getString(R.string.todo_due_date_required)
//            editTextTodoDueDate.requestFocus()
//            return
//        } else {
//            editTextTodoDueDate.error = null
//        }

//        if (selectedDueTime == null) {
//            Toast.makeText(this, getString(R.string.todo_due_time_required), Toast.LENGTH_LONG).show()
//            editTextTodoDueTime.error = getString(R.string.todo_due_time_required)
//            editTextTodoDueTime.requestFocus()
//            return
//        } else {
//            editTextTodoDueTime.error = null
//        }

        // Validate that due date and time are not in the past
//        val combinedDateTime = LocalDateTime.of(selectedDueDate!!, selectedDueTime!!)
//        val now = LocalDateTime.now()
//        if (combinedDateTime.isBefore(now)) {
//            Toast.makeText(this, R.string.todo_due_date_time_past_error, Toast.LENGTH_LONG).show()
////            editTextTodoDueDate.error = getString(R.string.todo_due_date_time_past_error)
//            editTextTodoDueTime.error = getString(R.string.todo_due_date_time_past_error)
//            editTextTodoDueTime.requestFocus()
//            return
//        } else {
//            editTextTodoDueDate.error = null
//            editTextTodoDueTime.error = null
//        }

        // Determine completedTimeInMillis based on current completion status
//        val isCurrentlyCompleted = todoItem?.isCompleted ?: false
//        newCompletedTimeInMillis = if (isCurrentlyCompleted) {
//            todoItem?.completedTimeInMillis ?: System.currentTimeMillis() // Keep existing or set now if just marked
//        } else {
//            null // Clear if not completed
//        }

        val dueDateTimeMillis = try {
            if (selectedDueDate != null && selectedDueTime != null) {
                val zoneId = ZoneId.systemDefault()
                LocalDateTime.of(selectedDueDate, selectedDueTime)
                    .atZone(zoneId)
                    .toInstant()
                    .toEpochMilli()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("AddTodoActivity", "Error calculating dueDateTimeMillis", e)
            null
        }

        val OZI = try {
            todoItem?.originalZoneId?.let { ZoneId.of(it) } ?: ZoneId.systemDefault()
        } catch (e: Exception) {
            ZoneId.systemDefault()
        }

        val newTodo = TodoItem(
            todoId = this.todoId,
            title = title,
            description = if (description.isEmpty()) null else description,
            dueDate = selectedDueDate,
            dueTime = selectedDueTime,
            isCompleted = isCompleted,
            priority = if (priority == getString(R.string.priority_none)) null else priority,
            creationDate = todoItem?.creationDate ?: LocalDate.now(),
            lastModified = LocalDateTime.now(OZI),
            validatedItem = true,
            completedTimeInMillis = newCompletedTimeInMillis,
            silenceNotifications = silenceNotifications,
            originalZoneId = todoItem?.originalZoneId ?: ZoneId.systemDefault().id,
            dueDateTimeMillis = dueDateTimeMillis
        )

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val rowsUpdated = todoDao.updateTodo(newTodo)
                    if (rowsUpdated == 0) {
                        Log.e("AddTodoActivity", "Failed to update To-Do with ID: $todoId. Rows updated: $rowsUpdated")
                    }

                    // Mark all attachments for this To-Do as validated and update/insert
                    val currentAttachments = attachmentAdapter.currentList
                    currentAttachments.forEach { attachment ->
                        val updatedAttachment = attachment.copy(
                            eventRefId = todoId, // Use the current todoId
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

                    // Mark all subtasks for this To-Do as validated and update/insert
                    val currentSubtasks = subtaskAdapter.currentList
                    currentSubtasks.forEach { subtask ->
                        val updatedSubtask = subtask.copy(
                            eventRefId = todoId, // Use the current todoId
                            validatedItem = true
                        )
                        if (subtask.subtaskId == 0L) { // New subtask
                            subtaskDao.insertSubtask(updatedSubtask)
                        } else { // Existing subtask
                            subtaskDao.updateSubtask(updatedSubtask)
                        }
                    }

                    // Delete any subtasks that were removed from the list
                    val subtasksToDelete = initialSubtasksLoaded.filter { initial ->
                        currentSubtasks.none { current -> current.subtaskId == initial.subtaskId }
                    }
                    subtasksToDelete.forEach { subtaskDao.deleteSubtask(it) }
                }

                // NEW: Updated notification logic
                if (newTodo.isCompleted || newTodo.silenceNotifications) {
                    notificationHelper.cancelTodoNotifications(newTodo.todoId!!)
                } else {
                    notificationHelper.scheduleTodoNotifications(newTodo)
                }

                Toast.makeText(this@AddTodoActivity, R.string.todo_save_success, Toast.LENGTH_SHORT).show()
                // Update initial states after successful save
                initialTodoItem = newTodo.copy()
                initialAttachmentsLoaded = attachmentAdapter.currentList.map { it.copy(validatedItem = true) }.toList()
                initialSubtasksLoaded = subtaskAdapter.currentList.map { it.copy(validatedItem = true) }.toList()
                finish()
            } catch (e: Exception) {
                Log.e("AddTodoActivity", "Error saving To-Do: ${e.message}", e)
                Toast.makeText(this@AddTodoActivity, getString(R.string.todo_save_failure, e.localizedMessage), Toast.LENGTH_LONG).show()

                if (isNewTodo) {
                    val currentTodo = withContext(Dispatchers.IO) { todoDao.getTodoById(todoId) }
                    if (currentTodo != null && !currentTodo.validatedItem) {
                        try {
                            attachmentDao.deleteUnvalidatedAttachmentsForEvent("ToDo", todoId)
                            subtaskDao.deleteUnvalidatedSubtasksForEvent("ToDo", todoId)
                            notificationHelper.cancelTodoNotifications(todoId)
                            todoDao.deleteTodo(todoId)
                            Log.d("AddTodoActivity", "Deleted unvalidated placeholder To-Do (ID: $todoId) and its unvalidated attachments/subtasks due to save failure.")
                        } catch (deleteEx: Exception) {
                            Log.e("AddTodoActivity", "Error deleting placeholder To-Do or attachments/subtasks after save failure: ${deleteEx.message}", deleteEx)
                        }
                    }
                }
            }
        }
    }

    private fun hasUnsavedChanges(): Boolean {
        val currentTitle = editTextTodoName.text.toString().trim()
        val currentDescription = editTextTodoDescription.text.toString().trim()
        val currentPriority = findViewById<RadioButton>(radioGroupPriority.checkedRadioButtonId)?.text?.toString() ?: "None"
        val currentIsCompleted = checkboxIsCompleted.isChecked
        val currentSilenceNotifications = checkboxSilenceNotifications.isChecked // NEW: Get current silenceNotifications state
        val currentAttachments = attachmentAdapter.currentList
        val currentSubtasks = subtaskAdapter.currentList

        val initial = initialTodoItem ?: return true

        Log.d("AddTodoActivity", "--- hasUnsavedChanges (Todo) ---")
        Log.d("AddTodoActivity", "Initial Todo: $initial")
        Log.d("AddTodoActivity", "Current UI State:")
        Log.d("AddTodoActivity", "  Title: '$currentTitle' (Default: '${initial.title}')")
        Log.d("AddTodoActivity", "  Description: '$currentDescription' (Default: '${initial.description ?: ""}')")
        Log.d("AddTodoActivity", "  DueDate: $selectedDueDate (Default: ${initial.dueDate})")
        Log.d("AddTodoActivity", "  DueTime: $selectedDueTime (Default: ${initial.dueTime})")
        Log.d("AddTodoActivity", "  Priority: '$currentPriority' (Default: '${initial.priority ?: "None"}')")
        Log.d("AddTodoActivity", "  Silence Notifications: $currentSilenceNotifications (Default: ${initial.silenceNotifications})") // NEW: Log silenceNotifications
        Log.d("AddTodoActivity", "  Attachments Empty: ${currentAttachments.isEmpty()}")
        Log.d("AddTodoActivity", "  Subtasks Empty: ${currentSubtasks.isEmpty()}")
        Log.d("AddTodoActivity", "------------------------------------------")

        // Capture current completedTimeInMillis and lastModified for comparison
        val currentCompletedTimeInMillis = if (todoItem?.isCompleted == true) todoItem?.completedTimeInMillis else null
        val currentLastModified = LocalDateTime.now() // Will be updated on save, but for comparison, we check if it *would* change

        if (isNewTodo) {
            // For new todos, compare against the default values set in createPlaceholderTodoAndLoadRelatedData
            // These defaults should match the UI's initial state.
            val defaultTitle = initial.title
            val defaultDescription = initial.description ?: ""
            val defaultDueDate = initial.dueDate
            val defaultDueTime = initial.dueTime
            val defaultPriority = initial.priority ?: "None"
            val defaultCompletedTimeInMillis = initial.completedTimeInMillis
            val defaultLastModified = initial.lastModified
            val defaultSilenceNotifications = initial.silenceNotifications // NEW: Get default silenceNotifications

            if (currentTitle != defaultTitle ||
                currentDescription != defaultDescription ||
                selectedDueDate != defaultDueDate ||
                selectedDueTime != defaultDueTime ||
                currentPriority != defaultPriority ||
                currentIsCompleted != initial.isCompleted ||
                currentSilenceNotifications != defaultSilenceNotifications ||
                currentAttachments.isNotEmpty() || // If attachments are added, it's a change
                currentSubtasks.isNotEmpty() || // If subtasks are added, it's a change
                currentCompletedTimeInMillis != defaultCompletedTimeInMillis || // Check completed time
                currentLastModified.atZone(ZoneOffset.UTC).toInstant().toEpochMilli() != defaultLastModified.atZone(
                    ZoneOffset.UTC).toInstant().toEpochMilli() // Check last modified
            ) {
                Log.d("AddTodoActivity", "hasUnsavedChanges: TRUE (New Todo - deviation from default or attachments/subtasks present)")
                return true
            } else {
                Log.d("AddTodoActivity", "hasUnsavedChanges: FALSE (New Todo - all fields at default, no attachments/subtasks)")
                return false
            }
        } else {
            // For existing todos, compare against the loaded initialTodoItem
            if (currentTitle != initial.title ||
                currentDescription != (initial.description ?: "") ||
                selectedDueDate != initial.dueDate ||
                selectedDueTime != initial.dueTime ||
                currentPriority != (initial.priority ?: "None") ||
                currentSilenceNotifications != initial.silenceNotifications || // NEW: Compare silenceNotifications
                (todoItem?.isCompleted != initial.isCompleted) || // Check if completion status changed
                (todoItem?.isCompleted == true && currentCompletedTimeInMillis != initial.completedTimeInMillis) // Check completedTimeInMillis only if completed
            ) {
                Log.d("AddTodoActivity", "hasUnsavedChanges: TRUE (Existing Todo - core fields changed)")
                return true
            }

            // Check for changes in attachments
            val currentValidatedAttachments = currentAttachments.filter { it.validatedItem }
            val currentUnvalidatedAttachments = currentAttachments.filter { !it.validatedItem }

            if (currentUnvalidatedAttachments.isNotEmpty()) {
                Log.d("AddTodoActivity", "hasUnsavedChanges: TRUE (Existing Todo - unvalidated attachments present)")
                return true
            }

            if (initialAttachmentsLoaded.size != currentValidatedAttachments.size) {
                Log.d("AddTodoActivity", "hasUnsavedChanges: TRUE (Existing Todo - validated attachment count changed)")
                return true
            }

            val initialAttachmentSet = initialAttachmentsLoaded.map { it.uriString to it.displayName }.toSet()
            val currentValidatedAttachmentSet = currentValidatedAttachments.map { it.uriString to it.displayName }.toSet()

            if (initialAttachmentSet != currentValidatedAttachmentSet) {
                Log.d("AddTodoActivity", "hasUnsavedChanges: TRUE (Existing Todo - validated attachment content changed)")
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

            // Check if subtask changes are only deletions (this logic is complex and might be simplified
            // if initialSubtaskSet != currentSubtaskSet is sufficient for all changes)
            val isOnlySubtaskDeletions = (
                    currentUnvalidatedSubtasks.isEmpty() && // No new or unvalidated subtasks
                            currentSubtasks.size <= initialSubtasksLoaded.size && // Current list is smaller or equal
                            currentSubtaskSet.all { (id, currentTuple) ->
                                initialSubtaskSet[id]?.let { initialTuple ->
                                    // For each subtask in current list, check if its properties are unchanged
                                    currentTuple == initialTuple
                                } ?: false // Subtask not in initial list would indicate an addition
                            }
                    )

            // If there are subtask changes AND it's not just deletions, then return true
            if (hasSubtaskChanges && !isOnlySubtaskDeletions) {
                Log.d("AddTodoActivity", "hasUnsavedChanges: TRUE (hasSubtaskChanges is TRUE and not only deletions)")
                return true
            }
            //            // If there are only deletions, we still consider it a change
            //            if (hasSubtaskChanges && isOnlySubtaskDeletions && initialSubtasksLoaded.size > currentValidatedSubtasks.size) {
            //                Log.d("AddTodoActivity", "hasUnsavedChanges: TRUE (Existing Todo - only subtask deletions)")
            //                return true
            //            }\
            Log.d("AddTodoActivity", "hasUnsavedChanges: FALSE (Existing Todo - no changes detected)")
            return false
        }
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

}