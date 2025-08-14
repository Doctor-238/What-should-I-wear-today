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
    var isDragging = false // isDragging을 public으로 변경하여 외부에서 접근 가능하도록 함
    private var initialX = 0f
    private var initialY = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

    var isRecyclerViewBusy = false

    // ▼▼▼▼▼ 핵심 수정: 스크롤 상태를 감지하여 터치 상태를 자동으로 리셋하는 리스너 추가 ▼▼▼▼▼
    init {
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)

                // 스크롤이 시작되면(사용자 드래그, 자동 스크롤 모두 포함)
                if (newState != RecyclerView.SCROLL_STATE_IDLE) {
                    // 진행 중이던 롱클릭 이벤트를 취소하고, 눌려있던 뷰의 상태를 초기화
                    handler.removeCallbacks(longPressRunnable)
                    if (touchedView != null) {
                        touchedView?.isPressed = false
                        touchedView = null
                    }
                    isDragging = true // 스크롤 중에는 클릭이 되지 않도록 드래그 상태로 설정
                } else {
                    // 스크롤이 멈추면 드래그 상태를 해제
                    isDragging = false
                }
            }
        })
    }
    // ▲▲▲▲▲ 핵심 수정 끝 ▲▲▲▲▲


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
                    }
                    else {
                        view.isPressed = true
                        handler.postDelayed(longPressRunnable, longPressTimeout)
                    }
                }

            }


            MotionEvent.ACTION_MOVE -> {
                val dx = abs(e.x - initialX)
                val dy = abs(e.y - initialY)

                if (dx > touchSlop || dy > touchSlop) {
                    isDragging = true
                    handler.removeCallbacks(longPressRunnable)
                    touchedView?.isPressed = false
                }
                else{
                    isDragging = false
                }

            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                val wasDragging = isDragging // isDragging이 false로 바뀌기 전 상태 저장
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


    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}
    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
}