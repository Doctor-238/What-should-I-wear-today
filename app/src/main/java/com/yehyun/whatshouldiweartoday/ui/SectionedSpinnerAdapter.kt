package com.yehyun.whatshouldiweartoday.ui

import android.content.Context
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.TextView
import com.yehyun.whatshouldiweartoday.R

/**
 * Spinner adapter that shows a labeled separator before the second section.
 * Headers are non-selectable; only Option entries trigger onItemSelected.
 */
class SectionedSpinnerAdapter(
    private val context: Context,
    baseItems: List<String>,
    sectionLabel: String,
    sectionItems: List<String>
) : BaseAdapter() {

    sealed class Entry {
        data class Option(val text: String) : Entry()
        data class Header(val title: String) : Entry()
    }

    private val entries: List<Entry> = buildList {
        addAll(baseItems.map { Entry.Option(it) })
        if (sectionItems.isNotEmpty()) {
            add(Entry.Header(sectionLabel))
            addAll(sectionItems.map { Entry.Option(it) })
        }
    }

    fun textAt(position: Int): String? = (entries.getOrNull(position) as? Entry.Option)?.text

    fun positionOf(text: String): Int =
        entries.indexOfFirst { it is Entry.Option && it.text == text }.coerceAtLeast(0)

    override fun getCount() = entries.size
    override fun getItem(pos: Int) = entries[pos]
    override fun getItemId(pos: Int) = pos.toLong()
    override fun isEnabled(pos: Int) = entries[pos] is Entry.Option
    // Spinner requires getViewTypeCount() == 1; use tag-based recycling in getDropDownView instead

    // Collapsed spinner button – always shows the selected Option text
    override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
        val view = (convertView?.takeIf { it.tag == "sel" })
            ?: LayoutInflater.from(context).inflate(R.layout.spinner_item_centered_normal, parent, false)
                .also { it.tag = "sel" }
        (view as? TextView)?.text = textAt(pos) ?: ""
        return view
    }

    override fun getDropDownView(pos: Int, convertView: View?, parent: ViewGroup): View {
        return when (val entry = entries[pos]) {
            is Entry.Header -> buildHeaderView(entry.title, convertView)
            is Entry.Option -> {
                val view = (convertView?.takeIf { it.tag == "item" })
                    ?: LayoutInflater.from(context).inflate(R.layout.spinner_dropdown_item, parent, false)
                        .also { it.tag = "item" }
                (view as? TextView)?.text = entry.text
                view
            }
        }
    }

    private fun buildHeaderView(title: String, convertView: View?): View {
        val container = (convertView as? LinearLayout)?.takeIf { it.tag == "hdr" }?.also { c ->
            (c.getChildAt(1) as? TextView)?.text = title
        } ?: LinearLayout(context).apply {
            tag = "hdr"
            orientation = LinearLayout.VERTICAL
            // Divider line
            addView(View(context).apply {
                setBackgroundColor(0xFFE2EDF6.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
                ).also { it.topMargin = dp(4) }
            })
            // Label
            addView(TextView(context).apply {
                text = title
                setTextColor(0xFF94ABBA.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setPadding(dp(16), dp(8), dp(16), dp(2))
            })
        }
        return container
    }

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()
}
