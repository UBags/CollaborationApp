package com.costheta.cortexa.util.uirefresh

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

/**
 * An abstract AppCompatActivity that provides a mechanism to periodically trigger a data refresh.
 * This optimized version uses a single Handler and a single Runnable instance for efficiency.
 *
 * @param refreshIntervalMillis The interval in milliseconds at which the UI should refresh.
 * Defaults to 60,000 milliseconds (1 minute).
 */
abstract class PeriodicallyRefreshAppCompatActivity(
    private val refreshIntervalMillis: Long = 60 * 1000L
) : AppCompatActivity() {

    // Use 'by lazy' to initialize the Handler only when it's first accessed.
    private val uiUpdateHandler: Handler by lazy { Handler(Looper.getMainLooper()) }

    // A single, reusable Runnable instance.
    private lateinit var uiUpdateRunnable: Runnable

    /**
     * Abstract method for subclasses to provide their RecyclerView.
     */
    protected abstract fun getRecyclerView(): RecyclerView

    /**
     * Abstract method for subclasses to provide their ListAdapter.
     */
    protected abstract fun getAdapter(): ListAdapter<*, *>

    /**
     * Abstract method for subclasses to implement their data loading and UI refresh logic.
     */
    protected abstract fun triggerDataRefresh()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize the single Runnable instance here.
        uiUpdateRunnable = Runnable {
            triggerDataRefresh()
            // Schedule the same instance to run again.
            uiUpdateHandler.postDelayed(uiUpdateRunnable, refreshIntervalMillis)
        }
    }

    override fun onResume() {
        super.onResume()
        // Start the periodic UI update when the activity is resumed.
        // removeCallbacks is called first to prevent duplicate posts if onResume is called multiple times.
        uiUpdateHandler.removeCallbacks(uiUpdateRunnable)
        uiUpdateHandler.post(uiUpdateRunnable)
    }

    override fun onPause() {
        super.onPause()
        // Stop the periodic UI update when the activity is paused.
        uiUpdateHandler.removeCallbacks(uiUpdateRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Ensure any pending callbacks are removed to prevent memory leaks.
        uiUpdateHandler.removeCallbacks(uiUpdateRunnable)
    }
}
