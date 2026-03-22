@file:Suppress("DEPRECATION") // This line is added to suppress all deprecation warnings for Google Sign-In.

package com.costheta.cortexa

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.provider.FontRequest
import androidx.emoji2.text.EmojiCompat
import androidx.emoji2.text.FontRequestEmojiCompatConfig
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.costheta.cortexa.action.ActionListActivity
import com.costheta.cortexa.action.AddActionActivity
import com.costheta.cortexa.assignment.AddAssignmentActivity
import com.costheta.cortexa.assignment.AssignmentListActivity
import com.costheta.cortexa.data.db.AppDatabase
import com.costheta.cortexa.data.db.dao.TimetableDao
import com.costheta.cortexa.data.models.Timetable
import com.costheta.cortexa.engagement.AddEngagementActivity
import com.costheta.cortexa.engagement.EngagementListActivity
import com.costheta.cortexa.exam.AddExamActivity
import com.costheta.cortexa.exam.ExamListActivity
// NEW: Import sync managers and Google Sign-In classes
import com.costheta.cortexa.external.calendar.CalendarSyncManager
import com.costheta.cortexa.external.tasks.TasksSyncManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.api.services.tasks.TasksScopes
import com.google.android.gms.common.api.Scope
// END NEW
import com.costheta.cortexa.note.AddNoteActivity
import com.costheta.cortexa.note.NotesListActivity
import com.costheta.cortexa.preferences.PreferencesActivity
import com.costheta.cortexa.timetable.AddTimetableActivity
import com.costheta.cortexa.timetable.ArchivedTimetableDeletionAdapter
import com.costheta.cortexa.timetable.ViewTimetablesActivity
import com.costheta.cortexa.todo.AddTodoActivity
import com.costheta.cortexa.todo.TodoListActivity
import com.costheta.cortexa.util.image2pdf.mlkit.DocumentScannerActivity
//import com.costheta.cortexa.util.image2pdf.ImageToPdfActivity
import com.costheta.cortexa.util.image2pdf.mlkit.old.MLKitScannerActivity
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    // --- UI elements ---
    private lateinit var uploadAcademicTimetableButton: MaterialButton
    private lateinit var uploadPersonalTimetableButton: MaterialButton
    private lateinit var viewTimetablesButton: MaterialButton
    private lateinit var addAssignmentButton: Button
    private lateinit var viewAssignmentsButton: Button
    private lateinit var addExamButton: Button
    private lateinit var viewExamsButton: Button
    private lateinit var addTodoButton: Button
    private lateinit var viewTodosButton: Button
    private lateinit var addNoteButton: Button
    private lateinit var viewNotesButton: Button
    private lateinit var addEngagementButton: Button
    private lateinit var viewEngagementsButton: Button
    private lateinit var addActionButton: Button
    private lateinit var viewActionsButton: Button
    private lateinit var menuButton: ImageButton
    // NEW: UI for Google Sign-In
    private lateinit var googleSignInButton: Button
    private lateinit var signOutButton: Button
    private lateinit var signedInUserTextView: TextView


    // --- Database & Helpers ---
    private lateinit var timetableDao: TimetableDao
    private lateinit var sharedPreferences: SharedPreferences

    // NEW: Variables for sync managers and Google Sign-In
    private lateinit var googleSignInClient: GoogleSignInClient
    private var calendarSyncManager: CalendarSyncManager? = null
    private lateinit var tasksSyncManager: TasksSyncManager


    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("MainActivity", "POST_NOTIFICATIONS permission granted.")
        } else {
            Toast.makeText(this, "Notification permission denied. You might not receive reminders.", Toast.LENGTH_LONG).show()
        }
    }

    // NEW: Launcher for the Google Sign-In flow
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                Log.d("MainActivity", "Google Sign-In successful for ${account.email}")
                onSignInSuccess(account)
            } catch (e: ApiException) {
                Log.e("MainActivity", "Google Sign-In failed", e)
                Toast.makeText(this, "Google Sign-In failed.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // NEW: Launcher for the Calendar Read permission request
    private val requestCalendarPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("MainActivity", "READ_CALENDAR permission granted. Starting sync.")
            calendarSyncManager?.startSync()
        } else {
            Toast.makeText(this, "Calendar permission denied. Cannot sync calendar events.", Toast.LENGTH_LONG).show()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeDependencies()
        initializeUiElements()
        setupClickListeners()
        initializeDefaultPreferences()
        initializeEmojiCompat()
        requestNotificationPermission()
        updateUploadTimetableButtonState()

        // NEW: Initialize Google Sign-In and Sync Managers
        setupGoogleSignIn()
        tasksSyncManager = TasksSyncManager(this)
    }

    override fun onStart() {
        super.onStart()
        // NEW: Check for an existing signed-in user
        val account = GoogleSignIn.getLastSignedInAccount(this)
        updateUi(account)
        if (account != null) {
            // If user is already signed in, ensure sync managers are ready
            initializeSyncManagers(account)
        }
    }

    override fun onResume() {
        super.onResume()
        updateUploadTimetableButtonState()
        // NEW: Start calendar sync if it's initialized and permission is granted
        if (hasReadCalendarPermission()) {
            calendarSyncManager?.startSync()
        }
    }

    // NEW: Stop the calendar observer when the activity is not visible
    override fun onStop() {
        super.onStop()
        calendarSyncManager?.stopSync()
    }

    private fun initializeDependencies() {
        // MODIFIED: Pointing to the new, dedicated preferences file.
        sharedPreferences = getSharedPreferences("user_preferences", Context.MODE_PRIVATE)
        timetableDao = AppDatabase.getDatabase(applicationContext).timetableDao()
    }

    private fun initializeUiElements() {
        uploadAcademicTimetableButton = findViewById(R.id.uploadAcademicTimetableButton)
        uploadPersonalTimetableButton = findViewById(R.id.uploadPersonalTimetableButton)
        viewTimetablesButton = findViewById(R.id.viewTimetablesButton)
        addAssignmentButton = findViewById(R.id.addAssignmentButton)
        viewAssignmentsButton = findViewById(R.id.viewAssignmentsButton)
        addExamButton = findViewById(R.id.addExamButton)
        viewExamsButton = findViewById(R.id.viewExamsButton)
        addTodoButton = findViewById(R.id.addTodoButton)
        viewTodosButton = findViewById(R.id.viewTodosButton)
        addNoteButton = findViewById(R.id.addNoteButton)
        viewNotesButton = findViewById(R.id.viewNotesButton)
        addEngagementButton = findViewById(R.id.addEngagementButton)
        viewEngagementsButton = findViewById(R.id.viewEngagementsButton)
        addActionButton = findViewById(R.id.addActionButton)
        viewActionsButton = findViewById(R.id.viewActionsButton)
        menuButton = findViewById(R.id.menuButton)
        // NEW: Initialize Sign-In UI
        googleSignInButton = findViewById(R.id.googleSignInButton) // Assumes you add this button to your layout
        signOutButton = findViewById(R.id.signOutButton) // Assumes you add this
        signedInUserTextView = findViewById(R.id.signedInUserTextView) // Assumes you add this
    }

    private fun setupClickListeners() {
        uploadAcademicTimetableButton.setOnClickListener {
            val intent = Intent(this, AddTimetableActivity::class.java).apply {
                putExtra("TIMETABLE_TYPE", "LECTURE")
            }
            startActivity(intent)
        }
        uploadPersonalTimetableButton.setOnClickListener {
            val intent = Intent(this, AddTimetableActivity::class.java).apply {
                putExtra("TIMETABLE_TYPE", "PERSONAL")
            }
            startActivity(intent)
        }
        viewTimetablesButton.setOnClickListener {
            startActivity(Intent(this, ViewTimetablesActivity::class.java))
        }
        addEngagementButton.setOnClickListener { startActivity(Intent(this, AddEngagementActivity::class.java)) }
        viewEngagementsButton.setOnClickListener { startActivity(Intent(this, EngagementListActivity::class.java)) }
        addAssignmentButton.setOnClickListener { startActivity(Intent(this, AddAssignmentActivity::class.java)) }
        viewAssignmentsButton.setOnClickListener { startActivity(Intent(this, AssignmentListActivity::class.java)) }
        addExamButton.setOnClickListener { startActivity(Intent(this, AddExamActivity::class.java)) }
        viewExamsButton.setOnClickListener { startActivity(Intent(this, ExamListActivity::class.java)) }
        addTodoButton.setOnClickListener { startActivity(Intent(this, AddTodoActivity::class.java)) }
        viewTodosButton.setOnClickListener { startActivity(Intent(this, TodoListActivity::class.java)) }
        addNoteButton.setOnClickListener { startActivity(Intent(this, AddNoteActivity::class.java)) }
        viewNotesButton.setOnClickListener { startActivity(Intent(this, NotesListActivity::class.java)) }
        addActionButton.setOnClickListener { startActivity(Intent(this, AddActionActivity::class.java)) }
        viewActionsButton.setOnClickListener { startActivity(Intent(this, ActionListActivity::class.java)) }
        menuButton.setOnClickListener { showPopupMenu(it) }

        // NEW: Click listeners for Sign-In and Sign-Out
        googleSignInButton.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        }
        signOutButton.setOnClickListener {
            googleSignInClient.signOut().addOnCompleteListener {
                updateUi(null)
                calendarSyncManager?.stopSync()
                calendarSyncManager = null
                Toast.makeText(this, "Signed out", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // NEW: All functions from here down are new or heavily modified for sync functionality

    private fun setupGoogleSignIn() {
        // Configure sign-in to request the user's ID, email address, and basic
        // profile. ID and basic profile are included in DEFAULT_SIGN_IN.
        // We also request the scope for reading Google Tasks.
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(TasksScopes.TASKS_READONLY))
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun onSignInSuccess(account: GoogleSignInAccount) {
        updateUi(account)
        initializeSyncManagers(account)

        // After sign-in, immediately trigger the syncs
        tasksSyncManager.triggerImmediateSync()
        // For calendar, we first need to request permission
        checkAndRequestCalendarPermission()
    }

    private fun initializeSyncManagers(account: GoogleSignInAccount) {
        // Initialize Calendar Sync Manager
        if (calendarSyncManager == null) {
            val syncedCalendarEventDao = AppDatabase.getDatabase(this).syncedCalendarEventDao()
            calendarSyncManager = CalendarSyncManager(this, syncedCalendarEventDao, lifecycleScope)
        }
        // Schedule periodic Tasks sync (WorkManager ensures this only happens once)
        tasksSyncManager.schedulePeriodicSync()
    }

    private fun checkAndRequestCalendarPermission() {
        when {
            hasReadCalendarPermission() -> {
                Log.d("MainActivity", "Calendar permission already granted. Starting sync.")
                calendarSyncManager?.startSync()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR) -> {
                // Show a dialog explaining why you need the permission
                AlertDialog.Builder(this)
                    .setTitle("Permission Needed")
                    .setMessage("This app needs to read your device calendar to show your events alongside your engagements.")
                    .setPositiveButton("OK") { _, _ ->
                        requestCalendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            else -> {
                requestCalendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
            }
        }
    }

    private fun updateUi(account: GoogleSignInAccount?) {
        if (account != null) {
            // Signed In
            googleSignInButton.visibility = View.GONE
            signOutButton.visibility = View.VISIBLE
            signedInUserTextView.visibility = View.VISIBLE
            signedInUserTextView.text = "Signed in as: ${account.email}"
        } else {
            // Signed Out
            googleSignInButton.visibility = View.VISIBLE
            signOutButton.visibility = View.GONE
            signedInUserTextView.visibility = View.GONE
        }
    }

    private fun hasReadCalendarPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun updateUploadTimetableButtonState() {
        lifecycleScope.launch {
            val hasActiveLecture = withContext(Dispatchers.IO) { timetableDao.getAllActiveTimetablesByType("LECTURE").isNotEmpty() }
            val hasActivePersonal = withContext(Dispatchers.IO) { timetableDao.getAllActiveTimetablesByType("PERSONAL").isNotEmpty() }

            withContext(Dispatchers.Main) {
                uploadAcademicTimetableButton.isEnabled = !hasActiveLecture
                uploadAcademicTimetableButton.alpha = if (hasActiveLecture) 0.5f else 1.0f

                uploadPersonalTimetableButton.isEnabled = !hasActivePersonal
                uploadPersonalTimetableButton.alpha = if (hasActivePersonal) 0.5f else 1.0f
            }
        }
    }

    private fun showPopupMenu(view: View) {
        val popup = PopupMenu(this, view)
        popup.menuInflater.inflate(R.menu.menu_main, popup.menu)

        // Use reflection to force icons to show.
        try {
            val method = popup.menu.javaClass.getDeclaredMethod(
                "setOptionalIconsVisible",
                Boolean::class.java
            )
            method.isAccessible = true
            method.invoke(popup.menu, true)
        } catch (e: Exception) {
            // Log the exception or handle it appropriately
            e.printStackTrace()
        }

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_see_schedule -> {
                    startActivity(Intent(this, ScheduleActivity::class.java))
                    true
                }
                R.id.menu_delete_archived -> {
                    showArchivedTimetablesForDeletion()
                    true
                }
                // NEW: Added handler for the new menu item
                R.id.menu_set_preferences -> {
                    startActivity(Intent(this, PreferencesActivity::class.java))
                    true
                }
                R.id.menu_my_profile -> {
                    showMyProfileDialog()
                    true
                }
                R.id.menu_help -> {
                    showHelpDialog()
                    true
                }
//                R.id.menu_images_to_pdf -> {
//                    startActivity(Intent(this, ImageToPdfActivity::class.java))
//                    true
//                }
//                R.id.menu_images_to_pdf -> {
//                    // CHANGE this line to start the new MLKitScannerActivity
//                    startActivity(Intent(this, MLKitScannerActivity::class.java))
//                    true
//                }
                R.id.menu_images_to_pdf -> {
                    // CHANGE this line to start the new DocumentScannerActivity
                    startActivity(Intent(this, DocumentScannerActivity::class.java))
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showArchivedTimetablesForDeletion() {
        lifecycleScope.launch {
            val archivedTimetables = withContext(Dispatchers.IO) { timetableDao.getAllArchivedTimetables() }
            if (archivedTimetables.isEmpty()) {
                Toast.makeText(this@MainActivity, getString(R.string.no_archived_timetables_to_delete), Toast.LENGTH_SHORT).show()
                return@launch
            }
            val dialogView = LayoutInflater.from(this@MainActivity).inflate(R.layout.timetable_delete_archived_list_dialog, null)
            val recyclerView: RecyclerView = dialogView.findViewById(R.id.archivedTimetablesRecyclerView)
            recyclerView.layoutManager = LinearLayoutManager(this@MainActivity)
            val adapter = ArchivedTimetableDeletionAdapter(archivedTimetables)
            recyclerView.adapter = adapter

            AlertDialog.Builder(this@MainActivity)
                .setTitle(getString(R.string.select_archived_timetables_delete))
                .setView(dialogView)
                .setPositiveButton(getString(R.string.button_delete_selected)) { dialog, _ ->
                    val selectedTimetables = adapter.getSelectedTimetables()
                    if (selectedTimetables.isNotEmpty()) {
                        confirmDeleteSelectedArchived(selectedTimetables)
                    } else {
                        Toast.makeText(this@MainActivity, getString(R.string.no_timetables_selected_for_deletion), Toast.LENGTH_SHORT).show()
                    }
                    dialog.dismiss()
                }
                .setNegativeButton(getString(R.string.dialog_cancel_button), null)
                .show()
        }
    }

    private fun confirmDeleteSelectedArchived(selectedTimetables: List<Timetable>) {
        val message = getString(R.string.confirm_delete_archived_message, selectedTimetables.joinToString("\n") { it.name.replaceFirst(Regex("_\\d{4}_\\d{2}_\\d{2}_\\d{2}_\\d{2}_\\d{2}"), "") })
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.confirm_delete_archived_title))
            .setMessage(message)
            .setPositiveButton(getString(R.string.button_delete)) { dialog, _ ->
                deleteSelectedArchivedTimetables(selectedTimetables)
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.dialog_cancel_button), null)
            .show()
    }

    private fun deleteSelectedArchivedTimetables(selectedTimetables: List<Timetable>) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    selectedTimetables.forEach { timetable ->
                        timetable.id?.let { id ->
                            val notificationHelper = NotificationHelper(applicationContext)
                            notificationHelper.cancelAllNotificationsForTimetable(id, timetableDao)
                        }
                        timetableDao.deleteTimetable(timetable)
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, getString(R.string.selected_delete_archived_success), Toast.LENGTH_SHORT).show()
                    updateUploadTimetableButtonState()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error deleting selected archived timetables: ${e.message}", e)
                Toast.makeText(this@MainActivity, getString(R.string.selected_delete_archived_failure, e.localizedMessage), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showMyProfileDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.my_profile_dialog, null)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_profile_title))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.dialog_ok_button)) { dialog, _ ->
                Toast.makeText(this, "Profile Saved (simulation)", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.dialog_cancel_button), null)
            .show()
    }

    private fun showHelpDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_help_title))
            .setMessage(getString(R.string.help_text))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun initializeEmojiCompat() {
        val fontRequest = FontRequest(
            "com.google.android.gms.fonts",
            "com.google.android.gms",
            "Noto Color Emoji Compat",
            R.array.com_google_android_gms_fonts_certs
        )
        val config = FontRequestEmojiCompatConfig(this, fontRequest)
        EmojiCompat.init(config)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun initializeDefaultPreferences() {
        val prefs = getSharedPreferences("user_preferences", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("initial_prefs_set", false)) {
            Log.d("MainActivity", "Initializing default preferences for the first time.")
            prefs.edit {
                // Timetable Notifs
                putInt("pref_timetable_notif1", 60)
                putInt("pref_timetable_notif2", 30)
                // Engagement Notifs
                putInt("pref_engagement_notif1", 60)
                putInt("pref_engagement_notif2", 30)
                putInt("pref_engagement_notif3", 10)
                // Action Notifs
                putInt("pref_action_notif1", 60)
                putInt("pref_action_notif2", 30)
                putInt("pref_action_notif3", 10)

                // Assignment Notifs
                putBoolean("pref_assign_notif_3_days", true)
                putBoolean("pref_assign_notif_2_days", true)
                putBoolean("pref_assign_notif_1_day", true)
                putBoolean("pref_assign_notif_12_hours", true)
                putBoolean("pref_assign_notif_6_hours", true)
                putBoolean("pref_assign_notif_3_hours", true)
                putBoolean("pref_assign_notif_1_hour", true)

                // Exam Notifs
                putBoolean("pref_exam_notif_5_days", true)
                putBoolean("pref_exam_notif_3_days", true)
                putBoolean("pref_exam_notif_1_day", true)
                putBoolean("pref_exam_notif_12_hours", true)
                putBoolean("pref_exam_notif_6_hours", true)
                putBoolean("pref_exam_notif_3_hours", true)
                putBoolean("pref_exam_notif_1_hour", true)

                // ToDo Notifs
                putBoolean("pref_todo_notif_30_days", true)
                putBoolean("pref_todo_notif_25_days", true)
                putBoolean("pref_todo_notif_20_days", true)
                putBoolean("pref_todo_notif_15_days", true)
                putBoolean("pref_todo_notif_10_days", true)
                putBoolean("pref_todo_notif_7_days", true)
                putBoolean("pref_todo_notif_5_days", true)
                putBoolean("pref_todo_notif_3_days", true)
                putBoolean("pref_todo_notif_1_day", true)

                // Auto-Deletion
                putBoolean("pref_timetable_auto_archive", true)
                putInt("pref_engagement_auto_delete", 180)
                putInt("pref_action_auto_delete", 180)
                putInt("pref_assignment_auto_delete", 180)
                putInt("pref_exam_auto_delete", 180)
                putInt("pref_todo_auto_delete", 180)
                putInt("pref_notes_auto_delete", 360)

                // Set flag to prevent this from running again
                putBoolean("initial_prefs_set", true)
            }
            Log.d("MainActivity", "Default preferences initialized and saved.")
        }
    }
}
