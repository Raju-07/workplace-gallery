package com.example.workplacegallery

import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView

class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val imgMatrix = Matrix()
    private var mode = NONE

    private val lastTouch = PointF()
    private var scaleFactor = 1f
    private val minScale = 1f
    private val maxScale = 4.5f

    private val scaleDetector: ScaleGestureDetector
    private val gestureDetector: GestureDetector

    companion object {
        private const val NONE = 0
        private const val DRAG = 1
    }

    init {
        scaleType = ScaleType.MATRIX

        scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scale = detector.scaleFactor
                val targetScale = scaleFactor * scale
                if (targetScale in minScale..maxScale) {
                    scaleFactor = targetScale
                    imgMatrix.postScale(scale, scale, detector.focusX, detector.focusY)
                    imageMatrix = imgMatrix
                }
                return true
            }
        })

        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                resetZoom()
                return true
            }
        })
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fitImageToView()
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        fitImageToView()
    }

    private fun fitImageToView() {
        val d = drawable ?: return
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        if (viewWidth <= 0f || viewHeight <= 0f) return

        val dWidth = d.intrinsicWidth.toFloat()
        val dHeight = d.intrinsicHeight.toFloat()
        if (dWidth <= 0f || dHeight <= 0f) return

        val scale = minOf(viewWidth / dWidth, viewHeight / dHeight)
        val dx = (viewWidth - dWidth * scale) / 2f
        val dy = (viewHeight - dHeight * scale) / 2f

        imgMatrix.reset()
        imgMatrix.postScale(scale, scale)
        imgMatrix.postTranslate(dx, dy)
        imageMatrix = imgMatrix
        scaleFactor = 1f
    }

    fun resetZoom() {
        fitImageToView()
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouch.set(event.x, event.y)
                mode = DRAG
                if (scaleFactor > 1.05f) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (mode == DRAG && scaleFactor > 1.05f) {
                    val dx = event.x - lastTouch.x
                    val dy = event.y - lastTouch.y
                    imgMatrix.postTranslate(dx, dy)
                    imageMatrix = imgMatrix
                    lastTouch.set(event.x, event.y)
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                mode = NONE
                if (scaleFactor <= 1.05f) {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    fitImageToView()
                }
            }
        }
        return true
    }
}
