package com.yehyun.whatshouldiweartoday.ui.style

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

class RecyclerItemClickListener(
    context: Context,
    private val recyclerView: RecyclerView,
    private val onItemClick: (view: View, position: Int) -> Unit,
    private val onItemLongClick: (view: View, position: Int) -> Unit
) : RecyclerView.OnItemTouchListener {

    private val handler = Handler(Looper.getMainLooper())
    private var touchedView: View? = null
    var isDragging = false
    private var initialX = 0f
    private var initialY = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    private val dragDelayTimeout = 500L // 0.5초 딜레이

    var isRecyclerViewBusy = false

    // ▼▼▼▼▼ 핵심 수정 1: 외부와 통신할 리스너 변수 추가 ▼▼▼▼▼
    private var dragStateListener: OnDragStateChangedListener? = null
    // ▲▲▲▲▲ 핵심 수정 1 ▲▲▲▲▲

    init {
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState != RecyclerView.SCROLL_STATE_IDLE) {
                    handler.removeCallbacks(longPressRunnable)
                    handler.removeCallbacks(dragDelayRunnable) // 딜레이 콜백도 제거
                    dragStateListener?.onDragEnd() // 스크롤 시작 시 알림 숨기기
                    if (touchedView != null) {
                        touchedView?.isPressed = false
                        touchedView = null
                    }
                    isDragging = true
                } else {
                    isDragging = false
                }
            }
        })
    }

    // ▼▼▼▼▼ 핵심 수정 2: 0.5초 후 알림을 띄우도록 요청하는 Runnable 추가 ▼▼▼▼▼
    private val dragDelayRunnable = Runnable {
        // 0.5초가 지났을 때 isDragging이 아직 false라면 (즉, 사용자가 움직이지 않았다면)
        if (!isDragging) {
            dragStateListener?.onDragStartDelayed()
        }
    }
    // ▲▲▲▲▲ 핵심 수정 2 ▲▲▲▲▲

    private val longPressRunnable = Runnable {
        touchedView?.let { view ->
            view.isPressed = false
            touchedView = null
            val position = recyclerView.getChildAdapterPosition(view)
            if (position != RecyclerView.NO_POSITION) {
                onItemLongClick(view, position)
            }
        }
    }

    // ▼▼▼▼▼ 핵심 수정 3: 외부에서 리스너를 설정할 수 있는 함수 추가 ▼▼▼▼▼
    fun setOnDragStateChangedListener(listener: OnDragStateChangedListener) {
        this.dragStateListener = listener
    }
    // ▲▲▲▲▲ 핵심 수정 3 ▲▲▲▲▲

    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
        if (isRecyclerViewBusy) {
            if (touchedView != null) {
                handler.removeCallbacks(longPressRunnable)
                touchedView?.isPressed = false
                touchedView = null
            }
            return false
        }

        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = e.x
                initialY = e.y
                touchedView = rv.findChildViewUnder(e.x, e.y)
                touchedView?.let { view ->
                    if(isDragging){
                        view.isPressed = false
                        handler.removeCallbacks(longPressRunnable)
                        handler.removeCallbacks(dragDelayRunnable) // 딜레이 콜백도 제거
                    }
                    else {
                        view.isPressed = true
                        handler.postDelayed(longPressRunnable, longPressTimeout)
                        // ▼▼▼▼▼ 핵심 수정 4: 0.5초 뒤 알림을 띄우도록 예약 ▼▼▼▼▼
                        handler.postDelayed(dragDelayRunnable, dragDelayTimeout)
                        // ▲▲▲▲▲ 핵심 수정 4 ▲▲▲▲▲
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = abs(e.x - initialX)
                val dy = abs(e.y - initialY)

                if (dx > touchSlop || dy > touchSlop) {
                    if (!isDragging) { // 드래그가 처음 시작되는 순간
                        isDragging = true
                        handler.removeCallbacks(longPressRunnable)
                        // ▼▼▼▼▼ 핵심 수정 5: 드래그가 시작되면 알림 예약 취소 및 숨김 요청 ▼▼▼▼▼
                        handler.removeCallbacks(dragDelayRunnable)
                        dragStateListener?.onDragEnd()
                        // ▲▲▲▲▲ 핵심 수정 5 ▲▲▲▲▲
                        touchedView?.isPressed = false
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // ▼▼▼▼▼ 핵심 수정 6: 터치가 끝나면 모든 예약 취소 및 알림 숨김 요청 ▼▼▼▼▼
                handler.removeCallbacks(longPressRunnable)
                handler.removeCallbacks(dragDelayRunnable)
                dragStateListener?.onDragEnd()
                // ▲▲▲▲▲ 핵심 수정 6 ▲▲▲▲▲
                val wasDragging = isDragging
                isDragging = false
                touchedView?.let { view ->
                    val wasPressed = view.isPressed
                    view.isPressed = false
                    if (!wasDragging && wasPressed) {
                        val position = recyclerView.getChildAdapterPosition(view)
                        if (position != RecyclerView.NO_POSITION) {
                            onItemClick(view, position)
                        }
                    }
                }
                touchedView = null
            }
        }
        return false
    }

    // ▼▼▼▼▼ 핵심 수정 7: 외부에서 드래그 상태를 강제로 취소하는 함수 추가 ▼▼▼▼▼
    fun cancelDragState() {
        handler.removeCallbacks(dragDelayRunnable)
        dragStateListener?.onDragEnd()
        isDragging = false
    }
    // ▲▲▲▲▲ 핵심 수정 7 ▲▲▲▲▲

    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}
    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
}