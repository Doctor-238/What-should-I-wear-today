package com.yehyun.whatshouldiweartoday.ui.mall

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
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

class MallAdminProductAdapter(
    private val onItemClick: (MallItem) -> Unit,
    private val onSelectionChanged: (Int) -> Unit
) : ListAdapter<MallItem, MallAdminProductAdapter.ViewHolder>(DIFF) {

    private val selectedIds = mutableSetOf<Int>()
    var isDeleteMode = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<MallItem>() {
            override fun areItemsTheSame(a: MallItem, b: MallItem) = a.id == b.id
            override fun areContentsTheSame(a: MallItem, b: MallItem) = a == b
        }
    }

    fun getSelectedIds(): Set<Int> = selectedIds.toSet()

    fun clearSelection() {
        selectedIds.clear()
        notifyDataSetChanged()
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val viewColor: View = view.findViewById(R.id.view_color)
        val ivImage: ImageView = view.findViewById(R.id.iv_image)
        val tvCategory: TextView = view.findViewById(R.id.tv_category)
        val tvBrand: TextView = view.findViewById(R.id.tv_brand)
        val tvName: TextView = view.findViewById(R.id.tv_name)
        val tvPrice: TextView = view.findViewById(R.id.tv_price)
        val cbSelect: CheckBox = view.findViewById(R.id.cb_select)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_mall_admin_product, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.tvCategory.text = item.category
        holder.tvBrand.text = item.brand
        holder.tvName.text = item.name
        holder.tvPrice.text = NumberFormat.getNumberInstance(Locale.KOREA).format(item.price) + "원"

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

        holder.cbSelect.visibility = if (isDeleteMode) View.VISIBLE else View.GONE
        holder.cbSelect.isChecked = selectedIds.contains(item.id)
        holder.cbSelect.setOnClickListener {
            if (selectedIds.contains(item.id)) selectedIds.remove(item.id) else selectedIds.add(item.id)
            onSelectionChanged(selectedIds.size)
        }

        holder.itemView.setOnClickListener {
            if (isDeleteMode) {
                if (selectedIds.contains(item.id)) selectedIds.remove(item.id) else selectedIds.add(item.id)
                holder.cbSelect.isChecked = selectedIds.contains(item.id)
                onSelectionChanged(selectedIds.size)
            } else {
                onItemClick(item)
            }
        }

        holder.itemView.setOnLongClickListener {
            if (!isDeleteMode) {
                isDeleteMode = true
                selectedIds.add(item.id)
                onSelectionChanged(selectedIds.size)
            }
            true
        }
    }
}
