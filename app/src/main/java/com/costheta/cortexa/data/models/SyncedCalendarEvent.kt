package com.costheta.cortexa.data.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a single event synced from the user's device calendar (via CalendarProvider).
 * This entity acts as a local cache of external calendar data.
 * The index on googleEventId ensures quick lookups to prevent duplicates.
 */
@Entity(
    tableName = "synced_calendar_events",
    indices = [Index(value = ["googleEventId"], unique = true)]
)
data class SyncedCalendarEvent(
    @PrimaryKey(autoGenerate = true)
    val localId: Long = 0,
    val googleEventId: Long, // The original ID from the CalendarProvider
    val googleCalendarId: Long, // The ID of the calendar this event belongs to
    val title: String,
    val description: String?,
    val location: String?,
    val startDateTimeMillis: Long,
    val endDateTimeMillis: Long,
    val isAllDay: Boolean,
    val eventColor: Int?,
    val lastSyncedTimestamp: Long = System.currentTimeMillis()
)
