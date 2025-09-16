package com.yehyun.whatshouldiweartoday.ui.style

import android.net.Uri
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView
import com.yehyun.whatshouldiweartoday.R
import com.yehyun.whatshouldiweartoday.data.database.ClothingItem
import com.yehyun.whatshouldiweartoday.data.preference.SettingsManager
import java.io.File

class SaveStyleAdapter(
    private val isItemSelected: (Int) -> Boolean,
    private val isItemRecommended: (Int) -> Boolean,
    private val isItemPackable: (Int) -> Boolean
) : ListAdapter<ClothingItem, SaveStyleAdapter.SelectableViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SelectableViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_clothing_selectable, parent, false)
        return SelectableViewHolder(view)
    }

    override fun onBindViewHolder(holder: SelectableViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, isItemSelected(item.id), isItemRecommended(item.id), isItemPackable(item.id))
    }

    override fun onBindViewHolder(holder: SelectableViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains("SELECTION_CHANGED")) {
            val item = getItem(position)
            holder.updateSelection(isSelected = isItemSelected(item.id), isRecommended = isItemRecommended(item.id), isPackable = isItemPackable(item.id))
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    class SelectableViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.item_image_selectable)
        private val cardView: MaterialCardView = itemView.findViewById(R.id.card_selectable_item)
        private val iconSpecial: ImageView = itemView.findViewById(R.id.icon_special)
        private val settingsManager = SettingsManager(itemView.context)

        fun bind(item: ClothingItem, isSelected: Boolean, isRecommended: Boolean, isPackable: Boolean) {
            val imageToShow = if (item.useProcessedImage && item.processedImageUri != null) {
                item.processedImageUri
            } else {
                item.imageUri
            }
            Glide.with(itemView.context)
                .load(Uri.fromFile(File(imageToShow)))
                .into(imageView)
            updateSelection(isSelected, isRecommended, isPackable)
        }

        fun updateSelection(isSelected: Boolean, isRecommended: Boolean, isPackable: Boolean) {
            val context = itemView.context
            if (isSelected) {
                val strokeWidthPx = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 4f, context.resources.displayMetrics
                ).toInt()
                cardView.strokeWidth = strokeWidthPx
                cardView.strokeColor = ContextCompat.getColor(context, R.color.settings_spinner_blue)
            } else {
                val strokeWidthPx = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 1.5f, context.resources.displayMetrics
                ).toInt()
                cardView.strokeWidth = strokeWidthPx
                cardView.strokeColor = ContextCompat.getColor(context, R.color.weather_card_blue_bg)
            }

            if (settingsManager.showRecommendationIcon) {
                if (isPackable) {
                    iconSpecial.setImageResource(R.drawable.ic_packable_bag)
                    iconSpecial.isVisible = true
                } else if (isRecommended) {
                    iconSpecial.setImageResource(R.drawable.sun)
                    iconSpecial.isVisible = true
                } else {
                    iconSpecial.isVisible = false
                }
            } else {
                iconSpecial.isVisible = false
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<ClothingItem>() {
            override fun areItemsTheSame(oldItem: ClothingItem, newItem: ClothingItem): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: ClothingItem, newItem: ClothingItem): Boolean {
                return oldItem == newItem
            }
        }
    }
}