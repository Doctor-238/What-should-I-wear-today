package com.yehyun.whatshouldiweartoday.ui.closet

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.yehyun.whatshouldiweartoday.R
import com.yehyun.whatshouldiweartoday.data.database.ClothingItem
import java.io.File

class ClothingAdapter(private val onItemClicked: (ClothingItem) -> Unit) : RecyclerView.Adapter<ClothingAdapter.ClothingViewHolder>() {

    private var items: List<ClothingItem> = listOf()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClothingViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_clothing, parent, false)
        return ClothingViewHolder(view)
    }

    override fun onBindViewHolder(holder: ClothingViewHolder, position: Int) {
        val currentItem = items[position]
        holder.itemView.setOnClickListener {
            onItemClicked(currentItem)
        }
        holder.bind(currentItem)
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<ClothingItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    class ClothingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.item_image)
        private val nameView: TextView = itemView.findViewById(R.id.item_name)

        fun bind(item: ClothingItem) {
            nameView.text = item.name

            // [해결 2] useProcessedImage 값에 따라 대표 이미지를 정확히 표시합니다.
            val imageToShow = if (item.useProcessedImage && item.processedImageUri != null) {
                item.processedImageUri
            } else {
                item.imageUri
            }

            Glide.with(itemView.context)
                .load(Uri.fromFile(File(imageToShow)))
                .into(imageView)
        }
    }
}