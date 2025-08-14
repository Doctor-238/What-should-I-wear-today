// main/java/com/yehyun/whatshouldiweartoday/ui/style/RecyclerItemClickListener.kt

package com.yehyun.whatshouldiweartoday.ui.style

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

// ▼▼▼▼▼ 핵심 수정: 인터페이스를 클래스 밖으로 이동시켜 독립적으로 만듭니다. ▼▼▼▼▼
interface OnDragStateChangedListener {
    fun onDragStartDelayed()
    fun onDragEnd()
}

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

    private var dragStateListener: OnDragStateChangedListener? = null
    private var longDragRunnable = Runnable {
        dragStateListener?.onDragStartDelayed()
    }

    init {
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState != RecyclerView.SCROLL_STATE_IDLE) {
                    handler.removeCallbacks(longDragRunnable)
                    if (isDragging) {
                        isDragging = false
                        dragStateListener?.onDragEnd()
                    }
                    if (touchedView != null) {
                        touchedView?.isPressed = false
                        touchedView = null
                    }
                }
            }
        })
    }

    fun setOnDragStateChangedListener(listener: OnDragStateChangedListener) {
        this.dragStateListener = listener
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
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = e.x
                initialY = e.y
                isDragging = false
                touchedView = rv.findChildViewUnder(e.x, e.y)
                touchedView?.let { view ->
                    view.isPressed = true
                    handler.postDelayed(longPressRunnable, longPressTimeout)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(e.x - initialX)
                val dy = abs(e.y - initialY)
                if (dx > touchSlop || dy > touchSlop) {
                    if (!isDragging) {
                        isDragging = true
                        dragStateListener?.onDragEnd()
                        handler.postDelayed(longDragRunnable, 500)
                    }
                    handler.removeCallbacks(longPressRunnable)
                    touchedView?.isPressed = false
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                handler.removeCallbacks(longDragRunnable)
                if (isDragging) {
                    isDragging = false
                    dragStateListener?.onDragEnd()
                }

                touchedView?.let { view ->
                    val wasPressed = view.isPressed
                    view.isPressed = false
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

    fun cancelDragState() {
        handler.removeCallbacks(longDragRunnable)
        if (isDragging) {
            isDragging = false
            dragStateListener?.onDragEnd()
        }
    }
}