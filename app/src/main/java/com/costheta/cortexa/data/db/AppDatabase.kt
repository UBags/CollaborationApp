// Path: app/src/main/java/com/example/cortexa/data/db/AppDatabase.kt
package com.costheta.cortexa.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.costheta.cortexa.data.db.dao.TimetableDao
import com.costheta.cortexa.data.db.dao.EngagementDao
import com.costheta.cortexa.data.db.dao.TodoDao
import com.costheta.cortexa.data.db.dao.AttachmentDao
import com.costheta.cortexa.data.db.dao.ExamDao
import com.costheta.cortexa.data.db.dao.NotesDao
import com.costheta.cortexa.data.db.dao.AssignmentDao
import com.costheta.cortexa.data.db.dao.SubtaskDao
import com.costheta.cortexa.data.db.dao.ActionDao
import com.costheta.cortexa.data.db.dao.LocationDao
import com.costheta.cortexa.data.db.dao.SyncedCalendarEventDao
import com.costheta.cortexa.data.db.dao.SyncedGoogleTaskDao

import com.costheta.cortexa.data.models.Assignment
import com.costheta.cortexa.data.models.Exam
import com.costheta.cortexa.data.models.Engagement
import com.costheta.cortexa.data.models.ClassSession
import com.costheta.cortexa.data.models.SessionException
import com.costheta.cortexa.data.models.Timetable
import com.costheta.cortexa.data.models.TodoItem
import com.costheta.cortexa.data.models.Attachment
import com.costheta.cortexa.data.models.NotesItem
import com.costheta.cortexa.data.models.Subtask
import com.costheta.cortexa.data.models.Action
import com.costheta.cortexa.data.models.EngagementException
import com.costheta.cortexa.data.models.Location
import com.costheta.cortexa.data.models.SyncedCalendarEvent
import com.costheta.cortexa.data.models.SyncedGoogleTask

import com.costheta.cortexa.util.Converters

@Database(
    entities = [
        Timetable::class,
        ClassSession::class,
        SessionException::class,
        Engagement::class,
        EngagementException::class,
        Assignment::class,
        Exam::class,
        TodoItem::class,
        Attachment::class,
        NotesItem::class,
        Subtask::class,
        Action::class,
        Location::class,
        SyncedCalendarEvent::class,
        SyncedGoogleTask::class
    ],
    version = 10, // Keep version 1 for now. If you modify existing table schemas later,
    // you'll need to increment this version and provide a migration.
    exportSchema = false // Set to true to export schema to a JSON file. Recommended for real apps.
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun timetableDao(): TimetableDao
    abstract fun engagementDao(): EngagementDao
    abstract fun todoDao(): TodoDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun notesDao(): NotesDao
    abstract fun examDao(): ExamDao
    abstract fun assignmentDao(): AssignmentDao
    abstract fun subtaskDao(): SubtaskDao
    abstract fun actionDao(): ActionDao
    abstract fun locationDao(): LocationDao
    abstract fun syncedCalendarEventDao(): SyncedCalendarEventDao
    abstract fun syncedGoogleTaskDao(): SyncedGoogleTaskDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "timetable_database"
                )
                    // If you increment the version in the future, you will need to add .addMigrations() here
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}