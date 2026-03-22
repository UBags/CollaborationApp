package com.costheta.cortexa.util.image2pdf.mlkit

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import coil.compose.rememberAsyncImagePainter
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class DocumentScannerActivity : ComponentActivity() {

    private val textRecognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val scanner by lazy {
        val options = GmsDocumentScannerOptions.Builder()
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .setGalleryImportAllowed(true)
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF
            )
            .build()
        GmsDocumentScanning.getClient(options)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                DocumentScannerScreen(
                    onScan = { scannerLauncher ->
                        scanner.getStartScanIntent(this)
                            .addOnSuccessListener { intentSender ->
                                scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                            }
                            .addOnFailureListener {
                                Toast.makeText(this, "Scanner failed to start: ${it.message}", Toast.LENGTH_LONG).show()
                                finish()
                            }
                    },
                    onProcessScannedImages = { imageUris, pdfUri, onResult ->
                        lifecycleScope.launch {
                            processImageUris(imageUris, pdfUri, textRecognizer, onResult)
                        }
                    }
                )
            }
        }
    }

    private suspend fun processImageUris(
        imageUris: List<Uri>,
        pdfUri: Uri?,
        recognizer: com.google.mlkit.vision.text.TextRecognizer,
        onResult: (List<OcrPage>, Uri?) -> Unit
    ) {
        withContext(Dispatchers.Main) {
            Toast.makeText(this@DocumentScannerActivity, "Starting image processing...", Toast.LENGTH_SHORT).show()
        }
        val ocrPages = mutableListOf<OcrPage>()

        if (imageUris.isEmpty()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@DocumentScannerActivity, "No images were found.", Toast.LENGTH_SHORT).show()
            }
            onResult(emptyList(), null)
            return
        }

        withContext(Dispatchers.IO) {
            for (uri in imageUris) {
                try {
                    val originalBitmap = contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it)
                    }

                    if (originalBitmap == null) {
                        Log.e("DocScanner", "Failed to decode bitmap from URI: $uri")
                        ocrPages.add(OcrPage(imageUri = uri, mlkitText = Text("", emptyList<Text.TextBlock>())))
                        continue
                    }

                    val ocrReadyBitmap = OcrImageProcessor.preprocessForOcr(originalBitmap)
                    originalBitmap.recycle()

                    val image = InputImage.fromBitmap(ocrReadyBitmap, 0)
                    val visionText = try {
                        recognizer.process(image).await()
                    } finally {
                        ocrReadyBitmap.recycle()
                    }

                    ocrPages.add(OcrPage(imageUri = uri, mlkitText = visionText ?: Text("", emptyList<Text.TextBlock>())))

                } catch (e: Exception) {
                    Log.e("DocScanner", "Error processing page URI: $uri", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@DocumentScannerActivity, "Failed to process a page: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                    ocrPages.add(OcrPage(imageUri = uri, mlkitText = Text("", emptyList<Text.TextBlock>())))
                }
            }
        }
        withContext(Dispatchers.Main) {
            Toast.makeText(this@DocumentScannerActivity, "Processing complete.", Toast.LENGTH_SHORT).show()
        }
        onResult(ocrPages, pdfUri)
    }
}

@OptIn(ExperimentalMaterial3Api :: class)
@Composable
fun DocumentScannerScreen(
    onScan: (androidx.activity.result.ActivityResultLauncher<IntentSenderRequest>) -> Unit,
    onProcessScannedImages: (List<Uri>, Uri?, (List<OcrPage>, Uri?) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val activity = (LocalView.current.context as? Activity)
    var ocrPages by remember { mutableStateOf<List<OcrPage>>(emptyList()) }
    var pdfUri by remember { mutableStateOf<Uri?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var hasScanned by remember { mutableStateOf(false) }
    var fileName by remember { mutableStateOf(TextFieldValue(defaultFileName())) }

    val scannerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        hasScanned = true
        if (result.resultCode == Activity.RESULT_OK) {
            val scanningResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            if (scanningResult != null) {
                isLoading = true
                val imageUris = scanningResult.pages?.map { it.imageUri } ?: emptyList()
                val pdfUriFromScan = scanningResult.pdf?.uri
                onProcessScannedImages(imageUris, pdfUriFromScan) { pages, pUri ->
                    ocrPages = pages
                    pdfUri = pUri
                    isLoading = false
                }
            } else {
                Toast.makeText(context, "Failed to retrieve scan result.", Toast.LENGTH_SHORT).show()
                activity?.finish()
            }
        } else {
            if (ocrPages.isEmpty()) {
                Toast.makeText(context, "Scan cancelled.", Toast.LENGTH_SHORT).show()
                activity?.finish()
            }
        }
    }

    LaunchedEffect(Unit) {
        onScan(scannerLauncher)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            saveFiles(context, fileName.text, ocrPages, pdfUri) { activity?.finish() }
        } else {
            Toast.makeText(context, "Storage permission is required to save files.", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Document Scanner") }) }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text("Processing images...")
            }

            if (ocrPages.isNotEmpty()) {
                Text("Scanned Pages", style = MaterialTheme.typography.titleMedium)
                LazyRow(modifier = Modifier.fillMaxWidth().height(150.dp)) {
                    items(ocrPages) { page ->
                        Image(
                            painter = rememberAsyncImagePainter(page.imageUri),
                            contentDescription = "Scanned Page",
                            modifier = Modifier.width(100.dp).height(150.dp).padding(horizontal = 4.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("File Name") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                            when (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                                PackageManager.PERMISSION_GRANTED -> saveFiles(context, fileName.text, ocrPages, pdfUri) { activity?.finish() }
                                else -> permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            }
                        } else {
                            saveFiles(context, fileName.text, ocrPages, pdfUri) { activity?.finish() }
                        }
                    },
                    enabled = !isLoading
                ) {
                    Text("Save & Exit")
                }
            } else if (hasScanned && !isLoading) {
                Text("No documents were scanned or process failed. Please try again.")
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { activity?.finish() }) { Text("Go Back") }
            }
        }
    }
}

private fun saveFiles(context: Context, baseName: String, pages: List<OcrPage>, pdfSourceUri: Uri?, onComplete: () -> Unit) {
    if (pages.isEmpty()) {
        Toast.makeText(context, "No pages to save.", Toast.LENGTH_SHORT).show()
        onComplete()
        return
    }
    val scope = CoroutineScope(Dispatchers.IO)
    scope.launch {
        val finalBaseName = baseName.ifBlank { defaultFileName() }
        var successMessage = ""
        var errorMessage = ""

        if (pdfSourceUri != null) {
            try {
                val pdfFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "$finalBaseName.pdf")
                context.contentResolver.openInputStream(pdfSourceUri)?.use { input ->
                    FileOutputStream(pdfFile).use { output -> input.copyTo(output) }
                }
                successMessage += "PDF saved successfully.\n"
            } catch (e: Exception) {
                errorMessage += "Failed to save PDF: ${e.message}\n"
            }
        } else {
            errorMessage += "PDF source not found.\n"
        }

        try {
            val docxFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "$finalBaseName.docx")
            val docxUri = DocxGenerator.createDocxFromOcr(context, pages, docxFile)
            if (docxUri != null) {
                successMessage += "DOCX saved successfully."
            } else {
                errorMessage += "Failed to create DOCX file."
            }
        } catch (e: Exception) {
            errorMessage += "Failed to save DOCX: ${e.message}"
        }

        withContext(Dispatchers.Main) {
            val message = (successMessage + errorMessage).trim()
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            onComplete()
        }
    }
}

private fun defaultFileName(): String {
    return "Scan_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}"
}

