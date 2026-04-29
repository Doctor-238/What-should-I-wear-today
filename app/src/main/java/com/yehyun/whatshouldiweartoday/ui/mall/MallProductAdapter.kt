package com.yehyun.whatshouldiweartoday.ui.mall

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.yehyun.whatshouldiweartoday.R
import com.yehyun.whatshouldiweartoday.data.database.mall.MallItem
import java.io.File
import java.text.NumberFormat
import java.util.Locale

class MallProductAdapter(
    private val onItemClick: (MallItem) -> Unit
) : ListAdapter<MallItem, MallProductAdapter.ViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<MallItem>() {
            override fun areItemsTheSame(a: MallItem, b: MallItem) = a.id == b.id
            override fun areContentsTheSame(a: MallItem, b: MallItem) = a == b
        }
    }

    inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val ivImage: ImageView = view.findViewById(R.id.iv_product_image)
        val viewColorBg: View = view.findViewById(R.id.view_color_bg)
        val tvCategoryChip: TextView = view.findViewById(R.id.tv_category_chip)
        val tvBrand: TextView = view.findViewById(R.id.tv_brand)
        val tvName: TextView = view.findViewById(R.id.tv_name)
        val tvPrice: TextView = view.findViewById(R.id.tv_price)
        val llTags: LinearLayout = view.findViewById(R.id.ll_tags)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_mall_product, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.tvBrand.text = item.brand
        holder.tvName.text = item.name
        holder.tvPrice.text = NumberFormat.getNumberInstance(Locale.KOREA).format(item.price) + "원"
        holder.tvCategoryChip.text = item.category

        val imageUri = if (item.useProcessedImage && !item.processedImageUri.isNullOrBlank()) item.processedImageUri else item.imageUri
        if (imageUri.isNotBlank() && File(imageUri).exists()) {
            holder.ivImage.visibility = View.VISIBLE
            holder.viewColorBg.visibility = View.GONE
            Glide.with(holder.view).load(File(imageUri)).centerCrop().into(holder.ivImage)
        } else {
            holder.ivImage.visibility = View.GONE
            holder.viewColorBg.visibility = View.VISIBLE
            val color = try { Color.parseColor(item.colorHex) } catch (e: Exception) { Color.parseColor("#FFCCCC") }
            val bg = GradientDrawable().apply {
                setColor(color)
            }
            holder.viewColorBg.background = bg
        }

        holder.llTags.removeAllViews()
        item.tags.split(",").take(3).filter { it.isNotBlank() }.forEach { tag ->
            val tv = TextView(holder.view.context).apply {
                text = "#$tag"
                textSize = 10f
                setTextColor(Color.parseColor("#FF6B6B"))
                setPadding(8, 3, 8, 3)
                setBackgroundResource(R.drawable.bg_tag_chip)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.marginEnd = 4
                layoutParams = params
            }
            holder.llTags.addView(tv)
        }

        holder.view.setOnClickListener { onItemClick(item) }
    }
}
