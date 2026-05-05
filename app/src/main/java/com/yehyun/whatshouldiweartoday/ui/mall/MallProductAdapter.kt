package com.yehyun.whatshouldiweartoday.ui.mall

import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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
import kotlin.math.abs

class MallProductAdapter(
    private val onItemClick: (MallItem) -> Unit
) : ListAdapter<MallItem, MallProductAdapter.ViewHolder>(DIFF) {

    private val wishlistSet = HashSet<Int>()

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<MallItem>() {
            override fun areItemsTheSame(a: MallItem, b: MallItem) = a.id == b.id
            override fun areContentsTheSame(a: MallItem, b: MallItem) = a == b
        }

        fun getDiscountRate(itemId: Int): Int {
            val options = intArrayOf(0, 0, 0, 10, 15, 20, 25, 30, 0, 0, 10, 0)
            return options[abs(itemId * 13 + 7) % options.size]
        }

        fun getRating(itemId: Int): Pair<Float, Int> {
            val a = abs(itemId.toLong() * 31L + 17L) % 12
            val avg = (38 + a) / 10f
            val count = 15 + abs(itemId * 71 + 39) % 385
            return Pair(avg, count)
        }

        fun isNewItem(itemId: Int): Boolean = (itemId % 7 == 0 || itemId % 11 == 0)
    }

    inner class ViewHolder(val root: View) : RecyclerView.ViewHolder(root) {
        val ivImage: ImageView = root.findViewById(R.id.iv_product_image)
        val viewColorBg: View = root.findViewById(R.id.view_color_bg)
        val tvDiscountBadge: TextView = root.findViewById(R.id.tv_discount_badge)
        val tvNewBadge: TextView = root.findViewById(R.id.tv_new_badge)
        val btnWishlist: FrameLayout = root.findViewById(R.id.btn_wishlist)
        val ivWishlist: ImageView = root.findViewById(R.id.iv_wishlist)
        val tvBrand: TextView = root.findViewById(R.id.tv_brand)
        val tvName: TextView = root.findViewById(R.id.tv_name)
        val tvPrice: TextView = root.findViewById(R.id.tv_price)
        val tvOriginalPrice: TextView = root.findViewById<TextView>(R.id.tv_original_price)
            .also { it.paintFlags = it.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG }
        val tvRating: TextView = root.findViewById(R.id.tv_rating)
        val tvReviewCount: TextView = root.findViewById(R.id.tv_review_count)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_mall_product, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)

        holder.tvBrand.text = item.brand
        holder.tvName.text = item.name

        val discount = getDiscountRate(item.id)
        val fmt = NumberFormat.getNumberInstance(Locale.KOREA)

        if (discount > 0) {
            val originalPrice = (item.price * 100L / (100 - discount)).toInt()
            holder.tvPrice.text = fmt.format(item.price) + "원"
            holder.tvOriginalPrice.text = fmt.format(originalPrice) + "원"
            holder.tvOriginalPrice.visibility = View.VISIBLE
            holder.tvDiscountBadge.text = "${discount}%"
            holder.tvDiscountBadge.visibility = View.VISIBLE
            holder.tvNewBadge.visibility = View.GONE
        } else if (isNewItem(item.id)) {
            holder.tvPrice.text = fmt.format(item.price) + "원"
            holder.tvOriginalPrice.visibility = View.GONE
            holder.tvDiscountBadge.visibility = View.GONE
            holder.tvNewBadge.visibility = View.VISIBLE
        } else {
            holder.tvPrice.text = fmt.format(item.price) + "원"
            holder.tvOriginalPrice.visibility = View.GONE
            holder.tvDiscountBadge.visibility = View.GONE
            holder.tvNewBadge.visibility = View.GONE
        }

        val (rating, count) = getRating(item.id)
        holder.tvRating.text = "%.1f".format(rating)
        holder.tvReviewCount.text = "(${fmt.format(count)})"

        val imageUri = if (item.useProcessedImage && !item.processedImageUri.isNullOrBlank()) item.processedImageUri else item.imageUri
        if (imageUri.isNotBlank() && File(imageUri).exists()) {
            holder.ivImage.visibility = View.VISIBLE
            holder.viewColorBg.visibility = View.GONE
            Glide.with(holder.root).load(File(imageUri)).centerCrop().into(holder.ivImage)
        } else {
            holder.ivImage.visibility = View.GONE
            holder.viewColorBg.visibility = View.VISIBLE
            val color = try { Color.parseColor(item.colorHex) } catch (e: Exception) { Color.parseColor("#FFCCCC") }
            holder.viewColorBg.background = GradientDrawable().apply { setColor(color) }
        }

        val isWishlisted = item.id in wishlistSet
        holder.ivWishlist.setImageResource(
            if (isWishlisted) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
        )

        holder.btnWishlist.setOnClickListener {
            val nowWished = if (item.id in wishlistSet) {
                wishlistSet.remove(item.id); false
            } else {
                wishlistSet.add(item.id); true
            }
            holder.ivWishlist.setImageResource(
                if (nowWished) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
            )
        }

        holder.root.setOnClickListener { onItemClick(item) }
    }
}
