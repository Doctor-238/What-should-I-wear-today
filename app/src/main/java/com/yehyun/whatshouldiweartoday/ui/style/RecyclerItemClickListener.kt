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
    private val onItemLongClick: (view: View, position: Int) -> Unit
) : RecyclerView.OnItemTouchListener {

    private val handler = Handler(Looper.getMainLooper())
    private var touchedView: View? = null
    private var isDragging = false
    private var initialX = 0f
    private var initialY = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

    var isRecyclerViewBusy = false
    var onDragStateChanged: ((Boolean) -> Unit)? = null

    init {
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                val newDraggingState = newState != RecyclerView.SCROLL_STATE_IDLE
                if (isDragging != newDraggingState) {
                    isDragging = newDraggingState
                    onDragStateChanged?.invoke(isDragging)
                }

                if (newDraggingState) {
                    handler.removeCallbacks(longPressRunnable)
                    if (touchedView != null) {
                        touchedView?.isPressed = false
                        touchedView = null
                    }
                }
            }
        })
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

    fun cancelDrag() {
        if (isDragging) {
            isDragging = false
            onDragStateChanged?.invoke(false)
        }
        handler.removeCallbacks(longPressRunnable)
        if (touchedView != null) {
            touchedView?.isPressed = false
            touchedView = null
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
                        onDragStateChanged?.invoke(true)
                    }
                    handler.removeCallbacks(longPressRunnable)
                    touchedView?.isPressed = false
                }

            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                val wasDragging = isDragging
                if (isDragging) {
                    isDragging = false
                    onDragStateChanged?.invoke(false)
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