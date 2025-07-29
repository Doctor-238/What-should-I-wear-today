package com.yehyun.whatshouldiweartoday.ui.style

import android.annotation.SuppressLint
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.yehyun.whatshouldiweartoday.R
import com.yehyun.whatshouldiweartoday.data.database.ClothingItem
import com.yehyun.whatshouldiweartoday.data.database.StyleWithItems
import com.yehyun.whatshouldiweartoday.ui.home.RecommendationAdapter

class SavedStylesAdapter(
    private val onItemClicked: (StyleWithItems) -> Unit,
    private val onItemLongClicked: (StyleWithItems) -> Unit,
    private val isDeleteMode: () -> Boolean,
    private val isItemSelected: (Long) -> Boolean
) : RecyclerView.Adapter<SavedStylesAdapter.StyleViewHolder>() {

    private var styles: List<StyleWithItems> = listOf()

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(newStyles: List<StyleWithItems>) {
        styles = newStyles
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StyleViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_saved_style, parent, false)
        return StyleViewHolder(view)
    }

    override fun onBindViewHolder(holder: StyleViewHolder, position: Int) {
        val currentStyle = styles[position]
        holder.bind(currentStyle, onItemClicked, onItemLongClicked, isDeleteMode, isItemSelected)
    }

    override fun onBindViewHolder(holder: StyleViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            for (payload in payloads) {
                if (payload == "DELETE_MODE_CHANGED") {
                    holder.updateDeleteModeUI(isDeleteMode())
                }
                if (payload == "SELECTION_CHANGED") {
                    holder.updateSelectionUI(isItemSelected(styles[position].style.styleId))
                }
            }
        }
    }

    override fun getItemCount(): Int = styles.size

    class StyleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val styleName: TextView = itemView.findViewById(R.id.tv_style_name)
        private val itemsRecyclerView: RecyclerView = itemView.findViewById(R.id.rv_style_items)
        private val itemsAdapter = RecommendationAdapter()
        private val deleteCheckbox: ImageView = itemView.findViewById(R.id.iv_delete_checkbox)

        init {
            itemsRecyclerView.adapter = itemsAdapter
        }

        @SuppressLint("ClickableViewAccessibility")
        fun bind(
            styleWithItems: StyleWithItems,
            clickAction: (StyleWithItems) -> Unit,
            longClickAction: (StyleWithItems) -> Unit,
            isDeleteMode: () -> Boolean,
            isItemSelected: (Long) -> Boolean
        ) {
            styleName.text = styleWithItems.style.styleName

            val categoryOrder = mapOf(
                "상의" to 1, "하의" to 2, "아우터" to 3, "신발" to 4,
                "가방" to 5, "모자" to 6, "기타" to 7
            )
            val sortedItems = styleWithItems.items.sortedWith(
                compareBy<ClothingItem> { categoryOrder[it.category] ?: 8 }
                    .thenBy { it.suitableTemperature }
            )
            itemsAdapter.submitList(sortedItems)

            // GestureDetector를 사용하여 클릭과 롱클릭 이벤트 모두 처리
            val gestureDetector = GestureDetector(itemView.context, object : GestureDetector.SimpleOnGestureListener() {
                // 짧게 클릭했을 때의 동작
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    clickAction(styleWithItems)
                    return true
                }

                // 길게 눌렀을 때의 동작
                override fun onLongPress(e: MotionEvent) {
                    if (!isDeleteMode()) {
                        longClickAction(styleWithItems)
                    }
                }
            })

            // 아이템뷰 전체에 터치 리스너 설정
            itemView.setOnTouchListener { _, event ->
                gestureDetector.onTouchEvent(event)
                true // 이벤트를 소비하여 중복 처리를 방지
            }

            // 내부 RecyclerView 터치 이벤트가 상위로 전달되도록 설정
            itemsRecyclerView.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
                override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                    gestureDetector.onTouchEvent(e)
                    return false // 이벤트를 가로채지 않고 계속 전달
                }

                override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}
                override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
            })


            updateDeleteModeUI(isDeleteMode())
            updateSelectionUI(isItemSelected(styleWithItems.style.styleId))
        }

        fun updateDeleteModeUI(isDelete: Boolean) {
            if (isDelete && deleteCheckbox.visibility == View.GONE) {
                deleteCheckbox.visibility = View.VISIBLE
                deleteCheckbox.startAnimation(AnimationUtils.loadAnimation(itemView.context, R.anim.scale_in))
            } else if (!isDelete && deleteCheckbox.visibility == View.VISIBLE) {
                deleteCheckbox.startAnimation(AnimationUtils.loadAnimation(itemView.context, R.anim.scale_out))
                deleteCheckbox.visibility = View.GONE
            }
        }

        fun updateSelectionUI(isSelected: Boolean) {
            deleteCheckbox.setImageResource(
                if (isSelected) R.drawable.ic_checkbox_checked_custom
                else R.drawable.ic_checkbox_unchecked_custom
            )
        }
    }
}