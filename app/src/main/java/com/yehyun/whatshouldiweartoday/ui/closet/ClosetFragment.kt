package com.yehyun.whatshouldiweartoday.ui.closet

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
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
        setupSortSpinner(view)

        view.findViewById<FloatingActionButton>(R.id.fab_add_clothing).setOnClickListener {
            findNavController().navigate(R.id.action_navigation_closet_to_addClothingFragment)
        }

        viewModel.clothes.observe(viewLifecycleOwner) { items ->
            clothingAdapter.submitList(items)
        }
    }

    private fun setupRecyclerView(view: View) {
        clothingAdapter = ClothingAdapter { clickedItem ->
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

    private fun setupSortSpinner(view: View) {
        val spinner = view.findViewById<Spinner>(R.id.spinner_sort)
        val sortOptions = listOf("최신순", "오래된 순", "이름 오름차순", "이름 내림차순", "온도 오름차순", "온도 내림차순")

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, sortOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                viewModel.setSortType(sortOptions[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
}
