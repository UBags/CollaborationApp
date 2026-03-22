package com.costheta.cortexa.todo

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.costheta.cortexa.data.db.AppDatabase
import com.costheta.cortexa.data.db.dao.TodoDao
import com.costheta.cortexa.data.db.dao.AttachmentDao
import com.costheta.cortexa.data.models.TodoItem
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import com.costheta.cortexa.data.db.dao.SubtaskDao
import android.annotation.SuppressLint
import android.widget.ArrayAdapter
import com.costheta.cortexa.util.uirefresh.PeriodicallyRefreshAppCompatActivity
import androidx.recyclerview.widget.ListAdapter
import com.costheta.cortexa.NotificationHelper
import com.costheta.cortexa.R
// MODIFIED: Import SyncedGoogleTaskDao and the new wrapper
import com.costheta.cortexa.data.db.dao.SyncedGoogleTaskDao
import com.costheta.cortexa.external.tasks.ViewGoogleTaskActivity
// END MODIFIED
import com.costheta.cortexa.util.autofill.FuzzyAutoCompleteTextView
import com.costheta.cortexa.util.keyboard.FuzzyAutoCompleteDisappearingKeyboard
import me.xdrop.fuzzywuzzy.FuzzySearch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId


class TodoListActivity : PeriodicallyRefreshAppCompatActivity() {

    private lateinit var todosRecyclerView: RecyclerView
    private lateinit var noTodosTextView: TextView
    private lateinit var addTodoFab: FloatingActionButton
    private lateinit var editTextSearchTodos: FuzzyAutoCompleteTextView
    private lateinit var todoAdapter: TodoAdapter
    private lateinit var todoDao: TodoDao
    // NEW: DAO for Google Tasks
    private lateinit var syncedGoogleTaskDao: SyncedGoogleTaskDao
    private lateinit var attachmentDao: AttachmentDao
    private lateinit var subtaskDao: SubtaskDao
    private lateinit var notificationHelper: NotificationHelper

    private var lastX: Int = 0
    private var lastY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var isClick: Boolean = true

    // MODIFIED: Cache now holds DisplayableTask objects
    private var allDisplayableTasksCache: List<DisplayableTask> = emptyList()
    private var allUniqueSuggestions: List<String> = emptyList()

    override fun getRecyclerView(): RecyclerView = todosRecyclerView
    override fun getAdapter(): ListAdapter<*, *> = todoAdapter

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.todo_activity_list)

        todosRecyclerView = findViewById(R.id.todosRecyclerView)
        noTodosTextView = findViewById(R.id.noTodosTextView)
        addTodoFab = findViewById(R.id.addTodoFab)
        editTextSearchTodos = findViewById(R.id.editTextSearchTodos)

        FuzzyAutoCompleteDisappearingKeyboard.getInstance().register(editTextSearchTodos)

        val db = AppDatabase.getDatabase(applicationContext)
        todoDao = db.todoDao()
        // NEW: Initialize Google Task DAO
        syncedGoogleTaskDao = db.syncedGoogleTaskDao()
        attachmentDao = db.attachmentDao()
        subtaskDao = db.subtaskDao()
        notificationHelper = NotificationHelper(applicationContext)

        setupRecyclerView()
        setupSearchBar()

        addTodoFab.setOnClickListener {
            val intent = Intent(this, AddTodoActivity::class.java)
            startActivity(intent)
        }

        addTodoFab.setOnTouchListener { view, event ->
            handleFabTouch(view, event)
        }

        addTodoFab.post {
            positionFab()
        }
    }

    private fun setupRecyclerView() {
        // MODIFIED: The adapter now handles DisplayableTask
        todoAdapter = TodoAdapter(
            onDeleteClick = { displayableTask ->
                // Only allow deleting native todos
                if (displayableTask is DisplayableTask.AppTodo) {
                    confirmDeleteTodo(displayableTask.todo)
                }
            },
            onEditClick = { displayableTask ->
                when (displayableTask) {
                    is DisplayableTask.AppTodo -> editTodo(displayableTask.todo)
                    is DisplayableTask.GoogleTask -> viewGoogleTask(displayableTask.task)
                }
            },
            onToggleComplete = { displayableTask, isCompleted ->
                // Only allow toggling native todos
                if (displayableTask is DisplayableTask.AppTodo) {
                    toggleTodoCompletion(displayableTask.todo, isCompleted)
                }
            },
            attachmentDao = attachmentDao,
            coroutineScope = lifecycleScope
        )
        todosRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@TodoListActivity)
            adapter = todoAdapter
        }
    }

    private fun setupSearchBar() {
        editTextSearchTodos.setAdapter(
            ArrayAdapter(
                this,
                android.R.layout.simple_dropdown_item_1line,
                emptyList<String>()
            )
        )

        // TextWatcher remains the same, but the data source for filtering changes
        editTextSearchTodos.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                editTextSearchTodos.filter.filter(s)
                filterAndSortTasks(s.toString(), allDisplayableTasksCache)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    // MODIFIED: This function now combines flows and handles both types of tasks
    private fun loadAndFilterTasks() {
        lifecycleScope.launch {
            try {
                val currentTimeMillis = Instant.now().toEpochMilli()

                // Combine the flow of native todos and Google tasks
                val nativeTodosFlow = todoDao.getDisplayableTodos(currentTimeMillis)
                val googleTasksFlow = syncedGoogleTaskDao.getAllDisplayableTasks(currentTimeMillis)

                nativeTodosFlow.combine(googleTasksFlow) { nativeTodos, googleTasks ->
                    val appTasks = nativeTodos.map { DisplayableTask.AppTodo(it) }
                    val gTasks = googleTasks.map { DisplayableTask.GoogleTask(it) }
                    appTasks + gTasks // Combine into a single list
                }.collectLatest { combinedList ->
                    allDisplayableTasksCache = combinedList

                    // Update search suggestions based on the combined list
                    allUniqueSuggestions = combinedList
                        .map { task -> "${task.title} ${task.notes.orEmpty()}" }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .sorted()

                    withContext(Dispatchers.Main) {
                        editTextSearchTodos.setFuzzySuggestions(allUniqueSuggestions)
                        editTextSearchTodos.threshold = 1

                        val currentSearchQuery = editTextSearchTodos.text.toString().trim()
                        filterAndSortTasks(currentSearchQuery, allDisplayableTasksCache)
                        updateNoTodosVisibility()
                    }
                }
            } catch (e: CancellationException) {
                Log.d("TodoListActivity", "Task loading job cancelled: ${e.message}")
            } catch (e: Exception) {
                Log.e("TodoListActivity", "Error loading tasks: ${e.message}", e)
            }
        }
    }


    override fun onResume() {
        super.onResume()
        triggerDataRefresh()
    }

    override fun triggerDataRefresh() {
        loadAndFilterTasks()
    }

    private fun getPriorityOrder(priority: String?): Int {
        return when (priority?.lowercase(Locale.getDefault())) {
            "high" -> 3
            "medium" -> 2
            "low" -> 1
            else -> 0
        }
    }

    // MODIFIED: This function now filters and sorts DisplayableTask objects
    @SuppressLint("NotifyDataSetChanged")
    private fun filterAndSortTasks(query: String, tasks: List<DisplayableTask>) {
        val filteredAndSortedList = if (query.isBlank()) {
            tasks.sortedWith(
                compareBy<DisplayableTask> { it.isCompleted }
                    .thenBy { it.dueDateTimeMillis ?: Long.MAX_VALUE }
                    .thenBy { it.title.lowercase(Locale.getDefault()) }
            )
        } else {
            val scoredTasks = tasks.map { task ->
                val targetString = "${task.title} ${task.notes.orEmpty()}"
                val score = FuzzySearch.weightedRatio(query.lowercase(Locale.getDefault()), targetString.lowercase(Locale.getDefault()))
                Triple(task, score, task.dueDateTimeMillis ?: Long.MAX_VALUE)
            }

            scoredTasks
                .filter { it.second > 45 }
                .sortedWith(
                    compareByDescending<Triple<DisplayableTask, Int, Long>> { it.second }
                        .thenBy { it.first.isCompleted }
                        .thenBy { it.third }
                        .thenBy { it.first.title.lowercase(Locale.getDefault()) }
                )
                .map { it.first }
        }

        todoAdapter.submitList(filteredAndSortedList)
        todoAdapter.notifyDataSetChanged()
    }

    private fun updateNoTodosVisibility() {
        noTodosTextView.visibility = if (todoAdapter.itemCount == 0) View.VISIBLE else View.GONE
        todosRecyclerView.visibility = if (todoAdapter.itemCount == 0) View.GONE else View.VISIBLE
    }

    private fun confirmDeleteTodo(todoItem: TodoItem) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.confirm_delete_title))
            .setMessage(getString(R.string.confirm_delete_todo_message, todoItem.title))
            .setPositiveButton(R.string.button_delete) { dialog, _ ->
                deleteTodo(todoItem)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.dialog_cancel_button) { dialog, _ ->
                dialog.cancel()
            }
            .show()
    }

    private fun deleteTodo(todoItem: TodoItem) {
        lifecycleScope.launch {
            try {
                val deletedRows = withContext(Dispatchers.IO) {
                    todoItem.todoId?.let { id ->
                        notificationHelper.cancelTodoNotifications(id)
                        attachmentDao.deleteAttachmentsForEvent("ToDo", id)
                        subtaskDao.deleteAllSubtasksForEvent("ToDo", id)
                        todoDao.deleteTodo(id)
                    }
                }
                withContext(Dispatchers.Main) {
                    if (deletedRows != null && deletedRows > 0) {
                        Toast.makeText(this@TodoListActivity, getString(R.string.todo_deleted_success, todoItem.title), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@TodoListActivity, getString(R.string.todo_delete_failure_error, "Item not found or already deleted."), Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("TodoListActivity", "Error deleting To-Do: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@TodoListActivity, "Error deleting To-Do", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun editTodo(todoItem: TodoItem) {
        val intent = Intent(this, AddTodoActivity::class.java).apply {
            putExtra("TODO_ID", todoItem.todoId)
        }
        startActivity(intent)
    }

    // NEW: Function to launch the ViewGoogleTaskActivity
    private fun viewGoogleTask(task: com.costheta.cortexa.data.models.SyncedGoogleTask) {
        val intent = Intent(this, ViewGoogleTaskActivity::class.java).apply {
            putExtra("TASK_LOCAL_ID", task.localId)
        }
        startActivity(intent)
    }

    private fun toggleTodoCompletion(todoItem: TodoItem, isCompleted: Boolean) {
        lifecycleScope.launch {
            try {
                val latestTodo = withContext(Dispatchers.IO) { todoItem.todoId?.let { todoDao.getTodoById(it) } }
                if (latestTodo == null) {
                    Log.e("TodoListActivity", "Cannot toggle completion, To-Do not found: ${todoItem.todoId}")
                    return@launch
                }

                val originalZoneId = try {
                    latestTodo.originalZoneId?.let { ZoneId.of(it) } ?: ZoneId.systemDefault()
                } catch (e: Exception) {
                    ZoneId.systemDefault()
                }

                val updatedTodo = latestTodo.copy(
                    isCompleted = isCompleted,
                    completedTimeInMillis = if (isCompleted) Instant.now().toEpochMilli() else null,
                    lastModified = LocalDateTime.now(originalZoneId)
                )
                withContext(Dispatchers.IO) { todoDao.updateTodo(updatedTodo) }

                if (isCompleted) {
                    notificationHelper.cancelTodoNotifications(updatedTodo.todoId!!)
                } else if (!updatedTodo.silenceNotifications) {
                    notificationHelper.scheduleTodoNotifications(updatedTodo)
                }

                Toast.makeText(this@TodoListActivity, if (isCompleted) getString(R.string.todo_completed_status) else getString(
                    R.string.todo_pending_status
                ), Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                Log.e("TodoListActivity", "Error toggling To-Do completion: ${e.message}", e)
                Toast.makeText(this@TodoListActivity, "Error updating status", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun handleFabTouch(view: View, event: MotionEvent): Boolean {
        val parentWidth = (view.parent as View).width - view.width
        val parentHeight = (view.parent as View).height - view.height

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.rawX.toInt()
                lastY = event.rawY.toInt()
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isClick = true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX.toInt() - lastX
                val dy = event.rawY.toInt() - lastY
                val newX = (view.x + dx).coerceIn(0f, parentWidth.toFloat())
                val newY = (view.y + dy).coerceIn(0f, parentHeight.toFloat())
                view.x = newX
                view.y = newY
                lastX = event.rawX.toInt()
                lastY = event.rawY.toInt()
                if (Math.abs(event.rawX - initialTouchX) > 10 || Math.abs(event.rawY - initialTouchY) > 10) {
                    isClick = false
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isClick) {
                    view.performClick()
                }
            }
            else -> return false
        }
        return true
    }

    private fun positionFab() {
        val parentView = addTodoFab.parent as View
        val marginEnd = resources.getDimensionPixelSize(R.dimen.fab_margin_end)
        val marginBottom = resources.getDimensionPixelSize(R.dimen.fab_margin_bottom)
        addTodoFab.x = (parentView.width - addTodoFab.width - marginEnd).toFloat()
        addTodoFab.y = (parentView.height - addTodoFab.height - marginBottom).toFloat()
    }
}
