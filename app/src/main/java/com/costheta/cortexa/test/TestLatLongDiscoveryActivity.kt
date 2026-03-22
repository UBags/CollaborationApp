package com.costheta.cortexa.test

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.costheta.cortexa.R

class TestLatLongDiscoveryActivity : AppCompatActivity() {

    private lateinit var urlEditText: EditText
    private lateinit var resolveUrlButton: Button
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar

    companion object {
        private const val TAG = "TestLatLongDiscovery"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Make sure you have a layout file named 'test_lat_long_discovery_activity.xml'
        setContentView(R.layout.test_lat_long_discovery_activity)

        urlEditText = findViewById(R.id.urlEditText)
        resolveUrlButton = findViewById(R.id.resolveUrlButton)
        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)

        // Basic WebView setup. JavaScript is needed for Google Maps redirects.
        webView.settings.javaScriptEnabled = true

        webView.webViewClient = object : WebViewClient() {
            // This method is called when the WebView has finished loading a page.
            // This will be the FINAL URL after all HTTP redirects.
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
                resolveUrlButton.isEnabled = true

                if (url != null) {
                    Log.d(TAG, "----------------------------------------------------")
                    Log.d(TAG, "WebView finished loading. Final URL is: $url")
                    Log.d(TAG, "----------------------------------------------------")

                    // Now you can pass this 'url' to your existing parsing logic.
                    // For this test, we'll just show a Toast.
                    Toast.makeText(this@TestLatLongDiscoveryActivity, "Final URL logged!", Toast.LENGTH_LONG).show()

                } else {
                    Log.e(TAG, "WebView finished loading but the URL was null.")
                    Toast.makeText(this@TestLatLongDiscoveryActivity, "Failed to get final URL.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        resolveUrlButton.setOnClickListener {
            val urlToLoad = urlEditText.text.toString().trim()
            if (urlToLoad.isNotEmpty()) {
                progressBar.visibility = View.VISIBLE
                resolveUrlButton.isEnabled = false
                Log.d(TAG, "Starting to load URL: $urlToLoad")
                webView.loadUrl(urlToLoad)
            } else {
                Toast.makeText(this, "Please enter a URL", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
