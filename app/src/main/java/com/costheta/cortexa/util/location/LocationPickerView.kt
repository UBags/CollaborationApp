package com.costheta.cortexa.util.location

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.LifecycleCoroutineScope
import com.costheta.cortexa.R
import com.costheta.cortexa.data.db.AppDatabase
import com.costheta.cortexa.data.db.dao.LocationDao
import com.costheta.cortexa.data.models.Location
import com.costheta.cortexa.util.keyboard.DisappearingKeyboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Custom UI component for capturing and displaying location/venue information,
 * including integration with a map picker.
 *
 * It consists of a text field for a user-friendly location name and a map icon.
 * Clicking the map icon launches a MapPickerActivity to select a precise location.
 *
 * @property eventType The type of event this location picker is associated with (e.g., "Exam", "ClassSession").
 * @property eventRefId The ID of the event this location picker is associated with.
 * @property lifecycleScope The CoroutineScope from the hosting Activity/Fragment for database operations.
 * @property mapPickerLauncher An ActivityResultLauncher to handle results from MapPickerActivity.
 */
class LocationPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private lateinit var locationNameEditText: EditText
    private lateinit var mapIconImageView: ImageButton

    private lateinit var locationDao: LocationDao

    // Callbacks to notify the host activity about location changes
    var onLocationChanged: ((Location?) -> Unit)? = null

    // Internal state for the currently associated location
    private var currentSavedLocation: Location? = null

    // Variables to be set by the host activity
    private var eventType: String = ""
    private var eventRefId: Long = -1L
    private var lifecycleScope: LifecycleCoroutineScope? = null
    private var mapPickerLauncher: ActivityResultLauncher<Intent>? = null

    companion object {
        private const val TAG = "LocationPickerView"
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.location_picker_view, this, true)
        orientation = HORIZONTAL

        locationNameEditText = findViewById(R.id.locationNameEditText)
        mapIconImageView = findViewById(R.id.mapIconImageView)
        DisappearingKeyboard.getInstance().register(locationNameEditText)

        locationDao = AppDatabase.getDatabase(context).locationDao() // Initialize DAO

        setupListeners()
    }

    /**
     * Initializes the LocationPickerView with necessary event context and launchers.
     * This method MUST be called by the hosting Activity/Fragment after inflation.
     *
     * @param eventType The type of the event (e.g., "Exam", "ClassSession").
     * @param eventRefId The ID of the event.
     * @param lifecycleScope The LifecycleCoroutineScope of the host.
     * @param mapPickerLauncher The ActivityResultLauncher for MapPickerActivity.
     */
    fun initialize(
        eventType: String,
        eventRefId: Long,
        lifecycleScope: LifecycleCoroutineScope,
        mapPickerLauncher: ActivityResultLauncher<Intent>
    ) {
        this.eventType = eventType
        this.eventRefId = eventRefId
        this.lifecycleScope = lifecycleScope
        this.mapPickerLauncher = mapPickerLauncher

        loadLocationForEvent() // Load existing location if any
    }

    /**
     * Sets up click listeners for the map icon.
     */
    private fun setupListeners() {
        mapIconImageView.setOnClickListener {
            launchMapPicker()
        }
    }

    /**
     * Loads the existing location for the associated event from the database.
     */
    private fun loadLocationForEvent() {
        if (eventRefId != -1L && lifecycleScope != null) {
            lifecycleScope?.launch {
                locationDao.getLocationForEvent(eventType, eventRefId).collect { location ->
                    withContext(Dispatchers.Main) {
                        currentSavedLocation = location
                        locationNameEditText.setText(location?.userFriendlyName ?: "")
                        onLocationChanged?.invoke(location)
                    }
                }
            }
        }
    }

    /**
     * Launches the MapPickerActivity.
     * Passes existing location data if available for pre-populating the map.
     */
    private fun launchMapPicker() {
        val intent = Intent(context, MapPickerActivity::class.java).apply {
            putExtra(MapPickerActivity.EXTRA_EVENT_TYPE, eventType)
            putExtra(MapPickerActivity.EXTRA_EVENT_REF_ID, eventRefId)
            putExtra(MapPickerActivity.EXTRA_USER_FRIENDLY_NAME, getUserFriendlyName())

            currentSavedLocation?.let {
                // Only pass location details if latitude and longitude are valid
                if (it.latitude != 0.0 || it.longitude != 0.0) {
                    putExtra(MapPickerActivity.EXTRA_LOCATION_ID, it.locationId)
                    putExtra(MapPickerActivity.EXTRA_GEOCODED_ADDRESS, it.geocodedAddress)
                    putExtra(MapPickerActivity.EXTRA_LATITUDE, it.latitude)
                    putExtra(MapPickerActivity.EXTRA_LONGITUDE, it.longitude)
                }
            }
        }
        mapPickerLauncher?.launch(intent)
    }

    /**
     * Handles the result from MapPickerActivity.
     * Updates the UI and the internal state with the selected location.
     *
     * @param location The Location returned from MapPickerActivity. Null if cancelled.
     */
    fun handleMapPickerResult(location: Location?) {
        currentSavedLocation = location
        // The returned location's userFriendlyName is authoritative. Set it.
        locationNameEditText.setText(location?.userFriendlyName ?: "")
        onLocationChanged?.invoke(location) // Notify host of updated location
    }

    /**
     * Retrieves the current user-friendly name entered in the text field.
     * @return The user-friendly name string.
     */
    fun getUserFriendlyName(): String {
        return locationNameEditText.text.toString().trim()
    }

    /**
     * Retrieves the currently saved Location.
     * @return The Location object, or null if no location is selected/saved.
     */
    fun getSavedLocation(): Location? {
        return currentSavedLocation
    }

    /**
     * Sets the user-friendly name in the text field.
     * This can be used to pre-populate the field from an external source.
     * @param name The name to set.
     */
    fun setUserFriendlyName(name: String?) {
        locationNameEditText.setText(name ?: "")
    }

    /**
     * Sets the current saved location. This is useful when loading an existing event.
     * @param location The Location to set as the current saved location.
     */
    fun setCurrentSavedLocation(location: Location?) {
        currentSavedLocation = location
        locationNameEditText.setText(location?.userFriendlyName ?: "")
    }

    /**
     * Clears the selected location and the text field.
     */
    fun clearLocation() {
        currentSavedLocation = null
        locationNameEditText.setText("")
        onLocationChanged?.invoke(null)
    }

    /**
     * Checks if the current state of the LocationPickerView has unsaved changes compared to the initial state.
     * This compares the user-friendly name and the presence/content of the saved map location.
     *
     * @param initialUserFriendlyName The user-friendly name loaded initially.
     * @param initialMapLocation The Location loaded initially.
     * @return True if there are unsaved changes, false otherwise.
     */
    fun hasUnsavedChanges(initialUserFriendlyName: String?, initialMapLocation: Location?): Boolean {
        val currentUserFriendlyName = getUserFriendlyName()
        val currentMapLocation = getSavedLocation()
        var changed = false

        // 1. Compare user-friendly names
        if (currentUserFriendlyName != (initialUserFriendlyName ?: "")) {
            Log.d(TAG, "hasUnsavedChanges: TRUE -> Name changed. Initial: '${initialUserFriendlyName ?: ""}', Current: '$currentUserFriendlyName'")
            changed = true
        }

        // 2. Compare map locations
        if (initialMapLocation == null && currentMapLocation != null) {
            Log.d(TAG, "hasUnsavedChanges: TRUE -> Location added. Initial was null, Current is not.")
            changed = true
        }
        if (initialMapLocation != null && currentMapLocation == null) {
            Log.d(TAG, "hasUnsavedChanges: TRUE -> Location removed. Initial was not null, Current is null.")
            changed = true
        }

        if (initialMapLocation != null && currentMapLocation != null) {
            if (initialMapLocation.latitude != currentMapLocation.latitude ||
                initialMapLocation.longitude != currentMapLocation.longitude) {
                Log.d(TAG, "hasUnsavedChanges: TRUE -> Coordinates changed.")
                Log.d(TAG, "  Initial Lat: ${initialMapLocation.latitude}, Current Lat: ${currentMapLocation.latitude}")
                Log.d(TAG, "  Initial Lng: ${initialMapLocation.longitude}, Current Lng: ${currentMapLocation.longitude}")
                changed = true
            }
        }

        if (!changed) {
            Log.d(TAG, "hasUnsavedChanges: FALSE -> No changes detected.")
        }
        return changed
    }
}
