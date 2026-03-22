package com.costheta.cortexa.util.image2pdf.mlkit.old

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.util.Log
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.LinkedList
import java.util.Queue
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.pow
import kotlin.math.sqrt
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale

object ImageProcessor {
    private const val TAG = "ImageProcessor"

    /**
     * Detects the corners of a document in a bitmap using image processing techniques.
     * This method avoids heavy libraries like OpenCV.
     */
    suspend fun detectDocumentCorners(context: Context, imageUri: Uri): FloatArray? = withContext(Dispatchers.IO) {
        try {
            val fullBitmap = BitmapFactory.decodeStream(context.contentResolver.openInputStream(imageUri))
            // Downscale for performance
            val scaleFactor = 300.0 / fullBitmap.width
            val bitmap = fullBitmap.scale(
                (fullBitmap.width * scaleFactor).toInt(),
                (fullBitmap.height * scaleFactor).toInt()
            )
            fullBitmap.recycle()

            // 1a. Determine if background is light or dark
            val isBackgroundLight = isBackgroundLight(bitmap)
            val kernelSize = 11 // Large kernel to remove text

            // Binarize and perform morphological operations
            var processedBitmap = binarize(bitmap)
            processedBitmap = if (isBackgroundLight) {
                // Light background, dark text. Use closing to fill text holes.
                morphologicalClose(processedBitmap, kernelSize)
            } else {
                // Dark background, light text. Use opening to remove text spots.
                morphologicalOpen(processedBitmap, kernelSize)
            }

            // 1c. Find the largest blob
            val largestContour = findLargestContour(processedBitmap, isBackgroundLight)
            processedBitmap.recycle()
            bitmap.recycle()

            if (largestContour.isNullOrEmpty()) {
                Log.w(TAG, "Could not find any significant contour.")
                return@withContext null
            }

            // 1d. Find the four corners of the contour
            val corners = findCornersOfContour(largestContour)

            // Scale corners back to original image size
            val unscale = 1.0 / scaleFactor
            return@withContext floatArrayOf(
                (corners[0].x * unscale).toFloat(), (corners[0].y * unscale).toFloat(), // TL
                (corners[1].x * unscale).toFloat(), (corners[1].y * unscale).toFloat(), // TR
                (corners[2].x * unscale).toFloat(), (corners[2].y * unscale).toFloat(), // BR
                (corners[3].x * unscale).toFloat(), (corners[3].y * unscale).toFloat()  // BL
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting document corners: ${e.message}", e)
            return@withContext null
        }
    }

    private fun isBackgroundLight(bitmap: Bitmap): Boolean {
        // 1. Crop the image to the central 50%
        val width = bitmap.width
        val height = bitmap.height
        val cropX = (width * 0.25).toInt()
        val cropY = (height * 0.25).toInt()
        val cropWidth = (width * 0.5).toInt()
        val cropHeight = (height * 0.5).toInt()

        if (cropWidth <= 0 || cropHeight <= 0) return true // Default to light for invalid size

        val croppedBitmap = Bitmap.createBitmap(bitmap, cropX, cropY, cropWidth, cropHeight)

        // 2. Convert the cropped area to grayscale
        val grayscaleBitmap = createBitmap(cropWidth, cropHeight)
        val canvas = Canvas(grayscaleBitmap)
        val paint = Paint()
        val colorMatrix = ColorMatrix().apply { setSaturation(0f) }
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(croppedBitmap, 0f, 0f, paint)
        croppedBitmap.recycle()

        // 3. Take the average of grayscale values
        val pixels = IntArray(cropWidth * cropHeight)
        grayscaleBitmap.getPixels(pixels, 0, cropWidth, 0, 0, cropWidth, cropHeight)
        grayscaleBitmap.recycle()

        var totalGrayValue = 0L
        for (pixel in pixels) {
            totalGrayValue += Color.red(pixel) // In grayscale, R=G=B
        }

        val averageGray = totalGrayValue / pixels.size.toFloat()

        // If average > 96, then background is light. Else, dark.
        return averageGray > 96
    }

    private fun binarize(bitmap: Bitmap): Bitmap {
        val grayscale = createBitmap(bitmap.width, bitmap.height)
        val canvas = Canvas(grayscale)
        val paint = Paint()
        val colorMatrix = ColorMatrix()
        colorMatrix.setSaturation(0f)
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        val pixels = IntArray(grayscale.width * grayscale.height)
        grayscale.getPixels(pixels, 0, grayscale.width, 0, 0, grayscale.width, grayscale.height)
        val threshold = 128
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val gray = Color.red(pixel) // Since it's grayscale, R=G=B
            val newColor = if (gray > threshold) Color.WHITE else Color.BLACK
            pixels[i] = newColor
        }
        grayscale.setPixels(pixels, 0, grayscale.width, 0, 0, grayscale.width, grayscale.height)
        return grayscale
    }

    private fun morphologicalClose(bitmap: Bitmap, kernelSize: Int) = erode(dilate(bitmap, kernelSize), kernelSize)
    private fun morphologicalOpen(bitmap: Bitmap, kernelSize: Int) = dilate(erode(bitmap, kernelSize), kernelSize)

    private fun dilate(bitmap: Bitmap, kernelSize: Int): Bitmap {
        return processMorphology(bitmap, kernelSize) { neighbors -> neighbors.maxOrNull() ?: 0 }
    }

    private fun erode(bitmap: Bitmap, kernelSize: Int): Bitmap {
        return processMorphology(bitmap, kernelSize) { neighbors -> neighbors.minOrNull() ?: 255 }
    }

    private fun processMorphology(bitmap: Bitmap, kernelSize: Int, operation: (List<Int>) -> Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val outputBitmap = createBitmap(width, height) // CORRECTED
        val offset = kernelSize / 2
        val inputPixels = IntArray(width * height)
        bitmap.getPixels(inputPixels, 0, width, 0, 0, width, height)

        val outputPixels = IntArray(width * height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val neighbors = mutableListOf<Int>()
                for (ky in -offset..offset) {
                    for (kx in -offset..offset) {
                        val nx = x + kx
                        val ny = y + ky
                        if (nx in 0 until width && ny in 0 until height) {
                            neighbors.add(Color.red(inputPixels[ny * width + nx]))
                        }
                    }
                }
                val resultValue = operation(neighbors)
                outputPixels[y * width + x] = Color.rgb(resultValue, resultValue, resultValue)
            }
        }
        outputBitmap.setPixels(outputPixels, 0, width, 0, 0, width, height)
        return outputBitmap
    }

    private fun findLargestContour(bitmap: Bitmap, isBackgroundLight: Boolean): List<Point>? {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val visited = BooleanArray(width * height)
        var largestContour: List<Point>? = null
        val targetColor = if (isBackgroundLight) Color.BLACK else Color.WHITE // Page is black on white bg, or white on black bg

        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                if (!visited[index] && pixels[index] == targetColor) {
                    val contour = mutableListOf<Point>()
                    val queue: Queue<Point> = LinkedList()
                    queue.add(Point(x, y))
                    visited[index] = true

                    while (queue.isNotEmpty()) {
                        val p = queue.poll() ?: continue
                        contour.add(p)
                        for (dy in -1..1) {
                            for (dx in -1..1) {
                                if (dx == 0 && dy == 0) continue
                                val nx = p.x + dx
                                val ny = p.y + dy
                                val nIndex = ny * width + nx
                                if (nx in 0 until width && ny in 0 until height && !visited[nIndex] && pixels[nIndex] == targetColor) {
                                    visited[nIndex] = true
                                    queue.add(Point(nx, ny))
                                }
                            }
                        }
                    }
                    if (largestContour == null || contour.size > largestContour.size) {
                        largestContour = contour
                    }
                }
            }
        }
        return largestContour
    }

    private fun findCornersOfContour(contour: List<Point>): Array<Point> {
        if (contour.isEmpty()) return Array(4) { Point(0, 0) }

        val center = Point(contour.map { it.x }.average().toInt(), contour.map { it.y }.average().toInt())
        var tl = contour[0]
        var tr = contour[0]
        var br = contour[0]
        var bl = contour[0]

        // Sums of coordinates
        var minSum = Int.MAX_VALUE // Top-left corner will have the smallest sum (x+y)
        var maxSum = Int.MIN_VALUE // Bottom-right corner will have the largest sum (x+y)

        // Diffs of coordinates
        var minDiff = Int.MAX_VALUE // Top-right corner will have the smallest difference (y-x)
        var maxDiff = Int.MIN_VALUE // Bottom-left corner will have the largest difference (y-x)


        for (point in contour) {
            val sum = point.x + point.y
            val diff = point.y - point.x

            if (sum < minSum) {
                minSum = sum
                tl = point
            }
            if (sum > maxSum) {
                maxSum = sum
                br = point
            }
            if (diff < minDiff) {
                minDiff = diff
                tr = point
            }
            if (diff > maxDiff) {
                maxDiff = diff
                bl = point
            }
        }
        return arrayOf(tl, tr, br, bl)
    }


    /**
     * Applies perspective transformation to crop the document.
     */
    fun applyPerspectiveTransform(originalBitmap: Bitmap, srcPoints: FloatArray): Bitmap? {
        val (tl, tr, br, bl) = srcPoints.toList().chunked(2).map { PointF(it[0], it[1]) }

        val widthA = sqrt((br.x - bl.x).pow(2) + (br.y - bl.y).pow(2))
        val widthB = sqrt((tr.x - tl.x).pow(2) + (tr.y - tl.y).pow(2))
        val maxWidth = widthA.coerceAtLeast(widthB)

        val heightA = sqrt((tr.x - br.x).pow(2) + (tr.y - br.y).pow(2))
        val heightB = sqrt((tl.x - bl.x).pow(2) + (tl.y - bl.y).pow(2))
        val maxHeight = heightA.coerceAtLeast(heightB)

        val targetWidth = maxWidth.toInt()
        val targetHeight = maxHeight.toInt()

        if (targetWidth <= 0 || targetHeight <= 0) return null

        val dstPoints = floatArrayOf(
            0f, 0f,
            targetWidth.toFloat(), 0f,
            targetWidth.toFloat(), targetHeight.toFloat(),
            0f, targetHeight.toFloat()
        )

        val matrix = Matrix()
        if (!matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)) {
            Log.e(TAG, "Failed to set up perspective transformation matrix.")
            return null
        }

        return try {
            val transformedBitmap = createBitmap(
                targetWidth,
                targetHeight,
                originalBitmap.config ?: Bitmap.Config.ARGB_8888
            )
            Canvas(transformedBitmap).drawBitmap(originalBitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
            transformedBitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error during perspective transformation: ${e.message}", e)
            null
        }
    }


    /**
     * Applies a two-stage cleanup process to the bitmap to improve document readability.
     */
    fun performCleanup(originalBitmap: Bitmap): Bitmap {
        // Stage 1: Increase contrast to make darks darker and lights lighter
        val contrastedBitmap = applyContrastAndBrightness(originalBitmap, 1.5f, -20f)

        // Stage 2: Make lighter elements closer to white
        val finalBitmap = applySelectiveWhitening(contrastedBitmap)
        contrastedBitmap.recycle()
        return finalBitmap
    }

    private fun applyContrastAndBrightness(bitmap: Bitmap, contrast: Float, brightness: Float): Bitmap {
        val colorMatrix = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, brightness,
            0f, contrast, 0f, 0f, brightness,
            0f, 0f, contrast, 0f, brightness,
            0f, 0f, 0f, 1f, 0f
        ))
        // val paint = Paint().apply { Paint.setColorFilter = ColorMatrixColorFilter(colorMatrix) }
        val adjustedBitmap =
            createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        // Canvas(adjustedBitmap).drawBitmap(bitmap, 0f, 0f, paint)
        // Canvas(adjustedBitmap).drawBitmap(bitmap, 0f, 0f, paint)
        return adjustedBitmap
    }

    private fun applySelectiveWhitening(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val color = pixels[i]
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            val luminance = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            if (luminance > 180) {
                val factor = (255 - luminance) / 2
                val newR = (r + factor).coerceAtMost(255)
                val newG = (g + factor).coerceAtMost(255)
                val newB = (b + factor).coerceAtMost(255)
                pixels[i] = Color.rgb(newR, newG, newB)
            }
        }

        val resultBitmap = createBitmap(width, height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        resultBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return resultBitmap
    }


    /**
     * Performs OCR on a bitmap and identifies the language.
     */
    suspend fun performOcr(context: Context, bitmap: Bitmap): OcrPage = withContext(Dispatchers.IO) {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val languageIdentifier = LanguageIdentification.getClient()

        val textResult = recognizer.process(inputImage).await()
        val language = suspendCoroutine { continuation ->
            languageIdentifier.identifyLanguage(textResult.text)
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resume("und") }
        }

        val uri = saveBitmapToCache(context, bitmap, "ocr_page_${System.currentTimeMillis()}.jpg")
            ?: Uri.EMPTY

        OcrPage(
            imageUri = uri,
            detectedLanguage = language,
            mlkitText = textResult
        )
    }

    /**
     * Saves a bitmap to a temporary file in the app's cache directory.
     */
    fun saveBitmapToCache(context: Context, bitmap: Bitmap, filename: String): Uri? {
        return try {
            val cacheDir = context.cacheDir
            val file = File(cacheDir, filename)
            FileOutputStream(file).use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)
            }
            Uri.fromFile(file)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving bitmap to cache", e)
            null
        }
    }
}

