package com.yehyun.whatshouldiweartoday.ui.style

import android.annotation.SuppressLint
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.yehyun.whatshouldiweartoday.R
import com.yehyun.whatshouldiweartoday.data.database.ClothingItem
import com.yehyun.whatshouldiweartoday.data.database.StyleWithItems
import com.yehyun.whatshouldiweartoday.ui.home.RecommendationAdapter

class SavedStylesAdapter(
    private val onItemClicked: (StyleWithItems) -> Unit
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
        // ViewHolder에 클릭 이벤트를 실행할 함수를 전달합니다.
        holder.bind(currentStyle) {
            onItemClicked(currentStyle)
        }
    }

    override fun getItemCount(): Int = styles.size

    class StyleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val styleName: TextView = itemView.findViewById(R.id.tv_style_name)
        private val itemsRecyclerView: RecyclerView = itemView.findViewById(R.id.rv_style_items)
        private val itemsAdapter = RecommendationAdapter() // 내부 리사이클러뷰 어댑터 (클릭 리스너 없음)

        init {
            itemsRecyclerView.adapter = itemsAdapter
        }

        @SuppressLint("ClickableViewAccessibility")
        fun bind(styleWithItems: StyleWithItems, clickAction: () -> Unit) {
            styleName.text = styleWithItems.style.styleName

            // --- 옷 정렬 로직 (기존과 동일) ---
            val categoryOrder = mapOf(
                "상의" to 1, "하의" to 2, "아우터" to 3, "신발" to 4,
                "가방" to 5, "모자" to 6, "기타" to 7
            )
            val sortedItems = styleWithItems.items.sortedWith(
                compareBy<ClothingItem> { categoryOrder[it.category] ?: 8 }
                    .thenBy { it.suitableTemperature }
            )
            itemsAdapter.submitList(sortedItems)
            // --- 정렬 로직 끝 ---

            // 아이템 전체(itemView)를 클릭했을 때의 동작 설정
            itemView.setOnClickListener {
                clickAction()
            }

            // 사진 목록(RecyclerView)의 터치 이벤트를 감지할 탐지기 생성
            val gestureDetector = GestureDetector(itemView.context, object : GestureDetector.SimpleOnGestureListener() {
                // '한번 탭' 했을 때만 감지하여 true를 반환
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    // 부모 뷰(itemView)의 클릭 이벤트를 강제로 실행시킴
                    itemView.performClick()
                    return true
                }
            })

            // 사진 목록에 터치 리스너를 설정
            itemsRecyclerView.setOnTouchListener { _, event ->
                // 터치 이벤트가 발생하면, 위에서 만든 탐지기로 전달
                gestureDetector.onTouchEvent(event)
                // true를 반환하여, 사진 목록의 스크롤 등 다른 동작은 막고 탭 동작만 처리
                return@setOnTouchListener true
            }
        }
    }
}