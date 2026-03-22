package com.costheta.cortexa.util.image2pdf

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.costheta.cortexa.R // Import your app's R file
import android.os.Build // Import Build for version check
import androidx.activity.OnBackPressedCallback // Explicitly import OnBackPressedCallback

class ImageEditorActivity : AppCompatActivity() {

    private lateinit var quadrilateralImageView: QuadrilateralImageView
    private lateinit var applyEditsButton: Button
    private lateinit var brightnessSeekBar: SeekBar
    private lateinit var contrastSeekBar: SeekBar
    private lateinit var brightnessValueTextView: TextView
    private lateinit var contrastValueTextView: TextView

    private var originalImageUri: Uri? = null // This will now be Image 2 URI (already transformed and cleaned)
    private var baseBitmapForEditing: Bitmap? = null // This will be Image 2
    private var currentPreviewBitmap: Bitmap? = null // To hold the bitmap currently shown in preview (Image 3 with brightness/contrast)

    // Default values for brightness and contrast
    private var currentBrightness: Float = 0f // -255 to 2.55 (scaled from -100 to 100)
    private var currentContrast: Float = 1.0f // 0.0 to 2.0 (scaled from 0 to 200)
    private val TAG : String = "ImageEditorActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.i2pdf_image_editor_activity) // Use the new layout

        quadrilateralImageView = findViewById(R.id.quadrilateral_image_view)
        applyEditsButton = findViewById(R.id.apply_edits_button)
        brightnessSeekBar = findViewById(R.id.brightness_seek_bar)
        contrastSeekBar = findViewById(R.id.contrast_seek_bar)
        brightnessValueTextView = findViewById(R.id.brightness_value_text_view)
        contrastValueTextView = findViewById(R.id.contrast_value_text_view)

        // Retrieve Image 2 URI from the intent
        originalImageUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("image_uri", Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("image_uri")
        }

        // The 'quad_points' passed from ImageToPdfActivity are from the *original* image (Image 1).
        // For this new flow, ImageEditorActivity will draw a *new* quadrilateral on Image 2.
        // So, we don't explicitly use initialQuadPoints to set the quad here, but we keep the
        // intent extra for compatibility or future reference if needed.
        // val initialQuadPoints = intent.getFloatArrayExtra("quad_points")

        if (originalImageUri == null) {
            Toast.makeText(this, "No image to edit.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Load Image 2 (the already transformed and smart-cleaned bitmap)
        try {
            val inputStream = contentResolver.openInputStream(originalImageUri!!)
            baseBitmapForEditing = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (baseBitmapForEditing == null) {
                Toast.makeText(this, "Failed to load image for editing.", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            // Set Image 2 to the image view.
            quadrilateralImageView.setImageBitmap(baseBitmapForEditing)
            // Explicitly reset and draw a new quadrilateral for fine-tuning on Image 2
            quadrilateralImageView.resetQuadrilateral()
            quadrilateralImageView.setDrawQuadrilateral(true)

            currentPreviewBitmap = baseBitmapForEditing // The first preview is Image 2
        } catch (e: Exception) {
            Log.e(TAG, "Error loading base image for editing: ${e.message}", e)
            Toast.makeText(this, "Error loading image: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupSeekBars()

        applyEditsButton.setOnClickListener {
            returnProcessedImage()
        }

        // Override back button behavior to return the processed image
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d(TAG, "Back button pressed. Returning processed image.")
                returnProcessedImage() // Save and return the current state of the image (Image 3)
            }
        })
    }

    private fun setupSeekBars() {
        // Brightness: -100 to +100 (mapping to -255 to 255 roughly)
        brightnessSeekBar.max = 200 // -100 to +100 range
        brightnessSeekBar.progress = 100 // Center at 0 brightness
        brightnessValueTextView.text = "Brightness: 0"

        brightnessSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentBrightness = (progress - 100) * 2.55f // Scale to -255 to 255
                brightnessValueTextView.text = "Brightness: ${currentBrightness.toInt()}"
                applyLivePreview() // Apply live preview
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Contrast: 0 to 200 (mapping to 0.0 to 2.0)
        contrastSeekBar.max = 200 // 0 to 200 for 0.0 to 2.0
        contrastSeekBar.progress = 100 // Center at 1.0 contrast
        contrastValueTextView.text = "Contrast: 1.0"

        contrastSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentContrast = progress / 100.0f // Scale to 0.0 to 2.0
                contrastValueTextView.text = "Contrast: %.1f".format(currentContrast)
                applyLivePreview() // Apply live preview
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    /**
     * Applies the current brightness and contrast settings to the baseBitmapForEditing (Image 2)
     * and updates the QuadrilateralImageView for live preview.
     * Manages recycling of previous preview bitmaps.
     */
    private fun applyLivePreview() {
        baseBitmapForEditing?.let { baseBitmap ->
            // Recycle the previously displayed preview bitmap if it's not the base
            if (currentPreviewBitmap != null && currentPreviewBitmap != baseBitmap) {
                currentPreviewBitmap?.recycle()
                currentPreviewBitmap = null
            }

            val previewBitmap = ImageUtils.applyBrightnessAndContrast(baseBitmap, currentBrightness, currentContrast)
            if (previewBitmap != null) {
                quadrilateralImageView.setImageBitmap(previewBitmap)
                currentPreviewBitmap = previewBitmap
            } else {
                Log.e(TAG, "Failed to apply live brightness/contrast preview.")
                // If preview fails, revert to base bitmap to avoid blank screen
                quadrilateralImageView.setImageBitmap(baseBitmap)
                currentPreviewBitmap = baseBitmap
            }
        }
    }

    /**
     * Applies the final perspective transform (based on user's new quad),
     * then applies brightness/contrast, saves the resulting Image 3,
     * and returns its URI to the calling activity.
     */
    private fun returnProcessedImage() {
        baseBitmapForEditing?.let { image2Bitmap ->
            val finalQuadPoints = quadrilateralImageView.getQuadrilateralPoints()

            // Define target dimensions for the transformed page (e.g., A4 ratio)
            val targetWidth = 1240 // Example: ~A4 width at 150 DPI
            val targetHeight = 1754 // Example: ~A4 height at 150 DPI

            // Step 1: Apply Perspective Transform to Image 2 using the newly adjusted quad points
            val reTransformedBitmap = ImageUtils.applyPerspectiveTransform(image2Bitmap, finalQuadPoints, targetWidth, targetHeight)

            if (reTransformedBitmap == null) {
                Toast.makeText(this, "Failed to apply final perspective transform.", Toast.LENGTH_SHORT).show()
                setResult(Activity.RESULT_CANCELED)
                finish()
                return
            }

            // Step 2: Apply Brightness and Contrast to the re-transformed bitmap (producing Image 3)
            val finalEditedBitmap = ImageUtils.applyBrightnessAndContrast(reTransformedBitmap, currentBrightness, currentContrast)
            reTransformedBitmap.recycle() // Recycle intermediate bitmap

            if (finalEditedBitmap == null) {
                Toast.makeText(this, "Failed to apply brightness and contrast.", Toast.LENGTH_SHORT).show()
                setResult(Activity.RESULT_CANCELED)
                finish()
                return
            }

            // Step 3: Save Image 3 to a temporary file
            val processedUri = ImageUtils.saveBitmapToFile(this,
                finalEditedBitmap, "final_edited_image_${System.currentTimeMillis()}.jpg")
            finalEditedBitmap.recycle() // Recycle final bitmap after saving

            if (processedUri != null) {
                val resultIntent = Intent().apply {
                    putExtra("processed_image_uri", processedUri)
                }
                setResult(Activity.RESULT_OK, resultIntent)
                Toast.makeText(this, "Image saved and returned!", Toast.LENGTH_SHORT).show()
            } else {
                setResult(Activity.RESULT_CANCELED)
                Toast.makeText(this, "Failed to save processed image.", Toast.LENGTH_SHORT).show()
            }
            // Recycle all remaining bitmaps before finishing
            baseBitmapForEditing?.recycle()
            baseBitmapForEditing = null
            currentPreviewBitmap?.recycle()
            currentPreviewBitmap = null
            finish()
        } ?: run {
            Toast.makeText(this, "No image to save.", Toast.LENGTH_SHORT).show()
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Ensure all bitmaps are recycled when the activity is destroyed
        baseBitmapForEditing?.recycle()
        baseBitmapForEditing = null
        if (currentPreviewBitmap != null && currentPreviewBitmap != baseBitmapForEditing) {
            currentPreviewBitmap?.recycle()
        }
        currentPreviewBitmap = null
    }
}
