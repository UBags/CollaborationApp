package com.costheta.cortexa.external.calendar

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.costheta.cortexa.data.db.dao.SyncedCalendarEventDao
import com.costheta.cortexa.data.models.SyncedCalendarEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Manages the synchronization of calendar events from the Android CalendarProvider
 * into the application's local database.
 *
 * @param context The application context.
 * @param dao The DAO for accessing the synced_calendar_events table.
 * @param coroutineScope A CoroutineScope for launching background tasks.
 */
class CalendarSyncManager(
    private val context: Context,
    private val dao: SyncedCalendarEventDao,
    private val coroutineScope: CoroutineScope
) {

    private val contentResolver: ContentResolver = context.contentResolver
    private var calendarObserver: ContentObserver? = null

    // Projection array specifying which columns to query from the CalendarProvider.
    private val eventProjection: Array<String> = arrayOf(
        CalendarContract.Events._ID,
        CalendarContract.Events.CALENDAR_ID,
        CalendarContract.Events.TITLE,
        CalendarContract.Events.DESCRIPTION,
        CalendarContract.Events.EVENT_LOCATION,
        CalendarContract.Events.DTSTART,
        CalendarContract.Events.DTEND,
        CalendarContract.Events.ALL_DAY,
        CalendarContract.Events.DISPLAY_COLOR
    )

    /**
     * Starts the synchronization process. It performs an initial full sync
     * and then registers a ContentObserver to listen for future changes.
     */
    fun startSync() {
        if (!hasReadCalendarPermission()) {
            Log.w("CalendarSyncManager", "READ_CALENDAR permission not granted. Sync aborted.")
            return
        }
        Log.d("CalendarSyncManager", "Starting initial calendar sync.")
        performFullSync()
        registerObserver()
    }

    /**
     * Stops listening for calendar changes. Should be called when the component
     * managing this sync is destroyed (e.g., in Activity's onDestroy).
     */
    fun stopSync() {
        calendarObserver?.let {
            contentResolver.unregisterContentObserver(it)
            calendarObserver = null
            Log.d("CalendarSyncManager", "Calendar observer unregistered.")
        }
    }

    private fun performFullSync() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val events = fetchAllCalendarEvents()
                if (events.isNotEmpty()) {
                    val currentEventIds = events.map { it.googleEventId }
                    // Remove events from our DB that were deleted from the user's calendar
                    dao.deleteStaleEvents(currentEventIds)
                    // Insert new events and update existing ones
                    dao.upsertAll(events)
                    Log.d("CalendarSyncManager", "Sync complete. Upserted ${events.size} events.")
                } else {
                    dao.clearAll()
                    Log.d("CalendarSyncManager", "No calendar events found. Cleared local cache.")
                }
            } catch (e: Exception) {
                Log.e("CalendarSyncManager", "Error during full sync", e)
            }
        }
    }

    private fun fetchAllCalendarEvents(): List<SyncedCalendarEvent> {
        val eventsList = mutableListOf<SyncedCalendarEvent>()
        val uri: Uri = CalendarContract.Events.CONTENT_URI

        // Query only visible calendars
        val selection = "${CalendarContract.Calendars.VISIBLE} = ?"
        val selectionArgs = arrayOf("1")

        contentResolver.query(uri, eventProjection, selection, selectionArgs, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(CalendarContract.Events._ID)
            val calIdCol = cursor.getColumnIndexOrThrow(CalendarContract.Events.CALENDAR_ID)
            val titleCol = cursor.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
            val descCol = cursor.getColumnIndexOrThrow(CalendarContract.Events.DESCRIPTION)
            val locCol = cursor.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION)
            val startCol = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
            val endCol = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTEND)
            val allDayCol = cursor.getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY)
            val colorCol = cursor.getColumnIndexOrThrow(CalendarContract.Events.DISPLAY_COLOR)

            while (cursor.moveToNext()) {
                eventsList.add(
                    SyncedCalendarEvent(
                        googleEventId = cursor.getLong(idCol),
                        googleCalendarId = cursor.getLong(calIdCol),
                        title = cursor.getString(titleCol) ?: "Untitled Event",
                        description = cursor.getString(descCol),
                        location = cursor.getString(locCol),
                        startDateTimeMillis = cursor.getLong(startCol),
                        endDateTimeMillis = cursor.getLong(endCol),
                        isAllDay = cursor.getInt(allDayCol) == 1,
                        eventColor = cursor.getInt(colorCol),
                        lastSyncedTimestamp = System.currentTimeMillis()
                    )
                )
            }
        }
        return eventsList
    }

    private fun registerObserver() {
        if (calendarObserver == null) {
            calendarObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    Log.d("CalendarSyncManager", "Calendar content change detected. Triggering sync.")
                    performFullSync()
                }
            }
            contentResolver.registerContentObserver(
                CalendarContract.Events.CONTENT_URI,
                true,
                calendarObserver!!
            )
            Log.d("CalendarSyncManager", "Calendar observer registered.")
        }
    }

    private fun hasReadCalendarPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }
}
