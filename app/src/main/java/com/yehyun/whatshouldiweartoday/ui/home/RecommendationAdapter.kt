package com.yehyun.whatshouldiweartoday.ui.home

import android.content.res.Configuration
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
import com.yehyun.whatshouldiweartoday.data.preference.SettingsManager
import com.yehyun.whatshouldiweartoday.ui.closet.AddClothingViewModel
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
        val context = parent.context
        val isTablet = (context.resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE
        if (isTablet) {
            val newSize = (140 * context.resources.displayMetrics.density).toInt()
            val layoutParams = view.layoutParams
            layoutParams.width = newSize
            layoutParams.height = newSize
            view.layoutParams = layoutParams
        }
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
        private val iconView: ImageView = itemView.findViewById(R.id.icon_special)
        private val cardView: MaterialCardView = itemView as MaterialCardView
        private val settingsManager = SettingsManager(itemView.context)

        fun bind(item: ClothingItem, isPackable: Boolean, isRecommended: Boolean) {
            val imageToShow = if (item.useProcessedImage && item.processedImageUri != null) item.processedImageUri else item.imageUri
            Glide.with(itemView.context).load(Uri.fromFile(File(imageToShow))).into(imageView)

            if (settingsManager.showRecommendationIcon) {
                if (isPackable) {
                    iconView.setImageResource(R.drawable.ic_packable_bag)
                    iconView.isVisible = true
                } else if (isRecommended) {
                    iconView.setImageResource(R.drawable.sun)
                    iconView.isVisible = true
                } else {
                    iconView.isVisible = false
                }
            } else {
                iconView.isVisible = false
            }

            val context = itemView.context
            val fitColor = getFitBorderColor(context, item)
            val defaultColor = ContextCompat.getColor(context, R.color.weather_card_blue_bg)
            val thickness = if (fitColor != defaultColor) 2.5f else 1.5f
            cardView.strokeWidth = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, thickness, context.resources.displayMetrics
            ).toInt()
            cardView.strokeColor = fitColor
        }

        private fun getFitBorderColor(context: android.content.Context, item: ClothingItem): Int {
            val defaultColor = ContextCompat.getColor(context, R.color.weather_card_blue_bg)
            if (!settingsManager.bodyFitEnabled || !settingsManager.bodyFitBorderEnabled || !settingsManager.isBodyRegistered) {
                return defaultColor
            }
            val level = AddClothingViewModel.calculateFitLevel(
                settingsManager.estimatedHeight, settingsManager.estimatedWeight,
                item.fitMinHeight, item.fitMaxHeight,
                item.fitMinWeight, item.fitMaxWeight
            )
            return when (level) {
                AddClothingViewModel.FIT_VERY_GOOD, AddClothingViewModel.FIT_GOOD ->
                    ContextCompat.getColor(context, R.color.fit_green)
                AddClothingViewModel.FIT_BAD, AddClothingViewModel.FIT_VERY_BAD ->
                    ContextCompat.getColor(context, R.color.fit_red)
                else -> defaultColor
            }
        }
    }
}