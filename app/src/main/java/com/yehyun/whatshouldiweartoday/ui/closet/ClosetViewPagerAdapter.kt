// 파일 경로: app/src/main/java/com/yehyun/whatshouldiweartoday/ui/closet/ClosetViewPagerAdapter.kt
package com.yehyun.whatshouldiweartoday.ui.closet

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class ClosetViewPagerAdapter(fragment: Fragment, private val categories: List<String>) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = categories.size

    override fun createFragment(position: Int): Fragment {
        return ClothingListFragment.newInstance(categories[position])
    }

    // ▼▼▼ 오류 해결을 위해 이 함수를 추가합니다 ▼▼▼
    fun getCategory(position: Int): String {
        if (position >= 0 && position < categories.size) {
            return categories[position]
        }
        return "전체" // 기본값
    }
    // ▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲
}