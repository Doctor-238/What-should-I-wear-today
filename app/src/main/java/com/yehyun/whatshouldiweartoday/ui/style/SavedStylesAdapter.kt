package com.yehyun.whatshouldiweartoday.ui.style

import android.view.LayoutInflater
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
        holder.itemView.setOnClickListener {
            onItemClicked(currentStyle)
        }
        holder.bind(currentStyle)
    }

    override fun getItemCount(): Int = styles.size

    class StyleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val styleName: TextView = itemView.findViewById(R.id.tv_style_name)
        private val itemsRecyclerView: RecyclerView = itemView.findViewById(R.id.rv_style_items)
        // [수정] 클릭 이벤트가 필요 없으므로 빈 람다를 전달합니다.
        private val itemsAdapter = RecommendationAdapter {}

        init {
            itemsRecyclerView.adapter = itemsAdapter
        }

        fun bind(styleWithItems: StyleWithItems) {
            styleName.text = styleWithItems.style.styleName

            // [추가] 정렬을 위한 카테고리 순서 정의
            val categoryOrder = mapOf(
                "상의" to 1,
                "하의" to 2,
                "아우터" to 3,
                "신발" to 4,
                "가방" to 5,
                "모자" to 6,
                "기타" to 7
            )

            // [추가] 새로운 정렬 규칙에 따라 옷 아이템 목록을 정렬
            val sortedItems = styleWithItems.items.sortedWith(
                compareBy<ClothingItem> { categoryOrder[it.category] ?: 8 } // 1. 카테고리 순서로 정렬
                    .thenBy { it.suitableTemperature } // 2. 온도가 낮은 순으로 정렬
            )

            // 정렬된 목록을 어댑터에 전달
            itemsAdapter.submitList(sortedItems)
        }
    }
}
