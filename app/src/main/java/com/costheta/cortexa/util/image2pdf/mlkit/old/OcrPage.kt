package com.costheta.cortexa.util.image2pdf.mlkit.old

import android.graphics.Rect
import android.net.Uri
import com.google.mlkit.vision.text.Text


/**
 * Data class to hold all the necessary information for a single processed page.
 *
 * @param imageUri The URI of the final, cleaned bitmap for this page.
 * @param detectedLanguage The language code detected by ML Kit (e.g., "en", "und").
 * @param mlkitText The complete, structured text recognition result from ML Kit.
 * This preserves the block/paragraph structure for the DOCX generator.
 */
data class OcrPage(
    val imageUri: Uri, // The URI of the cleaned, processed image for this page
    val detectedLanguage: String = "und", // Undetermined language code by default
    val mlkitText: Text // The complete, structured text recognition result from ML Kit.
    // In a more advanced implementation, you could also add a list of image elements.
)

/**
 * Represents a single piece of recognized text and its location.
 */
data class TextElement(
    val text: String,
    val boundingBox: Rect
)
