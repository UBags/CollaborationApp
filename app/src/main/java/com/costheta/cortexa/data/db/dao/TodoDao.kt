package com.costheta.cortexa.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.costheta.cortexa.data.models.TodoItem
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for interacting with the TodoItem entity.
 * Defines methods for inserting, querying, updating, and deleting To-Do items.
 */
@Dao
interface TodoDao {

    /**
     * Inserts a new TodoItem into the database.
     * @param todoItem The TodoItem object to insert.
     * @return The row ID of the newly inserted To-Do item.
     */
    @Insert
    suspend fun insertTodo(todoItem: TodoItem): Long

    /**
     * Updates an existing TodoItem in the database.
     * @param todoItem The TodoItem object to update.
     * @return The number of rows updated (should be 1 if successful).
     */
    @Update
    suspend fun updateTodo(todoItem: TodoItem): Int

    /**
     * Deletes a TodoItem from the database by its ID.
     * @param todoId The ID of the TodoItem to delete.
     * @return The number of rows deleted (should be 1 if successful).
     */
    @Query("DELETE FROM todos WHERE todoId = :todoId")
    suspend fun deleteTodo(todoId: Long): Int

    /**
     * Retrieves all TodoItems from the database that are marked as validated.
     * Ordered by completion status (incomplete first) and then by due date/time.
     * @return A Flow that emits a list of validated TodoItem objects.
     */
    @Query("SELECT * FROM todos WHERE validatedItem = 1 ORDER BY isCompleted ASC, dueDate ASC, dueTime ASC")
    fun getAllTodos(): Flow<List<TodoItem>>

    /**
     * Retrieves a single TodoItem by its ID.
     * @param todoId The ID of the TodoItem to retrieve.
     * @return The TodoItem object, or null if not found.
     */
    @Query("SELECT * FROM todos WHERE todoId = :todoId")
    suspend fun getTodoById(todoId: Long): TodoItem?

    /**
     * Deletes all TodoItems that have not been validated (i.e., temporary/incomplete entries).
     * This is useful for cleaning up after unexpected app closures or user discards.
     * @return The number of rows deleted.
     */
    @Query("DELETE FROM todos WHERE validatedItem = 0")
    suspend fun deleteUnvalidatedTodos(): Int

    /**
     * Retrieves all unique titles from validated TodoItems for autocomplete suggestions.
     * @return A Flow that emits a list of unique String titles.
     */
    @Query("SELECT DISTINCT title FROM todos WHERE validatedItem = 1")
    fun getAllTodoTitles(): Flow<List<String>>

    /**
     * Retrieves displayable TodoItems from the database.
     * Displayable means validated, and either incomplete OR completed where the completion time is
     * no more than 1 hour after the due date time.
     * Ordered by lastModified in descending order for initial display.
     * @return A Flow that emits a list of displayable TodoItem objects.
     */
    @Query("""
        SELECT * FROM todos 
        WHERE validatedItem = 1 
        AND (isCompleted = 0 OR (isCompleted = 1 AND dueDateTimeMillis IS NOT NULL AND completedTimeInMillis IS NOT NULL AND (completedTimeInMillis + 3600000) >= :currentTimeMillis))
        ORDER BY lastModified DESC
    """)
    fun getDisplayableTodos(currentTimeMillis: Long): Flow<List<TodoItem>>
}
