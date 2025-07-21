package com.yehyun.whatshouldiweartoday.ui.style

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class StyleViewPagerAdapter(fragment: Fragment, private val seasons: List<String>) : FragmentStateAdapter(fragment) {

    // ▼▼▼▼▼ 핵심 수정: 프래그먼트 인스턴스 저장 및 반환 로직 추가 ▼▼▼▼▼
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
    // ▲▲▲▲▲ 핵심 수정 ▲▲▲▲▲
}