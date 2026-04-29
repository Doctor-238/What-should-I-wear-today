package com.yehyun.whatshouldiweartoday.ui.mall

import android.graphics.Color
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
import java.io.File
import java.text.NumberFormat
import java.util.Locale

class CartAdapter(
    private val onIncrease: (Int) -> Unit,
    private val onDecrease: (Int) -> Unit,
    private val onRemove: (Int) -> Unit
) : ListAdapter<CartEntry, CartAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<CartEntry>() {
            override fun areItemsTheSame(a: CartEntry, b: CartEntry) = a.item.id == b.item.id
            override fun areContentsTheSame(a: CartEntry, b: CartEntry) = a == b
        }
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val ivImage: ImageView = view.findViewById(R.id.iv_image)
        val viewColor: View = view.findViewById(R.id.view_color)
        val tvName: TextView = view.findViewById(R.id.tv_name)
        val tvBrand: TextView = view.findViewById(R.id.tv_brand)
        val tvPrice: TextView = view.findViewById(R.id.tv_price)
        val tvQty: TextView = view.findViewById(R.id.tv_quantity)
        val btnInc: ImageView = view.findViewById(R.id.btn_increase)
        val btnDec: ImageView = view.findViewById(R.id.btn_decrease)
        val btnRemove: ImageView = view.findViewById(R.id.btn_remove)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_cart, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val entry = getItem(position)
        val item = entry.item
        holder.tvName.text = item.name
        holder.tvBrand.text = item.brand
        holder.tvPrice.text = NumberFormat.getNumberInstance(Locale.KOREA).format(item.price * entry.quantity) + "원"
        holder.tvQty.text = entry.quantity.toString()

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

        holder.btnInc.setOnClickListener { onIncrease(item.id) }
        holder.btnDec.setOnClickListener { onDecrease(item.id) }
        holder.btnRemove.setOnClickListener { onRemove(item.id) }
    }
}
