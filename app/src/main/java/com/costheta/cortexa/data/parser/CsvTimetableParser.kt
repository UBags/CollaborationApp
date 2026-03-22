package com.costheta.cortexa.data.parser

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action

import android.content.Context
import android.net.Uri
import android.util.Log
import com.costheta.cortexa.data.models.ClassSession
import com.costheta.cortexa.data.models.Timetable
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit // Import for calculating duration
import java.util.Locale

/**
 * Utility class for parsing CSV files into Timetable and ClassSession objects.
 * This version supports two flexible CSV formats:
 *
 * Format A (Days in Data, Times in Header):
 * - Header row: First cell "Day", subsequent cells are time ranges (e.g., "09:00-10:00").
 * (Also handles variant where first cell is blank but subsequent are time ranges)
 * - Data rows: First cell is a DayOfWeek name (e.g., Monday,Math,Physics), subsequent cells are Course Names.
 * Example:
 * Day,08:45 - 10:15,10:15 – 11:45
 * Monday,SubA,SubC
 * Tuesday,SubB,SubD
 *
 * Format B (Times in Data, Days in Header):
 * - Header row: First cell empty, subsequent cells are DayOfWeek names (e.g., ,Monday,Tuesday).
 * - Data rows: First cell is a time range (e.g., 09:00-10:00, 4:00-17:00), subsequent cells are Course Names.
 * Example:
 * ,Monday,Tuesday,Wednesday
 * 09:00-10:00,Math,Physics,Chemistry
 * 10:00-11:00,History,Biology,Math
 *
 * Common features supported:
 * - Blank rows (containing only commas or empty) are skipped.
 * - Cells can be unquoted, double-quoted, or single-quoted.
 * - Time parsing is flexible, attempting to interpret "4:00" as "16:00" if no AM/PM is specified
 * and it's a single-digit hour (1-11) as a start time (heuristic).
 * - Handles non-breaking spaces (' ') in course names.
 *
 * NEW: Cell content parsing for ClassSession courseName and notes:
 * - "(Information A) Information B": courseName = Information B, notes = Information A
 * - "Information A (Information B)": courseName = Information A, notes = Information B
 * - "Information A": courseName = Information A, notes = null
 * - "(Information B)": courseName = Information B, notes = null
 *
 * ENHANCEMENTS:
 * 1. Skipping leading "junk" rows: Scans up to the first 100 lines (or EOF) to find the actual timetable header.
 * 2. Discarding trailing "junk" rows: Allows up to 4 successive rows where the first column (day/timeslot) is invalid/blank,
 * then stops parsing. If the first column is valid, the row is processed, even if other cells are empty.
 */
class CsvTimetableParser(private val context: Context) {

    // Define multiple expected time formats for flexible parsing (12-hour and 24-hour)
    private val timeFormatters = listOf(
        DateTimeFormatter.ofPattern("H:mm a", Locale.US),  // e.g., "9:00 AM", "1:30 PM"
        DateTimeFormatter.ofPattern("h:mm a", Locale.US),  // e.g., "09:00 AM", "01:30 PM" (with leading zero for hour)
        DateTimeFormatter.ofPattern("HH:mm", Locale.US),   // e.g., "09:00", "13:30"
        DateTimeFormatter.ofPattern("H:mm", Locale.US)     // e.g., "9:00", "13:30" (single digit hour)
    )

    // Map for flexible DayOfWeek parsing (handles abbreviations and full names, any case)
    private val dayOfWeekMap = mapOf(
        "M" to DayOfWeek.MONDAY, "MO" to DayOfWeek.MONDAY, "MON" to DayOfWeek.MONDAY, "MONDAY" to DayOfWeek.MONDAY,
        "T" to DayOfWeek.TUESDAY, "TU" to DayOfWeek.TUESDAY, "TUE" to DayOfWeek.TUESDAY, "TUES" to DayOfWeek.TUESDAY, "TUESDAY" to DayOfWeek.TUESDAY,
        "W" to DayOfWeek.WEDNESDAY, "WE" to DayOfWeek.WEDNESDAY, "WED" to DayOfWeek.WEDNESDAY, "WEDNESDAY" to DayOfWeek.WEDNESDAY,
        "TH" to DayOfWeek.THURSDAY, "THU" to DayOfWeek.THURSDAY, "THUR" to DayOfWeek.THURSDAY, "THURS" to DayOfWeek.THURSDAY, "THURSDAY" to DayOfWeek.THURSDAY,
        "F" to DayOfWeek.FRIDAY, "FR" to DayOfWeek.FRIDAY, "FRI" to DayOfWeek.FRIDAY, "FRIDAY" to DayOfWeek.FRIDAY,
        "SA" to DayOfWeek.SATURDAY, "SAT" to DayOfWeek.SATURDAY, "SATU" to DayOfWeek.SATURDAY, "SATUR" to DayOfWeek.SATURDAY, "SATURDAY" to DayOfWeek.SATURDAY,
        "SU" to DayOfWeek.SUNDAY, "SUN" to DayOfWeek.SUNDAY, "SUNDAY" to DayOfWeek.SUNDAY
    )

    /**
     * Main parsing function that detects the CSV format and dispatches to the appropriate parser.
     *
     * @param csvUri The Uri of the CSV file to parse.
     * @param timetable The Timetable object this data belongs to (contains validity dates).
     * @return A list of ClassSession objects parsed from the CSV.
     * @throws IllegalArgumentException if the CSV file is empty, has no valid data, or the format is unrecognized.
     */
    fun parseCsv(csvUri: Uri, timetable: Timetable): List<ClassSession> {
        val reader = BufferedReader(InputStreamReader(context.contentResolver.openInputStream(csvUri)))
        val allLines = reader.readLines().filter { it.isNotBlank() && !it.all { char -> char == ',' || char.isWhitespace() || char == ' ' } }
        reader.close()

        if (allLines.isEmpty()) {
            throw IllegalArgumentException("CSV file is empty or contains no valid data.")
        }

        val classSessions = mutableListOf<ClassSession>()
        var currentLineIndex = 0
        val maxHeaderScanLines = 100

        while (currentLineIndex < allLines.size) {
            var headerLine: String? = null
            var headerStartIndex = -1
            var timeSlotColumns: List<Int> = emptyList()
            var dayColumns: List<Int> = emptyList()
            var firstColumnIndex = -1
            var allTimeRanges: List<Pair<LocalTime, LocalTime>> = emptyList()

            for (i in currentLineIndex until minOf(allLines.size, currentLineIndex + maxHeaderScanLines)) {
                val currentLine = allLines[i]
                val headerCells = currentLine.split(",").map { unquoteAndTrim(it) }
                if (headerCells.size < 2) continue

                var startTimeSlotIndex = -1
                var endTimeSlotIndex = -1
                var emptyCellCount = 0
                val tempTimeSlots = mutableListOf<Int>()
                for (j in headerCells.indices) {
                    if (isValidTimeRangeFormat(headerCells[j])) {
                        if (startTimeSlotIndex == -1)
                            startTimeSlotIndex = j
                        endTimeSlotIndex = j
                        tempTimeSlots.add(j)
                        emptyCellCount = 0
                    } else if (headerCells[j].isBlank() && startTimeSlotIndex != -1) {
                        emptyCellCount++
                        if (emptyCellCount > 3) {
                            break
                        }
                    } else if (headerCells[j].isNotBlank() && !isValidTimeRangeFormat(headerCells[j]) && startTimeSlotIndex != -1) {
                        break
                    }
                }

                if (tempTimeSlots.size >= 2 && startTimeSlotIndex != -1 && endTimeSlotIndex != -1) {
                    val times = tempTimeSlots.mapNotNull {
                        try {
                            parseTimeRange(headerCells[it], emptyList())
                        } catch (e: Exception) {
                            Log.w("CsvTimetableParser", "Invalid time range in potential Format A header at line ${i + 1}, col ${it + 1}: '${headerCells[it]}'. Skipping.")
                            null
                        }
                    }
                    if (times.size == tempTimeSlots.size && times.map { it.first } == times.map { it.first }.sorted()) {
                        var dayFound = false
                        for (j in i + 1 until minOf(i + 4, allLines.size)) {
                            val nextLineCells = allLines[j].split(",").map { unquoteAndTrim(it) }
                            if (nextLineCells.getOrNull(startTimeSlotIndex - 1)?.let { isDayOfWeek(it) } == true) {
                                dayFound = true
                                break
                            }
                        }
                        if (dayFound) {
                            allTimeRanges = tempTimeSlots.map {
                                parseTimeRange(headerCells[it], times)
                            }
                            headerLine = currentLine
                            headerStartIndex = i
                            firstColumnIndex = startTimeSlotIndex - 1
                            timeSlotColumns = tempTimeSlots
                            Log.d("CsvTimetableParser", "Found Format A header at line ${i + 1}: '$headerLine', columns ${firstColumnIndex + 1} to ${endTimeSlotIndex + 1}")
                            break
                        } else {
                            Log.d("CsvTimetableParser", "No day found in column ${startTimeSlotIndex} in next 3 rows after potential Format A header at line ${i + 1}. Skipping.")
                        }
                    }
                }

                val tempDayIndices = mutableListOf<Int>()
                var startDayIndex = -1
                var endDayIndex = -1
                var emptyDayCellCount = 0
                for (j in headerCells.indices.drop(1)) {
                    if (isDayOfWeek(headerCells[j])) {
                        if (startDayIndex == -1) startDayIndex = j
                        endDayIndex = j
                        tempDayIndices.add(j)
                        emptyDayCellCount = 0
                    } else if (headerCells[j].isBlank() && startDayIndex != -1) {
                        emptyDayCellCount++
                        if (emptyDayCellCount > 3) {
                            break
                        }
                    } else if (headerCells[j].isNotBlank() && !isDayOfWeek(headerCells[j]) && startDayIndex != -1) {
                        break
                    }
                }
                if (tempDayIndices.size >= 2) {
                    val days = tempDayIndices.mapNotNull {
                        try {
                            parseDayOfWeek(headerCells[it])
                        } catch (e: Exception) {
                            Log.w("CsvTimetableParser", "Invalid day in potential Format B header at line ${i + 1}, col ${it + 1}: '${headerCells[it]}'. Skipping.")
                            null
                        }
                    }
                    if (days.size == tempDayIndices.size && days == days.sortedBy { it.value }) {
                        var timeFound = false
                        for (j in i + 1 until minOf(i + 4, allLines.size)) {
                            val nextLineCells = allLines[j].split(",").map { unquoteAndTrim(it) }
                            if (nextLineCells.getOrNull(0)?.let { isValidTimeRangeFormat(it) } == true) {
                                timeFound = true
                                break
                            }
                        }
                        if (timeFound) {
                            headerLine = currentLine
                            headerStartIndex = i
                            firstColumnIndex = 0
                            dayColumns = tempDayIndices
                            Log.d("CsvTimetableParser", "Found Format B header at line ${i + 1}: '$headerLine', columns ${firstColumnIndex + 1} to ${tempDayIndices.last() + 1}")
                        }
                    }
                }

                for (j in headerCells.indices) {
                    if (isDayOfWeek(headerCells[j]) && j + 1 < headerCells.size && isValidTimeRangeFormat(headerCells[j + 1])) {
                        Log.d("CsvTimetableParser", "Found day '${headerCells[j]}' followed by time slot '${headerCells[j + 1]}' at line ${i + 1}. Skipping as invalid format.")
                        currentLineIndex = i + 1
                        break
                    }
                }
            }

            if (headerLine == null || headerStartIndex == -1) {
                currentLineIndex++
                continue
            }

            val dataLines = allLines.subList(headerStartIndex + 1, allLines.size)
            if (dataLines.isEmpty()) {
                Log.w("CsvTimetableParser", "Header at line ${headerStartIndex + 1}, but no data rows found.")
                currentLineIndex = headerStartIndex + 1
                continue
            }

            val (sessions, lastRowIndex) = if (timeSlotColumns.isNotEmpty()) {
                parseCsvFormatA(dataLines, timetable, headerLine, firstColumnIndex, timeSlotColumns, allTimeRanges, headerStartIndex)
            } else {
                parseCsvFormatB(dataLines, timetable, headerLine, firstColumnIndex, dayColumns)
            }

            if (sessions.isEmpty() && timeSlotColumns.isNotEmpty()) {
                Log.d("CsvTimetableParser", "No class data found for Format A header at line ${headerStartIndex + 1}. Continuing search.")
                currentLineIndex = headerStartIndex + 1
                continue
            }

            classSessions.addAll(sessions)
            currentLineIndex = headerStartIndex + 1 + lastRowIndex + 1
        }

        if (classSessions.isEmpty()) {
            Log.d("CsvTimetableParser", "No Format A or Format B data found. Attempting non-grid parsing.")
            parseNonGridData(allLines, 0, timetable, classSessions)
        }

        if (classSessions.isEmpty()) {
            Log.w("CsvTimetableParser", "No valid timetable or class data found in CSV.")
        }
        return classSessions
    }

    /**
     * Parses CSV files in Format A:
     * - Header row: First cell "Day", subsequent cells are time ranges (e.g., Day,09:00 - 10:15,10:00 - 11:00).
     * (Also handles variant where first cell is blank but subsequent are time ranges)
     * - Data rows: First cell is a DayOfWeek name (e.g., Monday,Math,Physics), subsequent cells are Course Names.
     */
    private fun parseCsvFormatA(
        lines: List<String>,
        timetable: Timetable,
        headerLine: String,
        firstColumnIndex: Int,
        timeSlotColumns: List<Int>,
        allTimeRanges: List<Pair<LocalTime, LocalTime>>,
        headerStartIndex: Int
    ): Pair<List<ClassSession>, Int> {
        val classSessions = mutableListOf<ClassSession>()
        val rawHeaders = headerLine.split(",")
        val periodTimes = mutableListOf<Pair<LocalTime, LocalTime>>()

        for (i in timeSlotColumns) {
            val headerCell = unquoteAndTrim(rawHeaders.getOrElse(i) { "" })
            if (headerCell.isBlank()) {
                Log.w("CsvTimetableParser", "Blank header cell at column $i (Format A). Skipping.")
                periodTimes.add(Pair(LocalTime.MIN, LocalTime.MIN))
                continue
            }
            try {
                periodTimes.add(parseTimeRange(headerCell, allTimeRanges))
            } catch (e: Exception) {
                Log.w("CsvTimetableParser", "Invalid time range in header (Format A, col $i): '$headerCell'. Skipping.", e)
                periodTimes.add(Pair(LocalTime.MIN, LocalTime.MIN))
            }
        }

        var lastValidRowIndex = -1
        var previousDay: DayOfWeek? = null
        var emptyRowCount = 0
        val maxEmptyRows = 3

        for (lineIndex in lines.indices) {
            if (emptyRowCount >= maxEmptyRows) {
                Log.d("CsvTimetableParser", "Found $maxEmptyRows consecutive empty/junk rows at line ${headerStartIndex + lineIndex + 1}. Ending Format A parsing.")
                break
            }

            val line = lines[lineIndex]
            val rawColumns = line.split(",")
            val firstCellContent = unquoteAndTrim(rawColumns.getOrElse(firstColumnIndex) { "" })

            if (firstCellContent.isNotBlank() && !isDayOfWeek(firstCellContent)) {
                Log.d("CsvTimetableParser", "Non-day content '$firstCellContent' found at line ${headerStartIndex + lineIndex + 2}, col ${firstColumnIndex + 1}. Ending Format A parsing.")
                break
            }
            if (firstCellContent.isBlank()) {
                emptyRowCount++
                continue
            }

            val dayOfWeek = try {
                parseDayOfWeek(firstCellContent)
            } catch (e: IllegalArgumentException) {
                Log.w("CsvTimetableParser", "Invalid day in column ${firstColumnIndex + 1} at line ${headerStartIndex + lineIndex + 2}: '$firstCellContent'. Treating as junk.")
                emptyRowCount++
                continue
            }

            if (previousDay != null && dayOfWeek.value <= previousDay.value) {
                Log.w("CsvTimetableParser", "Days not in ascending order at line ${headerStartIndex + lineIndex + 2}: '$dayOfWeek' follows '$previousDay'. Treating as junk.")
                emptyRowCount++
                continue
            }
            previousDay = dayOfWeek
            emptyRowCount = 0

            var sessionsAddedInRow = false
            for ((idx, colIndex) in timeSlotColumns.withIndex()) {
                if (idx < periodTimes.size && colIndex < rawColumns.size) {
                    val (startTime, endTime) = periodTimes[idx]
                    if (startTime != LocalTime.MIN || endTime != LocalTime.MIN) {
                        val cellContent = unquoteAndTrim(rawColumns.getOrElse(colIndex) { "" })
                        val cleanedCellContent = cellContent.replace(' ', ' ').trim()
                        if (cleanedCellContent.isNotBlank()) {
                            val (courseName, notes) = parseCellContent(cleanedCellContent)
                            val durationMinutes = ChronoUnit.MINUTES.between(startTime, endTime).toInt()
                            classSessions.add(
                                ClassSession(
                                    courseName = courseName,
                                    dayOfWeek = dayOfWeek,
                                    startTime = startTime,
                                    durationMinutes = durationMinutes,
                                    validityStartDate = timetable.validityStartDate,
                                    validityEndDate = timetable.validityEndDate,
                                    timetableId = timetable.id,
                                    location = null,
                                    notes = notes,
                                    notificationOffsetMinutes1 = timetable.defaultNotificationOffsetMinutes1,
                                    notificationOffsetMinutes2 = timetable.defaultNotificationOffsetMinutes2
                                )
                            )
                            sessionsAddedInRow = true
                        }
                    }
                }
            }
            if (sessionsAddedInRow) lastValidRowIndex = lineIndex
        }

        return Pair(classSessions, lastValidRowIndex)
    }

    /**
     * Parses CSV files in Format B:
     * - Header row: First cell empty, subsequent cells are DayOfWeek names (e.g., ,Monday,Tuesday).
     * - Data rows: First cell is a time range (e.g., 09:00-10:00, 4:00-17:00), subsequent cells are Course Names.
     */
    private fun parseCsvFormatB(
        lines: List<String>,
        timetable: Timetable,
        headerLine: String,
        firstColumnIndex: Int,
        dayColumns: List<Int>
    ): Pair<List<ClassSession>, Int> {
        val classSessions = mutableListOf<ClassSession>()
        val rawHeaders = headerLine.split(",")
        val days = mutableListOf<DayOfWeek>()

        for (i in dayColumns) {
            val dayString = unquoteAndTrim(rawHeaders.getOrElse(i) { "" })
            try {
                days.add(parseDayOfWeek(dayString))
            } catch (e: IllegalArgumentException) {
                Log.w("CsvTimetableParser", "Invalid day in header (Format B, col $i): '$dayString'. Skipping.", e)
            }
        }

        val allTimeRanges = mutableListOf<Pair<LocalTime, LocalTime>>()
        for (line in lines) {
            val rawColumns = line.split(",")
            val firstCellContent = unquoteAndTrim(rawColumns.getOrElse(firstColumnIndex) { "" })
            if (isValidTimeRangeFormat(firstCellContent)) {
                try {
                    allTimeRanges.add(parseTimeRange(firstCellContent, allTimeRanges))
                } catch (e: Exception) {
                    continue
                }
            }
        }

        var lastValidRowIndex = -1
        var previousTime: LocalTime? = null
        var emptyRowCount = 0
        val maxEmptyRows = 3

        for (lineIndex in lines.indices) {
            if (emptyRowCount >= maxEmptyRows) {
                Log.d("CsvTimetableParser", "Found $maxEmptyRows consecutive empty/junk rows at line ${lineIndex + 1}. Ending Format B parsing.")
                break
            }

            val line = lines[lineIndex]
            val rawColumns = line.split(",")
            val firstCellContent = unquoteAndTrim(rawColumns.getOrElse(firstColumnIndex) { "" })

            if (firstCellContent.isNotBlank() && !isValidTimeRangeFormat(firstCellContent)) {
                Log.d("CsvTimetableParser", "Non-time-slot content '$firstCellContent' found at line ${lineIndex + 1}, col ${firstColumnIndex + 1}. Ending Format B parsing.")
                break
            }

            if (firstCellContent.isBlank()) {
                emptyRowCount++
                continue
            }

            val (startTime, endTime) = try {
                parseTimeRange(firstCellContent, allTimeRanges)
            } catch (e: Exception) {
                Log.e("CsvTimetableParser", "Unexpected: Invalid time range after validation: '$firstCellContent'. Skipping.", e)
                emptyRowCount++
                continue
            }

            if (previousTime != null && !startTime.isAfter(previousTime)) {
                Log.w("CsvTimetableParser", "Time slots not in ascending order at line ${lineIndex + 1}: '$startTime' follows '$previousTime'. Skipping.")
                emptyRowCount++
                continue
            }
            previousTime = startTime
            emptyRowCount = 0

            var sessionsAddedInRow = false
            for ((idx, colIndex) in dayColumns.withIndex()) {
                if (idx < days.size && colIndex < rawColumns.size) {
                    val dayOfWeek = days[idx]
                    val cellContent = unquoteAndTrim(rawColumns.getOrElse(colIndex) { "" })
                    val cleanedCellContent = cellContent.replace(' ', ' ').trim()
                    if (cleanedCellContent.isNotBlank()) {
                        val (courseName, notes) = parseCellContent(cleanedCellContent)
                        val durationMinutes = ChronoUnit.MINUTES.between(startTime, endTime).toInt()
                        classSessions.add(
                            ClassSession(
                                courseName = courseName,
                                dayOfWeek = dayOfWeek,
                                startTime = startTime,
                                durationMinutes = durationMinutes,
                                validityStartDate = timetable.validityStartDate,
                                validityEndDate = timetable.validityEndDate,
                                timetableId = timetable.id,
                                location = null,
                                notes = notes,
                                notificationOffsetMinutes1 = timetable.defaultNotificationOffsetMinutes1,
                                notificationOffsetMinutes2 = timetable.defaultNotificationOffsetMinutes2
                            )
                        )
                        sessionsAddedInRow = true
                    }
                }
            }
            if (sessionsAddedInRow) lastValidRowIndex = lineIndex
        }

        return Pair(classSessions, lastValidRowIndex)
    }

    /**
     * Parses non-grid data (e.g., rows 1–14, 26–35) as individual class sessions.
     * Supports formats like: CourseName,Section,Instructor,Day,TimeRange[,Location].
     * Also handles decimal times (e.g., 0.347222 for 8:20 AM).
     * @return The index of the next line to process.
     */
    private fun parseNonGridData(
        lines: List<String>,
        startIndex: Int,
        timetable: Timetable,
        classSessions: MutableList<ClassSession>
    ): Int {
        var currentIndex = startIndex
        while (currentIndex < lines.size) {
            val line = lines[currentIndex]
            val cells = line.split(",").map { unquoteAndTrim(it) }
            if (cells.size < 4) {
                currentIndex++
                continue
            }

            val courseName = cells[0]
            val dayString = cells.getOrNull(3) ?: ""
            var timeRangeString = cells.getOrNull(4) ?: ""
            val instructor = cells.getOrNull(2)
            val location = cells.getOrNull(5)

            var startTime: LocalTime? = null
            var endTime: LocalTime? = null
            if (cells.size >= 6 && cells[4].matches(Regex("\\d+\\.\\d+")) && cells[5].matches(Regex("\\d+\\.\\d+"))) {
                startTime = parseDecimalTime(cells[4])
                endTime = parseDecimalTime(cells[5])
                if (startTime != null && endTime != null) {
                    timeRangeString = ""
                }
            } else if (isValidTimeRangeFormat(timeRangeString)) {
                try {
                    val (start, end) = parseTimeRange(timeRangeString, emptyList())
                    startTime = start
                    endTime = end
                } catch (e: Exception) {
                    Log.w("CsvTimetableParser", "Invalid time range in non-grid row ${currentIndex + 1}: '$timeRangeString'. Skipping.", e)
                    currentIndex++
                    continue
                }
            }

            if (courseName.isNotBlank() && isDayOfWeek(dayString) && startTime != null && endTime != null) {
                try {
                    val dayOfWeek = parseDayOfWeek(dayString)
                    val durationMinutes = ChronoUnit.MINUTES.between(startTime, endTime).toInt()
                    classSessions.add(
                        ClassSession(
                            courseName = courseName,
                            dayOfWeek = dayOfWeek,
                            startTime = startTime,
                            durationMinutes = durationMinutes,
                            validityStartDate = timetable.validityStartDate,
                            validityEndDate = timetable.validityEndDate,
                            timetableId = timetable.id,
                            location = location?.takeIf { it.isNotBlank() },
                            notes = instructor?.takeIf { it.isNotBlank() },
                            notificationOffsetMinutes1 = timetable.defaultNotificationOffsetMinutes1,
                            notificationOffsetMinutes2 = timetable.defaultNotificationOffsetMinutes2
                        )
                    )
                } catch (e: Exception) {
                    Log.w("CsvTimetableParser", "Failed to parse non-grid row ${currentIndex + 1}: '$line'. Skipping.", e)
                    currentIndex++
                    continue
                }
            } else {
                val isPotentialHeader = cells.drop(1).any { isValidTimeRangeFormat(it) || isDayOfWeek(it) }
                if (isPotentialHeader) {
                    return currentIndex
                }
            }
            currentIndex++
        }
        return currentIndex
    }

    /**
     * NEW FUNCTION: Parses the content of a CSV cell to extract courseName and notes.
     * Handles multiple formats, ordered by priority:
     * 1. "(Information A) Information B": courseName = Information B, notes = Information A
     * 2. "Information A (Information B)": courseName = Information A, notes = Information B
     * 3. "Information A": courseName = Information A, notes = null
     * 4. "(Information B)": courseName = Information B, notes = null
     *
     * @param cellContent The raw string content of the CSV cell.
     * @return A Pair of (courseName: String, notes: String?).
     */
//    private fun parseCellContent(cellContent: String): Pair<String, String?> {
//        val trimmedContent = cellContent.trim()
//
//        val regexNewFormat = Regex("^\\((.*?)\\)\\s*(.*)$")
//        val matchNewFormat = regexNewFormat.find(trimmedContent)
//        if (matchNewFormat != null) {
//            val infoA = matchNewFormat.groups[1]?.value?.trim() ?: ""
//            val infoB = matchNewFormat.groups[2]?.value?.trim() ?: ""
//            return Pair(infoB, infoA.ifBlank { null })
//        }
//
//        val regexOldFormat = Regex("^(.*?)\\s*\\((.*?)\\)$")
//        val matchOldFormat = regexOldFormat.find(trimmedContent)
//        if (matchOldFormat != null) {
//            val infoA = matchOldFormat.groups[1]?.value?.trim() ?: ""
//            val infoB = matchOldFormat.groups[2]?.value?.trim() ?: ""
//            return Pair(infoA, infoB.ifBlank { null })
//        }
//
//        if (trimmedContent.startsWith("(") && trimmedContent.endsWith(")")) {
//            val infoB = trimmedContent.substring(1, trimmedContent.length - 1).trim()
//            return Pair(infoB, null)
//        }
//
//        return Pair(trimmedContent, null)
//    }

    private fun parseCellContent(cellContent: String): Pair<String, String?> {
        val trimmedContent = cellContent.trim()
        if (trimmedContent.isEmpty()) return Pair("", null)

        // Split on whitespace, preserving content inside parentheses
        val parts = mutableListOf<String>()
        var currentPart = StringBuilder()
        var insideParentheses = false
        var i = 0

        while (i < trimmedContent.length) {
            when (val char = trimmedContent[i]) {
                '(' -> {
                    if (!insideParentheses && currentPart.isNotEmpty()) {
                        parts.add(currentPart.toString().trim())
                        currentPart = StringBuilder()
                    }
                    insideParentheses = true
                    currentPart.append(char)
                }
                ')' -> {
                    if (insideParentheses) {
                        currentPart.append(char)
                        parts.add(currentPart.toString().trim())
                        currentPart = StringBuilder()
                        insideParentheses = false
                    } else {
                        currentPart.append(char)
                    }
                }
                ' ' -> {
                    if (insideParentheses) {
                        currentPart.append(char)
                    } else if (currentPart.isNotEmpty()) {
                        parts.add(currentPart.toString().trim())
                        currentPart = StringBuilder()
                    }
                }
                else -> currentPart.append(char)
            }
            i++
        }
        if (currentPart.isNotEmpty()) {
            parts.add(currentPart.toString().trim())
        }

        // Separate bracketed and non-bracketed parts
        val bracketed = parts.filter { it.startsWith("(") && it.endsWith(")") }
            .map { it.substring(1, it.length - 1).trim() }
            .filter { it.isNotBlank() }
        val nonBracketed = parts.filterNot { it.startsWith("(") && it.endsWith(")") }
            .filter { it.isNotBlank() }

        // Join parts with space separator
        val notes = if (bracketed.isNotEmpty()) bracketed.joinToString(" ") else null
        val courseName = if (nonBracketed.isNotEmpty()) nonBracketed.joinToString(" ") else ""

        return Pair(courseName, notes)
    }

    /**
     * Removes leading/trailing whitespace and ALL occurrences of single quotes (') and double quotes (").
     */
    private fun unquoteAndTrim(value: String): String {
        return value.replace("\"", "").replace("'", "").trim()
    }

    /**
     * Parses a day string (e.g., "Mon", "Monday", "mo") into a DayOfWeek enum.
     * Throws IllegalArgumentException if the day string is not recognized.
     */
    private fun parseDayOfWeek(dayString: String): DayOfWeek {
        val normalizedDayString = dayString.uppercase(Locale.ROOT)
        return dayOfWeekMap[normalizedDayString]
            ?: throw IllegalArgumentException("Invalid day of week format: '$dayString'.")
    }

    /**
     * Helper function to check if a string represents a valid DayOfWeek (without throwing).
     */
    private fun isDayOfWeek(s: String): Boolean {
        return dayOfWeekMap.containsKey(s.uppercase(Locale.ROOT))
    }

    /**
     * Parses a time string (e.g., "9:00", "09:00", "1:30 PM", "13:30") into a LocalTime object.
     * Tries multiple predefined formatters.
     * Includes a heuristic: if a single-digit hour (1-11) is provided without AM/PM,
     * and parsing as 24-hour results in an hour < 12, it attempts to interpret it as PM (add 12 hours).
     * This addresses the "4:00 should be 16:00" requirement.
     * Throws DateTimeParseException if the time string cannot be parsed by any formatter.
     */
    private fun parseTime(timeString: String, allTimeRanges: List<Pair<LocalTime, LocalTime>>): LocalTime {
        var parsedTime: LocalTime? = null
        var usedFormatter: DateTimeFormatter? = null

        for (formatter in timeFormatters) {
            try {
                parsedTime = LocalTime.parse(timeString, formatter)
                usedFormatter = formatter
                break
            } catch (e: DateTimeParseException) {
                // Try next formatter
            }
        }

        if (parsedTime == null) {
            throw DateTimeParseException("Could not parse time: '$timeString' with any known format.", timeString, 0)
        }

        val nonNullParsedTime: LocalTime = parsedTime
        val containsAmPm = timeString.contains("am", ignoreCase = true) || timeString.contains("pm", ignoreCase = true) ||
                usedFormatter?.toString()?.contains("a", ignoreCase = true) == true

        var adjustedTime = nonNullParsedTime
        if (!containsAmPm && nonNullParsedTime.hour >= 1 && nonNullParsedTime.hour <= 11) {
            Log.d("CsvTimetableParser", "Applying PM heuristic for '$timeString' (hour ${nonNullParsedTime.hour}).")
            adjustedTime = nonNullParsedTime.plusHours(12)

            val earlierTimes = allTimeRanges.filter { it.first.isBefore(adjustedTime) }.map { it.first }
            val laterTimes = allTimeRanges.filter { it.first.isAfter(adjustedTime) }.map { it.first }
            val currentIndex = allTimeRanges.indexOfFirst { it.first == adjustedTime || it.first == nonNullParsedTime }
            if (currentIndex > 0 && earlierTimes.any { it.isAfter(adjustedTime) } ||
                currentIndex < allTimeRanges.size - 1 && laterTimes.any { it.isBefore(adjustedTime) }) {
                if (!nonNullParsedTime.isBefore(LocalTime.of(6, 0))) {
                    Log.d("CsvTimetableParser", "Reverting to AM for '$timeString' to maintain ascending order.")
                    adjustedTime = nonNullParsedTime
                }
            }
        }

        if (adjustedTime.isBefore(LocalTime.of(6, 0)) && !timeString.contains("am", ignoreCase = true)) {
            Log.d("CsvTimetableParser", "Adjusting time '$timeString' from $adjustedTime to PM (add 12 hours) due to no classes before 6:00 AM.")
            adjustedTime = adjustedTime.plusHours(12)
        }

        return adjustedTime
    }

    /**
     * Helper function to check if a string looks like a valid time range format (without full parsing).
     */
//    private fun isValidTimeRangeFormat(s: String): Boolean {
//        // This function should not remove quotes, as it's just checking format.
//        // The actual parsing in parseTimeRange will handle quote removal.
//        val parts = s.split(Regex("\\s*[-–—]+\\s*"))
//        return parts.size == 2 &&
//                parts[0].trim().matches(Regex("^\"?\\d{1,2}:\\d{2}(?:\\s*[AP]M)?\"?$", RegexOption.IGNORE_CASE)) && // Allow optional quotes for format check
//                parts[1].trim().matches(Regex("^\"?\\d{1,2}:\\d{2}(?:\\s*[AP]M)?\"?$", RegexOption.IGNORE_CASE))
//    }
    private fun isValidTimeRangeFormat(s: String): Boolean {
        val parts = s.split(Regex("\\s*[-–—]+\\s*"))
        return parts.size == 2 &&
                parts[0].trim().matches(Regex("^\"?\\d{1,2}:\\d{2}(?:\\s*[AP]M)?\"?$", RegexOption.IGNORE_CASE)) &&
                parts[1].trim().matches(Regex("^\"?\\d{1,2}:\\d{2}(?:\\s*[AP]M)?\"?$", RegexOption.IGNORE_CASE))
    }

    /**
     * Parses Excel decimal time (e.g., 0.347222 for 8:20 AM).
     */
    private fun parseDecimalTime(decimal: String): LocalTime? {
        try {
            val fraction = decimal.toDouble()
            val totalMinutes = (fraction * 24 * 60).toInt()
            var time = LocalTime.of(totalMinutes / 60, totalMinutes % 60)
            if (time.isBefore(LocalTime.of(6, 0))) {
                Log.d("CsvTimetableParser", "Adjusting decimal time '$decimal' from $time to PM (add 12 hours) due to no classes before 6:00 AM.")
                time = time.plusHours(12)
            }
            return time
        } catch (e: Exception) {
            Log.w("CsvTimetableParser", "Invalid decimal time: '$decimal'", e)
            return null
        }
    }

    /**
     * Parses a time range string (e.g., "09:00-10:00", "4:00-17:00") into a pair of LocalTime objects.
     * @param timeRangeString The string representing the time range.
     * @return A Pair of LocalTime (startTime, endTime).
     * @throws IllegalArgumentException if the format is invalid or times are out of order.
     */
    private fun parseTimeRange(timeRangeString: String, allTimeRanges: List<Pair<LocalTime, LocalTime>>): Pair<LocalTime, LocalTime> {
        val noQuotesString = timeRangeString.replace("\"", "").replace("'", "").trim()
        val parts = noQuotesString.split(Regex("\\s*[-–—]+\\s*"))

        if (parts.size != 2) {
            throw IllegalArgumentException("Invalid time range format: '$timeRangeString'. Expected 'HH:mm - HH:mm' or similar.")
        }

        val startTime = parseTime(parts[0].trim(), allTimeRanges)
        val endTime = parseTime(parts[1].trim(), allTimeRanges)

        if (startTime.isAfter(endTime) || startTime == endTime) {
            throw IllegalArgumentException("Start time ($startTime) must be before end time ($endTime) in range: '$timeRangeString'.")
        }
        return Pair(startTime, endTime)
    }
}