package com.costheta.cortexa.timetable

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import android.widget.CheckBox
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.costheta.cortexa.NotificationHelper
import com.costheta.cortexa.R
import com.costheta.cortexa.data.db.AppDatabase
import com.costheta.cortexa.data.db.dao.TimetableDao
import com.costheta.cortexa.data.models.ClassSession
import com.costheta.cortexa.data.models.Timetable
import com.costheta.cortexa.data.models.TimetableType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.SortedMap
import java.util.TreeMap
import kotlin.math.abs

class TimetableDetailsActivity : AppCompatActivity() {

    private var timetableId: Long = -1L
    private var currentTimetable: Timetable? = null
    private lateinit var timetableDao: TimetableDao
    private lateinit var notificationHelper: NotificationHelper

    // UI elements
    private lateinit var timetableNameTextView: TextView
    private lateinit var timetableTypeHeaderTextView: TextView
    private lateinit var timetableValidityTextView: TextView
    private lateinit var timetableTableLayout: TableLayout
    private lateinit var masterSilenceCheckbox: CheckBox

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.timetable_details_activity)

        timetableDao = AppDatabase.getDatabase(applicationContext).timetableDao()
        notificationHelper = NotificationHelper(applicationContext)

        timetableNameTextView = findViewById(R.id.timetableNameTextView)
        timetableTypeHeaderTextView = findViewById(R.id.timetableTypeHeaderTextView)
        timetableValidityTextView = findViewById(R.id.timetableValidityTextView)
        timetableTableLayout = findViewById(R.id.timetableTableLayout)
        masterSilenceCheckbox = findViewById(R.id.masterSilenceCheckbox)

        timetableId = intent.getLongExtra("TIMETABLE_ID", -1L)

        if (timetableId == -1L) {
            Toast.makeText(this, "Timetable ID not found.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Set listener for master silence checkbox
        masterSilenceCheckbox.setOnCheckedChangeListener { _, isChecked ->
            currentTimetable?.let { toggleMasterSilence(it, isChecked) }
        }

        // Set click listener for validity text to edit dates
        timetableValidityTextView.setOnClickListener {
            currentTimetable?.let { showTimetableValidityDialogForEdit(it) }
        }

        loadTimetableDetails()
    }

    private fun loadTimetableDetails() {
        lifecycleScope.launch {
            val timetable = withContext(Dispatchers.IO) { timetableDao.getTimetableById(timetableId) }
            currentTimetable = timetable

            if (timetable != null) {
                val sessions = withContext(Dispatchers.IO) { timetableDao.getClassSessionsForTimetable(timetable.id!!) }
                withContext(Dispatchers.Main) {
                    displayTimetableInfo(timetable)
                    displayTimetableGrid(sessions, timetable)
                }
            } else {
                Toast.makeText(this@TimetableDetailsActivity, "Timetable not found.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun displayTimetableInfo(timetable: Timetable) {
        timetableNameTextView.text = timetable.name.replaceFirst(Regex("_\\d{4}_\\d{2}_\\d{2}_\\d{2}_\\d{2}_\\d{2}"), "")
        timetableTypeHeaderTextView.text = when (timetable.type) {
            TimetableType.LECTURE -> getString(R.string.timetable_header_text)
            TimetableType.PERSONAL -> getString(R.string.personal_timetable_header_text)
        }
        val validityText = getString(
            R.string.timetable_validity_format,
            timetable.validityStartDate.format(dateFormatter),
            timetable.validityEndDate.format(dateFormatter)
        ) + "\nTimezone: ${timetable.creationTimeZone ?: "Not set"}"
        timetableValidityTextView.text = validityText
        masterSilenceCheckbox.isChecked = timetable.isMasterSilenced
    }

    private fun showTimetableValidityDialogForEdit(timetable: Timetable) {
        val dialogView = layoutInflater.inflate(R.layout.timetable_validity_dialog, null)
        val timetableNameEditText: EditText = dialogView.findViewById(R.id.timetableNameEditText)
        val startDateEditText: EditText = dialogView.findViewById(R.id.startDateEditText)
        val endDateEditText: EditText = dialogView.findViewById(R.id.endDateEditText)
        val defaultNotif1OffsetEditText: EditText = dialogView.findViewById(R.id.defaultNotif1OffsetEditText)
        val defaultNotif2OffsetEditText: EditText = dialogView.findViewById(R.id.defaultNotif2OffsetEditText)
        val defaultSilenceNotificationsCheckbox: CheckBox = dialogView.findViewById(R.id.defaultSilenceNotificationsCheckbox)

        timetableNameEditText.setText(timetable.name.replaceFirst(Regex("_\\d{4}_\\d{2}_\\d{2}_\\d{2}_\\d{2}_\\d{2}"), ""))
        timetableNameEditText.isEnabled = false

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

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_timetable_validity_title))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.dialog_ok_button)) { dialog, _ ->
                val newDefaultNotif1Offset = defaultNotif1OffsetEditText.text.toString().toIntOrNull()
                val newDefaultNotif2Offset = defaultNotif2OffsetEditText.text.toString().toIntOrNull()
                val newDefaultIsSilenced = defaultSilenceNotificationsCheckbox.isChecked

                if (selectedStartDate == null || selectedEndDate == null ||
                    newDefaultNotif1Offset == null || newDefaultNotif2Offset == null ||
                    newDefaultNotif1Offset < 0 || newDefaultNotif2Offset < 0) {
                    Toast.makeText(this, getString(R.string.dialog_validation_error), Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }

                if (selectedStartDate!!.isAfter(selectedEndDate)) {
                    Toast.makeText(this, getString(R.string.dialog_date_order_error), Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }

                val updatedTimetable = timetable.copy(
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
                            Toast.makeText(this@TimetableDetailsActivity, "Timetable details updated.", Toast.LENGTH_SHORT).show()
                            loadTimetableDetails()
                            if (updatedTimetable.isMasterSilenced) {
                                notificationHelper.cancelAllNotificationsForTimetable(updatedTimetable.id!!, timetableDao)
                            } else {
                                notificationHelper.scheduleNotificationsForTimetable(updatedTimetable, timetableDao)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("TimetableDetailsAct", "Error updating timetable details: ${e.message}", e)
                        Toast.makeText(this@TimetableDetailsActivity, "Failed to update timetable details: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.dialog_cancel_button)) { dialog, _ ->
                dialog.cancel()
            }
            .show()
    }

    private fun displayTimetableGrid(sessions: List<ClassSession>, timetable: Timetable) {
        timetableTableLayout.removeAllViews()
        timetableTableLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.light_gray))

        val sessionsByDayAndTime: SortedMap<DayOfWeek, SortedMap<LocalTime, ClassSession>> = TreeMap()
        sessions.forEach { session ->
            sessionsByDayAndTime.getOrPut(session.dayOfWeek) { TreeMap() }[session.startTime] = session
        }

        // val uniqueDays = sessionsByDayAndTime.keys.sortedBy { it.value }
        val allDays = DayOfWeek.entries
        val uniqueTimes = sessionsByDayAndTime.values.flatMap { it.keys }.distinct().sorted()

        if (allDays.isEmpty() || uniqueTimes.isEmpty()) {
            val noSessionsTextView = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(2, resources.getDimensionPixelSize(R.dimen.small_margin), 2, 2)
                }
                text = getString(R.string.no_class_sessions_found)
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(ContextCompat.getColor(context, R.color.black))
            }
            val wrapperLayout = LinearLayout(this).apply {
                layoutParams = TableLayout.LayoutParams(
                    TableLayout.LayoutParams.MATCH_PARENT,
                    TableLayout.LayoutParams.WRAP_CONTENT
                )
                gravity = Gravity.CENTER_HORIZONTAL
                addView(noSessionsTextView)
            }
            timetableTableLayout.addView(wrapperLayout)
            return
        }

        val headerRow = TableRow(this)
        headerRow.addView(createHeaderCell(""))

        allDays.forEach { day ->
            headerRow.addView(createHeaderCell(day.toString().substring(0, 3).uppercase(Locale.getDefault())))
        }
        timetableTableLayout.addView(headerRow)

        uniqueTimes.forEach { time ->
            val dataRow = TableRow(this)
            dataRow.addView(createHeaderCell(time.format(timeFormatter)))

            allDays.forEach { day ->
                val session = sessionsByDayAndTime[day]?.get(time)
                dataRow.addView(createSessionCell(session, timetable))
            }
            timetableTableLayout.addView(dataRow)
        }
    }

    private fun createHeaderCell(text: String): TextView {
        return TextView(this).apply {
            layoutParams = TableRow.LayoutParams(
                resources.getDimensionPixelSize(R.dimen.timetable_cell_width),
                TableRow.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(2, 2, 2, 2)
            }
            // Set a white background for time labels in the first column, or the default for day headers
//            if (text.isNotEmpty()) {
//                setBackgroundColor(ContextCompat.getColor(context, R.color.timetable_header_background))
//            } else {
//                setBackgroundColor(ContextCompat.getColor(context, R.color.white1))
//            }
//            setTextColor(ContextCompat.getColor(context, R.color.white1))
//            if (text.length == 3 && text.all { it.isUpperCase() }) {
            if (text.isNotEmpty()) {
                setBackgroundColor(ContextCompat.getColor(context, R.color.timetable_header_background))
                setTextColor(ContextCompat.getColor(context, R.color.white1))
            } else {
                setBackgroundColor(ContextCompat.getColor(context, android.R.color.white))
                setTextColor(ContextCompat.getColor(context, R.color.black))
            }
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(8, 8, 8, 8)
            this.text = text
        }
    }

    private fun createSessionCell(session: ClassSession?, timetable: Timetable): View {
        val cellView = LayoutInflater.from(this).inflate(R.layout.timetable_class_session_item, null, false)

        val layoutParams = TableRow.LayoutParams(
            resources.getDimensionPixelSize(R.dimen.timetable_cell_width),
            resources.getDimensionPixelSize(R.dimen.timetable_cell_height)
        ).apply {
            setMargins(2, 2, 2, 2)
        }
        cellView.layoutParams = layoutParams

        val viewHolder = ClassSessionViewHolder(
            cellView,
            this, // Pass context
            { s, n, o -> showNotificationTimePickerDialog(s, n, o) },
            { s, b -> updateSessionSilenceStatus(s, b) }
        )
        viewHolder.bind(session, timetable)

        return cellView
    }

    private fun showNotificationTimePickerDialog(session: ClassSession, notificationNumber: Int, currentOffset: Int) {
        val initialTime = session.startTime.minusMinutes(currentOffset.toLong())
        val hour = initialTime.hour
        val minute = initialTime.minute

        TimePickerDialog(this, { _, h, m ->
            val selectedNotifTime = LocalTime.of(h, m)
            val newOffsetMinutes = abs(java.time.Duration.between(session.startTime, selectedNotifTime).toMinutes().toInt())

            updateNotificationOffset(session, notificationNumber, newOffsetMinutes)
        }, hour, minute, true).show()
    }

    private fun updateNotificationOffset(session: ClassSession, notificationNumber: Int, newOffsetMinutes: Int) {
        lifecycleScope.launch {
            try {
                val updatedSession = if (notificationNumber == 1) {
                    session.copy(notificationOffsetMinutes1 = newOffsetMinutes)
                } else {
                    session.copy(notificationOffsetMinutes2 = newOffsetMinutes)
                }
                withContext(Dispatchers.IO) {
                    timetableDao.updateClassSession(updatedSession)
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@TimetableDetailsActivity, "Notification ${notificationNumber} offset updated.", Toast.LENGTH_SHORT).show()
                    loadTimetableDetails()
                    if (!updatedSession.isSilenced) {
                        notificationHelper.scheduleAllNotificationsForClassSession(updatedSession, currentTimetable!!)
                    }
                }
            } catch (e: Exception) {
                Log.e("TimetableDetailsAct", "Error updating notification offset: ${e.message}", e)
                Toast.makeText(this@TimetableDetailsActivity, "Failed to update notification offset: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun toggleMasterSilence(timetable: Timetable, isSilenced: Boolean) {
        lifecycleScope.launch {
            try {
                val updatedTimetable = timetable.copy(isMasterSilenced = isSilenced)
                withContext(Dispatchers.IO) {
                    timetableDao.updateTimetable(updatedTimetable)
                    timetable.id?.let { id ->
                        timetableDao.updateClassSessionsSilenceStatusForTimetable(id, isSilenced)
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@TimetableDetailsActivity,
                        if (isSilenced) getString(R.string.notifications_silenced_on, timetable.name)
                        else getString(R.string.notifications_unsilenced_on, timetable.name),
                        Toast.LENGTH_SHORT
                    ).show()
                    loadTimetableDetails()
                    if (isSilenced) {
                        notificationHelper.cancelAllNotificationsForTimetable(updatedTimetable.id!!, timetableDao)
                    } else {
                        notificationHelper.scheduleNotificationsForTimetable(updatedTimetable, timetableDao)
                    }
                }
            } catch (e: Exception) {
                Log.e("TimetableDetailsAct", "Error toggling master silence: ${e.message}", e)
                Toast.makeText(this@TimetableDetailsActivity, "Failed to update silence status: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateSessionSilenceStatus(session: ClassSession, isSilenced: Boolean) {
        lifecycleScope.launch {
            try {
                val updatedSession = session.copy(isSilenced = isSilenced)
                withContext(Dispatchers.IO) {
                    timetableDao.updateClassSession(updatedSession)
                }
                val endTime = session.startTime.plusMinutes(session.durationMinutes.toLong())
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@TimetableDetailsActivity,
                        if (isSilenced) getString(R.string.notifications_silenced_for_session, session.courseName, session.startTime.format(timeFormatter), endTime.format(timeFormatter))
                        else getString(R.string.notifications_unsilenced_for_session, session.courseName, session.startTime.format(timeFormatter), endTime.format(timeFormatter)),
                        Toast.LENGTH_SHORT
                    ).show()
                    loadTimetableDetails()
                    if (isSilenced) {
                        updatedSession.sessionId?.let { sessionId ->
                            val offset1 = updatedSession.notificationOffsetMinutes1 ?: currentTimetable?.defaultNotificationOffsetMinutes1 ?: notificationHelper.DEFAULT_NOTIFICATION_OFFSET_MINUTES_1
                            val offset2 = updatedSession.notificationOffsetMinutes2 ?: currentTimetable?.defaultNotificationOffsetMinutes2 ?: notificationHelper.DEFAULT_NOTIFICATION_OFFSET_MINUTES_2

                            notificationHelper.cancelNotificationsForClassSession(sessionId, offset1)
                            notificationHelper.cancelNotificationsForClassSession(sessionId, offset2)
                        }
                    } else {
                        notificationHelper.scheduleAllNotificationsForClassSession(updatedSession, currentTimetable!!)
                    }
                }
            } catch (e: Exception) {
                Log.e("TimetableDetailsAct", "Error updating session silence status: ${e.message}", e)
                Toast.makeText(this@TimetableDetailsActivity, "Failed to update silence status: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }
}