package com.costheta.cortexa.util.image2pdf.mlkit.old

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.costheta.cortexa.R
import com.costheta.cortexa.util.image2pdf.ThumbnailAdapter
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MLKitScannerActivity : AppCompatActivity() {

    private lateinit var cameraPreview: PreviewView
    private lateinit var quadImageView: MagnifierQuadrilateralImageView
    private lateinit var addPageButton: Button
    private lateinit var saveButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var thumbnailRecyclerView: RecyclerView
    private lateinit var cameraContainer: FrameLayout

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    private val processedPages = mutableListOf<OcrPage>()
    private val thumbnailUris = mutableListOf<Uri>()
    private lateinit var thumbnailAdapter: ThumbnailAdapter

    private var tempImageUriForEditing: Uri? = null
    private var pendingFileName: String? = null // To hold filename while requesting permissions

    private enum class AppState {
        CAMERA_PREVIEW,
        EDITING_CORNERS
    }
    private var currentState = AppState.CAMERA_PREVIEW

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mlkit_scanner)

        // Initialize Views
        cameraPreview = findViewById(R.id.camera_preview)
        quadImageView = findViewById(R.id.quad_image_view)
        addPageButton = findViewById(R.id.add_page_button)
        saveButton = findViewById(R.id.save_pdf_button)
        progressBar = findViewById(R.id.progress_bar)
        thumbnailRecyclerView = findViewById(R.id.thumbnails_recycler_view)
        cameraContainer = findViewById(R.id.camera_container)

        cameraExecutor = Executors.newSingleThreadExecutor()

        setupPermissions()
        setupUI()
    }

    private fun setupPermissions() {
        // Only camera permission is needed on startup. Storage permission is requested on-demand before saving.
        val cameraPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        if (cameraPermission == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(cameraPreview.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder().build()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
                Toast.makeText(this, "Failed to start camera.", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun setupUI() {
        thumbnailAdapter = ThumbnailAdapter(
            onItemClick = {},
            onDeleteClick = { uri ->
                val index = thumbnailUris.indexOf(uri)
                if (index != -1) {
                    thumbnailUris.removeAt(index)
                    processedPages.removeAt(index)
                    updateThumbnails()
                }
            }
        )
        thumbnailRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        thumbnailRecyclerView.adapter = thumbnailAdapter

        addPageButton.setOnClickListener {
            when (currentState) {
                AppState.CAMERA_PREVIEW -> captureImage()
                AppState.EDITING_CORNERS -> processImage()
            }
        }
        saveButton.setOnClickListener { showSaveDialog() }
        updateState(AppState.CAMERA_PREVIEW)
    }

    private fun captureImage() {
        val imageCapture = this.imageCapture ?: return
        setLoading(true)

        imageCapture.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bitmap = imageProxyToBitmap(image)
                image.close()

                tempImageUriForEditing = ImageProcessor.saveBitmapToCache(this@MLKitScannerActivity, bitmap, "capture.jpg")
                bitmap.recycle()

                lifecycleScope.launch {
                    val corners = tempImageUriForEditing?.let { ImageProcessor.detectDocumentCorners(this@MLKitScannerActivity, it) }
                    runOnUiThread {
                        if (tempImageUriForEditing != null && corners != null) {
                            quadImageView.setImageBitmap(BitmapFactory.decodeStream(contentResolver.openInputStream(tempImageUriForEditing!!)))
                            quadImageView.setQuadrilateralPoints(corners)
                            updateState(AppState.EDITING_CORNERS)
                        } else {
                            Toast.makeText(this@MLKitScannerActivity, "Failed to capture image.", Toast.LENGTH_SHORT).show()
                        }
                        setLoading(false)
                    }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
                runOnUiThread {
                    setLoading(false)
                    Toast.makeText(this@MLKitScannerActivity, "Photo capture failed.", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun processImage() {
        setLoading(true)
        val originalBitmap = (quadImageView.drawable as? BitmapDrawable)?.bitmap
        if (originalBitmap == null) {
            Toast.makeText(this, "No image to process.", Toast.LENGTH_SHORT).show()
            setLoading(false)
            return
        }

        val cornerPoints = quadImageView.getQuadrilateralPoints()

        lifecycleScope.launch {
            val transformedBitmap = ImageProcessor.applyPerspectiveTransform(originalBitmap, cornerPoints)
            if (transformedBitmap == null) {
                runOnUiThread {
                    Toast.makeText(this@MLKitScannerActivity, "Failed to transform image.", Toast.LENGTH_SHORT).show()
                    setLoading(false)
                }
                return@launch
            }

            val cleanedBitmap = ImageProcessor.performCleanup(transformedBitmap)
            transformedBitmap.recycle()

            val ocrResult = ImageProcessor.performOcr(this@MLKitScannerActivity, cleanedBitmap)
            cleanedBitmap.recycle()

            processedPages.add(ocrResult)
            thumbnailUris.add(ocrResult.imageUri)

            runOnUiThread {
                updateThumbnails()
                updateState(AppState.CAMERA_PREVIEW)
                setLoading(false)
            }
        }
    }

    private fun showSaveDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.image_save_pdf_dialog, null)
        val fileNameEditText = view.findViewById<TextInputEditText>(R.id.fileNameEditText)
        val timeStamp = SimpleDateFormat("MM_dd_yy_HH_mm_ss", Locale.US).format(Date())
        val defaultName = "CortexaScan_$timeStamp"
        fileNameEditText.setText(defaultName)

        AlertDialog.Builder(this)
            .setTitle("Save Documents")
            .setView(view)
            .setPositiveButton("Save") { dialog, _ ->
                val fileName = fileNameEditText.text.toString().ifBlank { defaultName }
                handleSaveRequest(fileName)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun handleSaveRequest(fileName: String) {
        pendingFileName = fileName
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val storagePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            if (storagePermission == PackageManager.PERMISSION_GRANTED) {
                saveDocuments(fileName)
            } else {
                requestStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        } else {
            // No special permission needed on Android 10+ to write to public directories
            saveDocuments(fileName)
        }
    }

    private fun saveDocuments(baseFileName: String) {
        setLoading(true)
        lifecycleScope.launch {
            val publicDocsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            if (!publicDocsDir.exists()) {
                publicDocsDir.mkdirs()
            }

            // Generate PDF from image URIs only
            val pdfFile = File(publicDocsDir, "${baseFileName}.pdf")
            val pdfUri = generatePdf(pdfFile)

            // Generate DOCX from the full OcrPage data
            val docxFile = File(publicDocsDir, "${baseFileName}.docx")
            val docxUri = DocxGenerator.createDocxFromOcr(this@MLKitScannerActivity, processedPages, docxFile)

            runOnUiThread {
                setLoading(false)
                var message = ""
                message += if (pdfUri != null) "PDF saved to: ${pdfFile.absolutePath}\n" else "Failed to save PDF.\n"
                message += if (docxUri != null) "DOCX saved to: ${docxFile.absolutePath}" else "Failed to save DOCX."

                Toast.makeText(this@MLKitScannerActivity, message, Toast.LENGTH_LONG).show()

                // Clear lists and finish
                processedPages.clear()
                thumbnailUris.clear()
                updateThumbnails()
                finish()
            }
        }
    }

    private suspend fun generatePdf(file: File): Uri? {
        if (thumbnailUris.isEmpty()) return null
        val document = PdfDocument()

        try {
            thumbnailUris.forEachIndexed { index, uri ->
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, index + 1).create()
                val page = document.startPage(pageInfo)
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    page.canvas.drawBitmap(bitmap, null, Rect(0, 0, 595, 842), null)
                    bitmap.recycle()
                }
                document.finishPage(page)
            }

            FileOutputStream(file).use { document.writeTo(it) }
            return Uri.fromFile(file)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving PDF", e)
            return null
        } finally {
            document.close()
        }
    }

    private fun updateState(newState: AppState) {
        currentState = newState
        when (newState) {
            AppState.CAMERA_PREVIEW -> {
                cameraPreview.visibility = View.VISIBLE
                quadImageView.visibility = View.GONE
                addPageButton.text = "Add Page"
            }
            AppState.EDITING_CORNERS -> {
                cameraPreview.visibility = View.GONE
                quadImageView.visibility = View.VISIBLE
                addPageButton.text = "Confirm & Process Page"
            }
        }
    }

    private fun updateThumbnails() {
        thumbnailAdapter.submitList(thumbnailUris.toList())
        saveButton.isEnabled = thumbnailUris.isNotEmpty()
    }

    private fun setLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        addPageButton.isEnabled = !isLoading
        saveButton.isEnabled = if (isLoading) false else thumbnailUris.isNotEmpty()
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private val requestCameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission is required to use this feature.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private val requestStoragePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            pendingFileName?.let {
                saveDocuments(it)
                pendingFileName = null // Clear after use
            }
        } else {
            Toast.makeText(this, "Storage permission is required to save documents.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "MLKitScannerActivity"
    }
}

