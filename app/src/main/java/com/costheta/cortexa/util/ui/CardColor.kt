package com.costheta.cortexa.util.ui

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action

import android.content.Context
import androidx.core.content.ContextCompat
import com.costheta.cortexa.R
import com.costheta.cortexa.data.models.Action
import com.costheta.cortexa.data.models.Assignment
import com.costheta.cortexa.data.models.Engagement
import com.costheta.cortexa.data.models.Exam
import com.costheta.cortexa.data.models.NotesItem
import com.costheta.cortexa.data.models.SyncedCalendarEvent
import com.costheta.cortexa.data.models.SyncedGoogleTask
import com.costheta.cortexa.data.models.TodoItem
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Utility object for determining card background and text colors based on item status.
 * This centralizes coloring logic, making it reusable and easier to maintain.
 */
object CardColor {

    /**
     * Determines the background and text colors for an Assignment card.
     *
     * @param assignment The Assignment object containing assignment data and calculated hours left.
     * @param context The Context to resolve color resources.
     * @return A Pair where the first Int is the background color and the second Int is the text color.
     */
    fun determineAssignmentCardColors(assignment: Assignment, context: Context): Pair<Int, Int> {
        // Use the reliable UTC timestamp and convert it to the user's current zone.
        val systemZoneId = ZoneId.systemDefault()
        val dueDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(assignment.dueDateTimeMillis), systemZoneId)
        val now = LocalDateTime.now(systemZoneId)
        val hoursLeft = ChronoUnit.MINUTES.between(now, dueDateTime).toFloat() / 60.0f
        val estimatedHours = assignment.estimatedHours.toFloat()

        val cardBackgroundColor: Int
        var textColor: Int = ContextCompat.getColor(context, R.color.black) // Default text color

        when {
            // If progress is 100%, then light green, regardless of time remaining
            assignment.currentProgress == 100 -> {
                cardBackgroundColor = ContextCompat.getColor(context, R.color.assignment_completed_green)
                textColor = ContextCompat.getColor(context, R.color.white1)
            }
            // If time_remaining < estimatedHours / 2, background color of card is light red
            hoursLeft < 0 -> {
                cardBackgroundColor = ContextCompat.getColor(context, R.color.assignment_overdue_red)
                textColor = ContextCompat.getColor(context, R.color.white1)
            }
            // If time_remaining < estimatedHours / 2, background color of card is light red
            hoursLeft < estimatedHours / 2 -> {
                cardBackgroundColor = ContextCompat.getColor(context, R.color.assignment_nearing_submission_red)
            }
            // If time_remaining < estimatedHours, background color of card is light yellow
            hoursLeft < 3 * estimatedHours / 4 -> {
                cardBackgroundColor = ContextCompat.getColor(context, R.color.assignment_imminent_yellow)
            }
            // If time_remaining < estimatedHours, background color of card is light yellow
            hoursLeft < estimatedHours -> {
                cardBackgroundColor = ContextCompat.getColor(context, R.color.assignment_upcoming_yellow)
            }
            // If progress > 0, then light green
            assignment.currentProgress > 0 -> {
                cardBackgroundColor = ContextCompat.getColor(context, R.color.assignment_in_progress_green)
            }
            // Else (progress is 0 and time_remaining >= estimatedHours), light-gray
            else -> {
                cardBackgroundColor = ContextCompat.getColor(context, R.color.assignment_default_light_dark_blue)
            }
        }
        return Pair(cardBackgroundColor, textColor)
    }

    /**
     * Determines the background and text colors for an Action card.
     *
     * @param action The Action object.
     * @param context The Context to resolve color resources.
     * @return A Pair where the first Int is the background color and the second Int is the text color.
     */
    fun determineActionCardColors(action: Action, context: Context): Pair<Int, Int> {
        val systemZoneId = ZoneId.systemDefault()
        val now = LocalDateTime.now(systemZoneId)
        val startDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(action.startDateTimeMillis), systemZoneId)
        val endDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(action.endDateTimeMillis), systemZoneId)
        val done: Boolean = action.isDone

        val notification1 = action.notificationMinutes1 ?: 0
        val notification2 = action.notificationMinutes2 ?: 0
        val notification3 = action.notificationMinutes3 ?: 0
        val X = maxOf(notification1, notification2, notification3)
        val Y = listOf(notification1, notification2, notification3).sorted()[1]
        val Z = minOf(notification1, notification2, notification3)

        val minutesUntilStart = ChronoUnit.MINUTES.between(now, startDateTime)

        val cardBackgroundColor: Int
        var textColor: Int = ContextCompat.getColor(context, R.color.black)

        when {
            done -> {
                cardBackgroundColor = ContextCompat.getColor(context, R.color.action_completed_green)
                textColor = ContextCompat.getColor(context, R.color.white1)
            }
            else -> {
                when {
                    now.isAfter(endDateTime) -> {
                        cardBackgroundColor = ContextCompat.getColor(context, R.color.action_over_not_done_dark_red)
                        textColor = ContextCompat.getColor(context, R.color.white1)
                    }
                    now.isAfter(startDateTime) && now.isBefore(endDateTime) -> {
                        cardBackgroundColor = ContextCompat.getColor(context, R.color.action_in_progress_green)
                    }
                    minutesUntilStart >= 0 && minutesUntilStart <= Z -> {
                        cardBackgroundColor = ContextCompat.getColor(context, R.color.action_about_to_start_light_red)
                    }
                    minutesUntilStart > Z && minutesUntilStart <= Y -> {
                        cardBackgroundColor = ContextCompat.getColor(context, R.color.action_imminent_yellow)
                    }
                    minutesUntilStart > Y && minutesUntilStart <= X -> {
                        cardBackgroundColor = ContextCompat.getColor(context, R.color.action_upcoming_yellow)
                    }
                    minutesUntilStart > X -> {
                        cardBackgroundColor = ContextCompat.getColor(context, R.color.action_default_light_dark_blue)
                    }
                    else -> {
                        cardBackgroundColor = ContextCompat.getColor(context, R.color.white1)
                    }
                }
            }
        }
        return Pair(cardBackgroundColor, textColor)
    }
    /**
     * Determines the background and text colors for an Exam card.
     *
     * @param exam The Exam object.
     * @param context The Context to resolve color resources.
     * @return A Pair where the first Int is the background color and the second Int is the text color.
     */
    fun determineExamCardColors(exam: Exam, context: Context): Pair<Int, Int> {
        val examStartDateTime = LocalDateTime.of(exam.examDate, exam.startTime)
        val examEndDateTime = LocalDateTime.of(exam.examDate, exam.endTime)
        val now = LocalDateTime.now()
        val hoursLeft = ChronoUnit.MINUTES.between(now, examStartDateTime).toFloat() / 60.0f

        val cardBackgroundColor: Int
        var textColor: Int = ContextCompat.getColor(context, R.color.black) // Default text color

        when {
            // 6. If current time is after Exam end datetime, then colour it in a darker shade of green. Text font is white.
            now.isAfter(examEndDateTime) -> {
                cardBackgroundColor = ContextCompat.getColor(context, R.color.exam_past_end_green)
                textColor = ContextCompat.getColor(context, R.color.white1)
            }
            // 5. If current time is more than Exam Start datetime, but before Exam end datetime, then colour it in a light shade of green. Text font is black.
            now.isAfter(examStartDateTime) && now.isBefore(examEndDateTime) -> {
                cardBackgroundColor = ContextCompat.getColor(context, R.color.exam_in_progress_green)
                textColor = ContextCompat.getColor(context, R.color.black)
            }
            // 4. If current time is less than 1 hours before Exam Start datetime, but before Exam Start datetime, then colour it a light shade of red. Text font is black.
            hoursLeft <= 1 && hoursLeft >= 0 -> { // hoursLeft > 0 ensures it's not already started/overdue
                cardBackgroundColor = ContextCompat.getColor(context, R.color.exam_about_to_start_light_red)
                textColor = ContextCompat.getColor(context, R.color.black)
            }
            // 3. If current time is less than 24 hours before Exam Start datetime, but more than 1 hour before Exam Start datetime, then colour it a darker shade of yellow. Text font is black.
            hoursLeft <= 24 && hoursLeft > 1 -> {
                cardBackgroundColor = ContextCompat.getColor(context, R.color.exam_imminent_yellow)
                textColor = ContextCompat.getColor(context, R.color.black)
            }
            // 2. If current time is less than 48 hours before Exam Start datetime, but more than 24 hours before Exam Start datetime, then colour it yellow. Text font is black.
            hoursLeft <= 48 && hoursLeft > 24 -> {
                cardBackgroundColor = ContextCompat.getColor(context, R.color.exam_upcoming_yellow)
                textColor = ContextCompat.getColor(context, R.color.black)
            }
            // 1. If current time is 48 hrs before Exam Start datetime, colour it light-gray. Text font is black.
            else -> { // This covers hoursLeft > 48
                cardBackgroundColor = ContextCompat.getColor(context, R.color.exam_default_light_dark_blue)
                textColor = ContextCompat.getColor(context, R.color.black)
            }
        }
        return Pair(cardBackgroundColor, textColor)
    }

    /**
     * Determines the background and text colors for a Notes card.
     *
     * @param noteItem The NotesItem object.
     * @param context The Context to resolve color resources.
     * @return A Pair where the first Int is the background color and the second Int is the text color.
     */
    fun determineNotesCardColors(noteItem: NotesItem, context: Context): Pair<Int, Int> {
        // TIMEZONE FIX: Use timezone-aware Instants for calculation.
        // This ensures the time difference is calculated correctly regardless of the user's current timezone.
        val lastModifiedInstant = Instant.ofEpochMilli(noteItem.lastModifiedMillis)
        val nowInstant = Instant.now()

        // To accurately calculate calendar units like "days" and "months", we convert the Instants
        // to LocalDate objects within the user's current timezone. This correctly handles
        // things like Daylight Saving Time.
        val systemZoneId = ZoneId.systemDefault()
        val lastModifiedDate = lastModifiedInstant.atZone(systemZoneId).toLocalDate()
        val nowDate = nowInstant.atZone(systemZoneId).toLocalDate()

        val daysSinceLastModified = ChronoUnit.DAYS.between(lastModifiedDate, nowDate)
        val monthsSinceLastModified = ChronoUnit.MONTHS.between(lastModifiedDate, nowDate)
        val isArchived = noteItem.archivedNote

        val cardBackgroundColor: Int
        var textColor: Int = ContextCompat.getColor(context, R.color.black) // Default text color

        when {
            isArchived -> {
                cardBackgroundColor = ContextCompat.getColor(context, R.color.notes_archived_dark_green)
                textColor = ContextCompat.getColor(context, R.color.white1)
            }
            else -> {
                when {
                    // for Notes whose lastModifiedTime > 4 months ago, background = light red, text = black
                    monthsSinceLastModified >= 4 -> {
                        cardBackgroundColor = ContextCompat.getColor(context, R.color.notes_old_red)
                    }
                    // for Notes whose lastModifiedTime > 3 months ago, background = darker shade of light yellow, text = black
                    monthsSinceLastModified >= 3 -> {
                        cardBackgroundColor =
                            ContextCompat.getColor(context, R.color.notes_medium_old_dark_yellow)
                    }
                    // for Notes whose lastModifiedTime > 2 months ago, background = light yellow, text = black
                    monthsSinceLastModified >= 2 -> {
                        cardBackgroundColor =
                            ContextCompat.getColor(context, R.color.notes_medium_old_light_yellow)
                    }
                    // for Notes whose lastModifiedTime > 1 months ago, background = darker shade of light blue, text = black
                    monthsSinceLastModified >= 1 -> {
                        cardBackgroundColor =
                            ContextCompat.getColor(
                                context,
                                R.color.notes_reasonably_recent_light_green
                            )
                    }
                    // for Notes whose lastModifiedTime > 15 days ago, background = darker shade of light green, text = black
                    else ->  {
                        cardBackgroundColor =
                            ContextCompat.getColor(context, R.color.notes_very_recent_dark_green)
                    }
                }
            }
        }
        return Pair(cardBackgroundColor, textColor)
    }

    /**
     * NEW: Determines the background and text colors for a ToDo card based on its status and time remaining.
     *
     * @param todoItem The TodoItem object.
     * @param context The Context to resolve color resources.
     * @return A Pair where the first Int is the background color and the second Int is the text color.
     */
    fun determineTodoCardColors(todoItem: TodoItem, context: Context): Pair<Int, Int> {
        val cardBackgroundColor: Int
        var textColor: Int = ContextCompat.getColor(context, R.color.black) // Default text color

        when {
            // a) Completed ToDo's are in dark green background, and white text
            todoItem.isCompleted -> {
                cardBackgroundColor = ContextCompat.getColor(context, R.color.todo_completed_dark_green)
                textColor = ContextCompat.getColor(context, R.color.white1)
            }
            // Logic for incomplete ToDo's based on time remaining
            else -> {
                val dueMillis = todoItem.dueDateTimeMillis
                if (dueMillis == null) {
                    // Default color for items with no due date
                    cardBackgroundColor = ContextCompat.getColor(context, R.color.todo_new_light_green)
                } else {
                    val secondsLeft = (dueMillis - System.currentTimeMillis()) / 1000

                    when {
                        // b) Overdue: Incomplete and past due date. Dark red background, white text.
                        secondsLeft < 0 -> {
                            cardBackgroundColor = ContextCompat.getColor(context, R.color.todo_very_old_dark_red)
                            textColor = ContextCompat.getColor(context, R.color.white1)
                        }
                        // c) Due within 3 days: Light red background, black text.
                        secondsLeft <= 259_200 -> { // 3 * 24 * 60 * 60
                            cardBackgroundColor = ContextCompat.getColor(context, R.color.todo_old_light_red)
                        }
                        // d) Due within 7 days: Dark yellow background, black text.
                        secondsLeft <= 604_800 -> { // 7 * 24 * 60 * 60
                            cardBackgroundColor = ContextCompat.getColor(context, R.color.todo_medium_old_dark_yellow)
                        }
                        // e) Due within 15 days: Light yellow background, black text.
                        secondsLeft <= 1_296_000 -> { // 15 * 24 * 60 * 60
                            cardBackgroundColor = ContextCompat.getColor(context, R.color.todo_medium_old_light_yellow)
                        }
                        // f) Due in more than 15 days: Light green background, black text.
                        else -> {
                            cardBackgroundColor = ContextCompat.getColor(context, R.color.todo_new_light_green)
                        }
                    }
                }
            }
        }
        return Pair(cardBackgroundColor, textColor)
    }

    /**
     * Determines the background and text colors for an Engagement card instance.
     *
     * @param engagement The Engagement instance with its nextOccurrenceDate populated.
     * @param context The Context to resolve color resources.
     * @return A Pair where the first Int is the background color and the second Int is the text color.
     */
    fun determineEngagementCardColors(engagement: Engagement, context: Context): Pair<Int, Int> {
        val now = LocalDateTime.now()
        val startDateTime = engagement.startDateTime ?: return Pair(
            ContextCompat.getColor(context, R.color.light_gray), // Default color if no start time
            ContextCompat.getColor(context, R.color.black)
        )
        val endDateTime = startDateTime.plusMinutes(engagement.durationMinutes.toLong())

        val minutesUntilStart = ChronoUnit.MINUTES.between(now, startDateTime)

        // Notification thresholds from the engagement object
        val notif1 = engagement.notification1Minutes
        val notif2 = engagement.notification2Minutes
        val notif3 = engagement.notification3Minutes
        val X = maxOf(notif1, notif2, notif3)
        val Y = listOf(notif1, notif2, notif3).sorted()[1]
        val Z = minOf(notif1, notif2, notif3)

        val cardBackgroundColor: Int
        val textColor: Int = ContextCompat.getColor(context, R.color.black) // Default text color is black

        when {
            // In Progress: Current time is between start and end
            now.isAfter(startDateTime) && now.isBefore(endDateTime) -> {
                cardBackgroundColor = ContextCompat.getColor(context, R.color.engagement_in_progress_green)
            }
            // Imminent: Time until start is less than or equal to the shortest notification time
            minutesUntilStart >= 0 && minutesUntilStart <= Z -> {
                cardBackgroundColor = ContextCompat.getColor(context, R.color.engagement_nearing_submission_red)
            }
            // Upcoming and near: Time is between notification 2 and 3
            minutesUntilStart > Z && minutesUntilStart <= Y -> {
                cardBackgroundColor = ContextCompat.getColor(context, R.color.engagement_imminent_yellow)
            }
            // Upcoming: Time is between notification 1 and 2
            minutesUntilStart > Y && minutesUntilStart <= X -> {
                cardBackgroundColor = ContextCompat.getColor(context, R.color.engagement_upcoming_yellow)
            }
            // Default: For all other future engagements
            else -> {
                cardBackgroundColor = ContextCompat.getColor(context, R.color.engagement_default_light_dark_blue)
            }
        }
        return Pair(cardBackgroundColor, textColor)
    }

    /**
     * Determines the background and text colors for a SyncedCalendarEvent card.
     *
     * @param event The SyncedCalendarEvent object.
     * @param context The Context to resolve color resources.
     * @return A Pair where the first Int is the background color and the second Int is the text color.
     */
    fun determineCalendarCardColors(event: SyncedCalendarEvent, context: Context): Pair<Int, Int> {
        val systemZoneId = ZoneId.systemDefault()
        val now = LocalDateTime.now(systemZoneId)
        val startDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(event.startDateTimeMillis), systemZoneId)
        val endDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(event.endDateTimeMillis), systemZoneId)

        val minutesUntilStart = ChronoUnit.MINUTES.between(now, startDateTime)

        val cardBackgroundColor: Int
        val textColor: Int = ContextCompat.getColor(context, R.color.black) // Default

        when {
            // In Progress: Current time is between start and end
            now.isAfter(startDateTime) && now.isBefore(endDateTime) -> {
                cardBackgroundColor = ContextCompat.getColor(context, R.color.engagement_in_progress_green)
            }
            // About to start: Within the next 60 minutes
            minutesUntilStart in 0..60 -> {
                cardBackgroundColor = ContextCompat.getColor(context, R.color.engagement_nearing_submission_red)
            }
            // Upcoming Today: Starts later today but more than an hour away
            startDateTime.toLocalDate().isEqual(now.toLocalDate()) && minutesUntilStart > 60 -> {
                cardBackgroundColor = ContextCompat.getColor(context, R.color.engagement_upcoming_yellow)
            }
            // Past event: Should not typically be displayed, but handle it
            now.isAfter(endDateTime) -> {
                cardBackgroundColor = ContextCompat.getColor(context, R.color.light_gray)
            }
            // Default: For all other future events
            else -> {
                cardBackgroundColor = ContextCompat.getColor(context, R.color.engagement_default_light_dark_blue)
            }
        }
        return Pair(cardBackgroundColor, textColor)
    }

    /**
     * NEW: Determines the background and text colors for a SyncedGoogleTask card.
     *
     * @param task The SyncedGoogleTask object.
     * @param context The Context to resolve color resources.
     * @return A Pair where the first Int is the background color and the second Int is the text color.
     */
    fun determineGoogleTaskCardColors(task: SyncedGoogleTask, context: Context): Pair<Int, Int> {
        val cardBackgroundColor: Int
        var textColor: Int = ContextCompat.getColor(context, R.color.black) // Default text color

        val dueMillis = task.dueDateTimeMillis
        if (dueMillis == null) {
            // Default color for tasks with no due date
            cardBackgroundColor = ContextCompat.getColor(context, R.color.google_task_blue)
            textColor = ContextCompat.getColor(context, R.color.white1)
        } else {
            val hoursLeft = (dueMillis - System.currentTimeMillis()) / 3_600_000f

            when {
                // Overdue: Past due date
                hoursLeft < 0 -> {
                    cardBackgroundColor = ContextCompat.getColor(context, R.color.todo_very_old_dark_red)
                    textColor = ContextCompat.getColor(context, R.color.white1)
                }
                // Due within 24 hours
                hoursLeft <= 24 -> {
                    cardBackgroundColor = ContextCompat.getColor(context, R.color.todo_old_light_red)
                }
                // Due within 3 days
                hoursLeft <= 72 -> {
                    cardBackgroundColor = ContextCompat.getColor(context, R.color.todo_medium_old_dark_yellow)
                }
                // Default for all other future tasks
                else -> {
                    cardBackgroundColor = ContextCompat.getColor(context, R.color.google_task_blue)
                    textColor = ContextCompat.getColor(context, R.color.white1)
                }
            }
        }
        return Pair(cardBackgroundColor, textColor)
    }
}
