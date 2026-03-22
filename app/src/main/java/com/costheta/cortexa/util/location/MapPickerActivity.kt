package com.costheta.cortexa.util.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.util.Log
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.costheta.cortexa.R
import com.costheta.cortexa.data.db.AppDatabase
import com.costheta.cortexa.data.db.dao.LocationDao
import com.costheta.cortexa.data.models.Location
import com.costheta.cortexa.util.keyboard.DisappearingKeyboard
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.util.Locale
import java.util.regex.Matcher
import java.util.regex.Pattern

class MapPickerActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var googleMap: GoogleMap
    private lateinit var selectedAddressTextView: TextView
    private lateinit var saveLocationButton: Button
    private lateinit var getDirectionsButton: Button
    private lateinit var locationDao: LocationDao
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var pasteLocationEditText: EditText
    private lateinit var findPastedLocationButton: Button
    private lateinit var helpButton: ImageButton
    private lateinit var urlResolverWebView: WebView
    private lateinit var progressBar: ProgressBar

    private var selectedLatLng: LatLng? = null
    private var selectedAddress: String = ""
    private var userFriendlyName: String = ""
    private var initialUserFriendlyName: String = ""
    private var eventType: String = ""
    private var eventRefId: Long = -1L
    private var existingLocationId: Long? = null
    private var coordinatesFound: Boolean = false

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.d(TAG, "Location permission granted.")
                getDeviceLocation()
            } else {
                Log.d(TAG, "Location permission denied.")
                Toast.makeText(this, R.string.location_permission_denied, Toast.LENGTH_LONG).show()
                moveCameraToDefaultLocation()
            }
        }

    companion object {
        private const val TAG = "MapPickerActivity"
        private const val DEFAULT_ZOOM = 15f
        private val LAT_LNG_PATTERN = Pattern.compile("(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)")
        private val FULL_URL_PATTERN = Pattern.compile("@(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)")
        private val LL_URL_PATTERN = Pattern.compile("[?&]ll=(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)")
        private val PLACE_NAME_PATTERN = Pattern.compile("/place/([^/]+)/@")

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
        setContentView(R.layout.map_picker_activity)

        selectedAddressTextView = findViewById(R.id.selectedAddressTextView)
        saveLocationButton = findViewById(R.id.saveLocationButton)
        getDirectionsButton = findViewById(R.id.getDirectionsButton)
        pasteLocationEditText = findViewById(R.id.pasteLocationEditText)
        findPastedLocationButton = findViewById(R.id.findPastedLocationButton)
        helpButton = findViewById(R.id.helpButton)
        urlResolverWebView = findViewById(R.id.urlResolverWebView)
        progressBar = findViewById(R.id.progressBar)

        DisappearingKeyboard.getInstance().register(pasteLocationEditText)

        selectedAddressTextView.isClickable = false
        selectedAddressTextView.isFocusable = false

        locationDao = AppDatabase.getDatabase(applicationContext).locationDao()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        handleIntentExtras()
        setupUrlResolverWebView()

        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapFragmentContainer) as SupportMapFragment
        mapFragment.getMapAsync(this)

        saveLocationButton.setOnClickListener { saveLocation() }
        getDirectionsButton.setOnClickListener { openGoogleMapsDirections() }
        findPastedLocationButton.setOnClickListener { processPastedLocation() }
        helpButton.setOnClickListener { showHelpDialog() }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupUrlResolverWebView() {
        urlResolverWebView.settings.javaScriptEnabled = true
        urlResolverWebView.webViewClient = object : WebViewClient() {
            // Modified
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // This logic is reverted to the simpler, working version from V1.
                // It resets the UI state and attempts to parse the URL every time a page
                // finishes loading. Intermediate redirects will fail to parse silently,
                // and the final one will succeed.
                progressBar.visibility = View.GONE
                findPastedLocationButton.isEnabled = true

                if (url != null) {
                    Log.d(TAG, "WebView finished loading. Final URL is: $url")
                    parseFullUrl(url)
                } else {
                    Toast.makeText(this@MapPickerActivity, "Could not resolve the URL.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap.setOnCameraIdleListener {
            val centerLatLng = googleMap.cameraPosition.target
            selectedLatLng = centerLatLng
            selectedAddress = String.format(Locale.getDefault(), "Lat: %.6f, Lng: %.6f", centerLatLng.latitude, centerLatLng.longitude)
            if (userFriendlyName.isBlank()) {
                selectedAddressTextView.text = selectedAddress
            }
            getDirectionsButton.visibility = View.VISIBLE
        }

        progressBar.visibility = View.VISIBLE
        if (selectedLatLng != null) {
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(selectedLatLng!!, DEFAULT_ZOOM))
            progressBar.visibility = View.GONE
        } else {
            checkLocationPermissionAndGetDeviceLocation()
        }
    }

    private fun showHelpDialog() {
        val helpHtml = """
            <div style="color:#333333;">
                <p>Follow these steps to import a location:</p>
                <ol>
                    <li>Open the <b>Google Maps</b> app.</li>
                    <li>Find a location by searching or dropping a pin.</li>
                    <li>Tap the <b style="color:#00796B;">Share</b> button for that location.</li>
                    <li>From the options, choose '<b>Copy to clipboard</b>'.</li>
                    <li>Return to this screen, tap in the text box, and <b>paste</b> the copied text.</li>
                    <li>Tap the <b style="color:#00796B;">Find</b> button to move the map.</li>
                </ol>
            </div>
        """.trimIndent()

        val textView = TextView(this).apply {
            text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Html.fromHtml(helpHtml, Html.FROM_HTML_MODE_LEGACY)
            } else {
                @Suppress("DEPRECATION")
                Html.fromHtml(helpHtml)
            }
            setPadding(40, 20, 40, 20)
        }

        val scrollView = ScrollView(this).apply { addView(textView) }
        AlertDialog.Builder(this)
            .setTitle("How to Copy & Paste a Location")
            .setView(scrollView)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun processPastedLocation() {
        val pastedText = pasteLocationEditText.text.toString().trim()
        if (pastedText.isBlank()) {
            Toast.makeText(this, "Please paste a location link or text.", Toast.LENGTH_SHORT).show()
            return
        }

        coordinatesFound = false
        progressBar.visibility = View.VISIBLE
        findPastedLocationButton.isEnabled = false

        when {
            pastedText.contains("maps.app.goo.gl") || pastedText.startsWith("http") -> {
                urlResolverWebView.loadUrl(pastedText)
            }
            else -> parseLatLngText(pastedText)
        }
    }

    // Modified
    private fun parseFullUrl(url: String) {
        var coordsMatcher = FULL_URL_PATTERN.matcher(url)
        var found = coordsMatcher.find()

        // If the first pattern doesn't find a match, try the second one.
        if (!found) {
            coordsMatcher = LL_URL_PATTERN.matcher(url)
            found = coordsMatcher.find()
        }

        // Proceed if either pattern found a match.
        if (found) {
            val nameMatcher = PLACE_NAME_PATTERN.matcher(url)
            val extractedName = if (nameMatcher.find()) {
                try {
                    URLDecoder.decode(nameMatcher.group(1), "UTF-8").replace('+', ' ')
                } catch (e: Exception) { "" }
            } else { "" }
            extractLatLngAndMoveMap(coordsMatcher, extractedName)
        }
        // No 'else' block needed. If parsing fails (e.g., on an intermediate redirect),
        // we do nothing and wait for the next onPageFinished event.
    }


    private fun parseLatLngText(text: String) {
        val matcher = LAT_LNG_PATTERN.matcher(text)
        if (matcher.find()) {
            extractLatLngAndMoveMap(matcher)
        } else {
            Toast.makeText(this, "Could not find valid coordinates in the text.", Toast.LENGTH_LONG).show()
            progressBar.visibility = View.GONE
            findPastedLocationButton.isEnabled = true
        }
    }

    private fun extractLatLngAndMoveMap(matcher: Matcher, extractedName: String = "") {
        try {
            val lat = matcher.group(1)?.toDouble()
            val lng = matcher.group(2)?.toDouble()
            if (lat != null && lng != null) {
                coordinatesFound = true
                val location = LatLng(lat, lng)
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(location, DEFAULT_ZOOM))
                pasteLocationEditText.text.clear()

                if (extractedName.isNotBlank() && initialUserFriendlyName.isBlank()) {
                    userFriendlyName = extractedName
                    selectedAddressTextView.text = extractedName
                }
                // UI state is reset here on success, which is correct.
                progressBar.visibility = View.GONE
                findPastedLocationButton.isEnabled = true
            }
        } catch (e: NumberFormatException) {
            Toast.makeText(this, "Could not parse valid coordinates from text.", Toast.LENGTH_LONG).show()
            progressBar.visibility = View.GONE
            findPastedLocationButton.isEnabled = true
        }
    }

    private fun checkLocationPermissionAndGetDeviceLocation() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                getDeviceLocation()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun getDeviceLocation() {
        Log.d(TAG, "Attempting to get device location...")
        fusedLocationClient.lastLocation.addOnCompleteListener(this) { task ->
            if (task.isSuccessful && task.result != null) {
                val lastKnownLocation = task.result
                Log.d(TAG, "Location found: $lastKnownLocation")
                val currentLatLng = LatLng(lastKnownLocation.latitude, lastKnownLocation.longitude)
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, DEFAULT_ZOOM))
            } else {
                Log.w(TAG, "Current location is null or task failed.")
                moveCameraToDefaultLocation()
            }
            progressBar.visibility = View.GONE
        }
    }

    private fun handleIntentExtras() {
        eventType = intent.getStringExtra(EXTRA_EVENT_TYPE) ?: ""
        eventRefId = intent.getLongExtra(EXTRA_EVENT_REF_ID, -1L)
        existingLocationId = intent.getLongExtra(EXTRA_LOCATION_ID, -1L).takeIf { it != -1L }

        initialUserFriendlyName = intent.getStringExtra(EXTRA_USER_FRIENDLY_NAME) ?: ""
        userFriendlyName = initialUserFriendlyName

        val initialLatitude = intent.getDoubleExtra(EXTRA_LATITUDE, 0.0)
        val initialLongitude = intent.getDoubleExtra(EXTRA_LONGITUDE, 0.0)

        if (initialLatitude != 0.0 || initialLongitude != 0.0) {
            selectedLatLng = LatLng(initialLatitude, initialLongitude)
            selectedAddress = String.format(Locale.getDefault(), "Lat: %.6f, Lng: %.6f", initialLatitude, initialLongitude)
            getDirectionsButton.visibility = View.VISIBLE
        }

        if (userFriendlyName.isNotBlank()) {
            selectedAddressTextView.text = userFriendlyName
        } else {
            selectedAddressTextView.text = getString(R.string.selected_location_placeholder)
        }
    }

    private fun moveCameraToDefaultLocation() {
        val defaultLatLng = LatLng(12.2958, 76.6394) // Mysuru
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLatLng, 12f))
        googleMap.uiSettings.isMyLocationButtonEnabled = false
        progressBar.visibility = View.GONE
    }

    private fun saveLocation() {
        if (selectedLatLng == null) {
            Toast.makeText(this, R.string.select_location_first, Toast.LENGTH_SHORT).show()
            return
        }

        val locationToSave = Location(
            locationId = existingLocationId,
            userFriendlyName = userFriendlyName,
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
