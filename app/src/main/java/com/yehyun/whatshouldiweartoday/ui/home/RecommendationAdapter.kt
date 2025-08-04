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
    private val onItemClicked: ((ClothingItem) -> Unit)? = null,
    private val onItemLongClicked: ((ClothingItem) -> Unit)? = null
) : RecyclerView.Adapter<RecommendationAdapter.RecommendationViewHolder>() {

    private var items: List<ClothingItem> = listOf()
    // ▼▼▼▼▼ 핵심 수정: ID 목록 -> 객체 목록으로 변경 ▼▼▼▼▼
    private var packableOuters: List<ClothingItem> = emptyList()

    fun submitList(newItems: List<ClothingItem>, packableOuters: List<ClothingItem> = emptyList()) {
        items = newItems
        this.packableOuters = packableOuters
        notifyDataSetChanged()
    }
    // ▲▲▲▲▲ 핵심 수정 끝 ▲▲▲▲▲

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecommendationViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_recommendation_clothing_square, parent, false)
        return RecommendationViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecommendationViewHolder, position: Int) {
        val currentItem = items[position]

        onItemClicked?.let { listener ->
            holder.itemView.setOnClickListener { listener(currentItem) }
        }

        onItemLongClicked?.let { listener ->
            holder.itemView.setOnLongClickListener {
                listener(currentItem)
                true
            }
        }

        // ▼▼▼▼▼ 핵심 수정: 현재 아이템이 '챙겨갈 아우터' 목록에 있는지 ID로 확인 ▼▼▼▼▼
        val isPackable = packableOuters.any { it.id == currentItem.id }
        holder.bind(currentItem, isPackable)
        // ▲▲▲▲▲ 핵심 수정 끝 ▲▲▲▲▲
    }

    override fun getItemCount(): Int = items.size

    class RecommendationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.item_image_recommend_square)
        private val iconView: ImageView = itemView.findViewById(R.id.icon_packable)

        fun bind(item: ClothingItem, isPackable: Boolean) {
            val imageToShow = if (item.useProcessedImage && item.processedImageUri != null) item.processedImageUri else item.imageUri
            Glide.with(itemView.context).load(Uri.fromFile(File(imageToShow))).into(imageView)
            iconView.isVisible = isPackable
        }
    }
}