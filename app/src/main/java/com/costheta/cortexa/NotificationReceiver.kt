package com.costheta.cortexa

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager // NEW: Import PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import android.Manifest

/**
 * BroadcastReceiver responsible for receiving notification triggers
 * and displaying the actual notification to the user.
 */
class NotificationReceiver : BroadcastReceiver() {

    // Companion object to define action and extra keys for the intent
    companion object {
        const val NOTIFICATION_ACTION = "com.example.cortexa.NOTIFICATION_ACTION"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val EXTRA_TITLE = "notification_title"
        const val EXTRA_MESSAGE = "notification_message"
        const val EXTRA_CHANNEL_ID = "notification_channel_id"

        /**
         * Helper function to display a notification.
         * This is made static (part of companion object) so it can be called directly
         * from NotificationHelper or other parts of the app.
         *
         * @param context The application context.
         * @param notificationId A unique ID for this notification.
         * @param title The title of the notification.
         * @param message The message body of the notification.
         * @param pendingIntent The PendingIntent to be launched when the notification is tapped.
         */
        fun showNotification(
            context: Context,
            notificationId: Int,
            title: String,
            message: String,
            pendingIntent: PendingIntent,
            channelId: String = NotificationHelper.DEFAULT_CHANNEL_ID
        ) {
            // Check for POST_NOTIFICATIONS permission on Android 13 (API 33) and higher
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // TIRAMISU is API 33
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Log.w(
                        "NotificationReceiver",
                        "POST_NOTIFICATIONS permission not granted. Cannot show notification ID: $notificationId"
                    )
                    // Optionally, you could try to inform the user, but direct UI from a BroadcastReceiver is tricky.
                    // A common approach is to show a persistent notification asking for permission or to guide them to settings.
                    return
                }
            }

//        // Get the default notification sound URI
//        val defaultSoundUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            // Build the notification
            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_launcher_foreground) // Use a suitable small icon
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH) // High priority for heads-up notification
                .setCategory(NotificationCompat.CATEGORY_REMINDER) // Category for reminders
                .setContentIntent(pendingIntent) // Set the intent to launch when clicked
                .setAutoCancel(true) // Dismiss notification when tapped

            // Show the notification
            try {
                with(NotificationManagerCompat.from(context)) {
                    notify(notificationId, builder.build())
                    Log.d(
                        "NotificationReceiver",
                        "Notification shown on channel '$channelId': ID=$notificationId"
                    )
                }
            } catch (e: SecurityException) {
                // This catch block handles cases where permission might be revoked right before notify()
                // or if there's any other security-related issue.
                Log.e(
                    "NotificationReceiver",
                    "SecurityException when showing notification: ${e.message}",
                    e
                )
            }
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        // Ensure context and intent are not null
        if (context == null || intent == null) {
            Log.e("NotificationReceiver", "Context or Intent is null in onReceive.")
            return
        }

        // Check if the received intent's action matches our defined action
        if (intent.action == NOTIFICATION_ACTION) {
            // Extract data from the intent
            val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
            val title = intent.getStringExtra(EXTRA_TITLE) ?: "Reminder"
            val message = intent.getStringExtra(EXTRA_MESSAGE) ?: "You have an upcoming scheduled event"
            // --- REVISED: Get the channel ID from the intent ---
            val channelId = intent.getStringExtra(EXTRA_CHANNEL_ID) ?: NotificationHelper.DEFAULT_CHANNEL_ID

            // Create a PendingIntent to open MainActivity when the notification is tapped
            val mainActivityIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent: PendingIntent = PendingIntent.getActivity(
                context,
                0, // Request code
                mainActivityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE // Use FLAG_IMMUTABLE for API 23+
            )

            // Call the static helper function to show the notification
            showNotification(context, notificationId, title, message,  pendingIntent, channelId)
        }
    }
}
