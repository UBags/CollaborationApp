package com.costheta.cortexa

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action

import android.app.Application
import android.util.Log
import androidx.emoji2.text.DefaultEmojiCompatConfig
import androidx.emoji2.text.EmojiCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Custom Application class for the TimetableApp.
 * This is where we perform global, app-wide initializations.
 */
class TimetableApplication : Application() {

    companion object {
        // This is now only used for the cleanup worker name
        private const val CLEANUP_WORK_NAME = "TimetableCleanupWork"
    }

    override fun onCreate() {
        super.onCreate()
        // Initialize EmojiCompat once for the entire application lifecycle.
        val config = DefaultEmojiCompatConfig.create(applicationContext)
        if (config != null) {
            EmojiCompat.init(config)
            Log.d("TimetableApplication", "EmojiCompat initialized successfully.")
        } else {
            Log.w("TimetableApplication", "EmojiCompat config is null, not initializing.")
        }

        // --- REVISED: Centralize channel creation ---
        // Create all notification channels on app startup.
        val notificationHelper = NotificationHelper(this)
        notificationHelper.createNotificationChannels()

        setupPeriodicCleanupWorker()
    }

    /**
     * Sets up and enqueues a periodic WorkManager task to run the CleanupWorker.
     */
    private fun setupPeriodicCleanupWorker() {
        val cleanupWorkRequest = PeriodicWorkRequestBuilder<CleanupWorker>(
            15, TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            CLEANUP_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            cleanupWorkRequest
        )
        Log.d("TimetableApplication", "Periodic CleanupWorker enqueued.")
    }
}
