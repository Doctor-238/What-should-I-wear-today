package com.yehyun.whatshouldiweartoday.ui.closet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yehyun.whatshouldiweartoday.databinding.FragmentClothingListBinding

class ClothingListFragment : Fragment() {

    private var _binding: FragmentClothingListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ClosetViewModel by viewModels({ requireParentFragment() })
    private lateinit var adapter: ClothingAdapter
    private var category: String = "전체"
    private var isViewJustCreated = false

    private var scrollOnNextDataUpdate = false
    private lateinit var dataObserver: RecyclerView.AdapterDataObserver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.getString("category")?.let {
            category = it
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentClothingListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isViewJustCreated = true

        setupRecyclerView()
        observeViewModel()

        viewModel.getClothesForCategory(category).observe(viewLifecycleOwner) {
            adapter.submitList(it)
        }

        viewModel.sortChangedEvent.observe(viewLifecycleOwner, Observer {
            if (!isViewJustCreated) {
                scrollOnNextDataUpdate = true
            }
            isViewJustCreated = false
        })
    }

    private fun setupRecyclerView() {
        adapter = ClothingAdapter(
            onItemClicked = { clickedItem ->
                if (viewModel.isDeleteMode.value == true) {
                    viewModel.toggleItemSelection(clickedItem.id)
                } else {
                    val action =
                        ClosetFragmentDirections.actionNavigationClosetToEditClothingFragment(clickedItem.id)
                    requireParentFragment().findNavController().navigate(action)
                }
            },
            onItemLongClicked = { longClickedItem ->
                viewModel.enterDeleteMode(longClickedItem.id)
            },
            isDeleteMode = { viewModel.isDeleteMode.value ?: false },
            isItemSelected = { itemId -> viewModel.selectedItems.value?.contains(itemId) ?: false }
        )

        dataObserver = object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                super.onChanged()
                if (scrollOnNextDataUpdate) {
                    scrollToTop()
                    scrollOnNextDataUpdate = false
                }
            }
        }
        adapter.registerAdapterDataObserver(dataObserver)

        binding.recyclerViewClothingList.layoutManager = GridLayoutManager(context, 2)
        binding.recyclerViewClothingList.adapter = adapter
    }

    fun notifyAdapter(payload: String) {
        if(::adapter.isInitialized) {
            adapter.notifyItemRangeChanged(0, adapter.itemCount, payload)
        }
    }


    private fun observeViewModel() {
        viewModel.getClothesForCategory(category).observe(viewLifecycleOwner) { items ->
            adapter.submitList(items)

            if (category == "전체" && items.isEmpty() && viewModel.searchQuery.value.isNullOrEmpty()) {
                binding.emptyViewContainer.visibility = View.VISIBLE
                binding.recyclerViewClothingList.visibility = View.GONE
            } else {
                binding.emptyViewContainer.visibility = View.GONE
                binding.recyclerViewClothingList.visibility = View.VISIBLE
            }
        }
    }

    fun scrollToTop() {
        binding.recyclerViewClothingList.smoothScrollToPosition(0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (::adapter.isInitialized && ::dataObserver.isInitialized) {
            adapter.unregisterAdapterDataObserver(dataObserver)
        }
        binding.recyclerViewClothingList.adapter = null
        _binding = null
    }


    companion object {
        private const val ARG_CATEGORY = "category"
        @JvmStatic
        fun newInstance(category: String) =
            ClothingListFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CATEGORY, category)
                }
            }
    }
}