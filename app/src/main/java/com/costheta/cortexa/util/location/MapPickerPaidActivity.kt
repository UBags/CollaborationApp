package com.costheta.cortexa.util.location

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.costheta.cortexa.R
import com.costheta.cortexa.data.db.AppDatabase
import com.costheta.cortexa.data.db.dao.LocationDao
import com.costheta.cortexa.data.models.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import com.google.android.gms.common.api.Status
import com.google.android.libraries.places.api.model.RectangularBounds

class MapPickerPaidActivity : AppCompatActivity(), OnMapReadyCallback,
    GoogleMap.OnMarkerDragListener {

    private lateinit var googleMap: GoogleMap
    private lateinit var selectedAddressTextView: TextView
    private lateinit var saveLocationButton: Button
    private lateinit var getDirectionsButton: Button
    private lateinit var locationDao: LocationDao

    // NEW: FusedLocationProviderClient for getting the device's current location
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var currentMarker: Marker? = null
    private var selectedLatLng: LatLng? = null
    private var selectedAddress: String = ""
    private var userFriendlyName: String = ""

    private var eventType: String = ""
    private var eventRefId: Long = -1L
    private var existingLocationId: Long? = null

    // NEW: ActivityResultLauncher for location permission requests
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // Permission is granted. Continue the action or workflow in your app.
                Log.d(TAG, "Location permission granted.")
                getDeviceLocation()
            } else {
                // Explain to the user that the feature is unavailable because the
                // features requires a permission that the user has denied.
                Log.d(TAG, "Location permission denied.")
                Toast.makeText(this, R.string.location_permission_denied, Toast.LENGTH_LONG).show()
                // Fallback to a default location if permission is denied
                moveCameraToDefaultLocation()
            }
        }

    companion object {
        private const val TAG = "MapPickerActivity"
        private const val DEFAULT_ZOOM = 15f

        const val EXTRA_EVENT_TYPE = "extra_event_type"
        const val EXTRA_EVENT_REF_ID = "extra_event_ref_id"
        const val EXTRA_LOCATION_ID = "extra_location_id"
        const val EXTRA_USER_FRIENDLY_NAME = "extra_user_friendly_name"
        const val EXTRA_GEOCODED_ADDRESS = "extra_geocoded_address"
        const val EXTRA_LATITUDE = "extra_latitude"
        const val EXTRA_LONGITUDE = "extra_longitude"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.map_picker_activity_paid)

        // Initialize UI components
        selectedAddressTextView = findViewById(R.id.selectedAddressTextView)
        saveLocationButton = findViewById(R.id.saveLocationButton)
        getDirectionsButton = findViewById(R.id.getDirectionsButton)
        locationDao = AppDatabase.getDatabase(applicationContext).locationDao()

        // NEW: Initialize the FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Get data from the calling activity
        handleIntentExtras()

        // Initialize map fragment
        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapFragmentContainer) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // NEW: Initialize Places SDK and set up the Autocomplete search fragment
        setupPlacesAutocomplete()

        // Set up button listeners
        saveLocationButton.setOnClickListener { saveLocation() }
        getDirectionsButton.setOnClickListener { openGoogleMapsDirections() }
    }

    /**
     * This callback is triggered when the map is ready to be used.
     */
    @SuppressLint("PotentialBehaviorOverride")
    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap.setOnMarkerDragListener(this)

        // When the camera stops moving (e.g., after a drag), update the address
        googleMap.setOnCameraIdleListener {
            val centerLatLng = googleMap.cameraPosition.target
            selectedLatLng = centerLatLng
            reverseGeocodeLocation(centerLatLng)
            addOrUpdateMarker(centerLatLng, userFriendlyName, "")
        }

        // If an existing location was passed, move the camera there.
        // Otherwise, try to get the user's current location.
        if (selectedLatLng != null) {
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(selectedLatLng!!, DEFAULT_ZOOM))
            addOrUpdateMarker(selectedLatLng!!, userFriendlyName, selectedAddress)
        } else {
            // This is the new flow: check permissions and get current location
            checkLocationPermissionAndGetDeviceLocation()
        }
    }

    // --- NEW: Location and Permission Handling ---

    /**
     * Checks for location permission and requests it if necessary.
     * If permission is already granted, it proceeds to get the device location.
     */
    private fun checkLocationPermissionAndGetDeviceLocation() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                // You can use the API that requires the permission.
                getDeviceLocation()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                // In an educational UI, explain to the user why your app requires this
                // permission for a specific feature to behave as expected.
                // For this example, we'll just request the permission directly.
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            else -> {
                // Directly ask for the permission.
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    /**
     * Gets the most recent location available.
     * Handles the case where permission is not granted.
     */
    @SuppressLint("MissingPermission") // Suppressed because we check permission before calling
    private fun getDeviceLocation() {
        try {
            googleMap.isMyLocationEnabled = true // Show the blue dot for user's location
            googleMap.uiSettings.isMyLocationButtonEnabled = true // Show button to center on user

            val locationResult = fusedLocationClient.lastLocation
            locationResult.addOnCompleteListener(this) { task ->
                if (task.isSuccessful && task.result != null) {
                    // Set the map's camera position to the current location of the device.
                    val lastKnownLocation = task.result
                    selectedLatLng = LatLng(lastKnownLocation.latitude, lastKnownLocation.longitude)
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(selectedLatLng!!, DEFAULT_ZOOM))
                    addOrUpdateMarker(selectedLatLng!!, "Your Location", "")
                    reverseGeocodeLocation(selectedLatLng!!)
                    updateAutocompleteLocationBias(selectedLatLng)
                } else {
                    Log.d(TAG, "Current location is null. Using defaults.")
                    Log.e(TAG, "Exception: %s", task.exception)
                    moveCameraToDefaultLocation()
                }
            }
        } catch (e: SecurityException) {
            Log.e("Exception: %s", e.message, e)
        }
    }

    // --- NEW: Places Autocomplete Search Handling ---

    /**
     * Initializes the Google Places SDK and sets up the Autocomplete search fragment.
     */
    private fun setupPlacesAutocomplete() {
        // Initialize Places.
        // Be sure to have your API key in AndroidManifest.xml
        val apiKey = try {
            packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
                .metaData.getString("com.google.android.geo.API_KEY")
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Failed to load meta-data, NameNotFound: " + e.message)
            null
        }

        if (apiKey.isNullOrEmpty() || apiKey == "YOUR_API_KEY") {
            Toast.makeText(this, "Places API Key not found in AndroidManifest.xml", Toast.LENGTH_LONG).show()
            Log.e(TAG, "API Key not found for Places SDK. Please add it to AndroidManifest.xml")
            return
        }

        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, apiKey)
        }

        val autocompleteFragment =
            supportFragmentManager.findFragmentById(R.id.autocomplete_fragment_holder)
                    as AutocompleteSupportFragment

        autocompleteFragment.setPlaceFields(listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG, Place.Field.ADDRESS))
        autocompleteFragment.setHint(getString(R.string.search_a_place))

        // Set initial location bias if we already have a location
        updateAutocompleteLocationBias(selectedLatLng)

        autocompleteFragment.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(place: Place) {
                Log.i(TAG, "Place selected: ${place.name}, ${place.latLng}")
                selectedLatLng = place.latLng
                selectedAddress = place.address ?: ""
                userFriendlyName = place.name ?: ""

                if (selectedLatLng != null) {
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(selectedLatLng!!, DEFAULT_ZOOM))
                }
            }

            override fun onError(status: Status) {
                // This is where the error toast likely comes from.
                // We log it for debugging instead of showing a generic toast.
                Log.e(TAG, "An error occurred during place selection: ${status.statusMessage}")
                if (status.isCanceled) return
                // A more informative toast for the user
                Toast.makeText(this@MapPickerPaidActivity, "Error finding place. Check connection or API key.", Toast.LENGTH_LONG).show()
            }
        })
    }

    /**
     * NEW: Updates the Autocomplete fragment to prioritize results near a specific location.
     * @param location The LatLng to bias the search results towards.
     */
    private fun updateAutocompleteLocationBias(location: LatLng?) {
        if (location == null) return

        val autocompleteFragment =
            supportFragmentManager.findFragmentById(R.id.autocomplete_fragment_holder)
                    as? AutocompleteSupportFragment ?: return

        // Create a rectangular boundary around the location.
        // This biases results to a 100km x 100km area (50km in each direction).
        val latRadian = Math.toRadians(location.latitude)
        val degLatKm = 110.574
        val degLngKm = 111.320 * Math.cos(latRadian)
        val latDelta = 50.0 / degLatKm
        val lngDelta = 50.0 / degLngKm

        val southwest = LatLng(location.latitude - latDelta, location.longitude - lngDelta)
        val northeast = LatLng(location.latitude + latDelta, location.longitude + lngDelta)

        autocompleteFragment.setLocationBias(RectangularBounds.newInstance(southwest, northeast))
        Log.d(TAG, "Autocomplete location bias updated to around: $location")
    }

    // --- Existing and Modified Logic ---

    private fun handleIntentExtras() {
        eventType = intent.getStringExtra(EXTRA_EVENT_TYPE) ?: ""
        eventRefId = intent.getLongExtra(EXTRA_EVENT_REF_ID, -1L)
        existingLocationId = intent.getLongExtra(EXTRA_LOCATION_ID, -1L).takeIf { it != -1L }
        userFriendlyName = intent.getStringExtra(EXTRA_USER_FRIENDLY_NAME) ?: ""
        val initialGeocodedAddress = intent.getStringExtra(EXTRA_GEOCODED_ADDRESS)
        val initialLatitude = intent.getDoubleExtra(EXTRA_LATITUDE, 0.0)
        val initialLongitude = intent.getDoubleExtra(EXTRA_LONGITUDE, 0.0)

        if (existingLocationId != null && initialLatitude != 0.0 && initialLongitude != 0.0) {
            selectedLatLng = LatLng(initialLatitude, initialLongitude)
            selectedAddress = initialGeocodedAddress ?: ""
            selectedAddressTextView.text = selectedAddress
            getDirectionsButton.visibility = View.VISIBLE
        } else {
            selectedAddressTextView.text = getString(R.string.selected_location_placeholder)
        }
    }

    /**
     * Fallback method to move the camera to a default location (e.g., Googleplex)
     * when the user's location cannot be determined.
     */
    private fun moveCameraToDefaultLocation() {
        val defaultLatLng = LatLng(37.4221, -122.0841) // Googleplex
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLatLng, 10f))
        googleMap.uiSettings.isMyLocationButtonEnabled = false
    }

    override fun onMarkerDragStart(marker: Marker) {
        selectedAddressTextView.text = getString(R.string.fetching_address)
    }

    override fun onMarkerDrag(marker: Marker) {
        // Not used to avoid excessive geocoding calls
    }

    override fun onMarkerDragEnd(marker: Marker) {
        selectedLatLng = marker.position
        googleMap.animateCamera(CameraUpdateFactory.newLatLng(selectedLatLng!!))
        reverseGeocodeLocation(selectedLatLng!!)
    }

    private fun addOrUpdateMarker(latLng: LatLng, title: String, snippet: String) {
        currentMarker?.remove()
        currentMarker = googleMap.addMarker(
            MarkerOptions()
                .position(latLng)
                .title(title.ifBlank { "Selected Location" })
                .snippet(snippet)
                .draggable(true)
        )
        // No need to show info window immediately, as address is in the bottom sheet
    }

    private fun reverseGeocodeLocation(latLng: LatLng) {
        selectedAddressTextView.text = getString(R.string.fetching_address)
        getDirectionsButton.visibility = View.GONE

        val geocoder = Geocoder(this, Locale.getDefault())

        // Use modern asynchronous API for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1) { addresses ->
                runOnUiThread { handleGeocodeResult(addresses) }
            }
        } else {
            // Use legacy synchronous API for older versions
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
                    withContext(Dispatchers.Main) {
                        handleGeocodeResult(addresses ?: emptyList())
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Geocoder service not available: ${e.message}")
                    runOnUiThread { handleGeocodeError() }
                }
            }
        }
    }

    private fun handleGeocodeResult(addresses: List<Address>) {
        if (addresses.isNotEmpty()) {
            val address = addresses[0]
            val addressText = address.getAddressLine(0)
            selectedAddress = addressText
            selectedAddressTextView.text = addressText
            currentMarker?.snippet = addressText
            getDirectionsButton.visibility = View.VISIBLE
        } else {
            selectedAddress = ""
            selectedAddressTextView.text = getString(R.string.no_address_found)
            currentMarker?.snippet = getString(R.string.no_address_found)
        }
    }

    private fun handleGeocodeError() {
        selectedAddress = ""
        selectedAddressTextView.text = getString(R.string.geocoder_error)
        currentMarker?.snippet = getString(R.string.geocoder_error)
    }

    private fun saveLocation() {
        if (selectedLatLng == null || selectedAddress.isBlank()) {
            Toast.makeText(this, R.string.select_location_first, Toast.LENGTH_SHORT).show()
            return
        }

        val finalUserFriendlyName = userFriendlyName.ifBlank { selectedAddress }

        val locationToSave = Location(
            locationId = existingLocationId,
            userFriendlyName = finalUserFriendlyName,
            geocodedAddress = selectedAddress,
            latitude = selectedLatLng!!.latitude,
            longitude = selectedLatLng!!.longitude,
            eventType = eventType,
            eventRefId = eventRefId
        )

        lifecycleScope.launch {
            val savedId = withContext(Dispatchers.IO) {
                if (existingLocationId == null) {
                    locationDao.insertLocation(locationToSave)
                } else {
                    locationDao.updateLocation(locationToSave)
                    existingLocationId!!
                }
            }
            returnResult(locationToSave.copy(locationId = savedId))
        }
    }

    private fun returnResult(location: Location) {
        val resultIntent = Intent().apply {
            putExtra(EXTRA_LOCATION_ID, location.locationId)
            putExtra(EXTRA_USER_FRIENDLY_NAME, location.userFriendlyName)
            putExtra(EXTRA_GEOCODED_ADDRESS, location.geocodedAddress)
            putExtra(EXTRA_LATITUDE, location.latitude)
            putExtra(EXTRA_LONGITUDE, location.longitude)
            putExtra(EXTRA_EVENT_TYPE, location.eventType)
            putExtra(EXTRA_EVENT_REF_ID, location.eventRefId)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    private fun openGoogleMapsDirections() {
        if (selectedLatLng == null) {
            Toast.makeText(this, R.string.no_location_for_directions, Toast.LENGTH_SHORT).show()
            return
        }
        val gmmIntentUri = "google.navigation:q=${selectedLatLng!!.latitude},${selectedLatLng!!.longitude}&mode=d".toUri()
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
            setPackage("com.google.android.apps.maps")
        }
        if (mapIntent.resolveActivity(packageManager) != null) {
            startActivity(mapIntent)
        } else {
            Toast.makeText(this, R.string.google_maps_not_installed, Toast.LENGTH_LONG).show()
        }
    }
}
