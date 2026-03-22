package com.costheta.cortexa.external.tasks

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.costheta.cortexa.data.models.SyncedGoogleTask
import com.costheta.cortexa.external.tasks.GoogleApiHelper
import com.costheta.cortexa.MainApplication
import com.costheta.cortexa.data.db.dao.SyncedGoogleTaskDao
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.services.tasks.model.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.api.client.util.DateTime

/**
 * A background worker managed by WorkManager to periodically sync Google Tasks.
 * This worker handles authenticating, fetching data, and updating the local database.
 */
class TasksSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    // Lazily initialize the DAO from the Application class.
    private val taskDao: SyncedGoogleTaskDao by lazy {
        (applicationContext as MainApplication).database.syncedGoogleTaskDao()
    }

    override suspend fun doWork(): Result {
        Log.d("TasksSyncWorker", "Worker starting background sync for Google Tasks.")
        // Perform the work on the IO dispatcher, suitable for network and database operations.
        return withContext(Dispatchers.IO) {
            try {
                // 1. Get the last signed-in Google account.
                // This is a prerequisite for making authenticated API calls.
                val account = GoogleSignIn.getLastSignedInAccount(applicationContext)
                if (account == null) {
                    Log.w("TasksSyncWorker", "No signed-in user found. Skipping sync.")
                    return@withContext Result.success() // Not a failure, just can't work.
                }

                // 2. Fetch all active tasks from the remote Google Tasks API.
                val fetchedTasks = fetchTasksFromApi(account)

                // 3. Perform a smart sync to keep the local database consistent.
                // Get the IDs of all tasks currently in our local database.
                val existingTaskIds = taskDao.getAll().map { it.googleTaskId }.toSet()
                // Get the IDs of all tasks we just fetched from the API.
                val fetchedTaskIds = fetchedTasks.map { it.googleTaskId }.toSet()

                // Determine which tasks have been deleted or completed in the Google Tasks app.
                // These are tasks that exist locally but were not in the fetched list.
                val tasksToDelete = existingTaskIds - fetchedTaskIds
                if (tasksToDelete.isNotEmpty()) {
                    taskDao.deleteTasksByIds(tasksToDelete.toList())
                    Log.d("TasksSyncWorker", "Deleted ${tasksToDelete.size} stale/completed tasks from local DB.")
                }

                // 4. Insert or update the fetched tasks into the local database.
                // The DAO's `upsertAll` method will handle both new and existing tasks.
                if (fetchedTasks.isNotEmpty()) {
                    taskDao.upsertAll(fetchedTasks)
                    Log.d("TasksSyncWorker", "Sync complete. Upserted ${fetchedTasks.size} tasks.")
                }

                Result.success()
            } catch (e: Exception) {
                // If any exception occurs (e.g., network error, auth issue), log it and retry.
                Log.e("TasksSyncWorker", "Error during background sync", e)
                Result.retry() // The sync failed; WorkManager will schedule a retry.
            }
        }
    }

    /**
     * Connects to the Google Tasks API, fetches all task lists for the user,
     * and then fetches all active (non-completed) tasks from each list.
     *
     * @param account The authenticated GoogleSignInAccount.
     * @return A list of [SyncedGoogleTask] objects ready for database insertion.
     */
    private suspend fun fetchTasksFromApi(account: com.google.android.gms.auth.api.signin.GoogleSignInAccount): List<SyncedGoogleTask> {
        // Use the helper to get an authenticated Tasks service instance.
        val service = GoogleApiHelper.getTasksService(applicationContext, account)
        val allSyncedTasks = mutableListOf<SyncedGoogleTask>()

        // First, get all task lists associated with the user's account.
        val taskLists = service.tasklists().list().execute().items ?: emptyList()

        // Iterate through each task list to fetch the tasks within it.
        for (taskList in taskLists) {
            // Fetch tasks from the current list. `setShowCompleted(false)` is crucial
            // as we only want to sync active, incomplete tasks.
            val tasksResult = service.tasks().list(taskList.id).setShowCompleted(false).execute()
            val tasks = tasksResult.items ?: emptyList()

            // Map the API Task models to our app's SyncedGoogleTask Room entity.
            val mappedTasks = tasks.map { apiTask ->
                mapApiTaskToSyncedGoogleTask(apiTask, taskList.id)
            }
            allSyncedTasks.addAll(mappedTasks)
        }
        return allSyncedTasks
    }


    /**
     * Maps a [com.google.api.services.tasks.model.Task] object from the Google API
     * to our app's local [SyncedGoogleTask] entity.
     *
     * @param apiTask The task object from the Google API.
     * @param taskListId The ID of the list this task belongs to.
     * @return A [SyncedGoogleTask] object.
     */
    private fun mapApiTaskToSyncedGoogleTask(apiTask: Task, taskListId: String): SyncedGoogleTask {
        // CORRECTED: The 'due' property is an RFC 3339 String. We must parse it.
        // The DateTime constructor can parse this string format directly.
        val dueTimestamp: Long? = apiTask.due?.let { rfc3339String ->
            try {
                DateTime(rfc3339String).value
            } catch (e: Exception) {
                // Handle cases where the date string might be malformed, though this is rare.
                null
            }
        }

        return SyncedGoogleTask(
            googleTaskId = apiTask.id,
            googleTaskListId = taskListId,
            title = apiTask.title ?: "No Title",
            notes = apiTask.notes,
            dueDateTimeMillis = dueTimestamp,
            isCompleted = apiTask.status == "completed",
            parentTaskId = apiTask.parent,
            lastSyncedTimestamp = System.currentTimeMillis()
        )
    }
}

