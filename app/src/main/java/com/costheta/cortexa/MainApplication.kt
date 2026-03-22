package com.costheta.cortexa

import android.app.Application
import com.costheta.cortexa.data.db.AppDatabase

/**
 * Custom Application class to provide a singleton instance of the database.
 * This is crucial for accessing the database from background workers.
 */
class MainApplication : Application() {
    // Using by lazy so the database and repository are only created when they're needed
    // rather than when the application starts.
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
}
