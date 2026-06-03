package com.yehyun.whatshouldiweartoday.ui.closet

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.yehyun.whatshouldiweartoday.R

class PurposeChipAdapter(
    private val onDeleteRequest: (position: Int, purpose: String) -> Unit,
    private val onAddRequest: () -> Unit,
    private val maxPurposes: Int = 3
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_CHIP = 0
        const val TYPE_ADD  = 1

        // gradient_blue_start → gradient_purple_end (left = highest priority)
        private val COLOR_HIGH = Color.parseColor("#5BBEF8")
        private val COLOR_LOW  = Color.parseColor("#7C6AE8")
    }

    private val purposes = mutableListOf<String>()

    fun submitList(list: List<String>) {
        purposes.clear()
        purposes.addAll(list)
        notifyDataSetChanged()
    }

    fun getPurposes(): List<String> = purposes.toList()

    fun moveItem(from: Int, to: Int) {
        val item = purposes.removeAt(from)
        purposes.add(to, item)
        notifyItemMoved(from, to)
    }

    override fun getItemCount() =
        purposes.size + if (purposes.size < maxPurposes) 1 else 0

    override fun getItemViewType(position: Int) =
        if (position < purposes.size) TYPE_CHIP else TYPE_ADD

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_CHIP)
            ChipHolder(inf.inflate(R.layout.item_purpose_chip, parent, false))
        else
            AddHolder(inf.inflate(R.layout.item_purpose_add_chip, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ChipHolder -> {
                val purpose = purposes[position]
                holder.text.text = purpose
                holder.itemView.background = pillBg(chipColor(position))
                holder.delete.setOnClickListener {
                    val pos = holder.adapterPosition
                    if (pos in purposes.indices) onDeleteRequest(pos, purposes[pos])
                }
            }
            is AddHolder -> holder.itemView.setOnClickListener { onAddRequest() }
        }
    }

    // Refresh gradient colors after a drag reorder
    fun refreshColors() = notifyDataSetChanged()

    private fun chipColor(position: Int): Int {
        val t = if (purposes.size > 1) position.toFloat() / (purposes.size - 1) else 0f
        return Color.rgb(
            lerp(Color.red(COLOR_HIGH),   Color.red(COLOR_LOW),   t),
            lerp(Color.green(COLOR_HIGH), Color.green(COLOR_LOW), t),
            lerp(Color.blue(COLOR_HIGH),  Color.blue(COLOR_LOW),  t)
        )
    }

    private fun lerp(a: Int, b: Int, t: Float) = (a + (b - a) * t).toInt()

    private fun pillBg(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 100f
        setColor(color)
    }

    class ChipHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView   = view.findViewById(R.id.tv_chip_text)
        val delete: ImageView = view.findViewById(R.id.btn_chip_delete)
    }

    class AddHolder(view: View) : RecyclerView.ViewHolder(view)
}
