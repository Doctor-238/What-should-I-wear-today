// app/src/main/java/com/yehyun/whatshouldiweartoday/ui/home/RecommendationAdapter.kt

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

// [핵심 수정] onItemLongClicked 람다 파라미터 추가
class RecommendationAdapter(
    private val onItemClicked: ((ClothingItem) -> Unit)? = null,
    private val onItemLongClicked: ((ClothingItem) -> Unit)? = null // 롱클릭 리스너 추가
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

        // 일반 클릭 리스너 설정
        onItemClicked?.let { listener ->
            holder.itemView.setOnClickListener { listener(currentItem) }
        }

        // [추가] 롱클릭 리스너 설정
        onItemLongClicked?.let { listener ->
            holder.itemView.setOnLongClickListener {
                listener(currentItem)
                true // 이벤트 소비
            }
        }

        holder.bind(currentItem, currentItem.id == packableOuterId)
    }

    override fun getItemCount(): Int = items.size

    class RecommendationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.item_image_recommend_square)
        private val iconView: ImageView = itemView.findViewById(R.id.icon_packable)

        fun bind(item: ClothingItem, isPackable: Boolean) {
            val imageToShow = if (item.useProcessedImage && item.processedImageUri != null) {
                item.processedImageUri
            } else { item.imageUri }
            Glide.with(itemView.context).load(Uri.fromFile(File(imageToShow))).into(imageView)
            iconView.isVisible = isPackable
        }
    }
}