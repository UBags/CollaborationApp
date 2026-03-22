package com.costheta.cortexa.data.db.dao

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.costheta.cortexa.data.models.Location
import kotlinx.coroutines.flow.Flow


/**
 * Data Access Object (DAO) for interacting with the LocationEntry entity.
 * Defines methods for inserting, querying, updating, and deleting LocationEntry items.
 */
@Dao
interface LocationDao {

    /**
     * Inserts a new LocationEntry into the database.
     * @param location The LocationEntry object to insert.
     * @return The row ID of the newly inserted location.
     */
    @Insert
    suspend fun insertLocation(location: Location): Long

    /**
     * Updates an existing LocationEntry in the database.
     * @param location The LocationEntry object to update.
     */
    @Update
    suspend fun updateLocation(location: Location)

    /**
     * Deletes a LocationEntry from the database by its ID.
     * @param locationId The ID of the LocationEntry to delete.
     * @return The number of rows deleted.
     */
    @Query("DELETE FROM locations WHERE locationId = :locationId")
    suspend fun deleteLocation(locationId: Long): Int

    /**
     * Retrieves a specific LocationEntry by its ID.
     * @param locationId The ID of the LocationEntry to retrieve.
     * @return The LocationEntry object if found, null otherwise.
     */
    @Query("SELECT * FROM locations WHERE locationId = :locationId")
    suspend fun getLocationById(locationId: Long): Location?

    /**
     * Retrieves a LocationEntry associated with a specific event.
     * @param eventType The type of the event (e.g., "Exam", "ClassSession").
     * @param eventRefId The ID of the event.
     * @return A Flow emitting the LocationEntry if found, null otherwise.
     */
    @Query("SELECT * FROM locations WHERE eventType = :eventType AND eventRefId = :eventRefId LIMIT 1")
    fun getLocationForEvent(eventType: String, eventRefId: Long): Flow<Location?>

    /**
     * Deletes all LocationEntry items associated with a specific event.
     * @param eventType The type of the event.
     * @param eventRefId The ID of the event.
     * @return The number of rows deleted.
     */
    @Query("DELETE FROM locations WHERE eventType = :eventType AND eventRefId = :eventRefId")
    suspend fun deleteLocationsForEvent(eventType: String, eventRefId: Long): Int

//    @Query("DELETE FROM locations WHERE validatedItem = 0")
//    suspend fun deleteUnvalidatedLocations(): Int

    // In LocationDao.kt
// This query assumes you can join or infer unvalidated status from the parent table.
// A simpler approach for general cleanup of locations whose *parent* is gone
// is to periodically check if eventRefId exists in the corresponding event table.
// For now, I've added a more direct approach based on Exam's unvalidated status.
//    @Query("""
//    DELETE FROM locations
//    WHERE eventType = :eventType
//    AND eventRefId NOT IN (SELECT examId FROM exams WHERE validatedItem = 1)
//""")
//    suspend fun deleteUnvalidatedLocationsForEvent(eventType: String): Int
}
