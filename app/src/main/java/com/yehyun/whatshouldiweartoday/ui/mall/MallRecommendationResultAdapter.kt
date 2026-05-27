package com.yehyun.whatshouldiweartoday.ui.mall

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.yehyun.whatshouldiweartoday.R
import com.yehyun.whatshouldiweartoday.data.database.mall.MallItem
import com.yehyun.whatshouldiweartoday.data.preference.SettingsManager
import com.yehyun.whatshouldiweartoday.ui.closet.AddClothingViewModel
import java.io.File
import java.text.NumberFormat
import java.util.Locale

class MallRecommendationResultAdapter(
    private val items: List<MallItem>,
    private val onItemClick: (MallItem) -> Unit,
    private val onSelectionChanged: () -> Unit
) : RecyclerView.Adapter<MallRecommendationResultAdapter.VH>() {

    private val selectedIds = mutableSetOf<Int>()

    fun getSelectedItems(): List<MallItem> = items.filter { it.id in selectedIds }
    fun selectAll() { selectedIds.addAll(items.map { it.id }); notifyDataSetChanged(); onSelectionChanged() }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val flCheckboxZone: FrameLayout = view.findViewById(R.id.fl_checkbox_zone)
        val llItemZone: LinearLayout = view.findViewById(R.id.ll_item_zone)
        val cb: CheckBox = view.findViewById(R.id.cb_select)
        val ivImage: ImageView = view.findViewById(R.id.iv_image)
        val viewColor: View = view.findViewById(R.id.view_color)
        val tvBrand: TextView = view.findViewById(R.id.tv_brand)
        val tvName: TextView = view.findViewById(R.id.tv_name)
        val tvPrice: TextView = view.findViewById(R.id.tv_price)
        val tvTemp: TextView = view.findViewById(R.id.tv_temp)
        val tvSize: TextView = view.findViewById(R.id.tv_size)
        val tvPurpose: TextView = view.findViewById(R.id.tv_purpose)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_recommendation_result, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val ctx = holder.itemView.context
        holder.tvBrand.text = item.brand
        holder.tvName.text = item.name
        holder.tvPrice.text = NumberFormat.getNumberInstance(Locale.KOREA).format(item.price) + "원"
        holder.tvTemp.text = "적정: %.0f°C~%.0f°C".format(item.suitableMinTemp, item.suitableMaxTemp)
        holder.cb.isChecked = selectedIds.contains(item.id)

        // 사이즈 표시
        val settings = SettingsManager(ctx)
        if (settings.bodyFitEnabled && settings.isBodyRegistered && item.category in AddClothingViewModel.SIZE_CATEGORIES) {
            val sizeLabel = AddClothingViewModel.calculateSizeLabel(
                item.category,
                settings.estimatedHeight, settings.estimatedWeight, settings.estimatedWaist,
                settings.sizeNotationType
            )
            if (sizeLabel != null) {
                holder.tvSize.text = "내 사이즈: $sizeLabel"
                holder.tvSize.isVisible = true
            } else {
                holder.tvSize.isVisible = false
            }
        } else {
            holder.tvSize.isVisible = false
        }

        // 용도 표시
        val purposes = item.purposes.split(",").filter { it.isNotBlank() }
        if (purposes.isNotEmpty()) {
            holder.tvPurpose.text = purposes.joinToString(" · ")
            holder.tvPurpose.isVisible = true
        } else {
            holder.tvPurpose.isVisible = false
        }

        // 이미지
        val imageUri = if (item.useProcessedImage && !item.processedImageUri.isNullOrBlank()) item.processedImageUri else item.imageUri
        if (imageUri.isNotBlank() && File(imageUri).exists()) {
            holder.ivImage.isVisible = true
            holder.viewColor.isVisible = false
            Glide.with(holder.itemView).load(File(imageUri)).centerCrop().into(holder.ivImage)
        } else {
            holder.ivImage.isVisible = false
            holder.viewColor.isVisible = true
            val color = try { Color.parseColor(item.colorHex) } catch (e: Exception) { Color.LTGRAY }
            holder.viewColor.setBackgroundColor(color)
        }

        // 체크박스 영역 클릭 = 선택 토글
        holder.flCheckboxZone.setOnClickListener {
            if (selectedIds.contains(item.id)) selectedIds.remove(item.id) else selectedIds.add(item.id)
            holder.cb.isChecked = selectedIds.contains(item.id)
            onSelectionChanged()
        }

        // 상품 정보 영역 클릭 = 상세 이동
        holder.llItemZone.setOnClickListener { onItemClick(item) }
    }
}
