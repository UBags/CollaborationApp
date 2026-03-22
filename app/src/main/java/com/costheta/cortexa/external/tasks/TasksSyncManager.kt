package com.costheta.cortexa.external.tasks

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action

import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Manages the setup and scheduling of background synchronization for Google Tasks
 * using Android's WorkManager.
 */
class TasksSyncManager(private val context: Context) {

    companion object {
        private const val UNIQUE_WORK_NAME = "GoogleTasksPeriodicSync"
    }

    /**
     * Schedules a periodic background task to sync Google Tasks.
     * If a task is already scheduled, it will be kept.
     *
     * The task is configured to run roughly every 4 hours and only when the
     * device is connected to a network.
     */
    fun schedulePeriodicSync() {
        Log.d("TasksSyncManager", "Scheduling periodic sync for Google Tasks.")

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicRequest = PeriodicWorkRequestBuilder<TasksSyncWorker>(4, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP, // If work exists, do nothing. Use REPLACE to update.
            periodicRequest
        )
    }

    /**
     * Triggers an immediate, one-time sync.
     * This is useful for a "Sync Now" button or for syncing on app launch.
     */
    fun triggerImmediateSync() {
        Log.d("TasksSyncManager", "Triggering immediate one-time sync for Google Tasks.")
        val oneTimeRequest = OneTimeWorkRequestBuilder<TasksSyncWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context).enqueue(oneTimeRequest)
    }

    /**
     * Cancels all scheduled sync work.
     */
    fun cancelAllSyncs() {
        Log.d("TasksSyncManager", "Cancelling all scheduled task syncs.")
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }
}
