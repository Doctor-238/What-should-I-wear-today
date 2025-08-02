// 파일 경로: app/src/main/java/com/yehyun/whatshouldiweartoday/ui/closet/ClothingAdapter.kt
package com.yehyun.whatshouldiweartoday.ui.closet

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
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
    private val onItemClicked: (ClothingItem) -> Unit,
    private val onItemLongClicked: (ClothingItem) -> Unit,
    private val isDeleteMode: () -> Boolean,
    private val isItemSelected: (Int) -> Boolean
) : ListAdapter<ClothingItem, ClothingAdapter.ClothingViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClothingViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_clothing, parent, false)
        return ClothingViewHolder(view)
    }

    override fun onBindViewHolder(holder: ClothingViewHolder, position: Int) {
        val currentItem = getItem(position)
        holder.bind(currentItem, onItemClicked, onItemLongClicked, isDeleteMode, isItemSelected)
    }

    override fun onBindViewHolder(holder: ClothingViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            for (payload in payloads) {
                if (payload == "DELETE_MODE_CHANGED") {
                    holder.updateDeleteModeUI(isDeleteMode())
                }
                if (payload == "SELECTION_CHANGED") {
                    holder.updateSelectionUI(isItemSelected(getItem(position).id))
                }
            }
        }
    }


    class ClothingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.iv_clothing_item)
        private val nameTextView: TextView = itemView.findViewById(R.id.tv_clothing_name)
        private val tempTextView: TextView = itemView.findViewById(R.id.tv_clothing_temp)
        private val colorView: View = itemView.findViewById(R.id.view_clothing_color)
        // ▼▼▼▼▼ CheckBox를 ImageView로 변경하고 ID를 교체합니다 ▼▼▼▼▼
        private val deleteCheckbox: ImageView = itemView.findViewById(R.id.iv_delete_checkbox)

        fun bind(
            item: ClothingItem,
            clickAction: (ClothingItem) -> Unit,
            longClickAction: (ClothingItem) -> Unit,
            isDeleteMode: () -> Boolean,
            isItemSelected: (Int) -> Boolean
        ) {
            nameTextView.text = item.name

            val settingsManager = SettingsManager(itemView.context)
            val constitutionAdjustment = settingsManager.getConstitutionAdjustment()

            if (item.category in listOf("상의", "하의", "아우터")) {
                val temperatureTolerance = settingsManager.getTemperatureTolerance()
                val adjustedTemp = item.suitableTemperature + constitutionAdjustment
                val minTemp = adjustedTemp - temperatureTolerance
                val maxTemp = adjustedTemp + temperatureTolerance
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
                val colorDrawable = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(Color.parseColor(item.colorHex))
                    setStroke(3, Color.BLACK)
                }
                colorView.background = colorDrawable
                colorView.visibility = View.VISIBLE
            } catch (e: Exception) {
                colorView.visibility = View.GONE
            }

            itemView.setOnClickListener {
                clickAction(item)
            }
            itemView.setOnLongClickListener {
                if (!isDeleteMode()) {
                    longClickAction(item)
                }
                true
            }

            updateDeleteModeUI(isDeleteMode())
            updateSelectionUI(isItemSelected(item.id))
        }

        fun updateDeleteModeUI(isDelete: Boolean) {
            // ▼▼▼▼▼ 애니메이션 대상을 deleteCheckbox(ImageView)로 변경합니다 ▼▼▼▼▼
            if (isDelete && deleteCheckbox.visibility == View.GONE) {
                deleteCheckbox.visibility = View.VISIBLE
                deleteCheckbox.startAnimation(AnimationUtils.loadAnimation(itemView.context, R.anim.scale_in))
            } else if (!isDelete && deleteCheckbox.visibility == View.VISIBLE) {
                deleteCheckbox.startAnimation(AnimationUtils.loadAnimation(itemView.context, R.anim.scale_out))
                deleteCheckbox.visibility = View.GONE
            }
        }

        fun updateSelectionUI(isSelected: Boolean) {
            // ▼▼▼▼▼ isChecked 대신 setImageResource를 사용하여 아이콘을 직접 교체합니다 ▼▼▼▼▼
            if (isSelected) {
                deleteCheckbox.setImageResource(R.drawable.ic_checkbox_checked_custom)
            } else {
                deleteCheckbox.setImageResource(R.drawable.ic_checkbox_unchecked_custom)
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