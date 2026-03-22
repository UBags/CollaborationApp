package com.costheta.cortexa.note

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
import com.costheta.cortexa.data.db.dao.NotesDao
import com.costheta.cortexa.data.db.dao.AttachmentDao
import com.costheta.cortexa.data.models.NotesItem
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import com.costheta.cortexa.data.db.dao.SubtaskDao // NEW: Import SubtaskDao
import com.costheta.cortexa.util.autofill.FuzzyAutoCompleteTextView // NEW: Import FuzzyAutoCompleteTextView
import me.xdrop.fuzzywuzzy.FuzzySearch // NEW: Import FuzzySearch
import com.costheta.cortexa.util.uirefresh.PeriodicallyRefreshAppCompatActivity // NEW: Import PeriodicallyRefreshAppCompatActivity
import androidx.recyclerview.widget.ListAdapter // NEW: Import ListAdapter
import com.costheta.cortexa.R
import com.costheta.cortexa.util.keyboard.FuzzyAutoCompleteDisappearingKeyboard
import java.time.Instant

class NotesListActivity : PeriodicallyRefreshAppCompatActivity() {

    private lateinit var notesRecyclerView: RecyclerView
    private lateinit var noNotesTextView: TextView
    private lateinit var addNoteFab: FloatingActionButton
    private lateinit var editTextSearchNotes: FuzzyAutoCompleteTextView // Changed to FuzzyAutoCompleteTextView
    private lateinit var notesAdapter: NotesAdapter
    private lateinit var notesDao: NotesDao
    private lateinit var attachmentDao: AttachmentDao
    private lateinit var subtaskDao: SubtaskDao // NEW: SubtaskDao

    // Store the latest list of all notes
    private var allDisplayedNotesCache: List<NotesItem> = emptyList()
    private var searchSuggestions: List<String> = emptyList()

    // Variables for FAB movement
    private var lastX: Int = 0
    private var lastY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var isClick: Boolean = true // Flag to distinguish between click and drag

    // Change 3: Implement abstract methods from PeriodicallyRefreshAppCompatActivity
    override fun getRecyclerView(): RecyclerView = notesRecyclerView
    override fun getAdapter(): ListAdapter<*, *> = notesAdapter

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.notes_activity_list)

        notesRecyclerView = findViewById(R.id.notesRecyclerView)
        noNotesTextView = findViewById(R.id.noNotesTextView)
        addNoteFab = findViewById(R.id.addNoteFab)
        editTextSearchNotes = findViewById(R.id.editTextSearchNotes) // Initialize search bar

        FuzzyAutoCompleteDisappearingKeyboard.getInstance().register(editTextSearchNotes)

        notesDao = AppDatabase.getDatabase(applicationContext).notesDao()
        attachmentDao = AppDatabase.getDatabase(applicationContext).attachmentDao()
        subtaskDao = AppDatabase.getDatabase(applicationContext).subtaskDao() // NEW: Initialize SubtaskDao

        // Initialize FuzzyAutoCompleteTextView with an empty adapter to prevent NPE
        editTextSearchNotes.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, ArrayList<String>()))

        setupRecyclerView()
        setupSearchBar()

        addNoteFab.setOnClickListener {
            val intent = Intent(this, AddNoteActivity::class.java)
            startActivity(intent)
        }

        // Set up FAB touch listener for movement
        addNoteFab.setOnTouchListener { view, event ->
            val parentView = view.parent as View
            val parentWidth = parentView.width - view.width
            val parentHeight = parentView.height - view.height

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
        addNoteFab.post {
            val parentView = addNoteFab.parent as View
            // Define these dimensions in dimens.xml if they don't exist
            val marginEnd = resources.getDimensionPixelSize(R.dimen.fab_margin_end) // Define this in dimens.xml
            val marginBottom = resources.getDimensionPixelSize(R.dimen.fab_margin_bottom) // Define this in dimens.xml

            addNoteFab.x = (parentView.width - addNoteFab.width - marginEnd).toFloat()
            addNoteFab.y = (parentView.height - addNoteFab.height - marginBottom).toFloat()
        }

        loadAndFilterNotes()
    }

    private fun setupRecyclerView() {
        notesAdapter = NotesAdapter(
            onDeleteClick = { noteItem -> showDeleteConfirmationDialog(noteItem) },
            onEditClick = { noteItem -> navigateToEditNote(noteItem) },
            onArchiveClick = { noteItem, isArchived -> handleNoteArchiveToggle(noteItem, isArchived) },
            attachmentDao = attachmentDao, // Pass attachmentDao
            coroutineScope = lifecycleScope // Pass lifecycleScope
        )

        notesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@NotesListActivity)
            adapter = notesAdapter
        }

    }

    /**
     * NEW: Handles the logic when the 'Archive Note' checkbox is toggled.
     */
    private fun handleNoteArchiveToggle(noteItem: NotesItem, isArchived: Boolean) {
        lifecycleScope.launch {
            try {
                // MODIFIED: Update lastModifiedMillis to the current time when archiving/unarchiving
                val updatedNote = noteItem.copy(
                    archivedNote = isArchived,
                    lastModifiedMillis = Instant.now().toEpochMilli()
                )
                withContext(Dispatchers.IO) {
                    notesDao.updateNote(updatedNote)
                }
                // The list will automatically refresh because we are collecting the flow.
                // Calling this ensures the filter is reapplied immediately.
                loadAndFilterNotes()
                val status = if (isArchived) "archived" else "unarchived"
                Toast.makeText(this@NotesListActivity, "Note '${noteItem.subject}' ${status}.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("NotesListActivity", "Error updating note archive status: ${e.message}", e)
                Toast.makeText(this@NotesListActivity, "Failed to update note status.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupSearchBar() {
        // NEW: Set an empty ArrayAdapter initially to prevent NullPointerException
        editTextSearchNotes.setAdapter(
            ArrayAdapter(
                this,
                android.R.layout.simple_dropdown_item_1line,
                emptyList<String>()
            )
        )

        lifecycleScope.launch {
            try {
                val currentTimeMillis = Instant.now().toEpochMilli()
                notesDao.getAllDisplayableNotes(currentTimeMillis).collectLatest { notes ->
                    // Create fuzzy search suggestions from all notes
                    // Create a list of unique concatenated strings for fuzzy search suggestions
                    // Format: "{courseName} {examDetails} {venue}"
                    searchSuggestions = notes
                        .map { note ->
                            "${note.subject.orEmpty()} ${note.description.orEmpty()} ${note.type.orEmpty()} ${note.keywords.orEmpty()}"
                        }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .sorted()

                    editTextSearchNotes.setFuzzySuggestions(searchSuggestions)

                    withContext(Dispatchers.Main) {
                        // Set the combined list of suggestions to the FuzzyAutoCompleteTextView
                        editTextSearchNotes.setFuzzySuggestions(searchSuggestions)
                        editTextSearchNotes.threshold = 1 // Start suggesting after 1 character
                    }
                }
            } catch (e: Exception) {
                Log.e("ExamListActivity", "Error loading unique names for search: ${e.message}", e)
            }
        }

        editTextSearchNotes.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // IMPORTANT: Explicitly trigger the FuzzyAutoCompleteTextView's own filter for suggestions
                editTextSearchNotes.filter.filter(s) // Correct way to trigger filtering

                // Also filter the main RecyclerView based on the search query
                filterNotes(s.toString(), allDisplayedNotesCache) // Filter based on the cache
            }
            override fun afterTextChanged(s: Editable?) {}
        })

    }

    override fun onResume() {
        super.onResume() // Call super.onResume() to trigger the periodic update from base class
//        loadNotes() // Refresh notes list when returning to this activity
    }

    override fun triggerDataRefresh() {
        loadAndFilterNotes() // Refresh notes list when returning to this activity
    }

    // Change 4: Remove manual ui update handler calls from onPause (now handled by base class)
    // No explicit onPause override is needed if no other custom logic is present.
    // If it exists, ensure super.onPause() is called.
    // override fun onPause() {
    //     super.onPause()
    // }

    private fun loadAndFilterNotes() {
        lifecycleScope.launch {
            val currentTimeMillis = Instant.now().toEpochMilli()
            notesDao.getAllDisplayableNotes(currentTimeMillis).collectLatest { notes ->
                allDisplayedNotesCache = notes // Update the cache with the latest notes
                val currentSearchQuery = editTextSearchNotes.text.toString().trim()
                // Always pass all notes to filterNotes, which will handle sorting based on query
                filterNotes(currentSearchQuery, allDisplayedNotesCache)
                updateNoNotesVisibility()
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun filterNotes(query: String, allNotes: List<NotesItem>) {
        val lowerCaseQuery = query.lowercase(Locale.getDefault()).trim()

        val filteredAndSortedList = if (lowerCaseQuery.isBlank()) {
            // If query is blank, sort by last modified date (descending)
            allNotes.sortedWith(compareByDescending<NotesItem> { it.lastModified }.thenBy { it.subject })
        } else {
            val scoredNotes = allNotes
                .map { note ->
                    val subject = note.subject?.lowercase(Locale.getDefault()) ?: ""
                    val type = note.type?.lowercase(Locale.getDefault()) ?: ""
                    val description = note.description?.lowercase(Locale.getDefault()) ?: ""
                    val keywords = note.keywords?.lowercase(Locale.getDefault()) ?: ""
                    val combinedString = "$subject $type $description $keywords"
                    val score = FuzzySearch.weightedRatio(lowerCaseQuery, combinedString.lowercase(Locale.getDefault()))
                    Triple(note, score, note.lastModified) // Store note, score, and lastModifiedTime
                }
            // .filter { it.second > 0 } // Only include notes that have a positive fuzzy match score

            // Define score brackets for sorting
            val brackets = listOf(100, 90, 80, 70, 60, 50, 40, 30, 20, 10, 0)

            val groupedAndSorted = brackets.flatMap { upperBound ->
                val lowerBound = if (upperBound == 0) 0 else upperBound - 10
                scoredNotes
                    .filter { (_, score, _) ->
                        // Ensure score ranges are mutually exclusive to prevent duplicates
                        if (upperBound == 100) {
                            (score >= lowerBound) && (score <= upperBound) // For the highest bracket, include the upper bound (100)
                        } else {
                            (score >= lowerBound) && (score < upperBound) // For all other brackets, include lower bound but exclude upper bound
                        }
                    }
                    .sortedWith(compareByDescending<Triple<NotesItem, Int, java.time.LocalDateTime>> { it.third } // Sort by lastModified (descending)
                        .thenBy { it.first.subject } // Then by subject (ascending)
                    )
                    .map { it.first } // Extract the original NotesItem object
            }
            groupedAndSorted
        }

        notesAdapter.submitList(filteredAndSortedList)
        // CORRECTED: Force a full redraw to update colors and order correctly on refresh.
        // This is necessary because DiffUtil won't detect changes based on the passage of time.
        notesAdapter.notifyDataSetChanged()

        // noNotesTextView.visibility = if (filteredAndSortedList.isEmpty()) View.VISIBLE else View.GONE
        // notesRecyclerView.visibility = if (filteredAndSortedList.isEmpty()) View.GONE else View.VISIBLE
    }

    /**
     * Updates the visibility of the "no actions found" TextView based on the adapter's item count.
     */
    private fun updateNoNotesVisibility() {
        noNotesTextView.visibility = if (notesAdapter.itemCount == 0) View.VISIBLE else View.GONE
        notesRecyclerView.visibility = if (notesAdapter.itemCount == 0) View.GONE else View.VISIBLE
    }


    private fun showDeleteConfirmationDialog(noteItem: NotesItem) {
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_delete_title)
            .setMessage(getString(R.string.confirm_delete_note_message, noteItem.subject ?: "Unnamed Note"))
            .setPositiveButton(R.string.button_delete) { dialog, _ ->
                deleteNote(noteItem)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.dialog_cancel_button) { dialog, _ ->
                dialog.cancel()
            }
            .show()
    }

    private fun deleteNote(noteItem: NotesItem) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    noteItem.noteId?.let { id ->
                        attachmentDao.deleteAttachmentsForEvent("Note", id) // Delete associated attachments
                        subtaskDao.deleteAllSubtasksForEvent("Note", id) // NEW: Delete associated subtasks
                        notesDao.deleteNote(id)
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@NotesListActivity, getString(R.string.note_deleted_success, noteItem.subject ?: "Unnamed Note"), Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@NotesListActivity, getString(R.string.note_delete_failure, e.localizedMessage), Toast.LENGTH_LONG).show()
                        Log.e("NotesListActivity", "Error deleting note: ${e.message}", e)
                    }
                }
            }
        }
    }

    private fun navigateToEditNote(noteItem: NotesItem) {
        val intent = Intent(this, AddNoteActivity::class.java).apply {
            putExtra("NOTE_ID", noteItem.noteId)
        }
        startActivity(intent)
    }
}
