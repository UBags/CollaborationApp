package com.costheta.cortexa.preferences

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.NumberPicker
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.costheta.cortexa.R

/**
 * An activity dedicated to managing user-configurable application preferences.
 * This screen allows users to set default notification timings and auto-deletion
 * policies for various types of events within the app.
 * All settings are persisted using SharedPreferences.
 */
class PreferencesActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences

    // --- UI Elements ---
    // Notification Pickers
    private lateinit var timetableNotif1Picker: NumberPicker
    private lateinit var timetableNotif2Picker: NumberPicker
    private lateinit var engagementNotif1Picker: NumberPicker
    private lateinit var engagementNotif2Picker: NumberPicker
    private lateinit var engagementNotif3Picker: NumberPicker
    private lateinit var actionNotif1Picker: NumberPicker
    private lateinit var actionNotif2Picker: NumberPicker
    private lateinit var actionNotif3Picker: NumberPicker

    // Notification CheckBoxes
    private lateinit var assignNotif3Days: CheckBox
    private lateinit var assignNotif2Days: CheckBox
    private lateinit var assignNotif1Day: CheckBox
    private lateinit var assignNotif12Hours: CheckBox
    private lateinit var assignNotif6Hours: CheckBox
    private lateinit var assignNotif3Hours: CheckBox
    private lateinit var assignNotif1Hour: CheckBox
    private lateinit var examNotif5Days: CheckBox
    private lateinit var examNotif3Days: CheckBox
    private lateinit var examNotif1Day: CheckBox
    private lateinit var examNotif12Hours: CheckBox
    private lateinit var examNotif6Hours: CheckBox
    private lateinit var examNotif3Hours: CheckBox
    private lateinit var examNotif1Hour: CheckBox
    private lateinit var todoNotif30Days: CheckBox
    private lateinit var todoNotif25Days: CheckBox
    private lateinit var todoNotif20Days: CheckBox
    private lateinit var todoNotif15Days: CheckBox
    private lateinit var todoNotif10Days: CheckBox
    private lateinit var todoNotif7Days: CheckBox
    private lateinit var todoNotif5Days: CheckBox
    private lateinit var todoNotif3Days: CheckBox
    private lateinit var todoNotif1Day: CheckBox


    // Auto-Deletion Pickers & CheckBoxes
    private lateinit var timetableAutoArchiveCheckbox: CheckBox
    private lateinit var engagementAutoDeletePicker: NumberPicker
    private lateinit var actionAutoDeletePicker: NumberPicker
    private lateinit var assignmentAutoDeletePicker: NumberPicker
    private lateinit var examAutoDeletePicker: NumberPicker
    private lateinit var todoAutoDeletePicker: NumberPicker
    private lateinit var notesAutoDeletePicker: NumberPicker

    private lateinit var saveButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.preferences_dialog)
        supportActionBar?.title = "Set Preferences"
        supportActionBar?.setDisplayHomeAsUpEnabled(true) // Add back button

        // Use a dedicated preferences file for these settings
        sharedPreferences = getSharedPreferences("user_preferences", Context.MODE_PRIVATE)

        initializeViews()
        setupPickers()
        loadPreferences()

        saveButton.setOnClickListener {
            showSaveConfirmationDialog()
        }
    }

    // NEW: Displays a confirmation dialog before saving preferences.
    private fun showSaveConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Confirm Save")
            .setMessage("The changes will apply to all new Events created from this moment on. Click OK to confirm, Cancel to Go Back, Exit to go back to the main page")
            // The "OK" button saves the preferences and closes the activity.
            .setPositiveButton("OK") { dialog, _ ->
                savePreferences()
                Toast.makeText(this, "Preferences saved!", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                finish()
            }
            // The "Cancel" button closes the dialog, allowing the user to continue editing.
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            // The "Exit" button closes the activity without saving any changes.
            .setNeutralButton("Exit to Main Page") { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .show()
    }

    /**
     * Handles the action bar's back button press.
     */
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    /**
     * Binds all the UI elements from the XML layout to their corresponding variables.
     */
    private fun initializeViews() {
        // Notification Pickers
        timetableNotif1Picker = findViewById(R.id.prefTimetableNotif1)
        timetableNotif2Picker = findViewById(R.id.prefTimetableNotif2)
        engagementNotif1Picker = findViewById(R.id.prefEngagementNotif1)
        engagementNotif2Picker = findViewById(R.id.prefEngagementNotif2)
        engagementNotif3Picker = findViewById(R.id.prefEngagementNotif3)
        actionNotif1Picker = findViewById(R.id.prefActionNotif1)
        actionNotif2Picker = findViewById(R.id.prefActionNotif2)
        actionNotif3Picker = findViewById(R.id.prefActionNotif3)

        // Assignment Notification CheckBoxes
        assignNotif3Days = findViewById(R.id.prefAssignNotif3Days)
        assignNotif2Days = findViewById(R.id.prefAssignNotif2Days)
        assignNotif1Day = findViewById(R.id.prefAssignNotif1Day)
        assignNotif12Hours = findViewById(R.id.prefAssignNotif12Hours)
        assignNotif6Hours = findViewById(R.id.prefAssignNotif6Hours)
        assignNotif3Hours = findViewById(R.id.prefAssignNotif3Hours)
        assignNotif1Hour = findViewById(R.id.prefAssignNotif1Hour)

        // Exam Notification CheckBoxes
        examNotif5Days = findViewById(R.id.prefExamNotif5Days)
        examNotif3Days = findViewById(R.id.prefExamNotif3Days)
        examNotif1Day = findViewById(R.id.prefExamNotif1Day)
        examNotif12Hours = findViewById(R.id.prefExamNotif12Hours)
        examNotif6Hours = findViewById(R.id.prefExamNotif6Hours)
        examNotif3Hours = findViewById(R.id.prefExamNotif3Hours)
        examNotif1Hour = findViewById(R.id.prefExamNotif1Hour)

        // ToDo Notification CheckBoxes
        todoNotif30Days = findViewById(R.id.prefTodoNotif30Days)
        todoNotif25Days = findViewById(R.id.prefTodoNotif25Days)
        todoNotif20Days = findViewById(R.id.prefTodoNotif20Days)
        todoNotif15Days = findViewById(R.id.prefTodoNotif15Days)
        todoNotif10Days = findViewById(R.id.prefTodoNotif10Days)
        todoNotif7Days = findViewById(R.id.prefTodoNotif7Days)
        todoNotif5Days = findViewById(R.id.prefTodoNotif5Days)
        todoNotif3Days = findViewById(R.id.prefTodoNotif3Days)
        todoNotif1Day = findViewById(R.id.prefTodoNotif1Day)

        // Auto-Deletion Controls
        timetableAutoArchiveCheckbox = findViewById(R.id.prefTimetableAutoArchive)
        engagementAutoDeletePicker = findViewById(R.id.prefEngagementAutoDelete)
        actionAutoDeletePicker = findViewById(R.id.prefActionAutoDelete)
        assignmentAutoDeletePicker = findViewById(R.id.prefAssignmentAutoDelete)
        examAutoDeletePicker = findViewById(R.id.prefExamAutoDelete)
        todoAutoDeletePicker = findViewById(R.id.prefTodoAutoDelete)
        notesAutoDeletePicker = findViewById(R.id.prefNotesAutoDelete)

        saveButton = findViewById(R.id.savePreferencesButton)
    }

    /**
     * Configures the min, max, and step values for all NumberPicker controls.
     */
    private fun setupPickers() {
        // Notification Pickers
        setupNumberPicker(timetableNotif1Picker, 60, 120, 10)
        setupNumberPicker(timetableNotif2Picker, 10, 50, 10)
        setupNumberPicker(engagementNotif1Picker, 60, 120, 10)
        setupNumberPicker(engagementNotif2Picker, 30, 50, 10)
        setupNumberPicker(engagementNotif3Picker, 5, 20, 5)
        setupNumberPicker(actionNotif1Picker, 60, 120, 10)
        setupNumberPicker(actionNotif2Picker, 30, 50, 10)
        setupNumberPicker(actionNotif3Picker, 5, 20, 5)

        // Auto-Deletion Pickers
        setupNumberPicker(engagementAutoDeletePicker, 180, 360, 30)
        setupNumberPicker(actionAutoDeletePicker, 180, 360, 30)
        setupNumberPicker(assignmentAutoDeletePicker, 180, 360, 30)
        setupNumberPicker(examAutoDeletePicker, 180, 360, 30)
        setupNumberPicker(todoAutoDeletePicker, 180, 360, 30)
        setupNumberPicker(notesAutoDeletePicker, 360, 720, 60)
    }

    /**
     * A helper function to configure a NumberPicker with a specific range and step.
     * @param picker The NumberPicker instance to configure.
     * @param min The minimum value of the range.
     * @param max The maximum value of the range.
     * @param step The increment between values.
     */
    private fun setupNumberPicker(picker: NumberPicker, min: Int, max: Int, step: Int) {
        val displayedValues = (min..max step step).map { it.toString() }.toTypedArray()
        picker.minValue = 0
        picker.maxValue = displayedValues.size - 1
        picker.displayedValues = displayedValues
    }

    /**
     * Loads all saved preferences from SharedPreferences and applies them to the UI controls.
     * If a preference is not found, it uses the specified default value.
     */
    private fun loadPreferences() {
        // --- Load Notification Preferences ---
        loadPickerValue(timetableNotif1Picker, "pref_timetable_notif1", 60, 60, 10)
        loadPickerValue(timetableNotif2Picker, "pref_timetable_notif2", 30, 10, 10)
        loadPickerValue(engagementNotif1Picker, "pref_engagement_notif1", 60, 60, 10)
        loadPickerValue(engagementNotif2Picker, "pref_engagement_notif2", 30, 30, 10)
        loadPickerValue(engagementNotif3Picker, "pref_engagement_notif3", 10, 5, 5)
        loadPickerValue(actionNotif1Picker, "pref_action_notif1", 60, 60, 10)
        loadPickerValue(actionNotif2Picker, "pref_action_notif2", 30, 30, 10)
        loadPickerValue(actionNotif3Picker, "pref_action_notif3", 10, 5, 5)

        assignNotif3Days.isChecked = sharedPreferences.getBoolean("pref_assign_notif_3_days", true)
        assignNotif2Days.isChecked = sharedPreferences.getBoolean("pref_assign_notif_2_days", true)
        assignNotif1Day.isChecked = sharedPreferences.getBoolean("pref_assign_notif_1_day", true)
        assignNotif12Hours.isChecked = sharedPreferences.getBoolean("pref_assign_notif_12_hours", true)
        assignNotif6Hours.isChecked = sharedPreferences.getBoolean("pref_assign_notif_6_hours", true)
        assignNotif3Hours.isChecked = sharedPreferences.getBoolean("pref_assign_notif_3_hours", true)
        assignNotif1Hour.isChecked = sharedPreferences.getBoolean("pref_assign_notif_1_hour", true)

        examNotif5Days.isChecked = sharedPreferences.getBoolean("pref_exam_notif_5_days", true)
        examNotif3Days.isChecked = sharedPreferences.getBoolean("pref_exam_notif_3_days", true)
        examNotif1Day.isChecked = sharedPreferences.getBoolean("pref_exam_notif_1_day", true)
        examNotif12Hours.isChecked = sharedPreferences.getBoolean("pref_exam_notif_12_hours", true)
        examNotif6Hours.isChecked = sharedPreferences.getBoolean("pref_exam_notif_6_hours", true)
        examNotif3Hours.isChecked = sharedPreferences.getBoolean("pref_exam_notif_3_hours", true)
        examNotif1Hour.isChecked = sharedPreferences.getBoolean("pref_exam_notif_1_hour", true)

        todoNotif30Days.isChecked = sharedPreferences.getBoolean("pref_todo_notif_30_days", true)
        todoNotif25Days.isChecked = sharedPreferences.getBoolean("pref_todo_notif_25_days", true)
        todoNotif20Days.isChecked = sharedPreferences.getBoolean("pref_todo_notif_20_days", true)
        todoNotif15Days.isChecked = sharedPreferences.getBoolean("pref_todo_notif_15_days", true)
        todoNotif10Days.isChecked = sharedPreferences.getBoolean("pref_todo_notif_10_days", true)
        todoNotif7Days.isChecked = sharedPreferences.getBoolean("pref_todo_notif_7_days", true)
        todoNotif5Days.isChecked = sharedPreferences.getBoolean("pref_todo_notif_5_days", true)
        todoNotif3Days.isChecked = sharedPreferences.getBoolean("pref_todo_notif_3_days", true)
        todoNotif1Day.isChecked = sharedPreferences.getBoolean("pref_todo_notif_1_day", true)


        // --- Load Auto-Deletion Preferences ---
        timetableAutoArchiveCheckbox.isChecked = sharedPreferences.getBoolean("pref_timetable_auto_archive", true)
        loadPickerValue(engagementAutoDeletePicker, "pref_engagement_auto_delete", 180, 180, 30)
        loadPickerValue(actionAutoDeletePicker, "pref_action_auto_delete", 180, 180, 30)
        loadPickerValue(assignmentAutoDeletePicker, "pref_assignment_auto_delete", 180, 180, 30)
        loadPickerValue(examAutoDeletePicker, "pref_exam_auto_delete", 180, 180, 30)
        loadPickerValue(todoAutoDeletePicker, "pref_todo_auto_delete", 180, 180, 30)
        loadPickerValue(notesAutoDeletePicker, "pref_notes_auto_delete", 360, 360, 60)
    }

    /**
     * Helper to load a saved integer value and set the correct index on a NumberPicker.
     */
    private fun loadPickerValue(picker: NumberPicker, key: String, defaultValue: Int, min: Int, step: Int) {
        val savedValue = sharedPreferences.getInt(key, defaultValue)
        val index = ((savedValue - min) / step).coerceIn(0, picker.maxValue)
        picker.value = index
    }

    /**
     * Saves the current state of all UI controls to SharedPreferences.
     */
    private fun savePreferences() {
        sharedPreferences.edit {
            // --- Save Notification Preferences ---
            savePickerValue(timetableNotif1Picker, "pref_timetable_notif1", 60, 10)
            savePickerValue(timetableNotif2Picker, "pref_timetable_notif2", 10, 10)
            savePickerValue(engagementNotif1Picker, "pref_engagement_notif1", 60, 10)
            savePickerValue(engagementNotif2Picker, "pref_engagement_notif2", 30, 10)
            savePickerValue(engagementNotif3Picker, "pref_engagement_notif3", 5, 5)
            savePickerValue(actionNotif1Picker, "pref_action_notif1", 60, 10)
            savePickerValue(actionNotif2Picker, "pref_action_notif2", 30, 10)
            savePickerValue(actionNotif3Picker, "pref_action_notif3", 5, 5)

            putBoolean("pref_assign_notif_3_days", assignNotif3Days.isChecked)
            putBoolean("pref_assign_notif_2_days", assignNotif2Days.isChecked)
            putBoolean("pref_assign_notif_1_day", assignNotif1Day.isChecked)
            putBoolean("pref_assign_notif_12_hours", assignNotif12Hours.isChecked)
            putBoolean("pref_assign_notif_6_hours", assignNotif6Hours.isChecked)
            putBoolean("pref_assign_notif_3_hours", assignNotif3Hours.isChecked)
            putBoolean("pref_assign_notif_1_hour", assignNotif1Hour.isChecked)

            putBoolean("pref_exam_notif_5_days", examNotif5Days.isChecked)
            putBoolean("pref_exam_notif_3_days", examNotif3Days.isChecked)
            putBoolean("pref_exam_notif_1_day", examNotif1Day.isChecked)
            putBoolean("pref_exam_notif_12_hours", examNotif12Hours.isChecked)
            putBoolean("pref_exam_notif_6_hours", examNotif6Hours.isChecked)
            putBoolean("pref_exam_notif_3_hours", examNotif3Hours.isChecked)
            putBoolean("pref_exam_notif_1_hour", examNotif1Hour.isChecked)

            putBoolean("pref_todo_notif_30_days", todoNotif30Days.isChecked)
            putBoolean("pref_todo_notif_25_days", todoNotif25Days.isChecked)
            putBoolean("pref_todo_notif_20_days", todoNotif20Days.isChecked)
            putBoolean("pref_todo_notif_15_days", todoNotif15Days.isChecked)
            putBoolean("pref_todo_notif_10_days", todoNotif10Days.isChecked)
            putBoolean("pref_todo_notif_7_days", todoNotif7Days.isChecked)
            putBoolean("pref_todo_notif_5_days", todoNotif5Days.isChecked)
            putBoolean("pref_todo_notif_3_days", todoNotif3Days.isChecked)
            putBoolean("pref_todo_notif_1_day", todoNotif1Day.isChecked)

            // --- Save Auto-Deletion Preferences ---
            putBoolean("pref_timetable_auto_archive", timetableAutoArchiveCheckbox.isChecked)
            savePickerValue(engagementAutoDeletePicker, "pref_engagement_auto_delete", 180, 30)
            savePickerValue(actionAutoDeletePicker, "pref_action_auto_delete", 180, 30)
            savePickerValue(assignmentAutoDeletePicker, "pref_assignment_auto_delete", 180, 30)
            savePickerValue(examAutoDeletePicker, "pref_exam_auto_delete", 180, 30)
            savePickerValue(todoAutoDeletePicker, "pref_todo_auto_delete", 180, 30)
            savePickerValue(notesAutoDeletePicker, "pref_notes_auto_delete", 360, 60)
        }
    }

    /**
     * Helper to get the actual value from a NumberPicker and save it to SharedPreferences.
     */
    private fun SharedPreferences.Editor.savePickerValue(picker: NumberPicker, key: String, min: Int, step: Int) {
        val actualValue = min + (picker.value * step)
        putInt(key, actualValue)
    }
}
