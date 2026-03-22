package com.costheta.cortexa.data.db.dao

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.costheta.cortexa.data.models.Action
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalTime

/**
 * Data Access Object (DAO) for interacting with the Action entity.
 * Defines methods for inserting, querying, updating, and deleting action data.
 */
@Dao
interface ActionDao {

    @Insert
    suspend fun insertAction(action: Action): Long

    @Update
    suspend fun updateAction(action: Action): Int

    @Query("DELETE FROM actions WHERE actionId = :actionId")
    suspend fun deleteAction(actionId: Long): Int

    @Query("SELECT * FROM actions WHERE actionId = :actionId")
    suspend fun getActionById(actionId: Long): Action?

    @Query("SELECT * FROM actions ORDER BY startDate ASC, startTime ASC")
    fun getAllActivities(): Flow<List<Action>>

    @Query("""
        SELECT * FROM actions
        WHERE validatedItem = 1
        AND (
            (endDate > :currentDate) OR
            (endDate = :currentDate AND endTime >= :currentTime)
        )
        ORDER BY startDate ASC, startTime ASC
    """)
    fun getActiveAndValidatedActivities(currentDate: LocalDate, currentTime: LocalTime): Flow<List<Action>>

    @Query("""
        SELECT * FROM actions
        WHERE (endDate < :currentDate) OR
        (endDate = :currentDate AND endTime < :currentTime)
    """)
    suspend fun getExpiredActivities(currentDate: LocalDate, currentTime: LocalTime): List<Action>

    @Query("DELETE FROM actions WHERE validatedItem = 0")
    suspend fun deleteUnvalidatedActivities(): Int

    @Query("UPDATE actions SET validatedItem = :validatedStatus WHERE actionId = :actionId")
    suspend fun updateActionValidationStatus(actionId: Long, validatedStatus: Boolean): Int

    /**
     * TIMEZONE CHANGE: Retrieves all displayable Action entries using endDateTimeMillis.
     * Displayable means the action's end date/time plus a 1-hour grace period
     * is in the future or present, and the item is validated.
     *
     * @param currentDateTimeMillis The current date and time in milliseconds since epoch (UTC).
     * @return A Flow emitting a list of displayable Action objects.
     */
    @Query("""
        SELECT * FROM actions
        WHERE validatedItem = 1
        AND (endDateTimeMillis >= (:currentDateTimeMillis - 3600000)) -- CORRECTED: Moved arithmetic to the parameter side
        ORDER BY startDate ASC, startTime ASC
    """)
    fun getDisplayableActions(currentDateTimeMillis: Long): Flow<List<Action>>
}
