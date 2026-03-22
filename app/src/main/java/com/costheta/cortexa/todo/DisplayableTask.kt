package com.costheta.cortexa.todo

import com.costheta.cortexa.data.models.SyncedGoogleTask
import com.costheta.cortexa.data.models.TodoItem
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * A sealed interface to represent any item that can be displayed in the to-do list.
 * This allows the RecyclerView adapter to handle both native TodoItems and synced
 * Google Tasks in a type-safe way.
 */
sealed interface DisplayableTask {
    val id: String
    val title: String
    val notes: String?
    val dueDateTimeMillis: Long?
    val isCompleted: Boolean

    data class AppTodo(val todo: TodoItem) : DisplayableTask {
        override val id: String get() = "todo_${todo.todoId}"
        override val title: String get() = todo.title
        override val notes: String? get() = todo.description
        override val dueDateTimeMillis: Long? get() = todo.dueDateTimeMillis
        override val isCompleted: Boolean get() = todo.isCompleted
    }

    data class GoogleTask(val task: SyncedGoogleTask) : DisplayableTask {
        override val id: String get() = "google_${task.localId}"
        override val title: String get() = task.title
        override val notes: String? get() = task.notes
        override val dueDateTimeMillis: Long? get() = task.dueDateTimeMillis
        override val isCompleted: Boolean get() = task.isCompleted
    }
}
