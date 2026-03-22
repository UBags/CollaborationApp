package com.costheta.cortexa

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import com.costheta.cortexa.data.db.dao.TimetableDao // Import TimetableDao
import com.costheta.cortexa.data.models.ClassSession
import com.costheta.cortexa.data.models.Exam
import com.costheta.cortexa.data.models.Timetable
import com.costheta.cortexa.data.models.Assignment
import com.costheta.cortexa.data.models.Engagement
import com.costheta.cortexa.data.models.TodoItem // Import TodoItem
import com.costheta.cortexa.data.models.Action // Import Action
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.random.Random

/**
 * Helper class to schedule and cancel notifications using AlarmManager.
 */
class NotificationHelper(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    // Changed SharedPreferences file name to read from the correct preferences file.
    private val sharedPreferences =
        context.getSharedPreferences("user_preferences", Context.MODE_PRIVATE)

    // --- Notification Channel Constants ---
    companion object {
        const val DEFAULT_CHANNEL_ID = "default_channel"
        const val URGENT_CHANNEL_ID = "urgent_channel"
        const val SILENT_CHANNEL_ID = "silent_channel"

        const val DEFAULT_CHANNEL_NAME = "Default Notifications"
        const val URGENT_CHANNEL_NAME = "Urgent Notifications"
        const val SILENT_CHANNEL_NAME = "Silent Notifications"
    }

    // Default notification offsets for ClassSessions (can be overridden by timetable/session specific settings)
    val DEFAULT_NOTIFICATION_OFFSET_MINUTES_1 = 30
    val DEFAULT_NOTIFICATION_OFFSET_MINUTES_2 = 15

    // Base IDs for different types of notifications to prevent clashes across event types
    // These are now private as they are used internally by generateNotificationId
    private val TIMETABLE_BASE_ID = 1000
    private val ASSIGNMENT_BASE_ID = 2000
    private val EXAM_BASE_ID = 3000
    private val ENGAGEMENT_BASE_ID = 4000
    private val TODO_BASE_ID = 5000 // Base ID for To-Do notifications
    private val ACTION_BASE_ID = 6000 // Base ID for Action notifications

    /**
     * Helper function to generate a unique Int ID from a Long event ID and other factors.
     * This aims to minimize collisions when converting a Long ID to an Int for notifications.
     *
     * @param eventId The Long ID of the event (e.g., sessionId, assignmentId). Can be null if not yet assigned.
     * @param baseId A base integer ID specific to the event type (e.g., TIMETABLE_BASE_ID).
     * @param offset An additional offset to differentiate notifications for the same event (e.g., different reminder times).
     * @return A unique Int notification ID.
     */
    private fun generateNotificationId(
        eventId: Long?,
        baseId: Int,
        offset: Int,
        date: LocalDate? = null
    ): Int {
        val idHash = eventId?.let {
            (it and 0xFFFFFFFFL).toInt() xor (it ushr 32).toInt()
        } ?: Random.nextInt(100000)

        // Incorporate the date's epoch day into the hash for uniqueness per day.
        val dateHash = date?.toEpochDay()?.toInt() ?: 0

        return (idHash xor baseId xor offset xor dateHash).coerceAtLeast(1)
    }

    /**
     * Creates all necessary notification channels for the app.
     * This should be called once on app startup from the Application class.
     */
    fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val defaultChannel = NotificationChannel(
                DEFAULT_CHANNEL_ID,
                DEFAULT_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Standard reminders"
                enableLights(true)
                lightColor = Color.BLUE
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null)
            }

            val urgentChannel = NotificationChannel(
                URGENT_CHANNEL_ID,
                URGENT_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High priority reminders"
                enableLights(true)
                lightColor = Color.RED
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), null)
                enableVibration(true)
                vibrationPattern = longArrayOf(100, 200, 300, 400, 500, 400, 300, 200, 400)
            }

            val silentChannel = NotificationChannel(
                SILENT_CHANNEL_ID,
                SILENT_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Silent reminders that do not make noise or vibrate"
                setSound(null, null)
                enableLights(false)
                enableVibration(false)
            }

            notificationManager.createNotificationChannel(defaultChannel)
            notificationManager.createNotificationChannel(urgentChannel)
            notificationManager.createNotificationChannel(silentChannel)
            Log.d("NotificationHelper", "All notification channels created.")
        }
    }

    /**
     * Generic function to schedule a single notification.
     *
     * @param notificationId A unique ID for this notification.
     * @param title The title of the notification.
     * @param message The message body of the notification.
     * @param alarmTimeMillis The absolute time in millis when the notification should fire.
     */
    private fun scheduleNotification(
        notificationId: Int,
        title: String,
        message: String,
        alarmTimeMillis: Long, // TIMEZONE CHANGE: Accept absolute time
        channelId: String = DEFAULT_CHANNEL_ID
    ) {
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = NotificationReceiver.NOTIFICATION_ACTION
            putExtra(NotificationReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(NotificationReceiver.EXTRA_TITLE, title)
            putExtra(NotificationReceiver.EXTRA_MESSAGE, message)
            putExtra(NotificationReceiver.EXTRA_CHANNEL_ID, channelId) // Pass the channel ID
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    alarmTimeMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    alarmTimeMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            Log.e("NotificationHelper", "SecurityException on scheduling exact alarm.", e)
        }
    }

    /**
     * Schedules all relevant notifications for a given Timetable.
     * This function will fetch all ClassSessions within the timetable and schedule their notifications.
     *
     * @param timetable The Timetable object for which to schedule notifications.
     * @param timetableDao The TimetableDao instance to fetch ClassSessions.
     */
    suspend fun scheduleNotificationsForTimetable(
        timetable: Timetable,
        timetableDao: TimetableDao
    ) {
        Log.d("NotificationHelper", "Scheduling notifications for timetable: ${timetable.name}")
        withContext(Dispatchers.IO) {
            timetable.id?.let { timetableId ->
                val sessions = timetableDao.getClassSessionsForTimetable(timetableId)
                sessions.forEach { session ->
                    // Pass the parent timetable to the session scheduler
                    scheduleAllNotificationsForClassSession(session, timetable)
                }
            }
        }
    }

    /**
     * Cancels all notifications for a given Timetable ID.
     * This will iterate through all ClassSessions within the timetable and cancel their notifications.
     * Also cancels the timetable archival notification.
     *
     * @param timetableId The ID of the Timetable to cancel notifications for.
     * @param timetableDao The TimetableDao instance to fetch ClassSessions.
     */
    suspend fun cancelAllNotificationsForTimetable(timetableId: Long, timetableDao: TimetableDao) {
        Log.d("NotificationHelper", "Cancelling all notifications for timetable ID: $timetableId")
        withContext(Dispatchers.IO) {
            // 1. Cancel notifications for all associated ClassSessions
            val sessions = timetableDao.getClassSessionsForTimetable(timetableId)
            sessions.forEach { session ->
                // Re-generate the exact IDs used for scheduling using the session's specific offsets
                val offset1 = session.notificationOffsetMinutes1 ?: sharedPreferences.getInt(
                    "defaultNotif1Offset",
                    DEFAULT_NOTIFICATION_OFFSET_MINUTES_1
                )
                val offset2 = session.notificationOffsetMinutes2 ?: sharedPreferences.getInt(
                    "defaultNotif2Offset",
                    DEFAULT_NOTIFICATION_OFFSET_MINUTES_2
                )

                cancelNotificationsForClassSession(session.sessionId!!, offset1)
                cancelNotificationsForClassSession(session.sessionId, offset2)
            }

            // 2. Cancel the timetable archival notification
            val archivalNotificationId = generateNotificationId(
                timetableId,
                TIMETABLE_BASE_ID,
                100
            ) // Match ID used in sendTimetableArchivedNotification
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                action = NotificationReceiver.NOTIFICATION_ACTION
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                archivalNotificationId,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent?.let {
                alarmManager.cancel(it)
                it.cancel()
                Log.d(
                    "NotificationHelper",
                    "Cancelled timetable archival notification with ID: $archivalNotificationId (Timetable ID: $timetableId)"
                )
            }
        }
        Log.d("NotificationHelper", "Completed cancellation for timetable ID: $timetableId")
    }

    /**
     * Schedules all relevant notifications for a given ClassSession.
     * This is primarily for regular timetable sessions.
     *
     * @param session The ClassSession object for which to schedule notifications.
     */
    /**
     * Schedules all relevant notifications for a given ClassSession using absolute UTC time.
     */
    fun scheduleAllNotificationsForClassSession(session: ClassSession, timetable: Timetable) {
        if (session.isSilenced || timetable.isMasterSilenced) return

        val offset1 =
            session.notificationOffsetMinutes1 ?: timetable.defaultNotificationOffsetMinutes1
        val offset2 =
            session.notificationOffsetMinutes2 ?: timetable.defaultNotificationOffsetMinutes2
        val sessionStartTimeMillis = session.startDateTimeMillis
//        val channelId = timetable.notificationChannelId // Use the channel from the parent timetable
        val channelId = DEFAULT_CHANNEL_ID // Use the channel from the parent timetable

        // Schedule notification 1
        val notificationTimeMillis1 = sessionStartTimeMillis - (offset1 * 60 * 1000L)
        if (notificationTimeMillis1 > System.currentTimeMillis()) {
            val notificationId1 =
                generateNotificationId(session.sessionId, TIMETABLE_BASE_ID, offset1)
            val title1 = context.getString(R.string.notification_title_upcoming_class)
            val message1 = context.getString(
                R.string.notification_message_upcoming_class,
                session.courseName,
                session.startTime.format(timeFormatter),
                session.location ?: ""
            )
            scheduleNotification(
                notificationId1,
                title1,
                message1,
                notificationTimeMillis1,
                channelId
            )
        }

        // Schedule notification 2
        val notificationTimeMillis2 = sessionStartTimeMillis - (offset2 * 60 * 1000L)
        if (notificationTimeMillis2 > System.currentTimeMillis()) {
            val notificationId2 =
                generateNotificationId(session.sessionId, TIMETABLE_BASE_ID, offset2)
            val title2 = context.getString(R.string.notification_title_upcoming_class)
            val message2 = context.getString(
                R.string.notification_message_upcoming_class,
                session.courseName,
                session.startTime.format(timeFormatter),
                session.location ?: ""
            )
            scheduleNotification(
                notificationId2,
                title2,
                message2,
                notificationTimeMillis2,
                channelId
            )
        }
    }

    // Schedules notifications for a SINGLE rescheduled instance of a class session.
    fun scheduleSingleInstanceNotifications(
        session: ClassSession,
        timetable: Timetable,
        date: LocalDate,
        startTime: LocalTime
    ) {
        if (session.isSilenced || timetable.isMasterSilenced) return

        val offset1 =
            session.notificationOffsetMinutes1 ?: timetable.defaultNotificationOffsetMinutes1
        val offset2 =
            session.notificationOffsetMinutes2 ?: timetable.defaultNotificationOffsetMinutes2
        val channelId = DEFAULT_CHANNEL_ID

        val instanceDateTime = LocalDateTime.of(date, startTime)
        val instanceMillis =
            instanceDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        // Schedule notification 1
        val notificationTimeMillis1 = instanceMillis - (offset1 * 60 * 1000L)
        if (notificationTimeMillis1 > System.currentTimeMillis()) {
            val notificationId1 =
                generateNotificationId(session.sessionId, TIMETABLE_BASE_ID, offset1, date)
            val title1 = context.getString(R.string.notification_title_upcoming_class)
            val message1 = context.getString(
                R.string.notification_message_upcoming_class,
                session.courseName,
                startTime.format(timeFormatter),
                session.location ?: ""
            )
            scheduleNotification(
                notificationId1,
                title1,
                message1,
                notificationTimeMillis1,
                channelId
            )
        }

        // Schedule notification 2
        val notificationTimeMillis2 = instanceMillis - (offset2 * 60 * 1000L)
        if (notificationTimeMillis2 > System.currentTimeMillis()) {
            val notificationId2 =
                generateNotificationId(session.sessionId, TIMETABLE_BASE_ID, offset2, date)
            val title2 = context.getString(R.string.notification_title_upcoming_class)
            val message2 = context.getString(
                R.string.notification_message_upcoming_class,
                session.courseName,
                startTime.format(timeFormatter),
                session.location ?: ""
            )
            scheduleNotification(
                notificationId2,
                title2,
                message2,
                notificationTimeMillis2,
                channelId
            )
        }
    }

    // Cancels notifications for a SINGLE instance of a class session on a specific date.
    fun cancelSingleInstanceNotifications(
        session: ClassSession,
        timetable: Timetable,
        date: LocalDate
    ) {
        val offset1 =
            session.notificationOffsetMinutes1 ?: timetable.defaultNotificationOffsetMinutes1
        val offset2 =
            session.notificationOffsetMinutes2 ?: timetable.defaultNotificationOffsetMinutes2

        // Cancel notification 1
        val notificationId1 =
            generateNotificationId(session.sessionId, TIMETABLE_BASE_ID, offset1, date)
        cancelNotificationById(notificationId1)

        // Cancel notification 2
        val notificationId2 =
            generateNotificationId(session.sessionId, TIMETABLE_BASE_ID, offset2, date)
        cancelNotificationById(notificationId2)
    }

    // Private helper to cancel any notification by its exact ID.
    private fun cancelNotificationById(notificationId: Int) {
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = NotificationReceiver.NOTIFICATION_ACTION
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
            Log.d("NotificationHelper", "Cancelled notification with exact ID: $notificationId")
        }
    }

    /**
     * Cancels a specific notification related to ClassSessions (Timetable events).
     * This function is specifically for notifications that use TIMETABLE_BASE_ID.
     *
     * @param sessionId The Long ID of the ClassSession.
     * @param offsetMinutes The actual minutes offset used when scheduling the notification.
     * This is crucial for matching the exact notification ID.
     */
    fun cancelNotificationsForClassSession(sessionId: Long, offsetMinutes: Int) {
        val notificationId = generateNotificationId(sessionId, TIMETABLE_BASE_ID, offsetMinutes)
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = NotificationReceiver.NOTIFICATION_ACTION
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
            Log.d(
                "NotificationHelper",
                "Cancelled ClassSession notification with ID: $notificationId (Session ID: $sessionId, Minutes Offset: $offsetMinutes)"
            )
        }
    }

    /**
     * Schedules all relevant notifications for a given Engagement.
     *
     * @param engagement The Engagement object for which to schedule notifications.
     */
    fun scheduleEngagementNotifications(engagement: Engagement) {
        if (engagement.isSilenced) {
            Log.d(
                "NotificationHelper",
                "Notifications silenced for engagement: ${engagement.engagementName}. Not scheduling."
            )
            return
        }

        // Use the notification times directly from the Engagement object
        val notificationOffsets = listOf(
            engagement.notification1Minutes,
            engagement.notification2Minutes,
            engagement.notification3Minutes
        )

        val today = LocalDate.now()
        val endDate = engagement.validityEndDate

        var currentCheckDate = today
        while (!currentCheckDate.isAfter(endDate)) {
            val isScheduledDay = when (engagement.daySelectionType) {
                "SPECIFIC_DAYS" -> engagement.selectedDaysOfWeek?.split(",")
                    ?.map { DayOfWeek.valueOf(it) }?.contains(currentCheckDate.dayOfWeek) ?: false

                "RECURRENCE" -> {
                    engagement.recurrenceIntervalDays?.let { interval ->
                        val daysSinceStart =
                            ChronoUnit.DAYS.between(engagement.validityStartDate, currentCheckDate)
                        daysSinceStart >= 0 && daysSinceStart % interval == 0L
                    } ?: false
                }

                "DAILY" -> true
                else -> false
            }

            if (isScheduledDay) {
                val engagementDateTime = LocalDateTime.of(currentCheckDate, engagement.startTime)

                notificationOffsets.forEachIndexed { index, offsetMinutes ->
                    // Only schedule if offset is non-negative
                    if (offsetMinutes >= 0) {
                        val notificationDateTime =
                            engagementDateTime.minusMinutes(offsetMinutes.toLong())
                        if (notificationDateTime.isAfter(LocalDateTime.now())) {
                            val notificationId = generateNotificationId(
                                engagement.engagementId,
                                ENGAGEMENT_BASE_ID,
                                offsetMinutes
                            )
                            val title =
                                context.getString(R.string.notification_title_upcoming_engagement)
                            val message = context.getString(
                                R.string.notification_message_upcoming_engagement,
                                engagement.engagementName,
                                engagement.startTime.format(timeFormatter)
                            )
                            scheduleNotification(
                                notificationId,
                                title,
                                message,
                                notificationDateTime.atZone(ZoneId.systemDefault()).toInstant()
                                    .toEpochMilli()
                            )
                            Log.d(
                                "NotificationHelper",
                                "Scheduled engagement (Notif ${index + 1}) for ${engagement.engagementName} on $currentCheckDate at ${
                                    notificationDateTime.format(timeFormatter)
                                } (Offset: $offsetMinutes min)"
                            )
                        }
                    } else {
                        Log.d(
                            "NotificationHelper",
                            "Skipping engagement notification (Notif ${index + 1}) for ${engagement.engagementName} due to negative offset: $offsetMinutes"
                        )
                    }
                }
            }
            currentCheckDate = currentCheckDate.plusDays(1)
        }
    }

    /**
     * Schedules all relevant notifications for a given Assignment.
     *
     * @param assignment The Assignment object for which to schedule notifications.
     */
    fun scheduleAssignmentNotifications(assignment: Assignment) {
        val notifPrefs = getAssignmentNotificationPreferences()
        // TIMEZONE CHANGE: Use the reliable UTC timestamp for all calculations
        val assignmentDueTimeMillis = assignment.dueDateTimeMillis

        notifPrefs.forEach { (offsetMinutes, isEnabled) ->
            if (isEnabled) {
                // TIMEZONE CHANGE: Calculate notification time directly from UTC milliseconds
                val notificationTimeMillis = assignmentDueTimeMillis - (offsetMinutes * 60 * 1000L)

                if (notificationTimeMillis > System.currentTimeMillis()) {
                    // Convert back to LocalDateTime in the system's timezone for the scheduleNotification method and logging
                    // val notificationDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(notificationTimeMillis), ZoneId.systemDefault())

                    val notificationId = generateNotificationId(
                        assignment.assignmentId,
                        ASSIGNMENT_BASE_ID,
                        offsetMinutes
                    )
                    val title = context.getString(R.string.notification_title_upcoming_assignment)
                    val message = context.getString(
                        R.string.notification_message_upcoming_assignment,
                        assignment.assignmentName,
                        assignment.courseName,
                        assignment.dueDate.format(dateFormatter),
                        assignment.submissionTime.format(timeFormatter)
                    )
                    scheduleNotification(notificationId, title, message, notificationTimeMillis)
                    // Log.d("NotificationHelper", "Scheduled assignment for ${assignment.assignmentName} at ${notificationDateTime.format(timeFormatter)} (Offset: $offsetMinutes min)")
                }
            }
        }
    }

    /**
     * TIMEZONE CHANGE: Schedules all relevant notifications for a given Exam using UTC timestamps.
     *
     * @param exam The Exam object for which to schedule notifications.
     */
    fun scheduleExamNotifications(exam: Exam) {
        val notifPrefs = getExamNotificationPreferences()
        val examStartTimeMillis = exam.startDateTimeMillis // Use the UTC start time

        notifPrefs.forEach { (offsetMinutes, isEnabled) ->
            if (isEnabled) {
                val notificationTimeMillis = examStartTimeMillis - (offsetMinutes * 60 * 1000L)

                if (notificationTimeMillis > System.currentTimeMillis()) {
                    // val notificationDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(notificationTimeMillis), ZoneId.systemDefault())
                    val notificationId =
                        generateNotificationId(exam.examId, EXAM_BASE_ID, offsetMinutes)
                    val title = context.getString(R.string.notification_title_upcoming_exam)
                    val message = context.getString(
                        R.string.notification_message_upcoming_exam,
                        exam.courseName,
                        exam.examDate.format(dateFormatter),
                        exam.startTime.format(timeFormatter)
                    )
                    scheduleNotification(notificationId, title, message, notificationTimeMillis)
                }
            }
        }
    }

    /**
     * Schedules all relevant notifications for a given To-Do item.
     *
     * @param todoItem The TodoItem object for which to schedule notifications.
     */
    fun scheduleTodoNotifications(todoItem: TodoItem) {
        // Do not schedule notifications for completed To-Do items or if silenced
        if (todoItem.isCompleted) {
            Log.d(
                "NotificationHelper",
                "To-Do '${todoItem.title}' is completed. Not scheduling notifications."
            )
            return
        }
        if (todoItem.silenceNotifications) { // Check for silenceNotifications
            Log.d(
                "NotificationHelper",
                "Notifications silenced for To-Do: '${todoItem.title}'. Not scheduling."
            )
            return
        }

        // Only schedule if a due date and the UTC timestamp are set
        val todoDueTimeMillis = todoItem.dueDateTimeMillis
        if (todoItem.dueDate == null || todoDueTimeMillis == null) {
            Log.d(
                "NotificationHelper",
                "To-Do '${todoItem.title}' has no due date/time. Not scheduling notifications."
            )
            return
        }

        val notifPrefs = getTodoNotificationPreferences()

        notifPrefs.forEach { (offsetMinutes, isEnabled) ->
            if (isEnabled) {
                // TIMEZONE FIX: Calculate notification time directly from the reliable UTC timestamp
                val notificationTimeMillis = todoDueTimeMillis - (offsetMinutes * 60 * 1000L)

                if (notificationTimeMillis > System.currentTimeMillis()) {
                    // Convert back to LocalDateTime in the system's timezone ONLY for the scheduleNotification method signature and logging
                    // val notificationDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(notificationTimeMillis), ZoneId.systemDefault())

                    val notificationId =
                        generateNotificationId(todoItem.todoId, TODO_BASE_ID, offsetMinutes)
                    val title = context.getString(R.string.notification_title_upcoming_todo)
                    val message = context.getString(
                        R.string.notification_message_upcoming_todo,
                        todoItem.title,
                        todoItem.description
                            ?: context.getString(R.string.no_description_provided_short),
                        todoItem.dueDate.format(dateFormatter),
                        (todoItem.dueTime ?: LocalTime.MIDNIGHT).format(timeFormatter)
                    )
                    scheduleNotification(notificationId, title, message, notificationTimeMillis)
                    // Log.d("NotificationHelper", "Scheduled To-Do for ${todoItem.title} at ${notificationDateTime.format(timeFormatter)} (Offset: $offsetMinutes min)")
                }
            }
        }
    }

    /**
     * Schedules notifications for a given Action.
     *
     * @param action The Action object for which to schedule notifications.
     */
    fun scheduleActionNotifications(action: Action) {
        if (action.silenceNotifications) {
            Log.d(
                "NotificationHelper",
                "Notifications silenced for action: ${action.actionName}. Not scheduling."
            )
            cancelActionNotifications(action.actionId!!)
            return
        }

        val actionStartTimeMillis = action.startDateTimeMillis // Use the UTC start time

        val notificationOffsets = listOfNotNull(
            action.notificationMinutes1,
            action.notificationMinutes2,
            action.notificationMinutes3
        )

        notificationOffsets.forEach { offsetMinutes ->
            if (offsetMinutes >= 0) {
                val notificationTimeMillis = actionStartTimeMillis - (offsetMinutes * 60 * 1000L)

                if (notificationTimeMillis > System.currentTimeMillis()) {
                    // val notificationDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(notificationTimeMillis), ZoneId.systemDefault())
                    val notificationId =
                        generateNotificationId(action.actionId, ACTION_BASE_ID, offsetMinutes)
                    val title = context.getString(R.string.notification_title_upcoming_action)
                    val message = context.getString(
                        R.string.notification_message_upcoming_action,
                        action.actionName,
                        action.description ?: "",
                        action.startDate.format(dateFormatter),
                        action.startTime.format(timeFormatter)
                    )
                    scheduleNotification(notificationId, title, message, notificationTimeMillis)
                    // Log.d("NotificationHelper", "Scheduled action for ${action.actionName} at ${notificationDateTime.format(timeFormatter)} (Offset: $offsetMinutes min)")
                }
            }
        }
    }

    /**
     * Cancels all notifications for a given Engagement.
     * This will iterate through all possible notification offsets and cancel them.
     * @param engagementId The ID of the Engagement to cancel notifications for.
     */
    fun cancelEngagementNotifications(engagementId: Long) {
        // Include the default notification offsets and potentially any custom ones
        // For a robust cancellation, ideally, we would fetch the engagement's specific
        // notification1Minutes, notification2Minutes, notification3Minutes from the DB here.
        // However, since this method only takes engagementId, we use a comprehensive list.
        val engagementNotifOffsets = listOf(
            0, 10, 15, 30, 60, // Default and common short offsets
            1 * 60, 3 * 60, 6 * 60, 12 * 60, 24 * 60, 2 * 24 * 60, 3 * 24 * 60, 5 * 24 * 60
        ).distinct() // Ensure no duplicate offsets

        engagementNotifOffsets.forEach { offset ->
            val notificationId = generateNotificationId(engagementId, ENGAGEMENT_BASE_ID, offset)
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                action = NotificationReceiver.NOTIFICATION_ACTION
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent?.let {
                alarmManager.cancel(it)
                it.cancel()
                Log.d(
                    "NotificationHelper",
                    "Cancelled engagement notification with ID: $notificationId (Offset: $offset)"
                )
            }
        }
    }

    /**
     * Cancels all notifications for a given Assignment.
     * @param assignmentId The ID of the Assignment to cancel notifications for.
     */
    fun cancelAssignmentNotifications(assignmentId: Long) {
        val assignNotifOffsets = listOf(
            0, 1 * 60, 3 * 60, 6 * 60, 12 * 60, 24 * 60, 2 * 24 * 60, 3 * 24 * 60
        )
        assignNotifOffsets.forEach { offset ->
            val notificationId = generateNotificationId(assignmentId, ASSIGNMENT_BASE_ID, offset)
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                action = NotificationReceiver.NOTIFICATION_ACTION
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent?.let {
                alarmManager.cancel(it)
                it.cancel()
                Log.d(
                    "NotificationHelper",
                    "Cancelled assignment notification with ID: $notificationId (Offset: $offset)"
                )
            }
        }
    }

    /**
     * Cancels all notifications for a given Exam.
     * @param examId The ID of the Exam to cancel notifications for.
     */
    fun cancelExamNotifications(examId: Long) {
        val examNotifOffsets = listOf(
            0, 1 * 60, 3 * 60, 6 * 60, 12 * 60, 24 * 60, 3 * 24 * 60, 5 * 24 * 60
        )
        examNotifOffsets.forEach { offset ->
            val notificationId = generateNotificationId(examId, EXAM_BASE_ID, offset)
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                action = NotificationReceiver.NOTIFICATION_ACTION
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent?.let {
                alarmManager.cancel(it)
                it.cancel()
                Log.d(
                    "NotificationHelper",
                    "Cancelled exam notification with ID: $notificationId (Offset: $offset)"
                )
            }
        }
    }

    /**
     * Cancels all notifications for a given To-Do item.
     * @param todoId The ID of the To-Do item to cancel notifications for.
     */
    fun cancelTodoNotifications(todoId: Long) {
        val todoNotifOffsets = listOf(
            0, // For immediate cancellation if needed, though not used for scheduling
            1 * 24 * 60, // 1 Day Before
            3 * 24 * 60, // 3 Days Before
            5 * 24 * 60, // 5 Days Before
            7 * 24 * 60, // 7 Days Before
            10 * 24 * 60, // 10 Days Before
            15 * 24 * 60, // 15 Days Before
            20 * 24 * 60, // 20 Days Before
            25 * 24 * 60, // 25 Days Before
            30 * 24 * 60  // 30 Days Before
        )
        todoNotifOffsets.forEach { offset ->
            val notificationId = generateNotificationId(todoId, TODO_BASE_ID, offset)
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                action = NotificationReceiver.NOTIFICATION_ACTION
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent?.let {
                alarmManager.cancel(it)
                it.cancel()
                Log.d(
                    "NotificationHelper",
                    "Cancelled To-Do notification with ID: $notificationId (Offset: $offset)"
                )
            }
        }
    }

    /**
     * Cancels all notifications for a given Action.
     * This will iterate through the two possible notification offsets (notificationMinutes1 and notificationMinutes2)
     * and cancel them if they were set.
     * @param actionId The ID of the Action to cancel notifications for.
     */
    fun cancelActionNotifications(actionId: Long) {
        // We only need to cancel the two specific notification IDs that could have been scheduled
        // for an Action. Since we don't know the exact offsets used at scheduling time here,
        // we'll assume a reasonable range or retrieve them from the database if necessary.
        // For simplicity, we'll cancel for a few common offsets around the default,
        // or ideally, retrieve the actual stored notificationMinutes1/2 from the DB.
        // For demonstration, let's use some common offsets that might be set.
        val possibleActionOffsets = listOf(
            0, 1, 5, 10, 15, 30, 45, // Minutes
            1 * 60, 2 * 60, 3 * 60, 6 * 60, 12 * 60, 24 * 60 // Hours/Days
        )

        // If you have access to the Action object here, use its specific notificationMinutes1 and 2
        // For example:
        // val action = actionDao.getActionById(actionId) // Requires actionDao instance here
        // action?.notificationMinutes1?.let { offset -> cancelSpecificActionNotification(actionId, offset) }
        // action?.notificationMinutes2?.let { offset -> cancelSpecificActionNotification(actionId, offset) }

        // As a fallback without action object:
        possibleActionOffsets.forEach { offset ->
            val notificationId = generateNotificationId(actionId, ACTION_BASE_ID, offset)
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                action = NotificationReceiver.NOTIFICATION_ACTION
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent?.let {
                alarmManager.cancel(it)
                it.cancel()
                Log.d(
                    "NotificationHelper",
                    "Cancelled action notification with ID: $notificationId (Offset: $offset)"
                )
            }
        }
    }


    /**
     * Sends a notification when a timetable has been automatically archived.
     * @param timetable The archived Timetable object.
     */
    fun sendTimetableArchivedNotification(timetable: Timetable) {
        val notificationId = generateNotificationId(timetable.id, TIMETABLE_BASE_ID, 100)
        val title = context.getString(R.string.notification_timetable_archived_title)
        val message =
            context.getString(R.string.notification_timetable_archived_message, timetable.name)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        NotificationReceiver.showNotification(
            context,
            notificationId,
            title,
            message,
            pendingIntent
        )
        Log.d("NotificationHelper", "Archival notification sent for timetable: ${timetable.name}")
    }

    /**
     * Sends a notification when an ad-hoc ClassSession has been automatically deleted.
     * This method is for backward compatibility with old ad-hoc ClassSessions.
     * @param session The deleted ClassSession object (ad-hoc type).
     */
    fun sendAdHocEngagementDeletionNotification(session: ClassSession) {
        val notificationId = generateNotificationId(session.sessionId, TIMETABLE_BASE_ID, 200)
        val title = context.getString(R.string.notification_adhoc_engagement_deleted_title)
        val message = context.getString(
            R.string.notification_adhoc_engagement_deleted_message,
            session.courseName
        )

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        NotificationReceiver.showNotification(
            context,
            notificationId,
            title,
            message,
            pendingIntent
        )
        Log.d(
            "NotificationHelper",
            "Deletion notification sent for ad-hoc ClassSession: ${session.courseName}"
        )
    }

    /**
     * Sends a notification when an Engagement has been automatically deleted.
     * @param engagement The deleted Engagement object.
     */
    fun sendEngagementDeletionNotification(engagement: Engagement) {
        val notificationId =
            generateNotificationId(engagement.engagementId, ENGAGEMENT_BASE_ID, 250)
        val title = context.getString(R.string.notification_engagement_deleted_title)
        val message = context.getString(
            R.string.notification_engagement_deleted_message,
            engagement.engagementName
        )

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        NotificationReceiver.showNotification(
            context,
            notificationId,
            title,
            message,
            pendingIntent
        )
        Log.d(
            "NotificationHelper",
            "Deletion notification sent for engagement: ${engagement.engagementName}"
        )
    }

    /**
     * Sends a notification when an exam has been automatically deleted.
     * @param exam The deleted Exam object.
     */
    fun sendExamDeletionNotification(exam: Exam) {
        val notificationId = generateNotificationId(exam.examId, EXAM_BASE_ID, 300)
        val title = context.getString(R.string.notification_exam_deleted_title)
        val message = context.getString(R.string.notification_exam_deleted_message, exam.courseName)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        NotificationReceiver.showNotification(
            context,
            notificationId,
            title,
            message,
            pendingIntent
        )
        Log.d(
            "NotificationHelper",
            "Deletion notification sent for exam: ${exam.courseName} on ${exam.examDate}"
        )
    }

    private fun getAssignmentNotificationPreferences(): List<Pair<Int, Boolean>> {
        return listOf(
            Pair(3 * 24 * 60, sharedPreferences.getBoolean("assignNotif3Days", true)),
            Pair(2 * 24 * 60, sharedPreferences.getBoolean("assignNotif2Days", true)),
            Pair(1 * 24 * 60, sharedPreferences.getBoolean("assignNotif1Day", true)),
            Pair(12 * 60, sharedPreferences.getBoolean("assignNotif12Hours", true)),
            Pair(6 * 60, sharedPreferences.getBoolean("assignNotif6Hours", true)),
            Pair(3 * 60, sharedPreferences.getBoolean("assignNotif3Hours", true)),
            Pair(1 * 60, sharedPreferences.getBoolean("assignNotif1Hour", true))
        )
    }

    private fun getExamNotificationPreferences(): List<Pair<Int, Boolean>> {
        return listOf(
            Pair(5 * 24 * 60, sharedPreferences.getBoolean("examNotif5Days", true)),
            Pair(3 * 24 * 60, sharedPreferences.getBoolean("examNotif3Days", true)),
            Pair(1 * 24 * 60, sharedPreferences.getBoolean("examNotif1Day", true)),
            Pair(12 * 60, sharedPreferences.getBoolean("examNotif12Hours", true)),
            Pair(6 * 60, sharedPreferences.getBoolean("examNotif6Hours", true)),
            Pair(3 * 60, sharedPreferences.getBoolean("examNotif3Hours", true)),
            Pair(1 * 60, sharedPreferences.getBoolean("examNotif1Hour", true))
        )
    }

    private fun getTodoNotificationPreferences(): List<Pair<Int, Boolean>> {
        return listOf(
            Pair(30 * 24 * 60, sharedPreferences.getBoolean("todoNotif30Days", true)),
            Pair(25 * 24 * 60, sharedPreferences.getBoolean("todoNotif25Days", true)),
            Pair(20 * 24 * 60, sharedPreferences.getBoolean("todoNotif20Days", true)),
            Pair(15 * 24 * 60, sharedPreferences.getBoolean("todoNotif15Days", true)),
            Pair(10 * 24 * 60, sharedPreferences.getBoolean("todoNotif10Days", true)),
            Pair(7 * 24 * 60, sharedPreferences.getBoolean("todoNotif7Days", true)),
            Pair(5 * 24 * 60, sharedPreferences.getBoolean("todoNotif5Days", true)),
            Pair(3 * 24 * 60, sharedPreferences.getBoolean("todoNotif3Days", true)),
            Pair(1 * 24 * 60, sharedPreferences.getBoolean("todoNotif1Day", true))
        )
    }
}
