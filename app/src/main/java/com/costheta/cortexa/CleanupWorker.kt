package com.costheta.cortexa

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.costheta.cortexa.data.db.AppDatabase
import com.costheta.cortexa.data.db.dao.ActionDao
import com.costheta.cortexa.data.db.dao.AssignmentDao
import com.costheta.cortexa.data.db.dao.AttachmentDao
import com.costheta.cortexa.data.db.dao.EngagementDao
import com.costheta.cortexa.data.db.dao.ExamDao
import com.costheta.cortexa.data.db.dao.LocationDao
import com.costheta.cortexa.data.db.dao.NotesDao
import com.costheta.cortexa.data.db.dao.TimetableDao
import com.costheta.cortexa.data.db.dao.TodoDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * A CoroutineWorker responsible for periodically cleaning up old and expired data
 * based on user-defined preferences.
 */
class CleanupWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    // DAOs for database access
    private val timetableDao: TimetableDao by lazy { AppDatabase.getDatabase(applicationContext).timetableDao() }
    private val engagementDao: EngagementDao by lazy { AppDatabase.getDatabase(applicationContext).engagementDao() }
    private val examDao: ExamDao by lazy { AppDatabase.getDatabase(applicationContext).examDao() }
    private val assignmentDao: AssignmentDao by lazy { AppDatabase.getDatabase(applicationContext).assignmentDao() }
    private val todoDao: TodoDao by lazy { AppDatabase.getDatabase(applicationContext).todoDao() }
    private val notesDao: NotesDao by lazy { AppDatabase.getDatabase(applicationContext).notesDao() }
    private val attachmentDao: AttachmentDao by lazy { AppDatabase.getDatabase(applicationContext).attachmentDao() }
    private val locationDao: LocationDao by lazy { AppDatabase.getDatabase(applicationContext).locationDao() }
    private val actionDao: ActionDao by lazy { AppDatabase.getDatabase(applicationContext).actionDao() }

    private val notificationHelper: NotificationHelper by lazy { NotificationHelper(applicationContext) }

    override suspend fun doWork(): Result {
        Log.d("CleanupWorker", "Starting cleanup task...")

        val prefs = applicationContext.getSharedPreferences("user_preferences", Context.MODE_PRIVATE)
        val shouldAutoArchiveTimetables = prefs.getBoolean("pref_timetable_auto_archive", true)
        val engagementDeleteDays = prefs.getInt("pref_engagement_auto_delete", 180)
        val actionDeleteDays = prefs.getInt("pref_action_auto_delete", 180)
        val assignmentDeleteDays = prefs.getInt("pref_assignment_auto_delete", 180)
        val examDeleteDays = prefs.getInt("pref_exam_auto_delete", 180)
        val todoDeleteDays = prefs.getInt("pref_todo_auto_delete", 180)
        val notesDeleteDays = prefs.getInt("pref_notes_auto_delete", 360)

        return withContext(Dispatchers.IO) {
            try {
                val currentDate = LocalDate.now()
                // NEW - Get current time in UTC milliseconds for consistent comparison
                val currentTimeMillis = Instant.now().toEpochMilli()
                val daysInMillis = 24 * 60 * 60 * 1000L

                // 1. Archive expired Timetables if preference is enabled
                if (shouldAutoArchiveTimetables) {
                    val expiredTimetables = timetableDao.getExpiredTimetables(currentDate)
                    Log.d("CleanupWorker", "Found ${expiredTimetables.size} expired timetables to archive.")
                    expiredTimetables.forEach { timetable ->
                        val updatedTimetable = timetable.copy(isActive = false)
                        timetableDao.updateTimetable(updatedTimetable)
                        updatedTimetable.id?.let { notificationHelper.cancelAllNotificationsForTimetable(it, timetableDao) }
                        notificationHelper.sendTimetableArchivedNotification(updatedTimetable)
                        Log.i("CleanupWorker", "Archived timetable: ${timetable.name}")
                    }
                }

                // 2. Delete old, completed Engagements
                val allEngagements = engagementDao.getAllEngagements()
                val engagementsToDelete = allEngagements.filter {
                    // NEW - Use endDateTimeInMillis for accurate, timezone-aware check
                    val deletionThreshold = it.endDateTimeInMillis + (engagementDeleteDays * daysInMillis)
                    deletionThreshold < currentTimeMillis
                }
                Log.d("CleanupWorker", "Found ${engagementsToDelete.size} engagements to delete.")
                engagementsToDelete.forEach { engagement ->
                    engagement.engagementId?.let { id ->
                        engagementDao.deleteEngagement(id)
                        cleanupEventData("Engagement", id)
                        Log.i("CleanupWorker", "Deleted old engagement: ${engagement.engagementName}")
                    }
                }

                // 3. Delete old, completed Actions
                val allActions = actionDao.getAllActivities().firstOrNull() ?: emptyList()
                val actionsToDelete = allActions.filter {
                    // NEW - Use endDateTimeMillis for accurate, timezone-aware check
                    it.isDone && (it.endDateTimeMillis + (actionDeleteDays * daysInMillis)) < currentTimeMillis
                }
                Log.d("CleanupWorker", "Found ${actionsToDelete.size} completed actions to delete.")
                actionsToDelete.forEach { action ->
                    action.actionId?.let { id ->
                        actionDao.deleteAction(id)
                        cleanupEventData("Action", id)
                        Log.i("CleanupWorker", "Deleted completed action: ${action.actionName}")
                    }
                }

                // 4. Delete old, completed Assignments
                val allAssignments = assignmentDao.getAllAssignments().firstOrNull() ?: emptyList()
                val assignmentsToDelete = allAssignments.filter {
                    // NEW - Use dueDateTimeMillis for accurate, timezone-aware check
                    it.currentProgress == 100 && (it.dueDateTimeMillis + (assignmentDeleteDays * daysInMillis)) < currentTimeMillis
                }
                Log.d("CleanupWorker", "Found ${assignmentsToDelete.size} completed assignments to delete.")
                assignmentsToDelete.forEach { assignment ->
                    assignment.assignmentId?.let { id ->
                        assignmentDao.deleteAssignment(id)
                        cleanupEventData("Assignment", id)
                        Log.i("CleanupWorker", "Deleted completed assignment: ${assignment.assignmentName}")
                    }
                }

                // 5. Delete old Exams
                val allExams = examDao.getAllExams().firstOrNull() ?: emptyList()
                val examsToDelete = allExams.filter {
                    // NEW - Use endDateTimeMillis for accurate, timezone-aware check
                    (it.endDateTimeMillis + (examDeleteDays * daysInMillis)) < currentTimeMillis
                }
                Log.d("CleanupWorker", "Found ${examsToDelete.size} old exams to delete.")
                examsToDelete.forEach { exam ->
                    exam.examId?.let { id ->
                        examDao.deleteExam(id)
                        cleanupEventData("Exam", id)
                        Log.i("CleanupWorker", "Deleted old exam: ${exam.courseName}")
                    }
                }

                // 6. Delete old, completed To-Dos
                val allTodos = todoDao.getAllTodos().firstOrNull() ?: emptyList()
                val todosToDelete = allTodos.filter {
                    // NEW - Use completedTimeInMillis for accurate, timezone-aware check
                    it.isCompleted && it.completedTimeInMillis != null && (it.completedTimeInMillis + (todoDeleteDays * daysInMillis)) < currentTimeMillis
                }
                Log.d("CleanupWorker", "Found ${todosToDelete.size} completed To-Do items to delete.")
                todosToDelete.forEach { todo ->
                    todo.todoId?.let { id ->
                        todoDao.deleteTodo(id)
                        cleanupEventData("ToDo", id)
                        Log.i("CleanupWorker", "Deleted completed To-Do: ${todo.title}")
                    }
                }

                // 7. MODIFIED: Archive old Notes instead of deleting them
                val allNotes = notesDao.getAllNotes().firstOrNull() ?: emptyList()
                // Find notes that are old enough and are NOT already archived
                val notesToArchive = allNotes.filter { note ->
                    !note.archivedNote && note.lastModified.plusDays(notesDeleteDays.toLong()).isBefore(LocalDateTime.now())
                }
                Log.d("CleanupWorker", "Found ${notesToArchive.size} old notes to archive.")
                notesToArchive.forEach { note ->
                    // Create an updated copy with archivedNote set to true
                    val updatedNote = note.copy(archivedNote = true)
                    // Update the note in the database (assuming notesDao has an updateNote method)
                    notesDao.updateNote(updatedNote)
                    // We don't call cleanupEventData because we are archiving, not deleting.
                    Log.i("CleanupWorker", "Archived old note: ${note.subject}")
                }

                Log.d("CleanupWorker", "Cleanup task completed successfully.")
                Result.success()
            } catch (e: Exception) {
                Log.e("CleanupWorker", "Error during cleanup task: ${e.message}", e)
                Result.failure()
            }
        }
    }

    /**
     * A helper function to clean up all associated data for a given event.
     * This includes notifications, attachments, and locations.
     * @param eventType The type of the event (e.g., "Engagement", "Action").
     * @param eventId The ID of the event being deleted.
     */
    private suspend fun cleanupEventData(eventType: String, eventId: Long) {
        when (eventType) {
            "Engagement" -> notificationHelper.cancelEngagementNotifications(eventId)
            "Action" -> notificationHelper.cancelActionNotifications(eventId)
            "Assignment" -> notificationHelper.cancelAssignmentNotifications(eventId)
            "Exam" -> notificationHelper.cancelExamNotifications(eventId)
            "ToDo" -> notificationHelper.cancelTodoNotifications(eventId)
            // Notes do not have notifications
        }
        attachmentDao.deleteAttachmentsForEvent(eventType, eventId)
        locationDao.deleteLocationsForEvent(eventType, eventId)
        Log.d("CleanupWorker", "Cleaned up associated data (notifications, attachments, locations) for $eventType ID: $eventId")
    }
}
