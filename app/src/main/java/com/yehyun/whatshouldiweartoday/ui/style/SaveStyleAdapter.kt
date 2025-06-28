package com.yehyun.whatshouldiweartoday.ui.style

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.yehyun.whatshouldiweartoday.R
import com.yehyun.whatshouldiweartoday.data.database.ClothingItem
import java.io.File

class SaveStyleAdapter : RecyclerView.Adapter<SaveStyleAdapter.SelectableViewHolder>() {

    private var allItems: List<ClothingItem> = listOf()
    private val selectedItemIds = mutableSetOf<Int>()

    fun submitList(items: List<ClothingItem>) {
        this.allItems = items
        notifyDataSetChanged()
    }

    fun setPreselectedItems(ids: IntArray) {
        selectedItemIds.clear()
        selectedItemIds.addAll(ids.toList())
        notifyDataSetChanged()
    }

    fun getSelectedItems(): List<ClothingItem> {
        return allItems.filter { it.id in selectedItemIds }
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
            if (isSelected) {
                selectedItemIds.remove(item.id)
            } else {
                if (selectedItemIds.size < 10) {
                    selectedItemIds.add(item.id)
                } else {
                    Toast.makeText(holder.itemView.context, "최대 10개까지 선택할 수 있습니다.", Toast.LENGTH_SHORT).show()
                }
            }
            notifyItemChanged(position)
        }
    }

    override fun getItemCount(): Int = allItems.size

    class SelectableViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.item_image_selectable)
        private val checkIcon: ImageView = itemView.findViewById(R.id.icon_checked)

        fun bind(item: ClothingItem, isSelected: Boolean) {
            val imageToShow = item.processedImageUri ?: item.imageUri
            Glide.with(itemView.context)
                .load(Uri.fromFile(File(imageToShow)))
                .into(imageView)

            checkIcon.visibility = if (isSelected) View.VISIBLE else View.GONE
        }
    }
}