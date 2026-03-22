package com.costheta.cortexa.test

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.costheta.cortexa.R
import com.costheta.cortexa.data.db.AppDatabase
import com.costheta.cortexa.data.db.dao.LocationDao
import com.costheta.cortexa.data.models.Location
import com.costheta.cortexa.util.location.LocationPickerView
import com.costheta.cortexa.util.location.MapPickerActivity
import kotlinx.coroutines.launch

class TestMapPickerActivity : AppCompatActivity() {

    private lateinit var launchMapPickerButton: Button
    private lateinit var resultTextViewDirect: TextView
    private lateinit var testLocationPickerView: LocationPickerView
    private lateinit var resultTextViewComponent: TextView

    private lateinit var locationDao: LocationDao

    // Define a unique event type and ID for testing purposes
    private val TEST_EVENT_TYPE = "TestEvent"
    private val TEST_EVENT_REF_ID = 999L // A dummy ID for testing

    // Launcher for the direct MapPickerActivity call
    private val directMapPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.let { intent ->
                val locationId = intent.getLongExtra(MapPickerActivity.EXTRA_LOCATION_ID, -1L).takeIf { it != -1L }
                val userFriendlyName = intent.getStringExtra(MapPickerActivity.EXTRA_USER_FRIENDLY_NAME)
                val geocodedAddress = intent.getStringExtra(MapPickerActivity.EXTRA_GEOCODED_ADDRESS)
                val latitude = intent.getDoubleExtra(MapPickerActivity.EXTRA_LATITUDE, 0.0)
                val longitude = intent.getDoubleExtra(MapPickerActivity.EXTRA_LONGITUDE, 0.0)

                if (geocodedAddress != null && latitude != 0.0 && longitude != 0.0) {
                    val resultText = "ID: $locationId\nName: $userFriendlyName\nAddress: $geocodedAddress\nLat: $latitude, Long: $longitude"
                    resultTextViewDirect.text = "Direct Result:\n$resultText"
                    Toast.makeText(this, "Location selected directly!", Toast.LENGTH_SHORT).show()
                } else {
                    resultTextViewDirect.text = "Direct Result: No location data returned."
                    Toast.makeText(this, "MapPicker cancelled or failed (direct).", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            resultTextViewDirect.text = "Direct Result: MapPicker cancelled or failed."
            Toast.makeText(this, "MapPicker cancelled or failed (direct).", Toast.LENGTH_SHORT).show()
        }
    }

    // Launcher for the LocationPickerView component's internal MapPickerActivity call
    // This launcher is passed to the LocationPickerView itself
    private val componentMapPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        // Delegate the result handling to the LocationPickerView
        if (result.resultCode == RESULT_OK) {
            result.data?.let { intent ->
                val locationId = intent.getLongExtra(MapPickerActivity.EXTRA_LOCATION_ID, -1L).takeIf { it != -1L }
                val userFriendlyName = intent.getStringExtra(MapPickerActivity.EXTRA_USER_FRIENDLY_NAME)
                val geocodedAddress = intent.getStringExtra(MapPickerActivity.EXTRA_GEOCODED_ADDRESS)
                val latitude = intent.getDoubleExtra(MapPickerActivity.EXTRA_LATITUDE, 0.0)
                val longitude = intent.getDoubleExtra(MapPickerActivity.EXTRA_LONGITUDE, 0.0)
                val eventType = intent.getStringExtra(MapPickerActivity.EXTRA_EVENT_TYPE) ?: ""
                val eventRefId = intent.getLongExtra(MapPickerActivity.EXTRA_EVENT_REF_ID, -1L)

                if (geocodedAddress != null && latitude != 0.0 && longitude != 0.0 && eventRefId != -1L) {
                    val selectedLocation = Location(
                        locationId = locationId,
                        userFriendlyName = userFriendlyName ?: "",
                        geocodedAddress = geocodedAddress,
                        latitude = latitude,
                        longitude = longitude,
                        eventType = eventType,
                        eventRefId = eventRefId
                    )
                    testLocationPickerView.handleMapPickerResult(selectedLocation)
                    Toast.makeText(this, "Location selected via component!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "MapPicker cancelled or failed (component).", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(this, "MapPicker cancelled or failed (component).", Toast.LENGTH_SHORT).show()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.test_map_picker_activity)

        launchMapPickerButton = findViewById(R.id.launchMapPickerButton)
        resultTextViewDirect = findViewById(R.id.resultTextViewDirect)
        testLocationPickerView = findViewById(R.id.testLocationPickerView)
        resultTextViewComponent = findViewById(R.id.resultTextViewComponent)

        locationDao = AppDatabase.getDatabase(applicationContext).locationDao()

        // --- Test direct MapPickerActivity launch ---
        launchMapPickerButton.setOnClickListener {
            val intent = Intent(this, MapPickerActivity::class.java).apply {
                // Pass some dummy data for initial display if needed
                // For a new selection, you might not pass any initial location data
                putExtra(MapPickerActivity.EXTRA_EVENT_TYPE, TEST_EVENT_TYPE)
                putExtra(MapPickerActivity.EXTRA_EVENT_REF_ID, TEST_EVENT_REF_ID)
                // If you want to test editing an existing location directly:
                // putExtra(MapPickerActivity.EXTRA_LATITUDE, 34.0522)
                // putExtra(MapPickerActivity.EXTRA_LONGITUDE, -118.2437)
                // putExtra(MapPickerActivity.EXTRA_USER_FRIENDLY_NAME, "Los Angeles")
                // putExtra(MapPickerActivity.EXTRA_GEOCODED_ADDRESS, "Los Angeles, CA, USA")
            }
            directMapPickerLauncher.launch(intent)
        }

        // --- Test LocationPickerView component ---
        // Initialize the component
        testLocationPickerView.initialize(
            eventType = TEST_EVENT_TYPE,
            eventRefId = TEST_EVENT_REF_ID,
            lifecycleScope = lifecycleScope,
            mapPickerLauncher = componentMapPickerLauncher // Pass the launcher here
        )

        // Set a listener to update the TextView when the component's location changes
        testLocationPickerView.onLocationChanged = { locationEntry ->
            if (locationEntry != null) {
                val resultText = "ID: ${locationEntry.locationId}\nName: ${locationEntry.userFriendlyName}\nAddress: ${locationEntry.geocodedAddress}\nLat: ${locationEntry.latitude}, Long: ${locationEntry.longitude}"
                resultTextViewComponent.text = "Component Result:\n$resultText"
            } else {
                resultTextViewComponent.text = "Component Result: No location selected."
            }
        }

        // Optional: Pre-populate the LocationPickerView with an existing location from DB for testing edit mode
        // This simulates loading an existing event that already has a location
        lifecycleScope.launch {
            val existingLocation = locationDao.getLocationForEvent(TEST_EVENT_TYPE, TEST_EVENT_REF_ID).collect { location ->
                testLocationPickerView.setCurrentSavedLocation(location)
                testLocationPickerView.setUserFriendlyName(location?.userFriendlyName ?: "")
            }
        }
    }
}
