package com.costheta.cortexa.external.tasks

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action

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
import com.costheta.cortexa.data.db.dao.SyncedGoogleTaskDao
import com.costheta.cortexa.data.models.Attachment
import com.costheta.cortexa.data.models.Subtask
import com.costheta.cortexa.data.models.SyncedGoogleTask
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

class ViewGoogleTaskActivity : AppCompatActivity(), AttachmentManagerCallbacks, SubtaskManagerCallbacks {

    // UI elements for task details
    private lateinit var taskTitleTextView: TextView
    private lateinit var taskNotesTextView: TextView
    private lateinit var taskDueTimeTextView: TextView

    // DAOs
    private lateinit var syncedGoogleTaskDao: SyncedGoogleTaskDao
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

    private var taskLocalId: Long = -1L
    private val newlyAddedAttachments = mutableListOf<Attachment>()

    // Activity Result Launchers
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
        setContentView(R.layout.activity_view_google_task)

        taskLocalId = intent.getLongExtra("TASK_LOCAL_ID", -1L)
        if (taskLocalId == -1L) {
            Toast.makeText(this, "Error: Task ID not found.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        initializeDaos()
        initializeViews()
        initializeManagers()
        initializeAdaptersAndRecyclerViews()
        setupListeners()
        loadTaskData()
    }

    private fun initializeDaos() {
        val db = AppDatabase.getDatabase(applicationContext)
        syncedGoogleTaskDao = db.syncedGoogleTaskDao()
        attachmentDao = db.attachmentDao()
        subtaskDao = db.subtaskDao()
    }

    private fun initializeViews() {
        taskTitleTextView = findViewById(R.id.taskTitleTextView)
        taskNotesTextView = findViewById(R.id.taskNotesTextView)
        taskDueTimeTextView = findViewById(R.id.taskDueTimeTextView)

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

    private fun loadTaskData() {
        lifecycleScope.launch {
            val task = syncedGoogleTaskDao.getById(taskLocalId)
            if (task == null) {
                Toast.makeText(this@ViewGoogleTaskActivity, "Could not load task.", Toast.LENGTH_LONG).show()
                finish()
                return@launch
            }
            populateUI(task)
            loadAttachments()
            loadSubtasks()
        }
    }

    private fun populateUI(task: SyncedGoogleTask) {
        taskTitleTextView.text = task.title
        taskNotesTextView.text = task.notes.takeIf { !it.isNullOrBlank() } ?: getString(R.string.no_description)

        if (task.dueDateTimeMillis != null) {
            val systemZoneId = ZoneId.systemDefault()
            val dueDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(task.dueDateTimeMillis), systemZoneId)
            val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy, HH:mm", Locale.ENGLISH)
            taskDueTimeTextView.text = getString(R.string.google_task_due_time_format, dueDateTime.format(formatter), systemZoneId.id)
            taskDueTimeTextView.visibility = View.VISIBLE
        } else {
            taskDueTimeTextView.text = getString(R.string.no_due_date)
        }
    }

    private fun loadAttachments() {
        lifecycleScope.launch {
            attachmentDao.getAttachmentsForEvent(getCurrentEventType(), taskLocalId).collectLatest { attachments ->
                attachmentAdapter.submitList(attachments)
                noAttachmentsTextView.visibility = if (attachments.isEmpty()) View.VISIBLE else View.GONE
                recyclerViewAttachments.visibility = if (attachments.isEmpty()) View.GONE else View.VISIBLE
                buttonShareAttachments.visibility = if (attachments.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    private fun loadSubtasks() {
        lifecycleScope.launch {
            subtaskDao.getSubtasksForEvent(getCurrentEventType(), taskLocalId).collectLatest { subtasks ->
                subtaskAdapter.submitList(subtasks)
                noSubtasksTextView.visibility = if (subtasks.isEmpty()) View.VISIBLE else View.GONE
                recyclerViewSubtasks.visibility = if (subtasks.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    // --- Callbacks for Managers ---
    override fun getCurrentEventType(): String = "SyncedGoogleTask"
    override fun getCurrentEventRefId(): Long = taskLocalId
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
