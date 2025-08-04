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
    private var isDragging = false
    private var initialX = 0f
    private var initialY = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

    // 외부(Fragment)에서 RecyclerView의 스크롤 상태를 전달받을 변수
    var isRecyclerViewBusy = false


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
        // RecyclerView가 스크롤 또는 관성 이동 중일 때는 모든 터치 로직을 건너뜁니다.
        if (isRecyclerViewBusy) {
            // 진행 중이던 터치가 있다면 깔끔하게 정리합니다.
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
                var dx = abs(e.x - initialX)
                var dy = abs(e.y - initialY)

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
                isDragging = false
                touchedView?.let { view ->
                    val wasPressed = view.isPressed
                    view.isPressed = false
                    // 드래그 중이 아니었고, 뷰가 눌려있던 상태일 때만 클릭으로 간주
                    if (!isDragging && wasPressed) {
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