package com.costheta.cortexa.util.image2pdf

// Copyright (c) 2025 Uddipan Bagchi. All rights reserved.
// See LICENSE in the project root for license information.package com.costheta.cortexa.action

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

class QuadrilateralImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val TAG = "QuadrilateralImageView"

    // The four corners of the quadrilateral (in image coordinates)
    private val quadPoints = arrayOf(
        PointF(), // Top-left
        PointF(), // Top-right
        PointF(), // Bottom-right
        PointF()  // Bottom-left
    )

    // Paint for drawing the quadrilateral lines
    private val linePaint = Paint().apply {
        color = Color.BLUE
        strokeWidth = 8f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    // Paint for drawing the corner circles
    private val cornerPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val cornerRadius = 30f // Radius of the corner circles for touch target
    private var activeCornerIndex: Int = -1 // -1 if no corner is being dragged
    private var lastTouchX: Float = 0f
    private var lastTouchY: Float = 0f
    private var initialDownX: Float = 0f // To detect if a drag occurred or just a tap
    private var initialDownY: Float = 0f // To detect if a drag occurred or just a tap

    // New flag to control whether the quadrilateral is drawn and interactive
    private var drawQuadrilateral: Boolean = true // Default to true, as it's initially for adjustment

    // Matrix to transform image coordinates to view coordinates and vice-versa
    private val imageMatrix = Matrix()
    private val inverseImageMatrix = Matrix()

    // Pre-allocate objects to avoid allocations during draw/layout operations
    private val drawPath = Path()
    private val viewPoints = FloatArray(8)
    private val imagePoints = FloatArray(8) // Also pre-allocate this for mapPoints input
    private val inversePoints = FloatArray(2) // For touch coordinate conversion
    private val viewRadiusInImageSpace = FloatArray(2) // For corner touch target calculation

    init {
        // isDrawingCacheEnabled = true // Deprecated and removed as per previous discussion
    }

    /**
     * Sets whether the quadrilateral and its interactive corners should be drawn.
     * @param shouldDraw True to draw and enable interaction, false to hide and disable interaction.
     */
    fun setDrawQuadrilateral(shouldDraw: Boolean) {
        Log.d(TAG, "setDrawQuadrilateral called with: $shouldDraw. Old state: ${this.drawQuadrilateral}")
        if (this.drawQuadrilateral != shouldDraw) {
            this.drawQuadrilateral = shouldDraw
            invalidate() // Redraw the view to reflect the change
            Log.d(TAG, "drawQuadrilateral changed to $shouldDraw. Invalidate called.")
        } else {
            Log.d(TAG, "setDrawQuadrilateral called with same state: $shouldDraw. No change.")
        }
    }

    /**
     * Getter for the drawQuadrilateral flag (for debugging).
     */
    fun getDrawQuadrilateral(): Boolean = drawQuadrilateral

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed) {
            Log.d(TAG, "onLayout changed. Recalculating matrix and potentially initializing quad.")
            // Recalculate matrix and initialize quadrilateral when layout changes
            updateImageMatrix()
            // Initialize quadrilateral to a default rectangle if not already set
            // Only initialize if we are supposed to draw it AND points are at default (0,0)
            if (drawQuadrilateral && quadPoints[0].x == 0f && quadPoints[0].y == 0f) {
                initDefaultQuadrilateral()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas) // Draw the image first
        Log.d(TAG, "onDraw called. drawQuadrilateral: $drawQuadrilateral")

        if (drawQuadrilateral) { // Only draw if the flag is true
            // Clear the path for reuse
            drawPath.reset()

            // Convert image coordinates to view coordinates for drawing
            imagePoints[0] = quadPoints[0].x; imagePoints[1] = quadPoints[0].y
            imagePoints[2] = quadPoints[1].x; imagePoints[3] = quadPoints[1].y
            imagePoints[4] = quadPoints[2].x; imagePoints[5] = quadPoints[2].y
            imagePoints[6] = quadPoints[3].x; imagePoints[7] = quadPoints[3].y
            imageMatrix.mapPoints(viewPoints, imagePoints)

            drawPath.moveTo(viewPoints[0], viewPoints[1]) // Top-left
            drawPath.lineTo(viewPoints[2], viewPoints[3]) // Top-right
            drawPath.lineTo(viewPoints[4], viewPoints[5]) // Bottom-right
            drawPath.lineTo(viewPoints[6], viewPoints[7]) // Bottom-left
            drawPath.close()
            canvas.drawPath(drawPath, linePaint)

            // Draw corner circles
            canvas.drawCircle(viewPoints[0], viewPoints[1], cornerRadius, cornerPaint) // Top-left
            canvas.drawCircle(viewPoints[2], viewPoints[3], cornerRadius, cornerPaint) // Top-right
            canvas.drawCircle(viewPoints[4], viewPoints[5], cornerRadius, cornerPaint) // Bottom-right
            canvas.drawCircle(viewPoints[6], viewPoints[7], cornerRadius, cornerPaint) // Bottom-left
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!drawQuadrilateral) { // If not drawing, do not allow interaction
            Log.d(TAG, "onTouchEvent: drawQuadrilateral is false, ignoring touch.")
            return super.onTouchEvent(event)
        }

        val touchX = event.x
        val touchY = event.y

        // Convert touch coordinates from view to image coordinates
        inverseImageMatrix.mapPoints(inversePoints, floatArrayOf(touchX, touchY))
        val imageTouchX = inversePoints[0]
        val imageTouchY = inversePoints[1]

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialDownX = touchX // Store initial touch for click detection
                initialDownY = touchY // Store initial touch for click detection
                activeCornerIndex = findTouchedCorner(imageTouchX, imageTouchY)
                lastTouchX = imageTouchX
                lastTouchY = imageTouchY
                if (activeCornerIndex != -1) {
                    parent.requestDisallowInterceptTouchEvent(true) // Prevent parent from intercepting touch events
                    Log.d(TAG, "ACTION_DOWN: Active corner found at index $activeCornerIndex.")
                    return true // Consume event
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeCornerIndex != -1) {
                    val dx = imageTouchX - lastTouchX
                    val dy = imageTouchY - lastTouchY

                    // Update the position of the active corner
                    val newX = quadPoints[activeCornerIndex].x + dx
                    val newY = quadPoints[activeCornerIndex].y + dy

                    // Clamp points to image bounds to prevent them from going off-screen
                    val imageWidth = drawable?.intrinsicWidth?.toFloat() ?: 0f
                    val imageHeight = drawable?.intrinsicHeight?.toFloat() ?: 0f

                    quadPoints[activeCornerIndex].x = newX.coerceIn(0f, imageWidth)
                    quadPoints[activeCornerIndex].y = newY.coerceIn(0f, imageHeight)

                    lastTouchX = imageTouchX
                    lastTouchY = imageTouchY
                    invalidate() // Redraw the view
                    return true // Consume event
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val dx = touchX - initialDownX
                val dy = touchY - initialDownY
                // If no corner was active or if the movement was minimal, consider it a click
                if (activeCornerIndex == -1 && (abs(dx) < 10 && abs(dy) < 10)) { // 10 is a small threshold for tap vs drag
                    Log.d(TAG, "ACTION_UP: Detected a click, calling performClick().")
                    performClick()
                }

                activeCornerIndex = -1
                parent.requestDisallowInterceptTouchEvent(false) // Allow parent to intercept events again
                Log.d(TAG, "ACTION_UP/CANCEL: Active corner reset.")
                return true // Consume event
            }
        }
        return super.onTouchEvent(event) // Let super handle other events (e.g., clicks if not dragging)
    }

    /**
     * Finds which corner (if any) was touched.
     * @param x Touch X coordinate in image space.
     * @param y Touch Y coordinate in image space.
     * @return Index of the touched corner (0-3), or -1 if no corner was touched.
     */
    private fun findTouchedCorner(x: Float, y: Float): Int {
        for (i in quadPoints.indices) {
            val cornerX = quadPoints[i].x
            val cornerY = quadPoints[i].y

            // Calculate distance in image coordinates
            val distance = sqrt((x - cornerX).pow(2) + (y - cornerY).pow(2))

            // Convert cornerRadius from view to image coordinates for comparison
            inverseImageMatrix.mapVectors(viewRadiusInImageSpace, floatArrayOf(cornerRadius, 0f))
            val effectiveCornerRadius = sqrt(viewRadiusInImageSpace[0].pow(2) + viewRadiusInImageSpace[1].pow(2))

            if (distance <= effectiveCornerRadius * 1.5) { // Increase touch target slightly
                return i
            }
        }
        return -1
    }

    /**
     * Initializes the quadrilateral to a default rectangle that fits within the image.
     * This is called when the image is first loaded or layout changes.
     */
    private fun initDefaultQuadrilateral() {
        val imageWidth = drawable?.intrinsicWidth?.toFloat() ?: 0f
        val imageHeight = drawable?.intrinsicHeight?.toFloat() ?: 0f

        if (imageWidth == 0f || imageHeight == 0f) {
            Log.w(TAG, "Image dimensions are zero, cannot initialize default quadrilateral.")
            return
        }

        // Set default quadrilateral to be slightly inset from the image boundaries
        val insetRatio = 0.1f
        val insetX = imageWidth * insetRatio
        val insetY = imageHeight * insetRatio

        quadPoints[0].set(insetX, insetY) // Top-left
        quadPoints[1].set(imageWidth - insetX, insetY) // Top-right
        quadPoints[2].set(imageWidth - insetX, imageHeight - insetY) // Bottom-right
        quadPoints[3].set(insetX, imageHeight - insetY) // Bottom-left

        invalidate() // Redraw the view with the new quadrilateral
        Log.d(TAG, "Default quadrilateral initialized.")
    }

    /**
     * Resets the quadrilateral points to (0,0) and then calls initDefaultQuadrilateral()
     * to ensure a fresh default quadrilateral is drawn.
     */
    fun resetQuadrilateral() {
        Log.d(TAG, "resetQuadrilateral called. Resetting points to (0,0) and re-initializing default.")
        // Reset all points to (0,0)
        for (point in quadPoints) {
            point.set(0f, 0f)
        }
        // Then call initDefaultQuadrilateral to set them based on image dimensions
        initDefaultQuadrilateral()
    }

    /**
     * Updates the internal matrix used for coordinate transformations.
     * This should be called whenever the image or view size changes.
     */
    private fun updateImageMatrix() {
        val imageRect = RectF(0f, 0f, drawable?.intrinsicWidth?.toFloat() ?: 0f, drawable?.intrinsicHeight?.toFloat() ?: 0f)
        val viewRect = RectF(0f, 0f, width.toFloat(), height.toFloat())

        // Get the matrix that scales and translates the image to fit the ImageView
        imageMatrix.setRectToRect(imageRect, viewRect, Matrix.ScaleToFit.CENTER)

        // Get the inverse matrix for converting view coordinates back to image coordinates
        imageMatrix.invert(inverseImageMatrix)
        Log.d(TAG, "Image matrix updated.")
    }

    /**
     * Returns the current quadrilateral points in original image coordinates.
     * Order: Top-Left, Top-Right, Bottom-Right, Bottom-Left.
     * @return A FloatArray of 8 values (x1, y1, x2, y2, x3, y3, x4, y4).
     */
    fun getQuadrilateralPoints(): FloatArray {
        return floatArrayOf(
            quadPoints[0].x, quadPoints[0].y,
            quadPoints[1].x, quadPoints[1].y,
            quadPoints[2].x, quadPoints[2].y,
            quadPoints[3].x, quadPoints[3].y
        )
    }

    /**
     * Sets the quadrilateral points.
     * @param points An array of 8 floats representing the (x,y) coordinates of the
     * four corners of the quadrilateral in image coordinates.
     * Order: top-left, top-right, bottom-right, bottom-left.
     */
    fun setQuadrilateralPoints(points: FloatArray) {
        if (points.size == 8) {
            quadPoints[0].set(points[0], points[1])
            quadPoints[1].set(points[2], points[3])
            quadPoints[2].set(points[4], points[5])
            quadPoints[3].set(points[6], points[7])
            invalidate() // Redraw the view with the new quadrilateral
            Log.d(TAG, "Quadrilateral points set externally.")
        } else {
            Log.e(TAG, "setQuadrilateralPoints: Invalid array size. Expected 8 floats.")
        }
    }

    /**
     * Sets the image to be displayed and initializes the quadrilateral.
     */
    override fun setImageBitmap(bm: Bitmap?) {
        super.setImageBitmap(bm)
        Log.d(TAG, "setImageBitmap called. Bitmap is null: ${bm == null}")
        // Ensure the matrix is updated after setting the bitmap
        updateImageMatrix()
        // If drawQuadrilateral is true, and we're setting a new bitmap,
        // we should ensure the quadrilateral is initialized/reset.
        // The resetQuadrilateral() method should be called externally
        // when a new image is loaded and a fresh quad is desired.
        // This method here just ensures redrawing if drawQuadrilateral is false.
        if (!drawQuadrilateral) {
            invalidate() // Just invalidate to ensure it redraws without quad
            Log.d(TAG, "setImageBitmap: drawQuadrilateral is false, just invalidating.")
        }
    }

    // Override performClick for accessibility and standard behavior
    override fun performClick(): Boolean {
        // Calls the superclass's performClick() method.
        // This is important for accessibility services (like TalkBack) and
        // for handling programmatic clicks (e.g., view.performClick()).
        Log.d(TAG, "performClick() called.")
        return super.performClick()
    }
}
