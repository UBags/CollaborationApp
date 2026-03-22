package com.costheta.cortexa.timetable

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.costheta.cortexa.NotificationHelper
import com.costheta.cortexa.R
import com.costheta.cortexa.data.db.AppDatabase
import com.costheta.cortexa.data.db.dao.TimetableDao
import com.costheta.cortexa.data.models.Timetable
import com.costheta.cortexa.data.parser.CsvTimetableParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.TimeZone

class AddTimetableActivity : AppCompatActivity() {

    private lateinit var timetableDao: TimetableDao
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var timetableType: String

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private val pickFileLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    Log.d("AddTimetableActivity", "Selected URI: $uri, Type: $timetableType")
                    val fileName = getFileName(uri)
                    if (fileName?.endsWith(".csv", ignoreCase = true) == true) {
                        showTimetableValidityDialog(uri, fileName, timetableType)
                    } else if (fileName?.endsWith(".xls", ignoreCase = true) == true || fileName?.endsWith(".xlsx", ignoreCase = true) == true) {
                        convertAndProcessExcel(uri, fileName, timetableType)
                    } else {
                        Toast.makeText(this, "Please select a CSV, XLS, or XLSX file.", Toast.LENGTH_LONG).show()
                        finish() // Close activity on error
                    }
                } ?: run {
                    Toast.makeText(this, "No file selected.", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } else {
                Toast.makeText(this, "File selection cancelled.", Toast.LENGTH_SHORT).show()
                finish() // Close activity if user cancels
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.timetable_activity_add)

        timetableDao = AppDatabase.getDatabase(applicationContext).timetableDao()
        notificationHelper = NotificationHelper(applicationContext)

        val type = intent.getStringExtra("TIMETABLE_TYPE")
        if (type == null) {
            Toast.makeText(this, "Error: Timetable type not specified.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        timetableType = type

        // Start the timetable addition flow
        startTimetableFlow()
    }

    private fun startTimetableFlow() {
        lifecycleScope.launch {
            val archivedTimetables = withContext(Dispatchers.IO) {
                timetableDao.getAllArchivedTimetablesByType(timetableType)
            }
            if (archivedTimetables.isNotEmpty()) {
                showChooseTimetableSourceDialog()
            } else {
                openFilePicker()
            }
        }
    }

    private fun showChooseTimetableSourceDialog() {
        val dialogView = layoutInflater.inflate(R.layout.timetable_choose_source_dialog, null)
        val uploadNewFileButton: Button = dialogView.findViewById(R.id.uploadNewFileButton)
        val activateArchivedButton: Button = dialogView.findViewById(R.id.activateArchivedButton)

//        val dialog = AlertDialog.Builder(this, R.style.Theme_TimetableApp)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .setOnCancelListener { finish() } // Finish activity if dialog is dismissed
            .create()

        uploadNewFileButton.setOnClickListener {
            dialog.dismiss()
            openFilePicker()
        }
        activateArchivedButton.setOnClickListener {
            dialog.dismiss()
            showArchivedTimetablesDialog()
        }
        dialog.show()
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        val chooserIntent = Intent.createChooser(intent, "Select a timetable file")
        try {
            pickFileLauncher.launch(chooserIntent)
        } catch (e: Exception) {
            Toast.makeText(this, "No app found to pick files.", Toast.LENGTH_SHORT).show()
            Log.e("AddTimetableActivity", "File picker could not be launched", e)
            finish()
        }
    }

    private fun showArchivedTimetablesDialog() {
        lifecycleScope.launch {
            val archivedTimetables = withContext(Dispatchers.IO) {
                timetableDao.getAllArchivedTimetablesByType(timetableType)
            }
            val timetableNames = archivedTimetables.map { it.name.replaceFirst(Regex("_\\d{4}_\\d{2}_\\d{2}_\\d{2}_\\d{2}_\\d{2}"), "") }.toTypedArray()

            AlertDialog.Builder(this@AddTimetableActivity, R.style.Theme_CortexaApp)
                .setTitle(getString(R.string.dialog_activate_archived_title))
                .setItems(timetableNames) { _, which ->
                    activateTimetable(archivedTimetables[which])
                }
                .setNegativeButton(getString(R.string.dialog_cancel_button)) { _, _ -> finish() }
                .setOnCancelListener { finish() }
                .show()
        }
    }

    private fun activateTimetable(timetable: Timetable) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    timetableDao.getAllActiveTimetablesByType(timetable.type.name).forEach { activeTt ->
                        timetableDao.updateTimetable(activeTt.copy(isActive = false))
                        activeTt.id?.let { notificationHelper.cancelAllNotificationsForTimetable(it, timetableDao) }
                    }
                    timetableDao.updateTimetable(timetable.copy(isActive = true))
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AddTimetableActivity, getString(R.string.timetable_activated_success, timetable.name.replaceFirst(Regex("_\\d{4}_\\d{2}_\\d{2}_\\d{2}_\\d{2}_\\d{2}"), "")), Toast.LENGTH_LONG).show()
                    if (!timetable.isMasterSilenced) {
                        notificationHelper.scheduleNotificationsForTimetable(timetable, timetableDao)
                    }
                    finish()
                }
            } catch (e: Exception) {
                Log.e("AddTimetableActivity", "Error activating timetable: ${e.message}", e)
                Toast.makeText(this@AddTimetableActivity, getString(R.string.timetable_activate_failure, e.localizedMessage), Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun showTimetableValidityDialog(fileUri: Uri, suggestedFileName: String, timetableType: String) {
        val dialogView = layoutInflater.inflate(R.layout.timetable_validity_dialog, null)
        val timetableNameEditText: EditText = dialogView.findViewById(R.id.timetableNameEditText)
        val startDateEditText: EditText = dialogView.findViewById(R.id.startDateEditText)
        val endDateEditText: EditText = dialogView.findViewById(R.id.endDateEditText)
        val timezoneAutoCompleteTextView: AutoCompleteTextView = dialogView.findViewById(R.id.timezoneAutoCompleteTextView)
        val defaultNotif1OffsetEditText: EditText = dialogView.findViewById(R.id.defaultNotif1OffsetEditText)
        val defaultNotif2OffsetEditText: EditText = dialogView.findViewById(R.id.defaultNotif2OffsetEditText)
        val defaultSilenceNotificationsCheckbox: CheckBox = dialogView.findViewById(R.id.defaultSilenceNotificationsCheckbox)
        val updateTimetableButton: Button = dialogView.findViewById(R.id.updateTimetableButton)

        updateTimetableButton.visibility = View.GONE
        timetableNameEditText.setText(suggestedFileName.removeSuffix(".csv").removeSuffix(".xls").removeSuffix(".xlsx"))

        val availableTimezones = TimeZone.getAvailableIDs().sorted()
        val timezoneAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, availableTimezones)
        // timezoneAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        timezoneAutoCompleteTextView.setAdapter(timezoneAdapter)

        val currentTimezoneId = TimeZone.getDefault().id
        if (availableTimezones.contains(currentTimezoneId)) {
            timezoneAutoCompleteTextView.setText(currentTimezoneId, false) // 'false' prevents filtering
        }

        val today = LocalDate.now()
        var selectedStartDate: LocalDate? = today
        startDateEditText.setText(selectedStartDate?.format(dateFormatter))
        var selectedEndDate: LocalDate? = today.plusMonths(4)
        endDateEditText.setText(selectedEndDate?.format(dateFormatter))

        // Get the shared preferences
        val prefs = getSharedPreferences("user_preferences", Context.MODE_PRIVATE)
        // Populate notification offsets from saved preferences, with original values as defaults
        val defaultNotif1 = prefs.getInt("pref_timetable_notif1", 60)
        val defaultNotif2 = prefs.getInt("pref_timetable_notif2", 30)
        defaultNotif1OffsetEditText.setText(defaultNotif1.toString())
        defaultNotif2OffsetEditText.setText(defaultNotif2.toString())

        defaultSilenceNotificationsCheckbox.isChecked = false

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

//        AlertDialog.Builder(this, R.style.Theme_TimetableApp)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_timetable_validity_title))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.dialog_ok_button)) { dialog, _ ->
                val timetableName = timetableNameEditText.text.toString().trim()
                val selectedTimezone = timezoneAutoCompleteTextView.text.toString()
                val defaultNotif1Offset = defaultNotif1OffsetEditText.text.toString().toIntOrNull()
                val defaultNotif2Offset = defaultNotif2OffsetEditText.text.toString().toIntOrNull()
                val defaultIsSilenced = defaultSilenceNotificationsCheckbox.isChecked

                if (timetableName.isBlank() || selectedStartDate == null || selectedEndDate == null || defaultNotif1Offset == null || defaultNotif2Offset == null || defaultNotif1Offset < 0 || defaultNotif2Offset < 0) {
                    Toast.makeText(this, getString(R.string.dialog_validation_error), Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                if (selectedStartDate!!.isAfter(selectedEndDate!!)) {
                    Toast.makeText(this, getString(R.string.dialog_date_order_error), Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }

                val timestampFormatter = DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm_ss")
                val finalTimetableName = "${timetableName}_${LocalDateTime.now().format(timestampFormatter)}"

                val newTimetable = Timetable(
                    name = finalTimetableName,
                    validityStartDate = selectedStartDate!!,
                    validityEndDate = selectedEndDate!!,
                    defaultNotificationOffsetMinutes1 = defaultNotif1Offset,
                    defaultNotificationOffsetMinutes2 = defaultNotif2Offset,
                    isMasterSilenced = defaultIsSilenced,
                    isActive = true,
                    type = com.costheta.cortexa.data.models.TimetableType.valueOf(timetableType),
                    creationTimeZone = selectedTimezone
                )
                parseAndSaveFile(fileUri, newTimetable)
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.dialog_cancel_button)) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun parseAndSaveFile(fileUri: Uri, timetable: Timetable) {
        Toast.makeText(this, getString(R.string.parsing_csv_message), Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                val parser = CsvTimetableParser(applicationContext)
                val classSessions = withContext(Dispatchers.IO) { parser.parseCsv(fileUri, timetable) }

                if (classSessions.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@AddTimetableActivity, "No class information found in the file.", Toast.LENGTH_LONG).show()
                    }
                } else {
                    val insertedTimetable = withContext(Dispatchers.IO) { timetableDao.insertFullTimetable(timetable, classSessions) }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@AddTimetableActivity, getString(R.string.csv_parsed_success, classSessions.size, insertedTimetable.name.replaceFirst(Regex("_\\d{4}_\\d{2}_\\d{2}_\\d{2}_\\d{2}_\\d{2}"), "")), Toast.LENGTH_LONG).show()
                        if (!insertedTimetable.isMasterSilenced) {
                            notificationHelper.scheduleNotificationsForTimetable(insertedTimetable, timetableDao)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AddTimetableActivity, getString(R.string.csv_parsing_error, e.localizedMessage), Toast.LENGTH_LONG).show()
                }
            } finally {
                finish() // Ensure activity closes after parsing is done
            }
        }
    }

    private fun convertAndProcessExcel(uri: Uri, fileName: String?, timetableType: String) {
        Toast.makeText(this, "Converting Excel file...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                val csvUri = withContext(Dispatchers.IO) { convertExcelToCsv(uri) }
                if (csvUri != null) {
                    Toast.makeText(this@AddTimetableActivity, "Excel file converted successfully.", Toast.LENGTH_SHORT).show()
                    showTimetableValidityDialog(csvUri, fileName ?: "Unnamed Timetable", timetableType)
                } else {
                    Toast.makeText(this@AddTimetableActivity, "Failed to convert Excel file.", Toast.LENGTH_LONG).show()
                    finish()
                }
            } catch (e: Exception) {
                Toast.makeText(this@AddTimetableActivity, "Error converting Excel file: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun convertExcelToCsv(excelUri: Uri): Uri? {
        return try {
            contentResolver.openInputStream(excelUri)?.use { inputStream ->
                val workbook = WorkbookFactory.create(inputStream)
                val sheet = workbook.getSheetAt(0)
                val dataFormatter = DataFormatter()
                val csvContent = StringBuilder()
                var maxColCount = 0
                for (r in sheet.firstRowNum..sheet.lastRowNum) {
                    sheet.getRow(r)?.lastCellNum?.toInt()?.let { if (it > maxColCount) maxColCount = it }
                }
                if (maxColCount == 0) maxColCount = 1

                for (r in sheet.firstRowNum..sheet.lastRowNum) {
                    val row = sheet.getRow(r)
                    val rowData = (0 until maxColCount).map { c ->
                        val cell = row?.getCell(c)
                        if (cell == null || cell.cellType == CellType.BLANK) ""
                        else dataFormatter.formatCellValue(cell).replace(",", ";")
                    }
                    csvContent.append(rowData.joinToString(",")).append("\n")
                }

                val csvFile = File(cacheDir, "temp_timetable_${System.currentTimeMillis()}.csv")
                FileOutputStream(csvFile).use { fos -> fos.write(csvContent.toString().toByteArray()) }
                Uri.fromFile(csvFile)
            }
        } catch (e: Exception) {
            Log.e("AddTimetableActivity", "Error converting Excel to CSV: ${e.message}", e)
            null
        }
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.lastPathSegment
        }
        return result
    }
}
