package com.costheta.cortexa.util.image2pdf.mlkit

import android.net.Uri
import com.google.mlkit.vision.text.Text

/**
 * Data class to hold all the necessary information for a single processed page.
 *
 * @param imageUri The URI of the final, scanned image for this page.
 * @param mlkitText The complete, structured text recognition result from ML Kit.
 * This preserves the block/paragraph structure for the DOCX generator.
 */
data class OcrPage(
    val imageUri: Uri,
    val mlkitText: Text
)
