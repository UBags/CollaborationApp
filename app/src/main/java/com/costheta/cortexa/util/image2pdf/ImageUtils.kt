package com.costheta.cortexa.util.image2pdf

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import androidx.core.graphics.createBitmap
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.min

object ImageUtils {

    private const val TAG = "ImageUtils"
    private const val OTSU_ADJUSTMENT : Int = 10
    private const val BRIGHTEN_PIXEL_BY : Int = 30
    private const val DARKEN_PIXEL_BY : Int = 30


    /**
     * Applies a perspective transformation to a bitmap based on four source points
     * and transforms it into a target rectangular size.
     *
     * @param originalBitmap The source bitmap.
     * @param srcPoints An array of 8 floats representing the (x,y) coordinates of the
     * four corners of the quadrilateral on the original bitmap.
     * Order: top-left, top-right, bottom-right, bottom-left.
     * @param targetWidth The desired width of the output bitmap (e.g., PDF page width).
     * @param targetHeight The desired height of the output bitmap (e.g., PDF page height).
     * @return The transformed bitmap, or null if an error occurs.
     */
    fun applyPerspectiveTransform(originalBitmap: Bitmap, srcPoints: FloatArray, targetWidth: Int, targetHeight: Int): Bitmap? {
        if (srcPoints.size != 8) {
            Log.e(TAG, "srcPoints must contain 8 float values (4 x,y pairs).")
            return null
        }

        // Define the destination points as a perfect rectangle
        val dstPoints = floatArrayOf(
            0f, 0f,                 // Top-left
            targetWidth.toFloat(), 0f,       // Top-right
            targetWidth.toFloat(), targetHeight.toFloat(), // Bottom-right
            0f, targetHeight.toFloat()      // Bottom-left
        )

        val matrix = Matrix()
        val success = matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)

        if (!success) {
            Log.e(TAG, "Failed to set up perspective transformation matrix.")
            return null
        }

        try {
            val transformedBitmap =
                originalBitmap.config?.let { createBitmap(targetWidth, targetHeight, it) }
            val paint = Paint(Paint.FILTER_BITMAP_FLAG) // Enable bitmap filtering for smoother scaling

            transformedBitmap?.let { Canvas(it) }?.drawBitmap(originalBitmap, matrix, paint)
            return transformedBitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error during perspective transformation: ${e.message}", e)
            return null
        }
    }

    /**
     * Applies brightness and contrast adjustments to a bitmap.
     *
     * @param originalBitmap The input bitmap.
     * @param brightness The brightness value (-255 to 255).
     * @param contrast The contrast value (0.0 to 2.0).
     * @return A new bitmap with brightness and contrast applied, or null if an error occurs.
     */
    fun applyBrightnessAndContrast(originalBitmap: Bitmap, brightness: Float, contrast: Float): Bitmap? {
        try {
            // Create a new bitmap with the same dimensions and configuration as the original
            val adjustedBitmap = originalBitmap.config?.let { originalBitmap.copy(it, true) }
            val paint = Paint()

            val cm = ColorMatrix()

            // Apply contrast
            cm.set(floatArrayOf(
                contrast, 0f, 0f, 0f, brightness, // Red
                0f, contrast, 0f, 0f, brightness, // Green
                0f, 0f, contrast, 0f, brightness, // Blue
                0f, 0f, 0f, 1f, 0f    // Alpha
            ))

            paint.colorFilter = ColorMatrixColorFilter(cm)
            adjustedBitmap?.let { Canvas(it) }?.drawBitmap(originalBitmap, 0f, 0f, paint)

            return adjustedBitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error applying brightness and contrast: ${e.message}", e)
            return null
        }
    }

    /**
     * Applies a "smart cleanup" to the image using Otsu binarization on 3x3 segments
     * and then adjusting pixel brightness/darkness based on the binarized result.
     * This version is optimized to use pixel arrays to avoid slow get/setPixel calls.
     *
     * @param bitmap The input bitmap (already perspective transformed).
     * @return The cleaned-up bitmap.
     */
    @SuppressLint("UseKtx")
    fun applySmartCleanup(bitmap: Bitmap): Bitmap? {
        val width = bitmap.width
        val height = bitmap.height
        val cleanedPixels = IntArray(width * height)
        bitmap.getPixels(cleanedPixels, 0, width, 0, 0, width, height)

        val segmentWidth = width / 3
        val segmentHeight = height / 3

        for (row in 0 until 3) {
            for (col in 0 until 3) {
                val xStart = col * segmentWidth
                val yStart = row * segmentHeight
                val currentSegmentWidth = if (col == 2) width - xStart else segmentWidth
                val currentSegmentHeight = if (row == 2) height - yStart else segmentHeight

                if (currentSegmentWidth <= 0 || currentSegmentHeight <= 0) continue

                val segmentBitmap = Bitmap.createBitmap(bitmap, xStart, yStart, currentSegmentWidth, currentSegmentHeight)
                var binarizedSegment = applyOtsuBinarization(segmentBitmap)
                binarizedSegment = applyMorphologicalClosing(binarizedSegment, 5)

                val originalSegmentPixels = IntArray(currentSegmentWidth * currentSegmentHeight)
                segmentBitmap.getPixels(originalSegmentPixels, 0, currentSegmentWidth, 0, 0, currentSegmentWidth, currentSegmentHeight)

                val binarizedSegmentPixels = IntArray(currentSegmentWidth * currentSegmentHeight)
                binarizedSegment.getPixels(binarizedSegmentPixels, 0, currentSegmentWidth, 0, 0, currentSegmentWidth, currentSegmentHeight)

                // Iterate through pixels in the segment and apply adjustments to the main pixel array
                for (y in 0 until currentSegmentHeight) {
                    for (x in 0 until currentSegmentWidth) {
                        val segmentIndex = y * currentSegmentWidth + x
                        val originalPixel = originalSegmentPixels[segmentIndex]
                        val binarizedPixel = binarizedSegmentPixels[segmentIndex]

                        var r = Color.red(originalPixel)
                        var g = Color.green(originalPixel)
                        var b = Color.blue(originalPixel)

                        if (Color.red(binarizedPixel) > 128) { // White pixel
                            r = max(128, r + BRIGHTEN_PIXEL_BY).coerceAtMost(255)
                            g = max(128, g + BRIGHTEN_PIXEL_BY).coerceAtMost(255)
                            b = max(128, b + BRIGHTEN_PIXEL_BY).coerceAtMost(255)
                        } else { // Black pixel
                            r = max(0, r - DARKEN_PIXEL_BY).coerceAtLeast(0)
                            g = min(0, g - DARKEN_PIXEL_BY).coerceAtLeast(0)
                            b = min(0, b - DARKEN_PIXEL_BY).coerceAtLeast(0)
                        }

                        val globalIndex = (yStart + y) * width + (xStart + x)
                        cleanedPixels[globalIndex] = Color.rgb(r, g, b)
                    }
                }
                segmentBitmap.recycle()
                binarizedSegment.recycle()
            }
        }

        val cleanedBitmap = createBitmap(width, height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        cleanedBitmap.setPixels(cleanedPixels, 0, width, 0, 0, width, height)
        return cleanedBitmap
    }

    /**
     * Applies Otsu's Binarization method to a grayscale bitmap.
     * This version is optimized to use a pixel array to avoid slow get/setPixel calls.
     *
     * @param bitmap The input bitmap. It will be converted to grayscale internally.
     * @return A binarized bitmap where pixels are either black (0xFF000000) or white (0xFFFFFFFF).
     */
    @SuppressLint("UseKtx")
    private fun applyOtsuBinarization(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // --- Histogram Calculation (unchanged) ---
        val histogram = IntArray(256)
        for (pixel in pixels) {
            val gray = (Color.red(pixel) * 0.299 + Color.green(pixel) * 0.587 + Color.blue(pixel) * 0.114).toInt()
            histogram[gray]++
        }

        var total = pixels.size
        var sum = 0
        for (i in 0 until 256) {
            sum += i * histogram[i]
        }

        // --- Threshold Calculation (unchanged) ---
        var sumB = 0
        var wB = 0
        var wF = 0
        var maxVariance = 0f
        var threshold = 0

        for (i in 0 until 256) {
            wB += histogram[i]
            if (wB == 0) continue
            wF = total - wB
            if (wF == 0) break

            sumB += i * histogram[i]
            val mB = sumB.toFloat() / wB
            val mF = (sum - sumB).toFloat() / wF

            val variance = wB.toFloat() * wF.toFloat() * (mB - mF) * (mB - mF)

            if (variance > maxVariance) {
                maxVariance = variance
                threshold = i
            }
        }

        // --- Binarization using pixel array ---
        val finalThreshold = threshold - OTSU_ADJUSTMENT
        val outputPixels = IntArray(width * height)
        for(i in pixels.indices) {
            val pixel = pixels[i]
            val gray = (Color.red(pixel) * 0.299 + Color.green(pixel) * 0.587 + Color.blue(pixel) * 0.114).toInt()
            outputPixels[i] = if (gray > finalThreshold) Color.WHITE else Color.BLACK
        }

        val binarizedBitmap = createBitmap(width, height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        binarizedBitmap.setPixels(outputPixels, 0, width, 0, 0, width, height)
        return binarizedBitmap
    }

    /**
     * Saves a bitmap to a temporary file in the app's cache directory.
     *
     * @param context The application context.
     * @param bitmap The bitmap to save.
     * @param filename The desired filename (e.g., "processed_image.jpg").
     * @return The URI of the saved file, or null if saving fails.
     */
    fun saveBitmapToFile(context: Context, bitmap: Bitmap, filename: String): Uri? {
        val cacheDir = context.cacheDir
        val file = File(cacheDir, filename)
        var fos: FileOutputStream? = null
        try {
            fos = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos) // Compress to JPEG with 90% quality
            fos.flush()
            return Uri.fromFile(file)
        } catch (e: IOException) {
            Log.e(TAG, "Error saving bitmap to file: ${e.message}", e)
            return null
        } finally {
            try {
                fos?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing FileOutputStream: ${e.message}", e)
            }
        }
    }

    /**
     * Applies a morphological closing operation to a binarized bitmap.
     * Closing is a dilation followed by an erosion, useful for filling small holes
     * and gaps in the foreground objects.
     *
     * @param bitmap The input binarized bitmap (pixels should be Color.WHITE or Color.BLACK).
     * @param kernelSize The size of the square kernel to use (e.g., 5 for a 5x5 kernel).
     * @return A new bitmap with the closing operation applied.
     */
    private fun applyMorphologicalClosing(bitmap: Bitmap, kernelSize: Int = 5): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val kernelOffset = kernelSize / 2

        // 1. DILATION PASS
        val dilatedBitmap = applyDilation(bitmap, width, height, kernelSize, kernelOffset)

        // 2. EROSION PASS
        val closedBitmap = applyErosion(dilatedBitmap, width, height, kernelSize, kernelOffset)

        dilatedBitmap.recycle() // Clean up intermediate bitmap

        return closedBitmap
    }

    /**
     * Helper function to perform the Dilation step.
     */
    private fun applyDilation(bitmap: Bitmap, width: Int, height: Int, kernelSize: Int, kernelOffset: Int): Bitmap {
        val inputPixels = IntArray(width * height)
        bitmap.getPixels(inputPixels, 0, width, 0, 0, width, height)
        val outputPixels = IntArray(width * height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                var makeWhite = false
                // Iterate over the kernel
                for (ky in -kernelOffset..kernelOffset) {
                    for (kx in -kernelOffset..kernelOffset) {
                        val nx = x + kx
                        val ny = y + ky

                        // Check if the neighbor is within bounds
                        if (nx in 0..<width && ny >= 0 && ny < height) {
                            val neighborPixel = inputPixels[ny * width + nx]
                            if (neighborPixel == Color.WHITE) {
                                makeWhite = true
                                break
                            }
                        }
                    }
                    if (makeWhite) break
                }
                outputPixels[y * width + x] = if (makeWhite) Color.WHITE else Color.BLACK
            }
        }

        val dilatedBitmap = createBitmap(width, height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        dilatedBitmap.setPixels(outputPixels, 0, width, 0, 0, width, height)
        return dilatedBitmap
    }

    /**
     * Helper function to perform the Erosion step.
     */
    private fun applyErosion(bitmap: Bitmap, width: Int, height: Int, kernelSize: Int, kernelOffset: Int): Bitmap {
        val inputPixels = IntArray(width * height)
        bitmap.getPixels(inputPixels, 0, width, 0, 0, width, height)
        val outputPixels = IntArray(width * height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                var makeBlack = false
                // Iterate over the kernel
                for (ky in -kernelOffset..kernelOffset) {
                    for (kx in -kernelOffset..kernelOffset) {
                        val nx = x + kx
                        val ny = y + ky

                        // Check if the neighbor is within bounds
                        if (nx in 0..<width && ny >= 0 && ny < height) {
                            val neighborPixel = inputPixels[ny * width + nx]
                            if (neighborPixel == Color.BLACK) {
                                makeBlack = true
                                break
                            }
                        }
                    }
                    if (makeBlack) break
                }
                outputPixels[y * width + x] = if (makeBlack) Color.BLACK else Color.WHITE
            }
        }

        val erodedBitmap = createBitmap(width, height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        erodedBitmap.setPixels(outputPixels, 0, width, 0, 0, width, height)
        return erodedBitmap
    }
}
