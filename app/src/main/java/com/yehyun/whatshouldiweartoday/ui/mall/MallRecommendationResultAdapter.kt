package com.yehyun.whatshouldiweartoday.ui.mall

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.yehyun.whatshouldiweartoday.R
import com.yehyun.whatshouldiweartoday.data.database.mall.MallItem
import java.io.File
import java.text.NumberFormat
import java.util.Locale

class MallRecommendationResultAdapter(
    private val items: List<MallItem>,
    private val onSelectionChanged: () -> Unit
) : RecyclerView.Adapter<MallRecommendationResultAdapter.VH>() {

    private val selectedIds = mutableSetOf<Int>()

    fun getSelectedItems(): List<MallItem> = items.filter { it.id in selectedIds }
    fun selectAll() { selectedIds.addAll(items.map { it.id }); notifyDataSetChanged(); onSelectionChanged() }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val cb: CheckBox = view.findViewById(R.id.cb_select)
        val ivImage: ImageView = view.findViewById(R.id.iv_image)
        val viewColor: View = view.findViewById(R.id.view_color)
        val tvBrand: TextView = view.findViewById(R.id.tv_brand)
        val tvName: TextView = view.findViewById(R.id.tv_name)
        val tvPrice: TextView = view.findViewById(R.id.tv_price)
        val tvTemp: TextView = view.findViewById(R.id.tv_temp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_recommendation_result, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvBrand.text = item.brand
        holder.tvName.text = item.name
        holder.tvPrice.text = NumberFormat.getNumberInstance(Locale.KOREA).format(item.price) + "원"
        holder.tvTemp.text = "적정: %.0f°C~%.0f°C".format(item.suitableMinTemp, item.suitableMaxTemp)
        holder.cb.isChecked = selectedIds.contains(item.id)

        val imageUri = if (item.useProcessedImage && !item.processedImageUri.isNullOrBlank()) item.processedImageUri else item.imageUri
        if (imageUri.isNotBlank() && File(imageUri).exists()) {
            holder.ivImage.visibility = View.VISIBLE
            holder.viewColor.visibility = View.GONE
            Glide.with(holder.itemView).load(File(imageUri)).centerCrop().into(holder.ivImage)
        } else {
            holder.ivImage.visibility = View.GONE
            holder.viewColor.visibility = View.VISIBLE
            val color = try { Color.parseColor(item.colorHex) } catch (e: Exception) { Color.LTGRAY }
            holder.viewColor.setBackgroundColor(color)
        }

        val toggle = {
            if (selectedIds.contains(item.id)) selectedIds.remove(item.id) else selectedIds.add(item.id)
            holder.cb.isChecked = selectedIds.contains(item.id)
            onSelectionChanged()
        }
        holder.cb.setOnClickListener { toggle() }
        holder.itemView.setOnClickListener { toggle() }
    }
}
