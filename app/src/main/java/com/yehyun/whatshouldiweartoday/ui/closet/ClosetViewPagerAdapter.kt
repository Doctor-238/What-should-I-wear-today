package com.yehyun.whatshouldiweartoday.ui.closet

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class ClosetViewPagerAdapter(fragment: Fragment, private val categories: List<String>) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = categories.size

    override fun createFragment(position: Int): Fragment {
        return ClothingListFragment.newInstance(categories[position])
    }

    fun getCategory(position: Int): String {
        if (position >= 0 && position < categories.size) {
            return categories[position]
        }
        return "전체" // 기본값
    }
}