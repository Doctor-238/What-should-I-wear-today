package com.yehyun.whatshouldiweartoday.ui.style

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

class RecyclerItemClickListener(
    context: Context,
    private val recyclerView: RecyclerView,
    private val onItemClick: (view: View, position: Int) -> Unit,
    private val onItemLongClick: (view: View, position: Int) -> Unit,
    private val onLongDragStateChanged: (isLongDragging: Boolean) -> Unit
) : RecyclerView.OnItemTouchListener {

    private val handler = Handler(Looper.getMainLooper())
    private var touchedView: View? = null
    var isDragging = false
    private var initialX = 0f
    private var initialY = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

    var isRecyclerViewBusy = false

    // 롱 드래그 감지를 위한 프로퍼티
    private val longDragHandler = Handler(Looper.getMainLooper())
    private var longDragRunnable: Runnable? = null
    private var isLongDragActive = false

    init {
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)

                if (newState != RecyclerView.SCROLL_STATE_IDLE) {
                    handler.removeCallbacks(longPressRunnable)
                    if (touchedView != null) {
                        touchedView?.isPressed = false
                        touchedView = null
                    }
                    isDragging = true
                } else {
                    isDragging = false
                    longDragRunnable?.let { longDragHandler.removeCallbacks(it) }
                    if (isLongDragActive) {
                        isLongDragActive = false
                        onLongDragStateChanged(false)
                    }
                }
            }
        })
    }

    fun resetDragState() {
        handler.removeCallbacks(longPressRunnable)
        longDragRunnable?.let { longDragHandler.removeCallbacks(it) }

        if (touchedView != null) {
            touchedView?.isPressed = false
            touchedView = null
        }

        isDragging = false
        if (isLongDragActive) {
            isLongDragActive = false
            onLongDragStateChanged(false) // UI 업데이트를 위해 콜백 호출
        }
    }


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
                    if (!isDragging) {
                        isDragging = true
                        longDragRunnable = Runnable {
                            if (isDragging) { // 0.5초 후에도 드래그 중이면
                                isLongDragActive = true
                                onLongDragStateChanged(true) // 알림 표시 콜백 호출
                            }
                        }
                        longDragHandler.postDelayed(longDragRunnable!!, 500)
                    }
                    handler.removeCallbacks(longPressRunnable)
                    touchedView?.isPressed = false
                }

            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                val wasDragging = isDragging
                isDragging = false

                longDragRunnable?.let { longDragHandler.removeCallbacks(it) }
                if (isLongDragActive) {
                    isLongDragActive = false
                    onLongDragStateChanged(false)
                }

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