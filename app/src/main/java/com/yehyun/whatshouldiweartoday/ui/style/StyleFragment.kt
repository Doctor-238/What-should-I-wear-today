package com.yehyun.whatshouldiweartoday.ui.style

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.yehyun.whatshouldiweartoday.R

class StyleFragment : Fragment(R.layout.fragment_style) {

    private val viewModel: StyleViewModel by viewModels()
    private lateinit var adapter: SavedStylesAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView(view)
        setupSearch(view)
        setupSortSpinner(view)

        view.findViewById<FloatingActionButton>(R.id.fab_add_style).setOnClickListener {
            findNavController().navigate(R.id.action_global_saveStyleFragment)
        }

        viewModel.styles.observe(viewLifecycleOwner) { styles ->
            adapter.submitList(styles)
        }
    }

    private fun setupRecyclerView(view: View) {
        // [수정] 클릭 이벤트를 정의하고, 수정 화면으로 이동시킴
        adapter = SavedStylesAdapter { clickedStyle ->
            val action = StyleFragmentDirections.actionNavigationStyleToEditStyleFragment(clickedStyle.style.styleId)
            findNavController().navigate(action)
        }
        val recyclerView = view.findViewById<RecyclerView>(R.id.rv_saved_styles)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }

    private fun setupSearch(view: View) {
        val searchView = view.findViewById<SearchView>(R.id.search_view_style)
        searchView.setOnQueryTextListener(object: SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.setSearchQuery(newText.orEmpty())
                return true
            }
        })
    }
    private fun setupSortSpinner(view: View) {
        val spinner = view.findViewById<Spinner>(R.id.spinner_sort_style)
        // [수정] 온도 관련 정렬 제거
        val sortOptions = listOf("최신순", "오래된 순", "이름 오름차순", "이름 내림차순")

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