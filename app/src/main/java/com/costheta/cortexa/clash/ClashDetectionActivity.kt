package com.costheta.cortexa.clash

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action

import androidx.appcompat.app.AppCompatActivity

class ClashDetectionActivity : AppCompatActivity() {
//
//    private lateinit var clashDetectionView: ClashDetectionView
//    private lateinit var timetableDao: TimetableDao
//    private lateinit var engagementDao: EngagementDao // NEW: Engagement DAO
//    private lateinit var clashDetectionTitle: TextView
//    private lateinit var clashDetectionScrollView: ScrollView // Reference to the ScrollView
//
//    private var currentDayOffset: Int = 0 // 0 for today, 1 for tomorrow, ..., 6 for 7th day
//
//    private val dayButtons = mutableListOf<Button>()
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_clash_detection)
//
//        clashDetectionView = findViewById(R.id.clashDetectionView)
//        timetableDao = AppDatabase.getDatabase(applicationContext).timetableDao()
//        engagementDao = AppDatabase.getDatabase(applicationContext).engagementDao() // NEW: Initialize EngagementDao
//        clashDetectionTitle = findViewById(R.id.clashDetectionTitle)
//        clashDetectionScrollView = findViewById(R.id.clashDetectionScrollView)
//
//        // Initialize day buttons
//        dayButtons.add(findViewById(R.id.btnDay1))
//        dayButtons.add(findViewById(R.id.btnDay2))
//        dayButtons.add(findViewById(R.id.btnDay3))
//        dayButtons.add(findViewById(R.id.btnDay4))
//        dayButtons.add(findViewById(R.id.btnDay5))
//        dayButtons.add(findViewById(R.id.btnDay6))
//        dayButtons.add(findViewById(R.id.btnDay7))
//
//        setupDayButtons()
//        updateDaySelection(0) // Select today by default
//    }
//
//    private fun setupDayButtons() {
//        for (i in 0 until dayButtons.size) {
//            val button = dayButtons[i]
//            button.setOnClickListener {
//                updateDaySelection(i)
//            }
//        }
//    }
//
//    private fun updateDaySelection(dayOffset: Int) {
//        currentDayOffset = dayOffset
//        val selectedDate = LocalDate.now().plusDays(dayOffset.toLong())
//        val dayOfWeek = selectedDate.dayOfWeek
//
//        // Update button selected state and text
//        for (i in 0 until dayButtons.size) {
//            val button = dayButtons[i]
//            val dateForButton = LocalDate.now().plusDays(i.toLong())
//            button.text = dateForButton.format(DateTimeFormatter.ofPattern("EEE\ndd", Locale.getDefault())) // e.g., Mon\n15
//            button.isSelected = (i == dayOffset)
//        }
//
//        clashDetectionTitle.text = getString(R.string.clash_detection_title_for_date, selectedDate.format(DateTimeFormatter.ofPattern("EEEE, MMM dd", Locale.getDefault())))
//        loadEventsForDate(selectedDate)
//    }
//
//    private fun loadEventsForDate(date: LocalDate) {
//        lifecycleScope.launch {
//            try {
//                val allEventsForDate = mutableListOf<ClashEvent>()
//
//                // 1. Get Class Sessions (Lectures, Personal)
//                val classSessions = timetableDao.getClassSessionsForDate(date, date.dayOfWeek)
//                Log.d("ClashDetectionActivity", "Found ${classSessions.size} class sessions for date $date")
//                classSessions.forEach { session ->
//                    allEventsForDate.add(
//                        ClashEvent(
//                            id = session.sessionId,
//                            name = session.courseName,
//                            type = ClashEventType.LECTURE, // Assuming regular sessions are lectures/personal
//                            date = date,
//                            startTime = session.startTime,
//                            endTime = session.startTime.plusMinutes(session.durationMinutes.toLong()),
//                            location = session.location,
//                            notes = session.notes
//                        )
//                    )
//                }
//
//                // 2. Get Engagements (Ad-hoc)
//                val engagements = engagementDao.getEngagementsForSpecificDate(date, date.dayOfWeek.name)
//                Log.d("ClashDetectionActivity", "Found ${engagements.size} engagements for date $date")
//                engagements.forEach { engagement ->
//                    // For recurrence type, need to check if the specific 'date' falls on a recurrence interval
//                    val isRecurrenceMatch = if (engagement.daySelectionType == "RECURRENCE" && engagement.recurrenceIntervalDays != null) {
//                        val daysSinceValidityStart = ChronoUnit.DAYS.between(engagement.validityStartDate, date)
//                        daysSinceValidityStart >= 0 && daysSinceValidityStart % engagement.recurrenceIntervalDays == 0L
//                    } else {
//                        true // For SPECIFIC_DAYS or DAILY, the DAO query already handles it
//                    }
//
//                    if (isRecurrenceMatch) {
//                        allEventsForDate.add(
//                            ClashEvent(
//                                id = engagement.engagementId,
//                                name = engagement.engagementName,
//                                type = ClashEventType.ENGAGEMENT, // NEW: Use ENGAGEMENT type
//                                date = date,
//                                startTime = engagement.startTime,
//                                endTime = engagement.startTime.plusMinutes(engagement.durationMinutes.toLong()),
//                                location = null, // Engagements don't have a direct location field in the model
//                                notes = engagement.notes
//                            )
//                        )
//                    }
//                }
//
//
//                // 3. Get Exams
//                val exams = timetableDao.getExamsForSpecificDate(date)
//                Log.d("ClashDetectionActivity", "Found ${exams.size} exams for date $date")
//                exams.forEach { exam ->
//                    allEventsForDate.add(
//                        ClashEvent(
//                            id = exam.examId,
//                            name = exam.courseName,
//                            type = ClashEventType.EXAM,
//                            date = date,
//                            startTime = exam.startTime,
//                            endTime = exam.endTime,
//                            location = exam.venue,
//                            notes = null // Exams don't have notes in the model
//                        )
//                    )
//                }
//
//                // Sort all events by start time
//                val sortedEvents = allEventsForDate.sortedBy { it.startTime }
//                Log.d("ClashDetectionActivity", "Total sorted events for $date: ${sortedEvents.size}")
//
//                withContext(Dispatchers.Main) {
//                    if (sortedEvents.isEmpty()) {
//                        Toast.makeText(this@ClashDetectionActivity, getString(R.string.no_events_found_for_day), Toast.LENGTH_SHORT).show()
//                        Log.d("ClashDetectionActivity", "No events found for $date. Displaying toast.")
//                    }
//                    clashDetectionView.events = sortedEvents
//                    // Scroll to the current time if it's today's schedule
//                    val now = LocalTime.now()
//                    val scrollTargetY = clashDetectionView.getScrollYForTime(now.hour, now.minute)
//                    clashDetectionScrollView.post {
//                        clashDetectionScrollView.scrollTo(0, scrollTargetY)
//                    }
//                }
//            } catch (e: Exception) {
//                withContext(Dispatchers.Main) {
//                    Toast.makeText(this@ClashDetectionActivity, getString(R.string.clash_detection_error, e.localizedMessage), Toast.LENGTH_LONG).show()
//                    Log.e("ClashDetectionActivity", "Error loading events for clash detection: ${e.message}", e)
//                }
//            }
//        }
//    }
//
//    /**
//     * Helper function to determine the next relevant occurrence date for a session.
//     * This is crucial for recurrence-based ad-hoc engagements.
//     *
//     * @param session The ClassSession (old ad-hoc) or Engagement object.
//     * @param fromDateTime The LocalDateTime from which to start checking for occurrences.
//     * @param logicalValidityStartDate The actual start date of the engagement's validity.
//     * @return The LocalDate of the next occurrence, or null if none found within a reasonable future.
//     */
//    private fun getNextOccurrenceDate(
//        session: ClassSession?, // Keep for backward compatibility
//        engagement: Engagement?, // NEW: For Engagement model
//        fromDateTime: LocalDateTime,
//        logicalValidityStartDate: LocalDate
//    ): LocalDate? {
//        var currentDate = fromDateTime.toLocalDate()
//        val endDate = session?.validityEndDate ?: engagement?.validityEndDate ?: LocalDate.MAX // Use max if null
//
//        // Limit the search to prevent infinite loops for very long validities
//        val maxSearchDays = 365 // Search up to one year into the future
//        val initialDate = currentDate
//
//        while (!currentDate.isAfter(endDate) && ChronoUnit.DAYS.between(initialDate, currentDate) < maxSearchDays) {
//            val startTime = session?.startTime ?: engagement?.startTime ?: LocalTime.MIN
//            val durationMinutes = session?.durationMinutes ?: engagement?.durationMinutes ?: 0
//
//            val occurrenceStartDateTime = LocalDateTime.of(currentDate, startTime)
//            val occurrenceEndDateTime = occurrenceStartDateTime.plusMinutes(durationMinutes.toLong())
//
//            val isOccurrenceRelevant = occurrenceEndDateTime.isAfter(fromDateTime)
//
//            if (isOccurrenceRelevant) {
//                // Handle ClassSession (old ad-hoc)
//                if (session != null) {
//                    when (session.daySelectionType) {
//                        "SPECIFIC_DAYS" -> {
//                            val selectedDays = session.selectedDaysOfWeek?.split(",")?.mapNotNull {
//                                try { DayOfWeek.valueOf(it) } catch (e: IllegalArgumentException) { null }
//                            } ?: emptyList()
//                            if (selectedDays.contains(currentDate.dayOfWeek)) {
//                                return occurrenceStartDateTime.toLocalDate()
//                            }
//                        }
//                        "RECURRENCE" -> {
//                            session.recurrenceIntervalDays?.let { interval ->
//                                val daysSinceLogicalStart = ChronoUnit.DAYS.between(logicalValidityStartDate, currentDate)
//                                if (daysSinceLogicalStart >= 0 && daysSinceLogicalStart % interval == 0L) {
//                                    return occurrenceStartDateTime.toLocalDate()
//                                }
//                            }
//                        }
//                        "DAILY" -> { // Handle explicit daily for old ClassSession
//                            return occurrenceStartDateTime.toLocalDate()
//                        }
//                        null -> { // Fallback for very old ad-hoc without daySelectionType
//                            if (session.dayOfWeek == currentDate.dayOfWeek) {
//                                return occurrenceStartDateTime.toLocalDate()
//                            }
//                        }
//                    }
//                }
//                // Handle Engagement (new ad-hoc)
//                else if (engagement != null) {
//                    when (engagement.daySelectionType) {
//                        "SPECIFIC_DAYS" -> {
//                            val selectedDays = engagement.selectedDaysOfWeek?.split(",")?.mapNotNull {
//                                try { DayOfWeek.valueOf(it) } catch (e: IllegalArgumentException) { null }
//                            } ?: emptyList()
//                            if (selectedDays.contains(currentDate.dayOfWeek)) {
//                                return occurrenceStartDateTime.toLocalDate()
//                            }
//                        }
//                        "RECURRENCE" -> {
//                            engagement.recurrenceIntervalDays?.let { interval ->
//                                val daysSinceLogicalStart = ChronoUnit.DAYS.between(logicalValidityStartDate, currentDate)
//                                if (daysSinceLogicalStart >= 0 && daysSinceLogicalStart % interval == 0L) {
//                                    return occurrenceStartDateTime.toLocalDate()
//                                }
//                            }
//                        }
//                        "DAILY" -> { // Handle explicit daily for new Engagement
//                            return occurrenceStartDateTime.toLocalDate()
//                        }
//                    }
//                }
//            }
//            currentDate = currentDate.plusDays(1)
//        }
//        return null
//    }
}
