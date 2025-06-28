package com.yehyun.whatshouldiweartoday.ui.home

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.yehyun.whatshouldiweartoday.R
import com.yehyun.whatshouldiweartoday.data.database.ClothingItem
import java.io.File

class RecommendationAdapter(
    private val onItemClicked: (ClothingItem) -> Unit
) : RecyclerView.Adapter<RecommendationAdapter.RecommendationViewHolder>() {

    private var items: List<ClothingItem> = listOf()
    private var packableOuterId: Int? = null

    fun submitList(newItems: List<ClothingItem>, packableId: Int? = null) {
        items = newItems
        packableOuterId = packableId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecommendationViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_recommendation_clothing_square, parent, false)
        return RecommendationViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecommendationViewHolder, position: Int) {
        val currentItem = items[position]
        holder.itemView.setOnClickListener {
            onItemClicked(currentItem)
        }
        // [개선] 챙겨갈 아우터인지 여부를 함께 전달하여 아이콘 표시
        holder.bind(currentItem, currentItem.id == packableOuterId)
    }

    override fun getItemCount(): Int = items.size

    class RecommendationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.item_image_recommend_square)
        private val iconView: ImageView = itemView.findViewById(R.id.icon_packable)

        fun bind(item: ClothingItem, isPackable: Boolean) {
            val imageToShow = if (item.useProcessedImage && item.processedImageUri != null) {
                item.processedImageUri
            } else {
                item.imageUri
            }
            Glide.with(itemView.context).load(Uri.fromFile(File(imageToShow))).into(imageView)

            // 챙겨갈 아우터일 경우에만 아이콘 표시
            iconView.isVisible = isPackable
        }
    }
}
