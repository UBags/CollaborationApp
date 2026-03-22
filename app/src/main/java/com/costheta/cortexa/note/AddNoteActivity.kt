package com.costheta.cortexa.note

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.costheta.cortexa.data.db.AppDatabase
import com.costheta.cortexa.data.db.dao.NotesDao
import com.costheta.cortexa.data.db.dao.AttachmentDao
import com.costheta.cortexa.data.db.dao.TimetableDao
import com.costheta.cortexa.data.models.Attachment
import com.costheta.cortexa.data.models.NotesItem
import com.costheta.cortexa.util.attachment.AttachmentAdapter
import com.costheta.cortexa.util.attachment.AttachmentManager
import com.costheta.cortexa.util.attachment.AttachmentManagerCallbacks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import androidx.activity.result.ActivityResultLauncher
import com.google.android.material.textfield.TextInputEditText
import com.costheta.cortexa.data.db.dao.SubtaskDao
import com.costheta.cortexa.data.models.Subtask
import com.costheta.cortexa.util.subtask.SubtaskAdapter
import com.costheta.cortexa.util.subtask.SubtaskManager
import com.costheta.cortexa.util.subtask.SubtaskManagerCallbacks
import android.widget.CheckBox
import android.widget.LinearLayout
import androidx.recyclerview.widget.ItemTouchHelper
import com.costheta.cortexa.R
import com.costheta.cortexa.util.keyboard.AutoCompleteDisappearingKeyboard
import com.costheta.cortexa.util.keyboard.DisappearingKeyboard
import com.costheta.cortexa.util.subtask.ItemMoveCallback
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale


class AddNoteActivity : AppCompatActivity(), AttachmentManagerCallbacks, SubtaskManagerCallbacks {

    // UI elements
    private lateinit var noteSubjectAutoCompleteTextView: AutoCompleteTextView
    private lateinit var editTextNoteType: AutoCompleteTextView
    private lateinit var editTextNoteDescription: AutoCompleteTextView
    private lateinit var editTextNoteKeywords: TextInputEditText
    private lateinit var saveNoteButton: Button
    private lateinit var addNoteTitle: TextView
    private lateinit var checkBoxArchivedNote: CheckBox

    // TIMEZONE CHANGE: UI Elements for timezone display
    // MODIFIED: Changed from lateinit to nullable to prevent crash
    private var timezoneInfoLayout: LinearLayout? = null
    private var currentTimezoneTextView: TextView? = null
    private var originalTimezoneTextView: TextView? = null

    // Attachment UI elements
    private lateinit var buttonPickFile: Button
    private lateinit var buttonTakePhotoVideo: Button
    private lateinit var buttonRecordAudio: Button
    private lateinit var buttonAddHyperlink: Button
    private lateinit var recyclerViewAttachments: RecyclerView
    private lateinit var noAttachmentsFoundTextView: TextView
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

    // DAOs
    private lateinit var notesDao: NotesDao
    private lateinit var attachmentDao: AttachmentDao
    private lateinit var timetableDao: TimetableDao
    private lateinit var subtaskDao: SubtaskDao

    private lateinit var attachmentAdapter: AttachmentAdapter
    private lateinit var attachmentManager: AttachmentManager
    private lateinit var subtaskManager: SubtaskManager

    // Note ID and state
    private var noteId: Long = -1L
    private var isNewNote: Boolean = false
    private var initialNotesItem: NotesItem? = null
    private var initialAttachmentsLoaded: List<Attachment> = emptyList()
    private var initialSubtasksLoaded: List<Subtask> = emptyList()
    private val newlyAddedAttachments = mutableListOf<Attachment>()

    // ActivityResultLaunchers for attachments
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
        setContentView(R.layout.note_activity_add)

        // Initialize UI elements
        addNoteTitle = findViewById(R.id.addNoteTitle)
        noteSubjectAutoCompleteTextView = findViewById(R.id.noteSubjectAutoCompleteTextView)
        editTextNoteType = findViewById(R.id.editTextNoteType)
        editTextNoteDescription = findViewById(R.id.editTextNoteDescription)
        editTextNoteKeywords = findViewById(R.id.editTextNoteKeywords)
        saveNoteButton = findViewById(R.id.saveNoteButton)
        checkBoxArchivedNote = findViewById(R.id.checkBoxArchivedNote)

        AutoCompleteDisappearingKeyboard.getInstance().register(noteSubjectAutoCompleteTextView)
        AutoCompleteDisappearingKeyboard.getInstance().register(editTextNoteDescription)
        AutoCompleteDisappearingKeyboard.getInstance().register(editTextNoteType)
        DisappearingKeyboard.getInstance().register(editTextNoteKeywords)

        // TIMEZONE CHANGE: Initialize timezone UI elements
        timezoneInfoLayout = findViewById(R.id.timezoneInfoLayout)
        currentTimezoneTextView = findViewById(R.id.currentTimezoneTextView)
        originalTimezoneTextView = findViewById(R.id.originalTimezoneTextView)

        // Initialize attachment UI elements
        val attachmentLayout = findViewById<View>(R.id.attachmentLayout)
        buttonPickFile = attachmentLayout.findViewById(R.id.buttonPickFile)
        buttonTakePhotoVideo = attachmentLayout.findViewById(R.id.buttonTakePhotoVideo)
        buttonRecordAudio = attachmentLayout.findViewById(R.id.buttonRecordAudio)
        buttonAddHyperlink = attachmentLayout.findViewById(R.id.buttonAddHyperlink)
        recyclerViewAttachments = attachmentLayout.findViewById(R.id.recyclerViewAttachments)
        noAttachmentsFoundTextView = attachmentLayout.findViewById(R.id.noAttachmentsFoundTextView)
        buttonShareAttachments = attachmentLayout.findViewById(R.id.buttonShareAttachments)
        buttonCopyAttachments = attachmentLayout.findViewById(R.id.buttonCopyAttachments)

        // Initialize subtask UI elements
        val subtasksLayout = findViewById<View>(R.id.subtasksLayout)
        editTextAddSubtask = subtasksLayout.findViewById(R.id.editTextAddSubtask)
        buttonAddSubtask = subtasksLayout.findViewById(R.id.buttonAddSubtask)
        buttonCopySubtasks = subtasksLayout.findViewById(R.id.buttonCopySubtasks)
        recyclerViewSubtasks = subtasksLayout.findViewById(R.id.recyclerViewSubtasks)
        noSubtasksFoundTextView = subtasksLayout.findViewById(R.id.noSubtasksFoundTextView)

        DisappearingKeyboard.getInstance().register(editTextAddSubtask)

        // Initialize DAOs
        notesDao = AppDatabase.getDatabase(applicationContext).notesDao()
        attachmentDao = AppDatabase.getDatabase(applicationContext).attachmentDao()
        timetableDao = AppDatabase.getDatabase(applicationContext).timetableDao()
        subtaskDao = AppDatabase.getDatabase(applicationContext).subtaskDao()

        // Setup Attachment RecyclerView
        attachmentAdapter = AttachmentAdapter(
            onDeleteClick = { attachment -> attachmentManager.showDeleteAttachmentDialog(attachment) },
            onItemClick = { attachment -> attachmentManager.viewAttachment(attachment) }
        )
        recyclerViewAttachments.apply {
            layoutManager = LinearLayoutManager(this@AddNoteActivity)
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
            layoutManager = LinearLayoutManager(this@AddNoteActivity)
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
        saveNoteButton.setOnClickListener { saveNote() }
        buttonShareAttachments.setOnClickListener { attachmentManager.shareAttachments(attachmentAdapter.currentList) }
        buttonCopyAttachments.setOnClickListener { attachmentManager.startCopyAttachmentsProcess() }
        buttonAddSubtask.setOnClickListener {
            subtaskManager.addSubtasksFromInput(editTextAddSubtask.text.toString())
            editTextAddSubtask.text?.clear()
        }
        buttonCopySubtasks.setOnClickListener { subtaskManager.startCopySubtasksProcess() }

        loadAutoSuggestions()

        // Check if we are in edit mode
        val intentNoteId = intent.getLongExtra("NOTE_ID", -1L)
        if (intentNoteId != -1L) {
            noteId = intentNoteId
            isNewNote = false
            addNoteTitle.text = getString(R.string.edit_note_title)
            saveNoteButton.text = getString(R.string.note_update_button_text)
            lifecycleScope.launch {
                val note = notesDao.getNoteById(noteId)
                note?.let {
                    initialNotesItem = it.copy()
                    loadNoteData(it)
                }
                loadAttachmentsForNote()
                loadSubtasksForNote()
            }
        } else {
            isNewNote = true
            addNoteTitle.text = getString(R.string.add_note_title)
            saveNoteButton.text = getString(R.string.save_note_button_text)
            lifecycleScope.launch {
                createPlaceholderNoteAndLoadRelatedData()
            }
        }

        buttonPickFile.setOnClickListener { attachmentManager.checkAndRequestMediaPermissionsForPicker() }
        buttonTakePhotoVideo.setOnClickListener { attachmentManager.checkAndRequestCameraPermission() }
        buttonRecordAudio.setOnClickListener { attachmentManager.launchRecordAudioActivity() }
        buttonAddHyperlink.setOnClickListener { attachmentManager.showAddHyperlinkDialog() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleOnBackPressedLogic()
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        attachmentManager.onDestroy()
        subtaskManager.onDestroy()
    }

    private fun handleOnBackPressedLogic() {
        if (hasUnsavedChanges()) {
            val dialogMessage = if (isNewNote) {
                getString(R.string.exit_without_saving_note_message)
            } else {
                getString(R.string.exit_without_saving_changes_note_message)
            }

            AlertDialog.Builder(this)
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

                                if (isNewNote) {
                                    // In ADD mode, delete the entire placeholder record and its children.
                                    val currentNote = notesDao.getNoteById(noteId)
                                    if (currentNote != null && !currentNote.validatedItem) {
                                        subtaskDao.deleteUnvalidatedSubtasksForEvent("Note", noteId)
                                        notesDao.deleteNote(noteId)
                                        Log.d("AddNoteActivity", "Deleted placeholder Note (ID: $noteId) and related data.")
                                    }
                                } else {
                                    // In EDIT mode, only clean up newly added (unvalidated) children.
                                    attachmentDao.deleteUnvalidatedAttachmentsForEvent("Note", noteId)
                                    subtaskDao.deleteUnvalidatedSubtasksForEvent("Note", noteId)
                                    Log.d("AddNoteActivity", "Cleaned up unvalidated items for existing Note (ID: $noteId).")
                                }
                            } catch (e: Exception) {
                                Log.e("AddNoteActivity", "Error during cleanup on back press: ${e.message}", e)
                            }
                        }
                        this@AddNoteActivity.finish()
                    }
                }
                .setNegativeButton(getString(R.string.dialog_cancel_button).uppercase(Locale.getDefault())) { dialog, _ ->
                    dialog.cancel()
                }
                .show()
        } else {
            this@AddNoteActivity.finish()
        }
    }

    private suspend fun createPlaceholderNoteAndLoadRelatedData() {

        withContext(Dispatchers.IO) {
            try {
                val currentZoneId = ZoneId.systemDefault()
                val placeholderNote = NotesItem(
                    subject = null,
                    type = null,
                    description = null,
                    keywords = null,
                    validatedItem = false,
                    archivedNote = false,
                    lastModifiedMillis = System.currentTimeMillis(),
                    creationTimezone = currentZoneId.id
                )
                noteId = notesDao.insertNote(placeholderNote)
                withContext(Dispatchers.Main) {
                    Log.d("AddNoteActivity", "Placeholder Note created with ID: $noteId")
                    loadAttachmentsForNote()
                    loadSubtasksForNote()
                    initialNotesItem = placeholderNote.copy(noteId = noteId)
                    updateTimezoneDisplay(initialNotesItem) // Display timezone info
                }
            } catch (e: Exception) {
                Log.e("AddNoteActivity", "Error creating placeholder Note: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AddNoteActivity, "Error initializing Note. Please restart.", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    private fun loadAttachmentsForNote() {
        if (noteId != -1L) {
            lifecycleScope.launch {
                attachmentDao.getAttachmentsForEvent("Note", noteId).collectLatest { attachments ->
                    attachmentAdapter.submitList(attachments)
                    initialAttachmentsLoaded = attachments.toList()
                    val hasAttachments = attachments.isNotEmpty()
                    noAttachmentsFoundTextView.visibility = if (hasAttachments) View.GONE else View.VISIBLE
                    recyclerViewAttachments.visibility = if (hasAttachments) View.VISIBLE else View.GONE
                    buttonShareAttachments.visibility = if (hasAttachments) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun loadSubtasksForNote() {
        if (noteId != -1L) {
            lifecycleScope.launch {
                subtaskDao.getSubtasksForEvent("Note", noteId).collectLatest { subtasks ->
                    subtaskAdapter.submitList(subtasks)
                    initialSubtasksLoaded = subtasks.toList()
                    val hasSubtasks = subtasks.isNotEmpty()
                    noSubtasksFoundTextView.visibility = if (hasSubtasks) View.GONE else View.VISIBLE
                    recyclerViewSubtasks.visibility = if (hasSubtasks) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun loadNoteData(note: NotesItem) {
        addNoteTitle.text = getString(R.string.edit_note_title)
        noteSubjectAutoCompleteTextView.setText(note.subject)
        editTextNoteType.setText(note.type)
        editTextNoteDescription.setText(note.description)
        editTextNoteKeywords.setText(note.keywords)
        checkBoxArchivedNote.isChecked = note.archivedNote
        updateTimezoneDisplay(note) // TIMEZONE CHANGE
    }

    // TIMEZONE CHANGE: New function to display timezone information
    private fun updateTimezoneDisplay(note: NotesItem?) {
        // MODIFIED: Use safe calls ?. to prevent crash if any view is not ready
        if (note == null) {
            timezoneInfoLayout?.visibility = View.GONE
            return
        }

        timezoneInfoLayout?.visibility = View.VISIBLE
        val dateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm (dd-MMM-yyyy)", Locale.ENGLISH)
        val currentSystemZone = ZoneId.systemDefault()

        val originalZoneId = try {
            note.creationTimezone?.let { ZoneId.of(it) } ?: currentSystemZone
        } catch (e: Exception) {
            currentSystemZone
        }

        val lastModifiedInstant = Instant.ofEpochMilli(note.lastModifiedMillis)

        // Display the time in the original timezone
        val timeInOriginalZone = LocalDateTime.ofInstant(lastModifiedInstant, originalZoneId)
        originalTimezoneTextView?.text = getString(R.string.original_timezone_display_format, timeInOriginalZone.format(dateTimeFormatter), originalZoneId.id)
        originalTimezoneTextView?.visibility = View.VISIBLE

        // If the current timezone is different, also show the time in the current timezone
        if (currentSystemZone.id != originalZoneId.id) {
            val timeInCurrentZone = LocalDateTime.ofInstant(lastModifiedInstant, currentSystemZone)
            currentTimezoneTextView?.text = getString(R.string.current_timezone_display_format, timeInCurrentZone.format(dateTimeFormatter), currentSystemZone.id)
            currentTimezoneTextView?.visibility = View.VISIBLE
        } else {
            currentTimezoneTextView?.visibility = View.GONE
        }
    }


    private fun loadAutoSuggestions() {
        lifecycleScope.launch {
            try {
                val classSessionCourseNames = withContext(Dispatchers.IO) {
                    timetableDao.getUniqueLectureCourseNames()
                }
                notesDao.getAllNotes().collectLatest { notes ->
                    val subjects = notes.mapNotNull { it.subject }.distinct()
                    val combinedSubjects = (classSessionCourseNames + subjects)
                        .filter { it.isNotBlank() }
                        .distinct()
                        .sorted()
                    val subjectAdapter = ArrayAdapter(this@AddNoteActivity, android.R.layout.simple_dropdown_item_1line, combinedSubjects)
                    noteSubjectAutoCompleteTextView.setAdapter(subjectAdapter)
                    noteSubjectAutoCompleteTextView.threshold = 1

                    val types = notes.mapNotNull { it.type }.distinct().sorted()
                    val typeAdapter = ArrayAdapter(this@AddNoteActivity, android.R.layout.simple_dropdown_item_1line, types)
                    editTextNoteType.setAdapter(typeAdapter)
                    editTextNoteType.threshold = 1

                    val descriptions = notes.mapNotNull { it.description }
                        .flatMap { it.split(Regex("[\\s.,;!?]+")) }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .sorted()
                    val descriptionAdapter = ArrayAdapter(this@AddNoteActivity, android.R.layout.simple_dropdown_item_1line, descriptions)
                    editTextNoteDescription.setAdapter(descriptionAdapter)
                    editTextNoteDescription.threshold = 1
                }
            } catch (e: Exception) {
                Log.e("AddNoteActivity", "Error loading auto-suggestions: ${e.message}", e)
            }
        }
    }



    private fun hasUnsavedChanges(): Boolean {
        val currentSubject = noteSubjectAutoCompleteTextView.text.toString().trim()
        val currentType = editTextNoteType.text.toString().trim()
        val currentDescription = editTextNoteDescription.text.toString().trim()
        val currentKeywords = editTextNoteKeywords.text.toString().trim() // NEW: Get keywords text
        val currentArchivedState = checkBoxArchivedNote.isChecked // NEW: Get current archived state
        val currentAttachments = attachmentAdapter.currentList
        val currentSubtasks = subtaskAdapter.currentList

        val initial = initialNotesItem
        if (initial == null) return true

        // Compare all content fields including keywords and archivedNote
        if (currentSubject != (initial.subject ?: "") ||
            currentType != (initial.type ?: "") ||
            currentDescription != (initial.description ?: "") ||
            currentKeywords != (initial.keywords ?: "") || // NEW: Compare keywords
            currentArchivedState != initial.archivedNote) { // NEW: Compare archived state
            Log.d("AddNoteActivity", "hasUnsavedChanges: TRUE (Core fields changed)")
            return true
        }

        val currentValidatedAttachments = currentAttachments.filter { it.validatedItem }
        val currentUnvalidatedAttachments = currentAttachments.filter { !it.validatedItem }

        if (currentUnvalidatedAttachments.isNotEmpty()) {
            Log.d("AddNoteActivity", "hasUnsavedChanges: TRUE (Unvalidated attachments present)")
            return true
        }

        if (initialAttachmentsLoaded.size != currentValidatedAttachments.size) {
            Log.d("AddNoteActivity", "hasUnsavedChanges: TRUE (Validated attachment count changed)")
            return true
        }

        val initialAttachmentSet = initialAttachmentsLoaded.map { it.uriString to it.displayName }.toSet()
        val currentValidatedAttachmentSet = currentValidatedAttachments.map { it.uriString to it.displayName }.toSet()

        if (initialAttachmentSet != currentValidatedAttachmentSet) {
            Log.d("AddNoteActivity", "hasUnsavedChanges: TRUE (Validated attachment content changed)")
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

        // Check if subtask changes are only deletions
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

        if (hasSubtaskChanges and !isOnlySubtaskDeletions) {
            Log.d("AddNoteActivity", "hasUnsavedChanges: TRUE (hasSubtaskChanges is TRUE)")
            return true
        }

        Log.d("AddNoteActivity", "hasUnsavedChanges: FALSE (No changes detected)")
        return false
    }

    private fun saveNote() {
        val subject = noteSubjectAutoCompleteTextView.text.toString().trim()
        val type = editTextNoteType.text.toString().trim()
        val description = editTextNoteDescription.text.toString().trim()
        val keywords = editTextNoteKeywords.text.toString().trim()
        val archivedNote = checkBoxArchivedNote.isChecked

        // --- MANDATORY FIELD VALIDATION ---
        if (subject.isBlank()) {
            Toast.makeText(this, R.string.note_subject_required, Toast.LENGTH_LONG).show()
            noteSubjectAutoCompleteTextView.error = getString(R.string.note_subject_required)
            noteSubjectAutoCompleteTextView.requestFocus()
            return
        } else {
            noteSubjectAutoCompleteTextView.error = null
        }

        if (type.isBlank()) {
            Toast.makeText(this, R.string.note_type_required, Toast.LENGTH_LONG).show()
            editTextNoteType.error = getString(R.string.note_type_required)
            editTextNoteType.requestFocus()
            return
        } else {
            editTextNoteType.error = null
        }

        if (description.isBlank()) {
            Toast.makeText(this, R.string.note_description_required, Toast.LENGTH_LONG).show()
            editTextNoteDescription.error = getString(R.string.note_description_required)
            editTextNoteDescription.requestFocus()
            return
        } else {
            editTextNoteDescription.error = null
        }

        // Original check, now less critical due to specific field validations above
        if (subject.isEmpty() && type.isEmpty() && description.isEmpty() && keywords.isEmpty() && attachmentAdapter.currentList.isEmpty() && subtaskAdapter.currentList.isEmpty()) { // NEW: Check keywords
            Toast.makeText(this, R.string.empty_note_warning, Toast.LENGTH_SHORT).show()
            return
        }

        val OZI = try {
            initialNotesItem?.creationTimezone?.let { ZoneId.of(it) } ?: ZoneId.systemDefault()
        } catch (e: Exception) {
            ZoneId.systemDefault()
        }


        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val noteToSave = initialNotesItem?.copy(
                        subject = if (subject.isEmpty()) null else subject,
                        type = if (type.isEmpty()) null else type,
                        description = if (description.isEmpty()) null else description,
                        keywords = if (keywords.isEmpty()) null else keywords, // NEW: Set keywords
                        lastModified = LocalDateTime.now(OZI),
                        validatedItem = true,
                        archivedNote = archivedNote // NEW: Set archived state
                    ) ?: NotesItem(
                        subject = if (subject.isEmpty()) null else subject,
                        type = if (type.isEmpty()) null else type,
                        description = if (description.isEmpty()) null else description,
                        keywords = if (keywords.isEmpty()) null else keywords, // NEW: Set keywords
                        validatedItem = true,
                        archivedNote = archivedNote // NEW: Set archived state
                    )

                    val savedNoteId: Long
                    if (noteToSave.noteId == null || noteToSave.noteId == 0L || noteToSave.noteId < 0) {
                        // Insert new note
                        savedNoteId = notesDao.insertNote(noteToSave)
                        Log.d("AddNoteActivity", "New note inserted with ID: $savedNoteId")
                    } else {
                        // Update existing note
                        notesDao.updateNote(noteToSave)
                        savedNoteId = noteToSave.noteId!!
                        Log.d("AddNoteActivity", "Note updated with ID: $savedNoteId")
                    }

                    // Update attachments with the actual savedNoteId and mark as validated
                    val currentAttachments = attachmentAdapter.currentList
                    currentAttachments.forEach { attachment ->
                        val updatedAttachment = attachment.copy(
                            eventRefId = savedNoteId,
                            validatedItem = true
                        )
                        if (attachment.attachmentId == 0L) { // New attachment
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

                    // Mark all subtasks for this Note as validated and update/insert
                    val currentSubtasks = subtaskAdapter.currentList
                    currentSubtasks.forEach { subtask ->
                        val updatedSubtask = subtask.copy(
                            eventRefId = savedNoteId,
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

                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@AddNoteActivity,
                            if (initialNotesItem == null) R.string.note_save_success else R.string.note_update_success,
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@AddNoteActivity,
                            if (initialNotesItem == null) getString(R.string.note_save_failure, e.localizedMessage) else getString(
                                R.string.note_update_failure, e.localizedMessage),
                            Toast.LENGTH_LONG
                        ).show()
                        Log.e("AddNoteActivity", "Error saving/updating note: ${e.message}", e)
                    }
                }
            }
        }
    }

    // --- AttachmentManagerCallbacks Implementation ---
    override fun getCurrentEventType(): String {
        return "Note"
    }

    override fun getCurrentEventRefId(): Long {
        return noteId
    }

    override fun onAttachmentsUpdated() {
        loadAttachmentsForNote() // Reload attachments when notified by AttachmentManager
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
        loadSubtasksForNote() // Reload subtasks when notified by SubtaskManager
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
        Log.d("AddNoteActivity", "Subtask copy process requested by SubtaskManager.")
    }
}
