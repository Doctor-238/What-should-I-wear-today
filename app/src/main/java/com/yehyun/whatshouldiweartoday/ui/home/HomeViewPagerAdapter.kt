package com.yehyun.whatshouldiweartoday.ui.home

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class HomeViewPagerAdapter(
    fragment: Fragment,
    private val showExtended: Boolean
) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = if (showExtended) 3 else 2

    override fun createFragment(position: Int): Fragment {
        val mode = when (position) {
            0 -> RecommendationFragment.MODE_TODAY
            1 -> RecommendationFragment.MODE_TOMORROW
            else -> RecommendationFragment.MODE_EXTENDED
        }
        return RecommendationFragment.newInstance(mode)
    }
}
