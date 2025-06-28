package com.yehyun.whatshouldiweartoday.ui.style

import android.os.Bundle
import android.view.View
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

        view.findViewById<FloatingActionButton>(R.id.fab_add_style).setOnClickListener {
            findNavController().navigate(R.id.action_global_saveStyleFragment)
        }

        viewModel.styles.observe(viewLifecycleOwner) { styles ->
            adapter.submitList(styles)
        }
    }

    private fun setupRecyclerView(view: View) {
        adapter = SavedStylesAdapter()
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
}