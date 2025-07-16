package com.yehyun.whatshouldiweartoday.ui.custom

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.TypedValue
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.yehyun.whatshouldiweartoday.R

class ProgressFab @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FloatingActionButton(context, attrs, defStyleAttr) {

    private var progress = 0
    private var isProgressMode = false

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 12f
        // ▼▼▼▼▼ 핵심 수정 부분 ▼▼▼▼▼
        color = Color.parseColor("#2196F3")
        // ▲▲▲▲▲ 핵심 수정 부분 ▲▲▲▲▲
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
            // 원형 진행 바 그리기
            val size = width.toFloat()
            val strokeWidth = progressPaint.strokeWidth
            progressRect.set(strokeWidth / 2, strokeWidth / 2, size - strokeWidth / 2, size - strokeWidth / 2)
            val sweepAngle = progress * 3.6f
            canvas.drawArc(progressRect, -90f, sweepAngle, false, progressPaint)

            // 퍼센트 텍스트 그리기 (정중앙 정렬)
            val text = "$progress%"
            val x = width / 2f
            val y = (height / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
            canvas.drawText(text, x, y, textPaint)
        }
    }

    fun showProgress(percentage: Int) {
        this.progress = percentage
        if (!isProgressMode) {
            this.isProgressMode = true
            this.isEnabled = false
            setImageResource(0) // 기존 아이콘 제거
        }
        invalidate() // 뷰를 다시 그리도록 요청
    }

    fun hideProgress() {
        if (isProgressMode) {
            this.isProgressMode = false
            this.isEnabled = true
            setImageResource(R.drawable.multiple) // [수정] 아이콘을 multiple로 복구
            invalidate()
        }
    }
}