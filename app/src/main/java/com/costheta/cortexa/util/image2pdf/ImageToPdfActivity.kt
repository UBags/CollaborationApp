package com.costheta.cortexa.util.image2pdf

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.costheta.cortexa.R // Import your app's R file to access resources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.widget.FrameLayout // Import FrameLayout
import androidx.lifecycle.lifecycleScope
import androidx.core.graphics.scale
import android.widget.CheckBox // Import CheckBox
import android.widget.TextView
import com.google.android.material.textfield.TextInputEditText

class ImageToPdfActivity : AppCompatActivity() {

    // UI Elements
    private lateinit var quadrilateralImageView: QuadrilateralImageView
    private lateinit var headerInstruction: TextView
    private lateinit var addSavePageButton: Button // This button toggles between "Add Page" and "Save Page"
    private lateinit var savePdfButton: Button
    private lateinit var thumbnailsRecyclerView: RecyclerView
    private lateinit var imageContainer: FrameLayout // This is the container for the quadrilateralImageView
    private lateinit var applyDocumentTransformCheckbox: CheckBox // New CheckBox

    // Data
    private val processedImageUris = mutableListOf<Uri>() // Stores URIs of processed images (thumbnails)
    private lateinit var thumbnailAdapter: ThumbnailAdapter
    private var currentPhotoUriForCamera: Uri? = null // Store the content URI provided to the camera

    // State
    private var isEditingCurrentPhoto: Boolean = false // True if a photo is loaded and ready to be processed/edited

    // ActivityResultLauncher for camera intent
    private val takePictureLauncher: ActivityResultLauncher<Uri> =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success: Boolean ->
            if (success) {
                currentPhotoUriForCamera?.let { uri ->
                    // Photo was successfully taken. Load it into the QuadrilateralImageView.
                    try {
                        val bitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(uri))
                        if (bitmap != null) {
                            quadrilateralImageView.setImageBitmap(bitmap)
                            // Explicitly reset and draw the quadrilateral for the new image (Image 1)
                            quadrilateralImageView.resetQuadrilateral()
                            quadrilateralImageView.setDrawQuadrilateral(true)
                            isEditingCurrentPhoto = true // Now in "Save Page" mode
                            addSavePageButton.text = getString(R.string.button_process_chosen_area)
                            headerInstruction.text = getString(R.string.text_mark_picture_area)
                            savePdfButton.isEnabled = false // Disable Save PDF while editing current image
                            Toast.makeText(this, "Photo loaded for editing.", Toast.LENGTH_SHORT).show()
                            Log.d("ImageToPdfActivity", "Photo loaded into ImageView: $uri. Quadrilateral set to visible.")
                        } else {
                            Toast.makeText(this, "Failed to load captured image.", Toast.LENGTH_SHORT).show()
                            Log.e("ImageToPdfActivity", "Bitmap was null for URI: $uri")
                            cleanupFailedCapture() // Discard and prepare for new capture
                        }
                    } catch (e: Exception) {
                        Log.e("ImageToPdfActivity", "Error loading captured image: ${e.message}", e)
                        Toast.makeText(this, "Error loading captured image: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        cleanupFailedCapture() // Discard and prepare for new capture
                    }
                }
            } else {
                // If capture failed or cancelled, clean up and prepare for new capture
                cleanupFailedCapture()
                Toast.makeText(this, "Photo capture cancelled or failed.", Toast.LENGTH_SHORT).show()
                Log.d("ImageToPdfActivity", "Photo capture cancelled or failed.")
            }
        }

    // ActivityResultLauncher for launching ImageEditorActivity and getting result back
    private val editImageLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val processedImageUri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    result.data?.getParcelableExtra("processed_image_uri", Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    result.data?.getParcelableExtra("processed_image_uri")
                }

                if (processedImageUri != null) {
                    processedImageUris.add(processedImageUri)
                    thumbnailAdapter.submitList(processedImageUris.toList()) // Update RecyclerView
                    Toast.makeText(this, "Page added!", Toast.LENGTH_SHORT).show()

                    // Load the newly added image (Image 3) into the main canvas
                    try {
                        val bitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(processedImageUri))
                        if (bitmap != null) {
                            quadrilateralImageView.setImageBitmap(bitmap)
                            quadrilateralImageView.setDrawQuadrilateral(false) // Hide quadrilateral for final page view
                        }
                    } catch (e: Exception) {
                        Log.e("ImageToPdfActivity", "Error loading processed image into main view: ${e.message}", e)
                    }

                    // Reset UI for next page capture
                    isEditingCurrentPhoto = false // Not in editing mode for main canvas
                    addSavePageButton.text = getString(R.string.button_add_page)
                    headerInstruction.text = getString(R.string.picture_preview_text)
                    savePdfButton.isEnabled = true // Enable Save PDF once at least one image is added
                    currentPhotoUriForCamera = null // Clear URI for next capture
                    applyDocumentTransformCheckbox.isChecked = true // Reset checkbox to checked
                    Log.d("ImageToPdfActivity", "Image received from editor and added. UI reset.")
                } else {
                    Toast.makeText(this, "Failed to get processed image from editor.", Toast.LENGTH_SHORT).show()
                    Log.e("ImageToPdfActivity", "Processed image URI was null from editor.")
                    // If no image returned, revert to "Add Page" state
                    isEditingCurrentPhoto = false
                    addSavePageButton.text = getString(R.string.button_add_page)
                    // If there are existing images, keep savePdfButton enabled
                    savePdfButton.isEnabled = processedImageUris.isNotEmpty()
                    applyDocumentTransformCheckbox.isChecked = true // Reset checkbox to checked
                }
            } else {
                // User cancelled editing in ImageEditorActivity
                Toast.makeText(this, "Image editing cancelled.", Toast.LENGTH_SHORT).show()
                Log.d("ImageToPdfActivity", "Image editing cancelled.")

                // If editing was cancelled, the user is back to ImageToPdfActivity.
                // We need to decide what to show in the main canvas.
                // If there were processed images before launching editor, show the last one.
                // Otherwise, clear the canvas and prepare for a new capture.
                if (processedImageUris.isNotEmpty()) {
                    val lastImageUri = processedImageUris.last()
                    try {
                        val bitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(lastImageUri))
                        if (bitmap != null) {
                            quadrilateralImageView.setImageBitmap(bitmap)
                            quadrilateralImageView.setDrawQuadrilateral(false) // Hide quad for previously processed image
                            isEditingCurrentPhoto = false // Not in editing mode
                            addSavePageButton.text = getString(R.string.button_add_page)
                            savePdfButton.isEnabled = true // Can save PDF
                            applyDocumentTransformCheckbox.isChecked = true // Reset checkbox to checked
                        }
                    } catch (e: Exception) {
                        Log.e("ImageToPdfActivity", "Error loading last processed image after editor cancel: ${e.message}", e)
                        quadrilateralImageView.setImageBitmap(null) // Clear if failed
                        quadrilateralImageView.setDrawQuadrilateral(false)
                        isEditingCurrentPhoto = false
                        addSavePageButton.text = getString(R.string.button_add_page)
                        savePdfButton.isEnabled = processedImageUris.isNotEmpty()
                        applyDocumentTransformCheckbox.isChecked = true // Reset checkbox to checked
                    }
                } else {
                    // No processed images, clear canvas and prepare for new capture
                    quadrilateralImageView.setImageBitmap(null)
                    quadrilateralImageView.setDrawQuadrilateral(false)
                    isEditingCurrentPhoto = false
                    addSavePageButton.text = getString(R.string.button_add_page)
                    savePdfButton.isEnabled = false
                    applyDocumentTransformCheckbox.isChecked = true // Reset checkbox to checked
                }
                currentPhotoUriForCamera = null // Ensure this is cleared
            }
        }

    // ActivityResultLauncher for permission requests
    private val requestPermissionsLauncher: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            Log.d("ImageToPdfActivity", "Permissions result: All granted = $allGranted")
            if (allGranted) {
                Toast.makeText(this, "Permissions granted!", Toast.LENGTH_SHORT).show()
                // If permissions are granted, immediately launch camera for the first photo
                dispatchTakePictureIntent()
            } else {
                Toast.makeText(this, "Permissions denied. Cannot proceed.", Toast.LENGTH_LONG).show()
                // If permissions are denied, disable buttons or show a message
                addSavePageButton.isEnabled = false
                savePdfButton.isEnabled = false
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.i2pdf_image_to_pdf_layout_activity) // Use the new layout

        // Initialize UI elements
        quadrilateralImageView = findViewById(R.id.quadrilateral_image_view)
        headerInstruction = findViewById(R.id.header_instruction)
        addSavePageButton = findViewById(R.id.add_save_page_button)
        savePdfButton = findViewById(R.id.save_pdf_button)
        thumbnailsRecyclerView = findViewById(R.id.thumbnails_recycler_view)
        imageContainer = findViewById(R.id.main_image_container) // This is the container for the quadrilateralImageView
        applyDocumentTransformCheckbox = findViewById(R.id.apply_document_transform_checkbox) // Initialize the checkbox

        // Setup Thumbnail RecyclerView
        thumbnailAdapter = ThumbnailAdapter(
            onItemClick = { uri ->
                // Optionally, allow re-editing a thumbnail or viewing it larger
                Toast.makeText(this, "Thumbnail clicked: $uri", Toast.LENGTH_SHORT).show()
            },
            onDeleteClick = { uri ->
                // Remove from list and update adapter
                Log.d("ImageToPdfActivity", "Deleting thumbnail with URI: $uri")
                processedImageUris.remove(uri)
                Log.d("ImageToPdfActivity", "processedImageUris after removal: $processedImageUris")
                val newList = ArrayList(processedImageUris) // Create a fresh list copy
                Log.d("ImageToPdfActivity", "Submitting new list with size: ${newList.size}, contents: $newList")
                thumbnailAdapter.submitList(null) // Clear adapter's currentList
                thumbnailAdapter.submitList(newList) // Submit new list
                Log.d("ImageToPdfActivity", "Adapter currentList size after submit: ${thumbnailAdapter.currentList.size}")
                thumbnailsRecyclerView.recycledViewPool.clear() // Clear recycled views
                thumbnailsRecyclerView.requestLayout() // Force layout refresh
                thumbnailAdapter.notifyDataSetChanged() // Force full refresh
                if (processedImageUris.isEmpty()) {
                    savePdfButton.isEnabled = false
                }
                Toast.makeText(this, "Thumbnail deleted.", Toast.LENGTH_SHORT).show()
                Log.d("ImageToPdfActivity", "Updated processedImageUris size: ${processedImageUris.size}")
            }
        )
        thumbnailsRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        thumbnailsRecyclerView.adapter = thumbnailAdapter

        // Set up button listeners
        addSavePageButton.setOnClickListener {
            Log.d("ImageToPdfActivity", "Add/Save Page button clicked. isEditingCurrentPhoto: $isEditingCurrentPhoto")
            if (isEditingCurrentPhoto) {
                // Currently in "Save Page" mode, process and launch ImageEditorActivity
                processAndLaunchEditor()
            } else {
                // Currently in "Add Page" mode, launch camera
                quadrilateralImageView.setImageBitmap(null) // Clear the current image
                currentPhotoUriForCamera = null
                dispatchTakePictureIntent()
            }
        }

        savePdfButton.setOnClickListener {
            Log.d("ImageToPdfActivity", "Save PDF button clicked. Processed images count: ${processedImageUris.size}")
            if (processedImageUris.isEmpty()) {
                Toast.makeText(this, "No pages to save to PDF!", Toast.LENGTH_SHORT).show()
            } else {
                showSavePdfDialog()
            }
        }

        // Initial permission check and camera launch
        Log.d("ImageToPdfActivity", "onCreate: Calling checkAndRequestPermissions()")
        checkAndRequestPermissions()


        // Override back button behavior
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.d("ImageToPdfActivity", "Back button pressed. isEditingCurrentPhoto: $isEditingCurrentPhoto, Processed images count: ${processedImageUris.size}")
                if (isEditingCurrentPhoto) {
                    // If an image is currently in the main view for initial quadrilateral adjustment,
                    // discard it and go back to camera.
                    Log.d("ImageToPdfActivity", "Discarding current image and re-launching camera.")
                    cleanupFailedCapture() // This clears the current image and resets UI
                    dispatchTakePictureIntent() // Re-launch camera
                } else if (processedImageUris.isNotEmpty()) {
                    // If there are processed images, show the save dialog
                    showSavePdfDialog()
                } else {
                    // If no images processed, just finish the activity
                    Log.d("ImageToPdfActivity", "No processed images. Finishing activity on back press.")
                    finish()
                }
            }
        })
    }

    /**
     * Cleans up the temporary URI if photo capture was cancelled or failed,
     * or if the user discards the current image.
     */
    private fun cleanupFailedCapture() {
        currentPhotoUriForCamera?.let { uri ->
            try {
                contentResolver.delete(uri, null, null)
                Log.d("ImageToPdfActivity", "Cleaned up temporary URI for failed/cancelled photo: $uri")
            } catch (e: Exception) {
                Log.e("ImageToPdfActivity", "Error cleaning up temporary URI: ${e.message}", e)
            }
        }
        currentPhotoUriForCamera = null
        isEditingCurrentPhoto = false
        addSavePageButton.text = getString(R.string.button_add_page)
        quadrilateralImageView.setImageBitmap(null) // Clear the image view
        quadrilateralImageView.setDrawQuadrilateral(false) // Hide quadrilateral
        savePdfButton.isEnabled = processedImageUris.isNotEmpty() // Enable if there are existing pages
        applyDocumentTransformCheckbox.isChecked = true // Reset checkbox to checked
        Log.d("ImageToPdfActivity", "cleanupFailedCapture completed. UI reset. Quadrilateral set to hidden.")
    }

    /**
     * Requests necessary permissions if not already granted.
     */
    private fun checkAndRequestPermissions() {
        // *** PERMISSION FIX ***
        // This logic is now simplified to only request what is strictly necessary for the workflow.
        // 1. CAMERA is always needed.
        // 2. WRITE_EXTERNAL_STORAGE is only needed for saving the final PDF on older Android versions (below API 29).
        // No other storage permissions are required because the app uses its own sandboxed storage for temporary files.

        val permissionsToRequest = mutableListOf<String>()

        // Always check for CAMERA permission.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
            Log.d("ImageToPdfActivity", "Requesting CAMERA permission.")
        }

        // Only check for WRITE_EXTERNAL_STORAGE on devices older than Android 10 (API 29).
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                Log.d("ImageToPdfActivity", "Requesting WRITE_EXTERNAL_STORAGE for legacy device.")
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            // If any permissions are missing, launch the request dialog.
            Log.d("ImageToPdfActivity", "Launching permission request launcher for: ${permissionsToRequest.joinToString()}")
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            // If all necessary permissions are already granted, proceed to launch the camera.
            Log.d("ImageToPdfActivity", "All necessary permissions already granted. Calling dispatchTakePictureIntent().")
            dispatchTakePictureIntent()
        }
    }

    /**
     * Creates a temporary file in the app's private external storage and returns its FileProvider URI.
     * This URI is then passed to the camera app for capturing the image.
     */
    @Throws(IOException::class)
    private fun createTemporaryFileForCamera(): Uri? {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "JPEG_${timeStamp}_"
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)

        if (storageDir == null || !storageDir.exists() && !storageDir.mkdirs()) {
            Log.e("ImageToPdfActivity", "Failed to create or access storage directory: $storageDir")
            throw IOException("Failed to create or access storage directory for camera output.")
        }

        val tempFile = File.createTempFile(fileName, ".jpg", storageDir)
        Log.d("ImageToPdfActivity", "Created temporary file for camera: ${tempFile.absolutePath}")

        val uri = FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.fileprovider",
            tempFile
        )
        Log.d("ImageToPdfActivity", "Generated FileProvider URI for camera: $uri")
        return uri
    }

    /**
     * Launches the camera intent to take a picture.
     */
    private fun dispatchTakePictureIntent() {
        Log.d("ImageToPdfActivity", "Attempting to dispatch take picture intent.")
        val photoUri: Uri? = try {
            createTemporaryFileForCamera()
        } catch (ex: IOException) {
            Log.e("ImageToPdfActivity", "Error creating photo URI: ${ex.message}", ex)
            Toast.makeText(this, "Error creating photo URI.", Toast.LENGTH_SHORT).show()
            null
        }

        photoUri?.also {
            currentPhotoUriForCamera = it
            Log.d("ImageToPdfActivity", "Launching camera with URI: $it")
            takePictureLauncher.launch(it)
        } ?: run {
            Toast.makeText(this, "Failed to prepare camera.", Toast.LENGTH_SHORT).show()
            Log.e("ImageToPdfActivity", "Photo URI was null, failed to prepare camera.")
        }
    }

    /**
     * Processes the current image displayed in QuadrilateralImageView (Image 1),
     * applies perspective transform and optionally smart cleanup to get Image 2,
     * then launches ImageEditorActivity with Image 2 for further adjustments.
     */
    private fun processAndLaunchEditor() {
        Log.d("ImageToPdfActivity", "Processing current image and launching editor.")
        if (!isEditingCurrentPhoto || quadrilateralImageView.drawable == null) {
            Toast.makeText(this, "No image to process or image not ready.", Toast.LENGTH_SHORT).show()
            Log.w("ImageToPdfActivity", "processAndLaunchEditor called but no image to process or not in editing mode.")
            return
        }

        // Disable button and change text during processing
        addSavePageButton.isEnabled = false
        val originalButtonText = addSavePageButton.text.toString() // Store original text
        addSavePageButton.text = getString(R.string.button_processing_saving) // Set new text

        val originalBitmap = (quadrilateralImageView.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
        if (originalBitmap == null) {
            Toast.makeText(this, "Failed to get bitmap from image view.", Toast.LENGTH_SHORT).show()
            Log.e("ImageToPdfActivity", "Original bitmap from ImageView was null.")
            // Re-enable button and revert text if an error occurs early
            addSavePageButton.isEnabled = true
            addSavePageButton.text = originalButtonText
            return
        }

        val quadPoints = quadrilateralImageView.getQuadrilateralPoints()
        Log.d("ImageToPdfActivity", "Quadrilateral points for initial transform: ${quadPoints.joinToString()}")

        val applySmartCleanup = applyDocumentTransformCheckbox.isChecked // Get checkbox state

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Define target dimensions for the transformed page (e.g., A4 ratio)
                val targetWidth = 1240 // Example: ~A4 width at 150 DPI
                val targetHeight = 1754 // Example: ~A4 height at 150 DPI
                Log.d("ImageToPdfActivity", "Target dimensions for initial transform: ${targetWidth}x${targetHeight}")

                // Step 1: Apply Perspective Transform (Image 1 -> Transformed)
                val transformedBitmap = ImageUtils.applyPerspectiveTransform(originalBitmap, quadPoints, targetWidth, targetHeight)
                Log.d("ImageToPdfActivity", "Perspective transform applied. Transformed Bitmap: ${transformedBitmap?.width}x${transformedBitmap?.height}")

                if (transformedBitmap == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ImageToPdfActivity, "Failed to apply perspective transform.", Toast.LENGTH_SHORT).show()
                        addSavePageButton.isEnabled = true
                        addSavePageButton.text = originalButtonText
                    }
                    Log.e("ImageToPdfActivity", "Transformed bitmap was null after perspective transform.")
                    return@launch
                }

                val finalIntermediateBitmap: Bitmap? = if (applySmartCleanup) {
                    // Step 2: Apply Smart Cleanup (Transformed -> Image 2) if checkbox is checked
                    val cleanedBitmap = ImageUtils.applySmartCleanup(transformedBitmap)
                    transformedBitmap.recycle() // Recycle transformed bitmap after cleanup
                    cleanedBitmap
                } else {
                    // If not applying smart cleanup, Transformed is Image 2
                    transformedBitmap
                }

                if (finalIntermediateBitmap == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ImageToPdfActivity, "Failed to process image for editor (cleanup issue).", Toast.LENGTH_SHORT).show()
                        addSavePageButton.isEnabled = true
                        addSavePageButton.text = originalButtonText
                    }
                    Log.e("ImageToPdfActivity", "Final intermediate bitmap was null after conditional cleanup.")
                    return@launch
                }

                // Step 3: Save Image 2 (or transformed-only image) to a temporary file to pass to ImageEditorActivity
                val image2Uri = ImageUtils.saveBitmapToFile(this@ImageToPdfActivity,
                    finalIntermediateBitmap, "image2_for_editor_${System.currentTimeMillis()}.jpg")
                finalIntermediateBitmap.recycle() // Recycle final intermediate bitmap after saving to file

                withContext(Dispatchers.Main) {
                    if (image2Uri != null) {
                        // Launch ImageEditorActivity with Image 2 and the current quad points
                        val intent = Intent(this@ImageToPdfActivity, ImageEditorActivity::class.java).apply {
                            putExtra("image_uri", image2Uri)
                            // Pass the quad points from ImageToPdfActivity to ImageEditorActivity
                            // for potential re-initialization or reference, though ImageEditor
                            // will now draw its own interactive quad on Image 2.
                            putExtra("quad_points", quadPoints) // Still pass original quad points
                        }
                        editImageLauncher.launch(intent)
                        Log.d("ImageToPdfActivity", "Launched ImageEditorActivity with Image 2 URI: $image2Uri")

                        // Reset button state immediately after launching editor
                        addSavePageButton.isEnabled = true
                        addSavePageButton.text = originalButtonText // Revert text
                        // Clear the main image view as editing moves to next activity
                        quadrilateralImageView.setImageBitmap(null)
                        quadrilateralImageView.setDrawQuadrilateral(false)
                        isEditingCurrentPhoto = false // No longer editing in this activity
                        savePdfButton.isEnabled = processedImageUris.isNotEmpty() // Keep enabled if pages exist
                        currentPhotoUriForCamera = null // Clear original camera URI
                    } else {
                        Toast.makeText(this@ImageToPdfActivity, "Failed to prepare image for editor.", Toast.LENGTH_SHORT).show()
                        Log.e("ImageToPdfActivity", "Image 2 URI was null, failed to launch editor.")
                        addSavePageButton.isEnabled = true
                        addSavePageButton.text = originalButtonText
                    }
                }
            } catch (e: Exception) {
                Log.e("ImageToPdfActivity", "Error processing image for editor: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ImageToPdfActivity, "Error preparing image for editor: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    addSavePageButton.isEnabled = true
                    addSavePageButton.text = originalButtonText
                }
            }
        }
    }

    /**
     * Shows a dialog to prompt the user for a PDF file name.
     * Handles unique naming if a file with the same name already exists.
     */
    private fun showSavePdfDialog() {
        Log.d("ImageToPdfActivity", "Showing Save PDF dialog.")
        val dialogView = LayoutInflater.from(this).inflate(R.layout.i2pdf_image_save_pdf_dialog, null)
        val fileNameEditText: TextInputEditText = dialogView.findViewById(R.id.fileNameEditText)

        val defaultFileName = "PDF_${SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.getDefault()).format(Date())}"
        fileNameEditText.setText(defaultFileName)

        AlertDialog.Builder(this)
            // .setTitle(getString(R.string.dialog_save_pdf_title))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.dialog_save_button)) { dialog, _ ->
                var desiredFileName = fileNameEditText.text.toString().trim()
                if (desiredFileName.isBlank()) {
                    desiredFileName = defaultFileName // Fallback to default if empty
                    Log.d("ImageToPdfActivity", "File name was blank, using default: $desiredFileName")
                }

                // Ensure .pdf extension
                if (!desiredFileName.endsWith(".pdf", ignoreCase = true)) {
                    desiredFileName += ".pdf"
                    Log.d("ImageToPdfActivity", "Added .pdf extension. New file name: $desiredFileName")
                }

                // Check for uniqueness and append (n) if needed
                val finalFileName = getUniquePdfFileName(desiredFileName)
                Log.d("ImageToPdfActivity", "Final unique file name for PDF: $finalFileName")
                generatePdfFromImages(finalFileName)
                dialog.dismiss()
            }
            .setNeutralButton(getString(R.string.dialog_exit_without_saving_button)) { dialog, _ ->
                Log.d("ImageToPdfActivity", "Exit without Saving clicked. Finishing activity.")
                dialog.dismiss()
                finish() // Exit the activity without saving
            }
            .setNegativeButton(getString(R.string.dialog_cancel_button)) { dialog, _ ->
                dialog.cancel()
                Log.d("ImageToPdfActivity", "Save PDF dialog cancelled.")
            }
            .show()
    }

    /**
     * Generates a unique PDF file name by appending (n) if a file with the same name exists.
     */
    private fun getUniquePdfFileName(baseFileName: String): String {
        var uniqueFileName = baseFileName
        var counter = 1
        val documentsDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        } else {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        }

        if (documentsDir.exists() && documentsDir.isDirectory && documentsDir.canRead()) {
            val nameWithoutExtension = baseFileName.removeSuffix(".pdf")
            val filesInDir = documentsDir.listFiles()
            if (filesInDir != null) {
                while (filesInDir.any { it.name.equals(uniqueFileName, ignoreCase = true) }) {
                    uniqueFileName = "${nameWithoutExtension}(${counter}).pdf"
                    counter++
                }
            }
        }
        Log.d("ImageToPdfActivity", "Generated unique file name: $uniqueFileName (from base: $baseFileName)")
        return uniqueFileName
    }


    /**
     * Generates a PDF document from the processed images and saves it.
     * @param filename The desired filename for the PDF.
     */
    private fun generatePdfFromImages(filename: String) {
        Log.d("ImageToPdfActivity", "Starting PDF generation for filename: $filename")
        val document = PdfDocument()

        val pageHeight = 842
        val pageWidth = 595

        lifecycleScope.launch(Dispatchers.IO) {
            for ((index, uri) in processedImageUris.withIndex()) {
                Log.d("ImageToPdfActivity", "Processing image $index for PDF: $uri")
                try {
                    val bitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(uri))
                    if (bitmap == null) {
                        Log.e("ImageToPdfActivity", "Failed to decode bitmap from URI: $uri")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@ImageToPdfActivity, "Failed to load image for PDF: $uri", Toast.LENGTH_LONG).show()
                        }
                        continue
                    }

                    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                    val page = document.startPage(pageInfo)
                    val canvas: Canvas = page.canvas

                    val imageWidth = bitmap.width
                    val imageHeight = bitmap.height

                    val scaleX = pageWidth.toFloat() / imageWidth
                    val scaleY = pageHeight.toFloat() / imageHeight
                    val scale = Math.min(scaleX, scaleY)

                    val scaledWidth = (imageWidth * scale).toInt()
                    val scaledHeight = (imageHeight * scale).toInt()

                    val left = (pageWidth - scaledWidth) / 2f
                    val top = (pageHeight - scaledHeight) / 2f

                    canvas.drawBitmap(bitmap.scale(scaledWidth, scaledHeight), left, top, null)

                    document.finishPage(page)
                    bitmap.recycle()
                    Log.d("ImageToPdfActivity", "Image $index processed and added to PDF. Bitmap recycled.")
                } catch (e: IOException) {
                    Log.e("ImageToPdfActivity", "Error processing image $uri for PDF: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ImageToPdfActivity, "Error processing image for PDF: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Log.e("ImageToPdfActivity", "General error processing image $uri for PDF: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ImageToPdfActivity, "General error processing image for PDF: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }

            // Save the PDF to the Documents directory
            val outputStream: FileOutputStream?
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = contentResolver
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS)
                    }
                    val pdfUri: Uri? = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
                    Log.d("ImageToPdfActivity", "Attempting to save PDF via MediaStore to URI: $pdfUri")

                    if (pdfUri != null) {
                        outputStream = resolver.openOutputStream(pdfUri) as FileOutputStream
                        document.writeTo(outputStream)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@ImageToPdfActivity, "PDF saved to Documents/$filename", Toast.LENGTH_LONG).show()
                            Log.d("ImageToPdfActivity", "PDF saved successfully via MediaStore.")
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@ImageToPdfActivity, "Failed to create PDF file URI.", Toast.LENGTH_LONG).show()
                            Log.e("ImageToPdfActivity", "Failed to get PDF file URI from MediaStore.")
                        }
                    }
                } else {
                    val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                    if (!documentsDir.exists()) {
                        documentsDir.mkdirs()
                        Log.d("ImageToPdfActivity", "Created Documents directory: ${documentsDir.absolutePath}")
                    }
                    val file = File(documentsDir, filename)
                    outputStream = FileOutputStream(file)
                    document.writeTo(outputStream)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ImageToPdfActivity, "PDF saved to ${file.absolutePath}", Toast.LENGTH_LONG).show()
                        Log.d("ImageToPdfActivity", "PDF saved successfully to file system (API < Q).")
                    }
                }
            } catch (e: IOException) {
                Log.e("ImageToPdfActivity", "Error saving PDF: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ImageToPdfActivity, "Error saving PDF: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                document.close()
                // Clear all processed images and thumbnails after PDF generation
                withContext(Dispatchers.Main) {
                    processedImageUris.clear()
                    thumbnailAdapter.submitList(emptyList())
                    savePdfButton.isEnabled = false // Disable save PDF button
                    // Keep the main image view ready for a new capture
                    quadrilateralImageView.setImageBitmap(null)
                    quadrilateralImageView.setDrawQuadrilateral(false) // Hide quadrilateral after PDF generation
                    addSavePageButton.text = getString(R.string.button_add_page)
                    isEditingCurrentPhoto = false
                    Log.d("ImageToPdfActivity", "PDF generation finished. UI reset. Current quad state: ${quadrilateralImageView.getDrawQuadrilateral()}")
                    // Finish the activity after PDF is saved
                    finish()
                }
            }
        }
    }

    /**
     * Shows a custom alert dialog instead of system alert().
     */
    private fun showAlertDialog(title: String, message: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setPositiveButton("OK") { dialog, _ ->
            dialog.dismiss()
        }
        val dialog = builder.create()
        dialog.show()
    }
}
