package com.yehyun.whatshouldiweartoday.ui.style

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class StyleViewPagerAdapter(fragment: Fragment, private val seasons: List<String>) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = seasons.size

    override fun createFragment(position: Int): Fragment {
        return StyleListFragment.newInstance(seasons[position])
    }
}