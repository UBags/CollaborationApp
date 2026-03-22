package com.costheta.cortexa.engagement

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action

import com.costheta.cortexa.data.models.Engagement
import com.costheta.cortexa.data.models.SyncedCalendarEvent
import java.time.LocalDateTime

/**
 * A sealed interface to represent any item that can be displayed in the engagement list.
 * This allows the RecyclerView adapter to handle both native Engagements and synced
 * Calendar Events in a type-safe way.
 */
sealed interface DisplayableItem {
    val id: String
    val startDateTime: LocalDateTime?

    data class AppEngagement(val engagement: Engagement) : DisplayableItem {
        override val id: String get() = "engagement_${engagement.engagementId}"
        override val startDateTime: LocalDateTime? get() = engagement.startDateTime
    }

    data class CalendarEvent(val event: SyncedCalendarEvent) : DisplayableItem {
        override val id: String get() = "calendar_${event.localId}"
        // Convert millis to LocalDateTime for sorting and display
        override val startDateTime: LocalDateTime?
            get() = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(event.startDateTimeMillis),
                java.time.ZoneId.systemDefault()
            )
    }
}
