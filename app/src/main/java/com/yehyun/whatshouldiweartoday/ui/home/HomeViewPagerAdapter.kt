package com.yehyun.whatshouldiweartoday.ui.home

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class HomeViewPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = 2 // 탭은 '오늘', '내일' 2개

    override fun createFragment(position: Int): Fragment {
        // 각 포지션에 맞는 RecommendationFragment를 생성하여 전달
        return RecommendationFragment.newInstance(isToday = position == 0)
    }
}