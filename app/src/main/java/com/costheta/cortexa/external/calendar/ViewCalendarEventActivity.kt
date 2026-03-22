package com.costheta.cortexa.external.calendar

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.costheta.cortexa.R
import com.costheta.cortexa.data.db.AppDatabase
import com.costheta.cortexa.data.db.dao.AttachmentDao
import com.costheta.cortexa.data.db.dao.SubtaskDao
import com.costheta.cortexa.data.db.dao.SyncedCalendarEventDao
import com.costheta.cortexa.data.models.Attachment
import com.costheta.cortexa.data.models.Subtask
import com.costheta.cortexa.data.models.SyncedCalendarEvent
import com.costheta.cortexa.util.attachment.AttachmentAdapter
import com.costheta.cortexa.util.attachment.AttachmentManager
import com.costheta.cortexa.util.attachment.AttachmentManagerCallbacks
import com.costheta.cortexa.util.subtask.ItemMoveCallback
import com.costheta.cortexa.util.subtask.SubtaskAdapter
import com.costheta.cortexa.util.subtask.SubtaskManager
import com.costheta.cortexa.util.subtask.SubtaskManagerCallbacks
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class ViewCalendarEventActivity : AppCompatActivity(), AttachmentManagerCallbacks, SubtaskManagerCallbacks {

    // UI elements for event details
    private lateinit var eventTitleTextView: TextView
    private lateinit var eventDescriptionTextView: TextView
    private lateinit var eventTimeTextView: TextView
    private lateinit var eventLocationTextView: TextView

    // DAOs
    private lateinit var syncedCalendarEventDao: SyncedCalendarEventDao
    private lateinit var attachmentDao: AttachmentDao
    private lateinit var subtaskDao: SubtaskDao

    // Managers for attachments and subtasks
    private lateinit var attachmentManager: AttachmentManager
    private lateinit var subtaskManager: SubtaskManager

    // Adapters for RecyclerViews
    private lateinit var attachmentAdapter: AttachmentAdapter
    private lateinit var subtaskAdapter: SubtaskAdapter

    // UI from included layouts
    private lateinit var recyclerViewAttachments: RecyclerView
    private lateinit var noAttachmentsTextView: TextView
    private lateinit var buttonShareAttachments: Button
    private lateinit var editTextAddSubtask: EditText
    private lateinit var buttonAddSubtask: Button
    private lateinit var recyclerViewSubtasks: RecyclerView
    private lateinit var noSubtasksTextView: TextView
    private var itemTouchHelper: ItemTouchHelper? = null

    private var eventLocalId: Long = -1L
    private val newlyAddedAttachments = mutableListOf<Attachment>()

    // Activity Result Launchers for handling file picking, camera, etc.
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
    private val requestCameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        attachmentManager.handleCameraPermissionResult(isGranted)
    }
    private val requestMediaPermissionsLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        attachmentManager.handleMediaPermissionsResult(permissions)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_calendar_event)

        eventLocalId = intent.getLongExtra("EVENT_LOCAL_ID", -1L)
        if (eventLocalId == -1L) {
            Toast.makeText(this, "Error: Event ID not found.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        initializeDaos()
        initializeViews()
        initializeManagers()
        initializeAdaptersAndRecyclerViews()
        setupListeners()
        loadEventData()
    }

    private fun initializeDaos() {
        val db = AppDatabase.getDatabase(applicationContext)
        syncedCalendarEventDao = db.syncedCalendarEventDao()
        attachmentDao = db.attachmentDao()
        subtaskDao = db.subtaskDao()
    }

    private fun initializeViews() {
        eventTitleTextView = findViewById(R.id.eventTitleTextView)
        eventDescriptionTextView = findViewById(R.id.eventDescriptionTextView)
        eventTimeTextView = findViewById(R.id.eventTimeTextView)
        eventLocationTextView = findViewById(R.id.eventLocationTextView)

        val attachmentLayout = findViewById<View>(R.id.attachmentLayout)
        recyclerViewAttachments = attachmentLayout.findViewById(R.id.recyclerViewAttachments)
        noAttachmentsTextView = attachmentLayout.findViewById(R.id.noAttachmentsFoundTextView)
        buttonShareAttachments = attachmentLayout.findViewById(R.id.buttonShareAttachments)

        val subtasksLayout = findViewById<View>(R.id.subtasksLayout)
        editTextAddSubtask = subtasksLayout.findViewById(R.id.editTextAddSubtask)
        buttonAddSubtask = subtasksLayout.findViewById(R.id.buttonAddSubtask)
        recyclerViewSubtasks = subtasksLayout.findViewById(R.id.recyclerViewSubtasks)
        noSubtasksTextView = subtasksLayout.findViewById(R.id.noSubtasksFoundTextView)
    }

    private fun initializeManagers() {
        attachmentManager = AttachmentManager(this, attachmentDao, lifecycleScope, this, pickFileLauncher, takePhotoLauncher, takeVideoLauncher, recordAudioLauncher, requestCameraPermissionLauncher, requestMediaPermissionsLauncher)
        subtaskManager = SubtaskManager(this, subtaskDao, lifecycleScope, this)
    }

    private fun initializeAdaptersAndRecyclerViews() {
        attachmentAdapter = AttachmentAdapter(
            onDeleteClick = { attachment -> attachmentManager.showDeleteAttachmentDialog(attachment) },
            onItemClick = { attachment -> attachmentManager.viewAttachment(attachment) }
        )
        recyclerViewAttachments.layoutManager = LinearLayoutManager(this)
        recyclerViewAttachments.adapter = attachmentAdapter

        subtaskAdapter = SubtaskAdapter(
            onToggleComplete = { subtask, isCompleted -> subtaskManager.toggleSubtaskCompletion(subtask, isCompleted) },
            onDeleteClick = { subtask -> subtaskManager.deleteSubtask(subtask) },
            onStartDrag = { viewHolder -> itemTouchHelper?.startDrag(viewHolder) },
            onSubtasksReordered = { reorderedList -> subtaskManager.reorderSubtasks(reorderedList) }
        )
        recyclerViewSubtasks.layoutManager = LinearLayoutManager(this)
        recyclerViewSubtasks.adapter = subtaskAdapter

        val callback = ItemMoveCallback(subtaskAdapter)
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper?.attachToRecyclerView(recyclerViewSubtasks)
    }

    private fun setupListeners() {
        findViewById<View>(R.id.buttonPickFile).setOnClickListener { attachmentManager.checkAndRequestMediaPermissionsForPicker() }
        findViewById<View>(R.id.buttonTakePhotoVideo).setOnClickListener { attachmentManager.checkAndRequestCameraPermission() }
        findViewById<View>(R.id.buttonRecordAudio).setOnClickListener { attachmentManager.launchRecordAudioActivity() }
        findViewById<View>(R.id.buttonAddHyperlink).setOnClickListener { attachmentManager.showAddHyperlinkDialog() }
        buttonShareAttachments.setOnClickListener { attachmentManager.shareAttachments(attachmentAdapter.currentList) }
        buttonAddSubtask.setOnClickListener {
            subtaskManager.addSubtasksFromInput(editTextAddSubtask.text.toString())
            editTextAddSubtask.text?.clear()
        }
        findViewById<View>(R.id.buttonCopySubtasks).setOnClickListener { subtaskManager.startCopySubtasksProcess() }
        findViewById<View>(R.id.buttonCopyAttachments).setOnClickListener { attachmentManager.startCopyAttachmentsProcess() }
    }

    private fun loadEventData() {
        lifecycleScope.launch {
            val event = syncedCalendarEventDao.getById(eventLocalId)
            if (event == null) {
                Toast.makeText(this@ViewCalendarEventActivity, "Could not load event.", Toast.LENGTH_LONG).show()
                finish()
                return@launch
            }
            populateUI(event)
            loadAttachments()
            loadSubtasks()
        }
    }

    private fun populateUI(event: SyncedCalendarEvent) {
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

        eventTitleTextView.text = event.title
        eventDescriptionTextView.text = event.description.takeIf { !it.isNullOrBlank() } ?: getString(R.string.no_description)
        eventLocationTextView.text = event.location.takeIf { !it.isNullOrBlank() } ?: getString(R.string.no_location_specified)

        val systemZoneId = ZoneId.systemDefault()
        val startDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(event.startDateTimeMillis), systemZoneId)
        val endDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(event.endDateTimeMillis), systemZoneId)

        val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy, HH:mm", Locale.ENGLISH)
        eventTimeTextView.text = getString(R.string.calendar_event_time_format, startDateTime.format(formatter), endDateTime.format(timeFormatter), systemZoneId.id)
    }

    private fun loadAttachments() {
        lifecycleScope.launch {
            attachmentDao.getAttachmentsForEvent(getCurrentEventType(), eventLocalId).collectLatest { attachments ->
                attachmentAdapter.submitList(attachments)
                noAttachmentsTextView.visibility = if (attachments.isEmpty()) View.VISIBLE else View.GONE
                recyclerViewAttachments.visibility = if (attachments.isEmpty()) View.GONE else View.VISIBLE
                buttonShareAttachments.visibility = if (attachments.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    private fun loadSubtasks() {
        lifecycleScope.launch {
            subtaskDao.getSubtasksForEvent(getCurrentEventType(), eventLocalId).collectLatest { subtasks ->
                subtaskAdapter.submitList(subtasks)
                noSubtasksTextView.visibility = if (subtasks.isEmpty()) View.VISIBLE else View.GONE
                recyclerViewSubtasks.visibility = if (subtasks.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    // --- Callbacks for Managers ---
    override fun getCurrentEventType(): String = "SyncedCalendarEvent"
    override fun getCurrentEventRefId(): Long = eventLocalId
    override fun onAttachmentsUpdated() = loadAttachments()
    override fun onSubtasksUpdated() = loadSubtasks()
    override fun onAttachmentAdded(attachment: Attachment) { newlyAddedAttachments.add(attachment) }
    override fun showToast(messageResId: Int, duration: Int) { Toast.makeText(this, messageResId, duration).show() }
    override fun showToast(message: String, duration: Int) { Toast.makeText(this, message, duration).show() }
    override fun requestPermission(permission: String, launcher: ActivityResultLauncher<String>) { launcher.launch(permission) }
    override fun requestPermissions(permissions: Array<String>, launcher: ActivityResultLauncher<Array<String>>) { launcher.launch(permissions) }
    override fun launchActivityForResult(intent: Intent, launcher: ActivityResultLauncher<Intent>) { launcher.launch(intent) }
    override fun launchUriActivityForResult(uri: Uri, launcher: ActivityResultLauncher<Uri>) { launcher.launch(uri) }
    override fun finishActivity() { finish() }
    override fun onAttachmentCopyRequested() { /* Can be used to show a loading indicator */ }
    override fun onAttachmentCopyCompleted() { /* Can be used to hide a loading indicator */ }
    override fun requestUiStateLogging(subtasks: List<Subtask>) { /* Not needed in this view-only activity */ }
    override fun onSubtaskCopyRequested() { /* Can be used to show a loading indicator */ }
}

