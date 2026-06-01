package com.yehyun.whatshouldiweartoday.ui.custom

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class CropOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val cropRect = RectF()
    private val minCropSize = 100f

    // Handle size for corner drag
    private val handleRadius = 24f

    private val dimPaint = Paint().apply {
        color = Color.parseColor("#80000000")
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
    }

    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val handleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CC2B8EE8")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private enum class DragMode {
        NONE, MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
        LEFT, RIGHT, TOP, BOTTOM
    }

    private var dragMode = DragMode.NONE
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Initialize crop rect to center 60% area
        val marginX = w * 0.2f
        val marginY = h * 0.2f
        cropRect.set(marginX, marginY, w - marginX, h - marginY)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        // Draw dimmed overlay outside crop rect using path with even-odd fill
        val path = Path().apply {
            addRect(0f, 0f, w, h, Path.Direction.CW)
            addRect(cropRect.left, cropRect.top, cropRect.right, cropRect.bottom, Path.Direction.CCW)
        }
        path.fillType = Path.FillType.EVEN_ODD
        canvas.drawPath(path, dimPaint)

        // Draw dashed border
        canvas.drawRect(cropRect, borderPaint)

        // Draw corner handles
        drawHandle(canvas, cropRect.left, cropRect.top)
        drawHandle(canvas, cropRect.right, cropRect.top)
        drawHandle(canvas, cropRect.left, cropRect.bottom)
        drawHandle(canvas, cropRect.right, cropRect.bottom)

        // Draw edge midpoint handles
        drawHandle(canvas, cropRect.centerX(), cropRect.top)
        drawHandle(canvas, cropRect.centerX(), cropRect.bottom)
        drawHandle(canvas, cropRect.left, cropRect.centerY())
        drawHandle(canvas, cropRect.right, cropRect.centerY())
    }

    private fun drawHandle(canvas: Canvas, x: Float, y: Float) {
        canvas.drawCircle(x, y, handleRadius * 0.6f, handlePaint)
        canvas.drawCircle(x, y, handleRadius * 0.6f, handleStrokePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragMode = detectDragMode(x, y)
                if (dragMode == DragMode.NONE) return false
                lastTouchX = x
                lastTouchY = y
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = x - lastTouchX
                val dy = y - lastTouchY
                applyDrag(dx, dy)
                lastTouchX = x
                lastTouchY = y
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragMode = DragMode.NONE
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun detectDragMode(x: Float, y: Float): DragMode {
        val threshold = handleRadius * 2f

        // Check corners first
        if (nearPoint(x, y, cropRect.left, cropRect.top, threshold)) return DragMode.TOP_LEFT
        if (nearPoint(x, y, cropRect.right, cropRect.top, threshold)) return DragMode.TOP_RIGHT
        if (nearPoint(x, y, cropRect.left, cropRect.bottom, threshold)) return DragMode.BOTTOM_LEFT
        if (nearPoint(x, y, cropRect.right, cropRect.bottom, threshold)) return DragMode.BOTTOM_RIGHT

        // Check edges
        if (abs(x - cropRect.left) < threshold && y in cropRect.top..cropRect.bottom) return DragMode.LEFT
        if (abs(x - cropRect.right) < threshold && y in cropRect.top..cropRect.bottom) return DragMode.RIGHT
        if (abs(y - cropRect.top) < threshold && x in cropRect.left..cropRect.right) return DragMode.TOP
        if (abs(y - cropRect.bottom) < threshold && x in cropRect.left..cropRect.right) return DragMode.BOTTOM

        // Check inside for move
        if (cropRect.contains(x, y)) return DragMode.MOVE

        return DragMode.NONE
    }

    private fun nearPoint(x: Float, y: Float, px: Float, py: Float, threshold: Float): Boolean {
        return abs(x - px) < threshold && abs(y - py) < threshold
    }

    private fun applyDrag(dx: Float, dy: Float) {
        val w = width.toFloat()
        val h = height.toFloat()

        when (dragMode) {
            DragMode.MOVE -> {
                var newLeft = cropRect.left + dx
                var newTop = cropRect.top + dy
                val cw = cropRect.width()
                val ch = cropRect.height()

                newLeft = max(0f, min(newLeft, w - cw))
                newTop = max(0f, min(newTop, h - ch))
                cropRect.set(newLeft, newTop, newLeft + cw, newTop + ch)
            }
            DragMode.TOP_LEFT -> {
                cropRect.left = max(0f, min(cropRect.left + dx, cropRect.right - minCropSize))
                cropRect.top = max(0f, min(cropRect.top + dy, cropRect.bottom - minCropSize))
            }
            DragMode.TOP_RIGHT -> {
                cropRect.right = min(w, max(cropRect.right + dx, cropRect.left + minCropSize))
                cropRect.top = max(0f, min(cropRect.top + dy, cropRect.bottom - minCropSize))
            }
            DragMode.BOTTOM_LEFT -> {
                cropRect.left = max(0f, min(cropRect.left + dx, cropRect.right - minCropSize))
                cropRect.bottom = min(h, max(cropRect.bottom + dy, cropRect.top + minCropSize))
            }
            DragMode.BOTTOM_RIGHT -> {
                cropRect.right = min(w, max(cropRect.right + dx, cropRect.left + minCropSize))
                cropRect.bottom = min(h, max(cropRect.bottom + dy, cropRect.top + minCropSize))
            }
            DragMode.LEFT -> {
                cropRect.left = max(0f, min(cropRect.left + dx, cropRect.right - minCropSize))
            }
            DragMode.RIGHT -> {
                cropRect.right = min(w, max(cropRect.right + dx, cropRect.left + minCropSize))
            }
            DragMode.TOP -> {
                cropRect.top = max(0f, min(cropRect.top + dy, cropRect.bottom - minCropSize))
            }
            DragMode.BOTTOM -> {
                cropRect.bottom = min(h, max(cropRect.bottom + dy, cropRect.top + minCropSize))
            }
            DragMode.NONE -> {}
        }
    }

    /**
     * Returns the crop rectangle as a fraction of the view dimensions [0..1].
     */
    fun getCropRectF(): RectF {
        val w = width.toFloat()
        val h = height.toFloat()
        return RectF(
            cropRect.left / w,
            cropRect.top / h,
            cropRect.right / w,
            cropRect.bottom / h
        )
    }
}
