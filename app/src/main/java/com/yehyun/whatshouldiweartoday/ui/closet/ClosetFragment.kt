package com.yehyun.whatshouldiweartoday.ui.closet

import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.yehyun.whatshouldiweartoday.R

class ClosetFragment : Fragment(R.layout.fragment_closet) {

    private val viewModel: ClosetViewModel by viewModels()
    private lateinit var clothingAdapter: ClothingAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView(view)
        setupTabs(view)
        setupSearch(view)

        view.findViewById<FloatingActionButton>(R.id.fab_add_clothing).setOnClickListener {
            findNavController().navigate(R.id.action_navigation_closet_to_addClothingFragment)
        }

        viewModel.clothes.observe(viewLifecycleOwner) { items ->
            clothingAdapter.submitList(items)
        }
    }

    private fun setupRecyclerView(view: View) {
        // 어댑터를 생성할 때, 클릭 시 실행될 동작을 람다로 전달합니다.
        clothingAdapter = ClothingAdapter { clickedItem ->
            // [중요] 아래 코드가 오류 없이 작동하려면,
            // 1. Safe Args 플러그인이 build.gradle에 모두 설정되어 있어야 하고,
            // 2. mobile_navigation.xml 파일에 'editClothingFragment'와 관련 action이 정확히 정의되어 있어야 합니다.
            // 3. EditClothingFragment.kt 파일이 존재해야 합니다.
            val action = ClosetFragmentDirections.actionNavigationClosetToEditClothingFragment(clickedItem.id)
            findNavController().navigate(action)
        }
        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerView_closet)
        recyclerView.adapter = clothingAdapter
    }

    private fun setupTabs(view: View) {
        val tabLayout = view.findViewById<TabLayout>(R.id.tab_layout_category)
        val categories = listOf("전체", "상의", "하의", "아우터", "신발", "가방", "모자", "기타")

        categories.forEach { category ->
            tabLayout.addTab(tabLayout.newTab().setText(category))
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                viewModel.setCategory(tab?.text.toString())
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupSearch(view: View) {
        val searchView = view.findViewById<SearchView>(R.id.search_view_closet)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.setSearchQuery(newText.orEmpty())
                return true
            }
        })
    }
}