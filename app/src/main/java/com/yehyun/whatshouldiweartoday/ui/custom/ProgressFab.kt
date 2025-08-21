package com.yehyun.whatshouldiweartoday.ui.custom

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.animation.DecelerateInterpolator
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.yehyun.whatshouldiweartoday.R

class ProgressFab @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FloatingActionButton(context, attrs, defStyleAttr) {

    private var isProgressMode = false
    private var progressAnimator: ObjectAnimator? = null

    var progress: Int = 0
        set(value) {
            field = value
            invalidate() // 이 값이 변할 때마다 뷰를 다시 그려서 애니메이션 효과를 줌
        }

    private var displayPercentage: Int = 0


    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 18f
        color = Color.parseColor("#0059ff")
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = Color.parseColor("#424242")
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, 16f, resources.displayMetrics
        )
    }

    private val progressRect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (isProgressMode) {
            val size = width.toFloat()
            val strokeWidth = progressPaint.strokeWidth
            progressRect.set(strokeWidth / 2, strokeWidth / 2, size - strokeWidth / 2, size - strokeWidth / 2)

            val sweepAngle = progress * 3.6f
            canvas.drawArc(progressRect, -90f, sweepAngle, false, progressPaint)

            val text = "$displayPercentage%"
            val x = width / 2f
            val y = (height / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
            canvas.drawText(text, x, y, textPaint)
        }
    }

    fun showProgress(percentage: Int) {
        if (!isProgressMode) {
            isProgressMode = true
            isEnabled = false
            setImageResource(0)
        }

        this.displayPercentage = percentage

        progressAnimator?.cancel()

        // 애니메이션은 progress 프로퍼티를 대상으로 실행
        progressAnimator = ObjectAnimator.ofInt(this, "progress", progress, percentage).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
        }
        progressAnimator?.start()
    }

    fun hideProgress() {
        progressAnimator?.cancel()

        if (isProgressMode) {
            isProgressMode = false
            isEnabled = true
            progress = 0
            displayPercentage = 0 // 텍스트 값도 초기화
            setImageResource(R.drawable.multiple)
            invalidate()
        }
    }
}