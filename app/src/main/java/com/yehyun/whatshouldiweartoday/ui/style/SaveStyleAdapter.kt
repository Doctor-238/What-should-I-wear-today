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

// [수정] 생성자에 onItemLongClicked 람다 추가
class SaveStyleAdapter(
    private val onItemClicked: (item: ClothingItem, isSelected: Boolean) -> Unit,
    private val onItemLongClicked: (item: ClothingItem) -> Unit // 꾹 누르기 이벤트 핸들러
) : RecyclerView.Adapter<SaveStyleAdapter.SelectableViewHolder>() {

    private var allItems: List<ClothingItem> = listOf()
    private var selectedItemIds = setOf<Int>()

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

        // 일반 클릭 이벤트
        holder.itemView.setOnClickListener {
            onItemClicked(item, isSelected)
        }

        // [추가] 꾹 누르기(Long Click) 이벤트
        holder.itemView.setOnLongClickListener {
            onItemLongClicked(item)
            true // 이벤트를 소비했음을 시스템에 알림
        }
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