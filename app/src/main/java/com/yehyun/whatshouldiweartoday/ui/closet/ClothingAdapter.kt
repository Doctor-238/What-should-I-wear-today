package com.yehyun.whatshouldiweartoday.ui.closet

import android.graphics.Color
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.yehyun.whatshouldiweartoday.R
import com.yehyun.whatshouldiweartoday.data.database.ClothingItem
import com.yehyun.whatshouldiweartoday.data.preference.SettingsManager
import java.io.File

class ClothingAdapter(
    private val onItemClicked: (ClothingItem) -> Unit
) : ListAdapter<ClothingItem, ClothingAdapter.ClothingViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClothingViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_clothing, parent, false)
        return ClothingViewHolder(view)
    }

    override fun onBindViewHolder(holder: ClothingViewHolder, position: Int) {
        val currentItem = getItem(position)
        holder.bind(currentItem, onItemClicked)
    }

    class ClothingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.iv_clothing_item)
        private val nameTextView: TextView = itemView.findViewById(R.id.tv_clothing_name)
        private val tempTextView: TextView = itemView.findViewById(R.id.tv_clothing_temp)
        private val colorView: View = itemView.findViewById(R.id.view_clothing_color)

        fun bind(item: ClothingItem, clickAction: (ClothingItem) -> Unit) {
            nameTextView.text = item.name

            // bind가 호출될 때마다 최신 설정 값을 가져와서 온도 범위를 계산합니다.
            val settingsManager = SettingsManager(itemView.context)

            if (item.category in listOf("상의", "하의", "아우터")) {
                val temperatureTolerance = settingsManager.getTemperatureTolerance()
                val minTemp = item.suitableTemperature - temperatureTolerance
                val maxTemp = item.suitableTemperature + temperatureTolerance
                tempTextView.text = "%.1f°C ~ %.1f°C".format(minTemp, maxTemp)
                tempTextView.visibility = View.VISIBLE
            } else {
                tempTextView.visibility = View.GONE
            }

            val imageToShow = if (item.useProcessedImage && item.processedImageUri != null) {
                item.processedImageUri
            } else {
                item.imageUri
            }
            Glide.with(itemView.context).load(Uri.fromFile(File(imageToShow))).into(imageView)

            try {
                colorView.setBackgroundColor(Color.parseColor(item.colorHex))
                colorView.visibility = View.VISIBLE
            } catch (e: IllegalArgumentException) {
                colorView.visibility = View.GONE
            }

            itemView.setOnClickListener {
                clickAction(item)
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