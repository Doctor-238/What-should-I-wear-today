package com.yehyun.whatshouldiweartoday.ui.style

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.yehyun.whatshouldiweartoday.R
import com.yehyun.whatshouldiweartoday.data.database.ClothingItem
import java.io.File

// ▼▼▼▼▼ 핵심 수정: 생성자에서 클릭 리스너를 모두 제거합니다. ▼▼▼▼▼
class SaveStyleAdapter : RecyclerView.Adapter<SaveStyleAdapter.SelectableViewHolder>() {

    private var allItems: List<ClothingItem> = listOf()
    private var selectedItemIds = setOf<Int>()

    // ▼▼▼▼▼ 핵심 수정: 외부에서 아이템 정보를 가져올 수 있도록 함수를 추가합니다. ▼▼▼▼▼
    fun getItem(position: Int): ClothingItem? {
        return allItems.getOrNull(position)
    }

    fun submitList(items: List<ClothingItem>) {
        this.allItems = items
        notifyDataSetChanged()
    }

    fun setSelectedItems(ids: Set<Int>) {
        this.selectedItemIds = ids
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SelectableViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_clothing_selectable, parent, false)
        return SelectableViewHolder(view)
    }

    override fun onBindViewHolder(holder: SelectableViewHolder, position: Int) {
        val item = allItems[position]
        val isSelected = item.id in selectedItemIds

        holder.bind(item, isSelected)

        // ▼▼▼▼▼ 핵심 수정: 클릭 및 롱클릭 리스너 설정을 모두 제거합니다. ▼▼▼▼▼
        // holder.itemView.setOnClickListener { ... }
        // holder.itemView.setOnLongClickListener { ... }
    }

    override fun getItemCount(): Int = allItems.size

    class SelectableViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.item_image_selectable)
        private val checkIcon: ImageView = itemView.findViewById(R.id.icon_checked)

        fun bind(item: ClothingItem, isSelected: Boolean) {
            val imageToShow = if (item.useProcessedImage && item.processedImageUri != null) {
                item.processedImageUri
            } else {
                item.imageUri
            }
            Glide.with(itemView.context)
                .load(Uri.fromFile(File(imageToShow)))
                .into(imageView)

            checkIcon.visibility = if (isSelected) View.VISIBLE else View.GONE
        }
    }
}