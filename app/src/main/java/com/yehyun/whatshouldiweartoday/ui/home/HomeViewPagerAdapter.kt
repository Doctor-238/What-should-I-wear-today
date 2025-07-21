package com.yehyun.whatshouldiweartoday.ui.home

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class HomeViewPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        // ▼▼▼▼▼ 핵심 수정: 프래그먼트 맵 관리 로직 제거 ▼▼▼▼▼
        return RecommendationFragment.newInstance(isToday = position == 0)
        // ▲▲▲▲▲ 핵심 수정 ▲▲▲▲▲
    }
}