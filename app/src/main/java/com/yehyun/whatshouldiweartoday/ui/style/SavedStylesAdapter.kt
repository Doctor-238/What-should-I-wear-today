package com.yehyun.whatshouldiweartoday.ui.style

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.yehyun.whatshouldiweartoday.R
import com.yehyun.whatshouldiweartoday.data.database.ClothingItem
import com.yehyun.whatshouldiweartoday.data.database.StyleWithItems
import com.yehyun.whatshouldiweartoday.ui.home.RecommendationAdapter

class SavedStylesAdapter(
    private val isDeleteMode: () -> Boolean,
    private val isItemSelected: (Long) -> Boolean
) : ListAdapter<StyleWithItems, SavedStylesAdapter.StyleViewHolder>(diffUtil) {

    fun getStyleAt(position: Int): StyleWithItems? {
        return getItem(position)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StyleViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_saved_style, parent, false)
        return StyleViewHolder(view)
    }

    override fun onBindViewHolder(holder: StyleViewHolder, position: Int) {
        val currentStyle = getItem(position)
        holder.bind(currentStyle, isDeleteMode, isItemSelected)
    }

    override fun onBindViewHolder(holder: StyleViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            for (payload in payloads) {
                when (payload) {
                    "DELETE_MODE_CHANGED" -> holder.updateDeleteModeUI(isDeleteMode())
                    // ▼▼▼▼▼ 핵심 수정: isItemSelected 람다를 호출하여 Boolean 값을 전달하도록 수정 ▼▼▼▼▼
                    "SELECTION_CHANGED" -> holder.updateSelectionUI(isItemSelected(getItem(position).style.styleId))
                    // ▲▲▲▲▲ 핵심 수정 ▲▲▲▲▲
                }
            }
        }
    }

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

            itemsRecyclerView.setOnTouchListener { v, event ->
                itemView.onTouchEvent(event)
                false
            }

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

    companion object {
        val diffUtil = object : DiffUtil.ItemCallback<StyleWithItems>() {
            override fun areItemsTheSame(oldItem: StyleWithItems, newItem: StyleWithItems): Boolean {
                return oldItem.style.styleId == newItem.style.styleId
            }

            override fun areContentsTheSame(oldItem: StyleWithItems, newItem: StyleWithItems): Boolean {
                return oldItem == newItem
            }
        }
    }
}