package com.costheta.cortexa.util.image2pdf.mlkit.old

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.pow
import kotlin.math.sqrt

class MagnifierQuadrilateralImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val quadPoints = Array(4) { PointF() }
    private var activeCornerIndex: Int = -1
    private var drawQuadrilateral = true
    private var underlyingBitmap: Bitmap? = null

    // Magnifier properties
    private val magnifierPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val magnifierPath = Path()
    private val magnifierMatrix = Matrix()
    private var showMagnifier = false
    private val magnifierPosition = PointF()
    private val magnifierRadius = 120f // Radius of the magnifier circle in view coordinates
    private val magnifierZoomFactor = 2f // How much to zoom inside the magnifier

    // Paints
    private val linePaint = Paint().apply {
        color = Color.RED
        strokeWidth = 6f
        style = Paint.Style.STROKE
    }
    private val cornerPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
    }
    private val cornerRadius = 30f

    // Coordinate transformation matrices
    private val viewToImageMatrix = Matrix()
    private val imageToViewMatrix = Matrix()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (drawable == null) return

        if (drawQuadrilateral) {
            drawQuad(canvas)
        }

        if (showMagnifier && activeCornerIndex != -1 && underlyingBitmap != null) {
            drawMagnifier(canvas)
        }
    }

    private fun drawQuad(canvas: Canvas) {
        val viewPoints = mapPointsToView(quadPoints)
        val path = Path().apply {
            moveTo(viewPoints[0].x, viewPoints[0].y)
            lineTo(viewPoints[1].x, viewPoints[1].y)
            lineTo(viewPoints[2].x, viewPoints[2].y)
            lineTo(viewPoints[3].x, viewPoints[3].y)
            close()
        }
        canvas.drawPath(path, linePaint)
        viewPoints.forEach { canvas.drawCircle(it.x, it.y, cornerRadius, cornerPaint) }
    }

    private fun drawMagnifier(canvas: Canvas) {
        magnifierPath.reset()
        magnifierPath.addCircle(magnifierPosition.x, magnifierPosition.y, magnifierRadius, Path.Direction.CW)

        canvas.save()
        canvas.clipPath(magnifierPath)

        // Calculate translation and zoom for the bitmap inside the magnifier
        val zoom = magnifierZoomFactor
        val dx = magnifierPosition.x - quadPoints[activeCornerIndex].x * (imageToViewMatrix.mapRadius(1f))
        val dy = magnifierPosition.y - quadPoints[activeCornerIndex].y * (imageToViewMatrix.mapRadius(1f))

        magnifierMatrix.set(imageToViewMatrix)
        magnifierMatrix.postScale(zoom, zoom, magnifierPosition.x, magnifierPosition.y)
        magnifierMatrix.postTranslate(dx - magnifierPosition.x, dy - magnifierPosition.y)

        canvas.drawBitmap(underlyingBitmap!!, magnifierMatrix, magnifierPaint)

        // Draw crosshairs in the center of the magnifier
        val crosshairPaint = Paint().apply { color = Color.BLACK; strokeWidth = 3f }
        canvas.drawLine(magnifierPosition.x - 20, magnifierPosition.y, magnifierPosition.x + 20, magnifierPosition.y, crosshairPaint)
        canvas.drawLine(magnifierPosition.x, magnifierPosition.y - 20, magnifierPosition.x, magnifierPosition.y + 20, crosshairPaint)

        canvas.restore()

        // Draw magnifier border
        val borderPaint = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 5f }
        canvas.drawCircle(magnifierPosition.x, magnifierPosition.y, magnifierRadius, borderPaint)
    }


    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!drawQuadrilateral || drawable == null) return super.onTouchEvent(event)
        val touchPoint = PointF(event.x, event.y)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val touchPointInImage = mapPointToImage(touchPoint)
                activeCornerIndex = findTouchedCorner(touchPointInImage)
                if (activeCornerIndex != -1) {
                    showMagnifier = true
                    updateMagnifierPosition(touchPoint)
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeCornerIndex != -1) {
                    val imageTouchPoint = mapPointToImage(touchPoint)
                    // Clamp points to image bounds
                    quadPoints[activeCornerIndex].x = imageTouchPoint.x.coerceIn(0f, drawable.intrinsicWidth.toFloat())
                    quadPoints[activeCornerIndex].y = imageTouchPoint.y.coerceIn(0f, drawable.intrinsicHeight.toFloat())
                    updateMagnifierPosition(touchPoint)
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activeCornerIndex = -1
                showMagnifier = false
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateMagnifierPosition(touchPoint: PointF) {
        magnifierPosition.set(touchPoint.x, touchPoint.y - magnifierRadius - 40) // Position above the finger
    }

    private fun findTouchedCorner(touchPoint: PointF): Int {
        val touchRadius = 60f / imageToViewMatrix.mapRadius(1f) // Larger touch area in image space
        return quadPoints.indexOfFirst { point ->
            val distance = sqrt((touchPoint.x - point.x).pow(2) + (touchPoint.y - point.y).pow(2))
            distance < touchRadius
        }
    }

    // Public methods
    fun getQuadrilateralPoints(): FloatArray {
        return quadPoints.flatMap { listOf(it.x, it.y) }.toFloatArray()
    }

    fun setQuadrilateralPoints(points: FloatArray?) {
        if (points != null && points.size == 8) {
            points.toList().chunked(2).forEachIndexed { index, pair ->
                quadPoints[index] = PointF(pair[0], pair[1])
            }
        } else {
            // Default to image corners
            val width = drawable.intrinsicWidth.toFloat()
            val height = drawable.intrinsicHeight.toFloat()
            quadPoints[0] = PointF(0f, 0f)
            quadPoints[1] = PointF(width, 0f)
            quadPoints[2] = PointF(width, height)
            quadPoints[3] = PointF(0f, height)
        }
        invalidate()
    }

    override fun setImageBitmap(bm: Bitmap?) {
        super.setImageBitmap(bm)
        this.underlyingBitmap = bm
        updateCoordinateMatrices()
        setQuadrilateralPoints(null) // Reset points on new image
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateCoordinateMatrices()
    }

    private fun updateCoordinateMatrices() {
        if (drawable == null || width == 0 || height == 0) return
        val imageRect = RectF(0f, 0f, drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat())
        val viewRect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        imageToViewMatrix.setRectToRect(imageRect, viewRect, Matrix.ScaleToFit.CENTER)
        imageToViewMatrix.invert(viewToImageMatrix)
    }

    private fun mapPointToImage(point: PointF): PointF {
        val pointArray = floatArrayOf(point.x, point.y)
        viewToImageMatrix.mapPoints(pointArray)
        return PointF(pointArray[0], pointArray[1])
    }

    private fun mapPointsToView(points: Array<PointF>): Array<PointF> {
        val result = Array(points.size) { PointF() }
        val flatPoints = points.flatMap { listOf(it.x, it.y) }.toFloatArray()
        imageToViewMatrix.mapPoints(flatPoints)
        flatPoints.toList().chunked(2).forEachIndexed { index, pair ->
            result[index] = PointF(pair[0], pair[1])
        }
        return result
    }

    private fun Matrix.mapRadius(radius: Float): Float {
        val array = floatArrayOf(radius, 0f)
        this.mapVectors(array)
        return sqrt(array[0].pow(2) + array[1].pow(2))
    }
}
