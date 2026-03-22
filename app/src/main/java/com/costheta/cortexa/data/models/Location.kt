package com.costheta.cortexa.data.models

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a stored location entry, potentially linked to an event.
 *
 * @property locationId Unique ID for the location entry. Auto-generated.
 * @property userFriendlyName A name given by the user (e.g., "Jack's Office").
 * @property geocodedAddress The full address obtained from geocoding (e.g., "1600 Amphitheatre Parkway, Mountain View, CA").
 * @property latitude The latitude coordinate of the location.
 * @property longitude The longitude coordinate of the location.
 * @property eventType The type of event this location is associated with (e.g., "Exam", "ClassSession").
 * @property eventRefId The ID of the event this location is associated with.
 */
@Entity(tableName = "locations")
data class Location(
    @PrimaryKey(autoGenerate = true)
    val locationId: Long? = null,
    val userFriendlyName: String,
    val geocodedAddress: String,
    val latitude: Double,
    val longitude: Double,
    val eventType: String,
    val eventRefId: Long
)
