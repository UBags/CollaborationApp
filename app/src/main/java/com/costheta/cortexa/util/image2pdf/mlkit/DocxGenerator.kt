package com.costheta.cortexa.util.image2pdf.mlkit

import android.content.Context
import android.net.Uri
import android.util.Log
import org.apache.poi.util.Units
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object DocxGenerator {

    private const val TAG = "DocxGenerator"

    /**
     * Creates a .docx file from a list of OcrPage objects.
     * For each page, it inserts the scanned image followed by the OCR'd text,
     * maintaining paragraph structure.
     * @param context Application context.
     * @param pages The list of processed pages.
     * @param outputFile The file to save the DOCX to.
     * @return The Uri of the saved .docx file, or null on failure.
     */
    fun createDocxFromOcr(context: Context, pages: List<OcrPage>, outputFile: File): Uri? {
        if (pages.isEmpty()) {
            Log.w(TAG, "No pages to generate DOCX.")
            return null
        }

        val document = XWPFDocument()
        // A4 page width in points minus some margin
        val targetWidthInPoints = 595 - 100

        try {
            pages.forEachIndexed { index, pageData ->
                // --- Insert the Scanned Image ---
                try {
                    context.contentResolver.openInputStream(pageData.imageUri)?.use { inputStream ->
                        val tempFile = File(context.cacheDir, "temp_image_for_docx.jpg")
                        tempFile.outputStream().use { fileOut -> inputStream.copyTo(fileOut) }

                        FileInputStream(tempFile).use { fileInputStream ->
                            // Get image dimensions to scale it correctly
                            val options = android.graphics.BitmapFactory.Options().apply {
                                inJustDecodeBounds = true
                            }
                            android.graphics.BitmapFactory.decodeFile(tempFile.absolutePath, options)
                            val imageWidth = options.outWidth
                            val imageHeight = options.outHeight

                            // Scale image to fit the target width while maintaining aspect ratio
                            val scaledWidth = targetWidthInPoints
                            val scaledHeight = (scaledWidth.toDouble() / imageWidth * imageHeight).toInt()

                            val imageParagraph = document.createParagraph()
                            imageParagraph.createRun().addPicture(
                                FileInputStream(tempFile),
                                XWPFDocument.PICTURE_TYPE_JPEG,
                                "page_${index + 1}.jpg",
                                Units.toEMU(scaledWidth.toDouble()),
                                Units.toEMU(scaledHeight.toDouble())
                            )
                        }
                        tempFile.delete() // Clean up temp file
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to insert image for page ${index + 1} into DOCX.", e)
                    // Add a placeholder text if image insertion fails
                    document.createParagraph().createRun().setText("[Image for Page ${index + 1} could not be loaded]")
                }


                // Add a separator and title for the OCR text
                val textTitleRun = document.createParagraph().createRun()
                textTitleRun.addBreak()
                textTitleRun.setText("Recognized Text:")
                textTitleRun.isItalic = true

                // --- Write OCR Text, Grouped by Paragraphs ---
                if (pageData.mlkitText.textBlocks.isEmpty()) {
                    val p = document.createParagraph()
                    p.createRun().setText("[No text detected on this page]")
                } else {
                    // Each MLKit TextBlock is treated as a paragraph
                    pageData.mlkitText.textBlocks.forEach { block ->
                        val paragraph = document.createParagraph()
                        // The block's text directly represents the paragraph content
                        paragraph.createRun().setText(block.text)
                    }
                }

                // Add a page break after each page except the last one
                if (index < pages.size - 1) {
                    document.createParagraph().isPageBreak = true
                }
            }

            // Save the document
            FileOutputStream(outputFile).use {
                document.write(it)
            }
            Log.d(TAG, "DOCX saved successfully to ${outputFile.absolutePath}")
            return Uri.fromFile(outputFile)

        } catch (e: Exception) {
            Log.e(TAG, "Error creating DOCX file", e)
            return null
        } finally {
            document.close()
        }
    }
}
