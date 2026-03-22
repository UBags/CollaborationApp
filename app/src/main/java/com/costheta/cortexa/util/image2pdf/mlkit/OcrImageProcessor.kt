package com.costheta.cortexa.util.image2pdf.mlkit

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.Log
import androidx.core.graphics.createBitmap
import kotlin.math.pow
import kotlin.math.sqrt
import androidx.core.graphics.set

object OcrImageProcessor {

    /**
     * Pre-processes a bitmap for OCR using adaptive Otsu thresholding.
     * This is the main function to call from the activity.
     */
    fun preprocessForOcr(bitmap: Bitmap): Bitmap {
        try {
            val grayscaleBitmap = toGrayscale(bitmap)
            val isLight = isBackgroundLight(grayscaleBitmap)
            val binarizedBitmap = adaptiveOtsuBinarization(grayscaleBitmap, isLight)
            grayscaleBitmap.recycle()
            return binarizedBitmap
        } catch (e: Exception) {
            Log.e("OcrImageProcessor", "Failed to preprocess for OCR. Returning original.", e)
            // As a fallback, return a copy of the original bitmap
            return bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
        }
    }

    /**
     * Determines if the background of a grayscale image is light or dark.
     * @param bitmap A grayscale bitmap.
     * @return True if the background is considered light (average pixel value >= 80).
     */
    private fun isBackgroundLight(bitmap: Bitmap): Boolean {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var totalLuminance = 0L
        for (pixel in pixels) {
            totalLuminance += Color.red(pixel) // In grayscale, R=G=B
        }
        val avg = totalLuminance / pixels.size
        return avg >= 80
    }

    /**
     * Binarizes the image by splitting it into a 3x3 grid and applying a locally
     * calculated (and adjusted) Otsu threshold to each tile.
     */
    private fun adaptiveOtsuBinarization(bitmap: Bitmap, isLightBackground: Boolean): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val outputBitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)
        val tileWidth = width / 3
        val tileHeight = height / 3

        for (i in 0..2) {
            for (j in 0..2) {
                val x = j * tileWidth
                val y = i * tileHeight
                // Ensure the last tile extends to the edge of the bitmap
                val w = if (j < 2) tileWidth else width - x
                val h = if (i < 2) tileHeight else height - y

                if (w <= 0 || h <= 0) continue

                val tile = Bitmap.createBitmap(bitmap, x, y, w, h)
                var threshold = calculateOtsuThreshold(tile)

                // Adjust the threshold based on the overall background color
                threshold = if (isLightBackground) {
                    (threshold + 10).coerceAtMost(255)
                } else {
                    (threshold - 10).coerceAtLeast(0)
                }

                val binarizedTile = binarizeTile(tile, threshold)
                canvas.drawBitmap(binarizedTile, x.toFloat(), y.toFloat(), null)

                // Clean up intermediate bitmaps
                tile.recycle()
                binarizedTile.recycle()
            }
        }
        return outputBitmap
    }

    /**
     * Calculates the optimal threshold for a grayscale image tile using Otsu's method.
     */
    private fun calculateOtsuThreshold(bitmap: Bitmap): Int {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val histogram = IntArray(256)
        for (pixel in pixels) {
            histogram[Color.red(pixel)]++ // Assumes grayscale
        }

        val total = pixels.size
        var sum = 0f
        for (i in 0..255) sum += (i * histogram[i]).toFloat()

        var sumB = 0f
        var wB = 0
        var wF = 0
        var varMax = 0f
        var threshold = 0

        for (i in 0..255) {
            wB += histogram[i]
            if (wB == 0) continue
            wF = total - wB
            if (wF == 0) break

            sumB += (i * histogram[i]).toFloat()
            val mB = sumB / wB
            val mF = (sum - sumB) / wF
            val varBetween = wB.toFloat() * wF.toFloat() * (mB - mF) * (mB - mF)

            if (varBetween > varMax) {
                varMax = varBetween
                threshold = i
            }
        }
        return threshold
    }

    /**
     * Binarizes a single tile based on a given threshold.
     */
    private fun binarizeTile(bitmap: Bitmap, threshold: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            pixels[i] = if (Color.red(pixels[i]) > threshold) Color.WHITE else Color.BLACK
        }

        val binarizedBitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
        binarizedBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return binarizedBitmap
    }

    /**
     * Converts a color bitmap to grayscale.
     */
    private fun toGrayscale(bitmap: Bitmap): Bitmap {
        val grayscaleBitmap = createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(grayscaleBitmap)
        val paint = Paint()
        val colorMatrix = ColorMatrix().apply { setSaturation(0f) }
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return grayscaleBitmap
    }

    /**
     * (Unused) Applies Gaussian adaptive binarization to a grayscale image.
     * This is computationally more intensive but can produce better results for images
     * with varying illumination.
     *
     * @param bitmap The grayscale input bitmap.
     * @param blockSize The size of the neighborhood area (e.g., 11). Must be an odd number.
     * @param c A constant subtracted from the mean.
     * @return A binarized bitmap.
     */
    fun gaussianAdaptiveBinarization(bitmap: Bitmap, blockSize: Int = 11, c: Double = 5.0): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val outputBitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Pre-calculate an integral image for fast neighborhood sums
        val integralImage = Array(height) { LongArray(width) }
        for (y in 0 until height) {
            var rowSum = 0L
            for (x in 0 until width) {
                rowSum += Color.red(pixels[y * width + x])
                integralImage[y][x] = if (y > 0) integralImage[y - 1][x] + rowSum else rowSum
            }
        }

        val halfBlock = blockSize / 2

        for (y in 0 until height) {
            for (x in 0 until width) {
                val x1 = (x - halfBlock - 1).coerceAtLeast(0)
                val x2 = (x + halfBlock).coerceAtMost(width - 1)
                val y1 = (y - halfBlock - 1).coerceAtLeast(0)
                val y2 = (y + halfBlock).coerceAtMost(height - 1)

                val count = (x2 - x1) * (y2 - y1)

                val sum = integralImage[y2][x2] - integralImage[y1][x2] - integralImage[y2][x1] + integralImage[y1][x1]

                val threshold = (sum.toDouble() / count) - c
                val originalPixelValue = Color.red(pixels[y * width + x])

                outputBitmap[x, y] =
                    if (originalPixelValue > threshold) Color.WHITE else Color.BLACK
            }
        }
        return outputBitmap
    }


    // --- Morphological operations are kept but are not used, as requested ---
    fun applyMorphologicalClose(bitmap: Bitmap): Bitmap {
        Log.w("OcrImageProcessor", "applyMorphologicalClose is deprecated and should not be used.")
        return bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
    }
}

