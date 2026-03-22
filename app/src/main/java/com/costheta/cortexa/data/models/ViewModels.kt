package com.costheta.cortexa.data.models

import java.time.LocalDate
import java.time.LocalTime
import java.time.LocalDateTime

// Define a sealed class to represent different types of events in the schedule view.
// This allows us to handle different data models (ClassSession, Exam, Assignment, Engagement, TodoItem)
// uniformly in the ScheduleView, while retaining their specific properties.
sealed class ScheduleEvent(
    open val id: Long?,
    open val name: String,
    open val date: LocalDate,
    open val startTime: LocalTime,
    open val durationMinutes: Int,
    open val type: ScheduleEventType, // Use the enum defined below
    open val location: String? = null,
    open val notes: String? = null,
    open val originalItem: Any? = null // Reference to the original data object
) {
    // Enum to categorize the type of event for drawing and display purposes.
    enum class ScheduleEventType {
        LECTURE, PERSONAL, EXAM, ASSIGNMENT, ENGAGEMENT, TODO, ACTION, CALENDAR,
        // NEW: Added TASK
        TASK
    }

    // Concrete data class for ClassSession events.
    data class ClassSessionEvent(
        override val id: Long?,
        override val name: String,
        override val date: LocalDate,
        override val startTime: LocalTime,
        override val durationMinutes: Int,
        override val type: ScheduleEventType,
        override val location: String?,
        override val notes: String?,
        override val originalItem: ClassSession?,
        val isModified: Boolean = false
    ) : ScheduleEvent(id, name, date, startTime, durationMinutes, type, location, notes, originalItem)

    // Concrete data class for Exam events.
    data class ExamEvent(
        override val id: Long?,
        override val name: String,
        override val date: LocalDate,
        override val startTime: LocalTime,
        override val durationMinutes: Int,
        override val type: ScheduleEventType,
        override val location: String?, // Venue for exams
        override val notes: String? = null,
        override val originalItem: Exam?
    ) : ScheduleEvent(id, name, date, startTime, durationMinutes, type, location, notes, originalItem)

    // Concrete data class for Assignment events.
    data class AssignmentEvent(
        override val id: Long?,
        override val name: String, // Assignment name
        override val date: LocalDate, // Due date
        override val startTime: LocalTime, // Submission time
        override val durationMinutes: Int = 0, // Assignments are point events, no duration
        override val type: ScheduleEventType = ScheduleEventType.ASSIGNMENT,
        override val location: String? = null, // Course name can be passed here or in notes
        override val notes: String? = null, // Course name or other details
        override val originalItem: Assignment?
    ) : ScheduleEvent(id, name, date, startTime, durationMinutes, type, location, notes, originalItem)

    // Concrete data class for Engagement events.
    data class EngagementEvent(
        override val id: Long?,
        override val name: String,
        override val date: LocalDate,
        override val startTime: LocalTime,
        override val durationMinutes: Int,
        override val type: ScheduleEventType = ScheduleEventType.ENGAGEMENT,
        override val location: String? = null,
        override val notes: String?,
        override val originalItem: Engagement?,
        val isModified: Boolean = false
    ) : ScheduleEvent(id, name, date, startTime, durationMinutes, type, location, notes, originalItem)

    // Concrete data class for SyncedCalendarEvent events
    data class CalendarEvent(
        override val id: Long?,
        override val name: String,
        override val date: LocalDate,
        override val startTime: LocalTime,
        override val durationMinutes: Int,
        override val type: ScheduleEventType = ScheduleEventType.CALENDAR,
        override val location: String?,
        override val notes: String?,
        override val originalItem: SyncedCalendarEvent?
    ) : ScheduleEvent(id, name, date, startTime, durationMinutes, type, location, notes, originalItem)


    // Concrete data class for To-Do events
    data class TodoEvent(
        override val id: Long?,
        override val name: String, // To-Do title
        override val date: LocalDate, // Due date
        override val startTime: LocalTime, // Due time
        override val durationMinutes: Int = 0, // To-Do's are point events
        override val type: ScheduleEventType = ScheduleEventType.TODO,
        override val location: String? = null, // Not applicable for To-Do, can be null
        override val notes: String? = null, // To-Do description
        val priority: String?, // High, Medium, Low, None
        override val originalItem: TodoItem?
    ) : ScheduleEvent(id, name, date, startTime, durationMinutes, type, location, notes, originalItem)

    // NEW: Concrete data class for SyncedGoogleTask events
    data class TaskEvent(
        override val id: Long?,
        override val name: String, // Task title
        override val date: LocalDate, // Due date
        override val startTime: LocalTime, // Due time
        override val durationMinutes: Int = 0, // Tasks are point events
        override val type: ScheduleEventType = ScheduleEventType.TASK,
        override val location: String? = null,
        override val notes: String?, // Task notes
        val priority: String = "Medium", // Hardcoded as requested
        override val originalItem: SyncedGoogleTask?
    ) : ScheduleEvent(id, name, date, startTime, durationMinutes, type, location, notes, originalItem)


    data class ActionEvent(
        override val id: Long,
        override val name: String,
        override val date: LocalDate,
        override val startTime: LocalTime,
        override val durationMinutes: Int,
        override val type: ScheduleEventType = ScheduleEventType.ACTION,
        override val location: String? = null,
        override val notes: String? = null,
        override val originalItem: Action?
    ) : ScheduleEvent(id, name, date, startTime, durationMinutes, type, location, notes, originalItem)

    // Helper to get end time
    val endTime: LocalTime
        get() = startTime.plusMinutes(durationMinutes.toLong())

    // Helper to get the full LocalDateTime for start and end
    fun getStartDateTime(): LocalDateTime = LocalDateTime.of(date, startTime)
    fun getEndDateTime(): LocalDateTime = LocalDateTime.of(date, endTime)

}
