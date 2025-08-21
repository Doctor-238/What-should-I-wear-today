package com.yehyun.whatshouldiweartoday.ui.style

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class StyleViewPagerAdapter(fragment: Fragment, private val seasons: List<String>) : FragmentStateAdapter(fragment) {

    private val fragments = mutableMapOf<Int, Fragment>()

    override fun getItemCount(): Int = seasons.size

    override fun createFragment(position: Int): Fragment {
        val fragment = StyleListFragment.newInstance(seasons[position])
        fragments[position] = fragment
        return fragment
    }

    fun getFragment(position: Int): Fragment? {
        return fragments[position]
    }
}