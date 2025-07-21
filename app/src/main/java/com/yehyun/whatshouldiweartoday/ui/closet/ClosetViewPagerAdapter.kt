package com.yehyun.whatshouldiweartoday.ui.closet

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class ClosetViewPagerAdapter(fragment: Fragment, private val categories: List<String>) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = categories.size

    override fun createFragment(position: Int): Fragment {
        // ▼▼▼▼▼ 핵심 수정: 프래그먼트 맵 관리 로직 제거 ▼▼▼▼▼
        return ClothingListFragment.newInstance(categories[position])
        // ▲▲▲▲▲ 핵심 수정 ▲▲▲▲▲
    }
}