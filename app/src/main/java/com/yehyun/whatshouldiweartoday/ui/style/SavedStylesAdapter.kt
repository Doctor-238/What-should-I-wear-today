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
        holder.bind(currentStyle, onItemClicked)
    }

    override fun getItemCount(): Int = styles.size

    class StyleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val styleName: TextView = itemView.findViewById(R.id.tv_style_name)
        private val itemsRecyclerView: RecyclerView = itemView.findViewById(R.id.rv_style_items)
        private val itemsAdapter = RecommendationAdapter()

        init {
            itemsRecyclerView.adapter = itemsAdapter
        }

        @SuppressLint("ClickableViewAccessibility")
        fun bind(styleWithItems: StyleWithItems, clickAction: (StyleWithItems) -> Unit) {
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

            itemView.setOnClickListener {
                clickAction(styleWithItems)
            }

            val gestureDetector = GestureDetector(itemView.context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    itemView.performClick()
                    return true
                }
            })

            itemsRecyclerView.setOnTouchListener { _, event ->
                gestureDetector.onTouchEvent(event)
                return@setOnTouchListener false
            }
        }
    }
}