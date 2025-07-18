// 파일 경로: app/src/main/java/com/yehyun/whatshouldiweartoday/ui/custom/ProgressFab.kt
package com.yehyun.whatshouldiweartoday.ui.custom

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.*
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

    // 애니메이션 효과를 위한 프로퍼티 (진행 바 각도 계산에 사용)
    var progress: Int = 0
        set(value) {
            field = value
            invalidate() // 이 값이 변할 때마다 뷰를 다시 그려서 애니메이션 효과를 줌
        }

    // ▼▼▼▼▼ 핵심 수정 부분 1: 텍스트 표시용 프로퍼티 추가 ▼▼▼▼▼
    private var displayPercentage: Int = 0
    // ▲▲▲▲▲ 핵심 수정 부분 1 ▲▲▲▲▲


    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 12f
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

            // 진행 바는 애니메이션 값(progress)을 사용
            val sweepAngle = progress * 3.6f
            canvas.drawArc(progressRect, -90f, sweepAngle, false, progressPaint)

            // ▼▼▼▼▼ 핵심 수정 부분 2: 텍스트는 고정된 목표 값(displayPercentage)을 사용 ▼▼▼▼▼
            val text = "$displayPercentage%"
            // ▲▲▲▲▲ 핵심 수정 부분 2 ▲▲▲▲▲
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

        // ▼▼▼▼▼ 핵심 수정 부분 3: 텍스트용 값은 즉시 업데이트 ▼▼▼▼▼
        this.displayPercentage = percentage
        // ▲▲▲▲▲ 핵심 수정 부분 3 ▲▲▲▲▲

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