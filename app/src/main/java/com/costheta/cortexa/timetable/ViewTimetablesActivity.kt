package com.costheta.cortexa.timetable

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.costheta.cortexa.data.db.AppDatabase
import com.costheta.cortexa.data.db.dao.TimetableDao
import com.costheta.cortexa.data.models.Timetable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter
import java.time.LocalDate
import android.app.DatePickerDialog
import android.view.View
import android.widget.CheckBox
import com.costheta.cortexa.NotificationHelper
import com.costheta.cortexa.R

/**
 * A new activity dedicated to viewing and managing active timetables.
 * This screen displays the timetable cards that were previously on MainActivity.
 */
class ViewTimetablesActivity : AppCompatActivity() {

    private lateinit var timetableRecyclerView: RecyclerView
    private lateinit var timetableAdapter: TimetableAdapter
    private lateinit var timetableDao: TimetableDao
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var noTimetablesTextView: TextView
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.timetables_activity_view)

        // Initialize DAOs and helpers
        timetableDao = AppDatabase.getDatabase(applicationContext).timetableDao()
        notificationHelper = NotificationHelper(applicationContext)

        // Initialize UI elements
        timetableRecyclerView = findViewById(R.id.timetableRecyclerView)
        noTimetablesTextView = findViewById(R.id.noTimetablesTextView)

        // Setup RecyclerView
        setupRecyclerView()

        // Load data
        loadTimetables()
    }

    override fun onResume() {
        super.onResume()
        // Refresh the list of timetables every time the activity resumes
        loadTimetables()
    }

    private fun setupRecyclerView() {
        timetableAdapter = TimetableAdapter(
            onDeleteClick = { item ->
                if (item is Timetable) {
                    showArchiveDeleteTimetableDialog(item)
                }
            },
            onEditClick = { item ->
                if (item is Timetable) {
                    showEditTimetableDialog(item)
                } else {
                    Log.w("ViewTimetablesActivity", "Unhandled item type clicked for edit: ${item::class.simpleName}")
                    Toast.makeText(this, "Cannot edit this type of item.", Toast.LENGTH_SHORT).show()
                }
            },
            onTimetableClick = { timetable ->
                val intent = Intent(this, TimetableDetailsActivity::class.java).apply {
                    putExtra("TIMETABLE_ID", timetable.id)
                }
                startActivity(intent)
            }
        )
        timetableRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ViewTimetablesActivity)
            adapter = timetableAdapter
        }
    }

    /**
     * Loads all active timetables from the database and updates the RecyclerView.
     */
    private fun loadTimetables() {
        lifecycleScope.launch {
            try {
                val activeLectureTimetable = withContext(Dispatchers.IO) {
                    timetableDao.getAllActiveTimetablesByType("LECTURE").firstOrNull()
                }
                val activePersonalTimetable = withContext(Dispatchers.IO) {
                    timetableDao.getAllActiveTimetablesByType("PERSONAL").firstOrNull()
                }

                val combinedList = mutableListOf<Any>()
                activeLectureTimetable?.let { combinedList.add(it) }
                activePersonalTimetable?.let { combinedList.add(it) }

                withContext(Dispatchers.Main) {
                    timetableAdapter.submitList(combinedList)
                    if (combinedList.isEmpty()) {
                        Toast.makeText(this@ViewTimetablesActivity, getString(R.string.no_active_timetables_found), Toast.LENGTH_LONG).show()
                        timetableRecyclerView.visibility = View.GONE
                        noTimetablesTextView.visibility = TextView.VISIBLE
                    } else {
                        timetableRecyclerView.visibility = View.VISIBLE
                        noTimetablesTextView.visibility = TextView.GONE
                    }
                }
            } catch (e: Exception) {
                Log.e("ViewTimetablesActivity", "Error loading timetables: ${e.message}", e)
                Toast.makeText(this@ViewTimetablesActivity, "Error loading data: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Shows a dialog to choose between archiving or deleting a timetable.
     */
    private fun showArchiveDeleteTimetableDialog(timetable: Timetable) {
        val dialogView = layoutInflater.inflate(R.layout.timetable_dialog_archive_delete, null)
        val dialogMessageTextView: TextView = dialogView.findViewById(R.id.dialogMessageTextView)
        val archiveButton: Button = dialogView.findViewById(R.id.archiveButton)
        val deleteButton: Button = dialogView.findViewById(R.id.deleteButton)

        dialogMessageTextView.text = getString(R.string.dialog_archive_delete_message_format, timetable.name.replaceFirst(Regex("_\\d{4}_\\d{2}_\\d{2}_\\d{2}_\\d{2}_\\d{2}"), ""))

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        archiveButton.setOnClickListener {
            dialog.dismiss()
            archiveTimetable(timetable)
        }

        deleteButton.setOnClickListener {
            dialog.dismiss()
            confirmDeleteItem(timetable)
        }
        dialog.show()
    }

    /**
     * Archives a timetable.
     */
    private fun archiveTimetable(timetable: Timetable) {
        lifecycleScope.launch {
            try {
                val archivedTimetable = timetable.copy(isActive = false)
                withContext(Dispatchers.IO) {
                    timetableDao.updateTimetable(archivedTimetable)
                    timetable.id?.let { id ->
                        timetableDao.updateClassSessionsSilenceStatusForTimetable(id, true)
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ViewTimetablesActivity, getString(R.string.timetable_archived_success, timetable.name.replaceFirst(Regex("_\\d{4}_\\d{2}_\\d{2}_\\d{2}_\\d{2}_\\d{2}"), "")), Toast.LENGTH_LONG).show()
                    timetable.id?.let { id ->
                        notificationHelper.cancelAllNotificationsForTimetable(id, timetableDao)
                    }
                    loadTimetables()
                }
            } catch (e: Exception) {
                Log.e("ViewTimetablesActivity", "Error archiving timetable: ${e.message}", e)
                Toast.makeText(this@ViewTimetablesActivity, getString(R.string.timetable_archive_failure, e.localizedMessage), Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Shows a confirmation dialog before deleting an item.
     */
    private fun confirmDeleteItem(item: Timetable) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.confirm_delete_title))
            .setMessage(getString(R.string.confirm_delete_timetable_message, item.name.replaceFirst(Regex("_\\d{4}_\\d{2}_\\d{2}_\\d{2}_\\d{2}_\\d{2}"), "")))
            .setPositiveButton(getString(R.string.button_delete)) { dialog, _ ->
                deleteItem(item)
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.dialog_cancel_button)) { dialog, _ ->
                dialog.cancel()
            }
            .show()
    }

    /**
     * Deletes a timetable from the database.
     */
    private fun deleteItem(item: Timetable) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    item.id?.let { id ->
                        notificationHelper.cancelAllNotificationsForTimetable(id, timetableDao)
                    }
                    timetableDao.deleteTimetable(item)
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ViewTimetablesActivity, getString(R.string.timetable_deleted_success, item.name.replaceFirst(Regex("_\\d{4}_\\d{2}_\\d{2}_\\d{2}_\\d{2}_\\d{2}"), "")), Toast.LENGTH_SHORT).show()
                    loadTimetables()
                }
            } catch (e: Exception) {
                Log.e("ViewTimetablesActivity", "Error deleting item: ${e.message}", e)
                Toast.makeText(this@ViewTimetablesActivity, getString(R.string.timetable_delete_failure, e.localizedMessage), Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Shows a dialog to edit timetable details.
     */
    private fun showEditTimetableDialog(timetable: Timetable) {
        val dialogView = layoutInflater.inflate(R.layout.timetable_validity_dialog, null)
        val timetableNameEditText: EditText = dialogView.findViewById(R.id.timetableNameEditText)
        val startDateEditText: EditText = dialogView.findViewById(R.id.startDateEditText)
        val endDateEditText: EditText = dialogView.findViewById(R.id.endDateEditText)
        val defaultNotif1OffsetEditText: EditText = dialogView.findViewById(R.id.defaultNotif1OffsetEditText)
        val defaultNotif2OffsetEditText: EditText = dialogView.findViewById(R.id.defaultNotif2OffsetEditText)
        val defaultSilenceNotificationsCheckbox: CheckBox = dialogView.findViewById(R.id.defaultSilenceNotificationsCheckbox)
        val updateTimetableButton: Button = dialogView.findViewById(R.id.updateTimetableButton)

        updateTimetableButton.visibility = Button.VISIBLE
        timetableNameEditText.setText(timetable.name.replaceFirst(Regex("_\\d{4}_\\d{2}_\\d{2}_\\d{2}_\\d{2}_\\d{2}"), ""))
        var selectedStartDate: LocalDate? = timetable.validityStartDate
        startDateEditText.setText(selectedStartDate?.format(dateFormatter))
        var selectedEndDate: LocalDate? = timetable.validityEndDate
        endDateEditText.setText(selectedEndDate?.format(dateFormatter))
        defaultNotif1OffsetEditText.setText(timetable.defaultNotificationOffsetMinutes1.toString())
        defaultNotif2OffsetEditText.setText(timetable.defaultNotificationOffsetMinutes2.toString())
        defaultSilenceNotificationsCheckbox.isChecked = timetable.isMasterSilenced

        startDateEditText.setOnClickListener {
            val initialDate = selectedStartDate ?: LocalDate.now()
            DatePickerDialog(this, { _, y, m, d ->
                selectedStartDate = LocalDate.of(y, m + 1, d)
                startDateEditText.setText(selectedStartDate?.format(dateFormatter))
            }, initialDate.year, initialDate.monthValue - 1, initialDate.dayOfMonth).show()
        }

        endDateEditText.setOnClickListener {
            val initialDate = selectedEndDate ?: LocalDate.now()
            DatePickerDialog(this, { _, y, m, d ->
                selectedEndDate = LocalDate.of(y, m + 1, d)
                endDateEditText.setText(selectedEndDate?.format(dateFormatter))
            }, initialDate.year, initialDate.monthValue - 1, initialDate.dayOfMonth).show()
        }

        val dialogBuilder = AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_timetable_validity_title))
            .setView(dialogView)
            .setNegativeButton(getString(R.string.dialog_cancel_button), null)

        val dialog = dialogBuilder.create()

        updateTimetableButton.setOnClickListener {
            val newTimetableName = timetableNameEditText.text.toString().trim()
            val newDefaultNotif1Offset = defaultNotif1OffsetEditText.text.toString().toIntOrNull()
            val newDefaultNotif2Offset = defaultNotif2OffsetEditText.text.toString().toIntOrNull()
            val newDefaultIsSilenced = defaultSilenceNotificationsCheckbox.isChecked

            if (newTimetableName.isBlank() || selectedStartDate == null || selectedEndDate == null || newDefaultNotif1Offset == null || newDefaultNotif2Offset == null || newDefaultNotif1Offset < 0 || newDefaultNotif2Offset < 0) {
                Toast.makeText(this, getString(R.string.dialog_validation_error), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            if (selectedStartDate!!.isAfter(selectedEndDate)) {
                Toast.makeText(this, getString(R.string.dialog_date_order_error), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val updatedTimetable = timetable.copy(
                name = newTimetableName,
                validityStartDate = selectedStartDate!!,
                validityEndDate = selectedEndDate!!,
                defaultNotificationOffsetMinutes1 = newDefaultNotif1Offset,
                defaultNotificationOffsetMinutes2 = newDefaultNotif2Offset,
                isMasterSilenced = newDefaultIsSilenced
            )

            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        timetableDao.updateTimetable(updatedTimetable)
                        if (timetable.isMasterSilenced != updatedTimetable.isMasterSilenced) {
                            timetable.id?.let { id ->
                                timetableDao.updateClassSessionsSilenceStatusForTimetable(id, updatedTimetable.isMasterSilenced)
                            }
                        }
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ViewTimetablesActivity, getString(R.string.timetable_details_saved), Toast.LENGTH_SHORT).show()
                        loadTimetables()
                        if (updatedTimetable.isMasterSilenced) {
                            updatedTimetable.id?.let { notificationHelper.cancelAllNotificationsForTimetable(it, timetableDao) }
                        } else {
                            notificationHelper.scheduleNotificationsForTimetable(updatedTimetable, timetableDao)
                        }
                        dialog.dismiss()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ViewTimetablesActivity, getString(R.string.timetable_details_save_error, e.localizedMessage), Toast.LENGTH_LONG).show()
                        Log.e("ViewTimetablesActivity", "Error updating timetable details: ${e.message}", e)
                    }
                }
            }
        }
        dialog.show()
    }
}