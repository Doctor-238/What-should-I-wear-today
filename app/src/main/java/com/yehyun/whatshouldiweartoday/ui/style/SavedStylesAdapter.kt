package com.yehyun.whatshouldiweartoday.ui.style

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.yehyun.whatshouldiweartoday.R
import com.yehyun.whatshouldiweartoday.data.database.StyleWithItems
import com.yehyun.whatshouldiweartoday.ui.home.RecommendationAdapter

class SavedStylesAdapter : RecyclerView.Adapter<SavedStylesAdapter.StyleViewHolder>() {

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
        holder.bind(styles[position])
    }

    override fun getItemCount(): Int = styles.size

    class StyleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val styleName: TextView = itemView.findViewById(R.id.tv_style_name)
        private val itemsRecyclerView: RecyclerView = itemView.findViewById(R.id.rv_style_items)
        private val itemsAdapter = RecommendationAdapter {}

        init {
            itemsRecyclerView.adapter = itemsAdapter
        }

        fun bind(styleWithItems: StyleWithItems) {
            styleName.text = styleWithItems.style.styleName
            itemsAdapter.submitList(styleWithItems.items)
        }
    }
}
