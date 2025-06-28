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

class SaveStyleAdapter(
    private val onItemClicked: (item: ClothingItem, isSelected: Boolean) -> Unit
) : RecyclerView.Adapter<SaveStyleAdapter.SelectableViewHolder>() {

    private var allItems: List<ClothingItem> = listOf()
    private var selectedItemIds = setOf<Int>()

    // Fragment로부터 전체 옷 목록을 전달받는 함수
    fun submitList(items: List<ClothingItem>) {
        this.allItems = items
        notifyDataSetChanged()
    }

    // Fragment로부터 현재 선택된 아이템들의 ID 목록을 전달받아, 체크 표시를 업데이트하는 함수
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
        holder.itemView.setOnClickListener {
            // 아이템이 클릭되면, Fragment에게 "이 아이템이 클릭되었고, 현재 선택 상태는 OOO입니다" 라고 보고합니다.
            onItemClicked(item, isSelected)
        }
    }

    override fun getItemCount(): Int = allItems.size

    class SelectableViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.item_image_selectable)
        private val checkIcon: ImageView = itemView.findViewById(R.id.icon_checked)

        fun bind(item: ClothingItem, isSelected: Boolean) {
            val imageToShow = item.processedImageUri ?: item.imageUri
            Glide.with(itemView.context)
                .load(Uri.fromFile(File(imageToShow!!)))
                .into(imageView)

            checkIcon.visibility = if (isSelected) View.VISIBLE else View.GONE
        }
    }
}