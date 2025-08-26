package com.yehyun.whatshouldiweartoday.ui.home

import android.graphics.Color
import android.net.Uri
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView
import com.yehyun.whatshouldiweartoday.R
import com.yehyun.whatshouldiweartoday.data.database.ClothingItem
import java.io.File

class RecommendationAdapter(
    private val onItemClicked: ((ClothingItem) -> Unit)? = null,
    private val onItemLongClicked: ((ClothingItem) -> Unit)? = null
) : RecyclerView.Adapter<RecommendationAdapter.RecommendationViewHolder>() {

    private var items: List<ClothingItem> = listOf()
    private var packableOuters: List<ClothingItem> = emptyList()
    private var recommendedIds: Set<Int> = emptySet()

    fun submitList(newItems: List<ClothingItem>, packableOuters: List<ClothingItem> = emptyList()) {
        items = newItems
        this.packableOuters = packableOuters
        notifyDataSetChanged()
    }

    fun setRecommendedIds(ids: Set<Int>) {
        this.recommendedIds = ids
        notifyDataSetChanged()
    }

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

        val isPackable = packableOuters.any { it.id == currentItem.id }
        val isRecommended = recommendedIds.contains(currentItem.id)
        holder.bind(currentItem, isPackable, isRecommended)
    }

    override fun getItemCount(): Int = items.size

    class RecommendationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.item_image_recommend_square)
        private val iconView: ImageView = itemView.findViewById(R.id.icon_packable)
        private val cardView: MaterialCardView = itemView as MaterialCardView

        fun bind(item: ClothingItem, isPackable: Boolean, isRecommended: Boolean) {
            val imageToShow = if (item.useProcessedImage && item.processedImageUri != null) item.processedImageUri else item.imageUri
            Glide.with(itemView.context).load(Uri.fromFile(File(imageToShow))).into(imageView)
            iconView.isVisible = isPackable

            val context = itemView.context
            if (isRecommended) {
                cardView.strokeColor = ContextCompat.getColor(context, R.color.temp_high_red)
                cardView.strokeWidth = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 3.5f, context.resources.displayMetrics
                ).toInt()
            } else {
                cardView.strokeColor = ContextCompat.getColor(context, R.color.weather_card_blue_bg)
                cardView.strokeWidth = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 1.5f, context.resources.displayMetrics
                ).toInt()
            }
        }
    }
}