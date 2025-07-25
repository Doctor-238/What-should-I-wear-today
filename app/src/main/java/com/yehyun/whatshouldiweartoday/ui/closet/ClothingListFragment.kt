package com.yehyun.whatshouldiweartoday.ui.closet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.yehyun.whatshouldiweartoday.databinding.FragmentClothingListBinding

class ClothingListFragment : Fragment() {

    private var _binding: FragmentClothingListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ClosetViewModel by viewModels({requireParentFragment()})
    private lateinit var adapter: ClothingAdapter
    private var category: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            category = it.getString(ARG_CATEGORY)
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
        setupRecyclerView()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        adapter = ClothingAdapter { clickedItem ->
            val action = ClosetFragmentDirections.actionNavigationClosetToEditClothingFragment(clickedItem.id)
            requireParentFragment().findNavController().navigate(action)
        }
        binding.recyclerViewClothingList.layoutManager = GridLayoutManager(context, 2)
        binding.recyclerViewClothingList.adapter = adapter
    }

    private fun observeViewModel() {
        val categoryToObserve = category ?: "전체"
        viewModel.getClothesForCategory(categoryToObserve).observe(viewLifecycleOwner) { items ->
            adapter.submitList(items)

            // ▼▼▼▼▼ 핵심 추가 로직 ▼▼▼▼▼
            // '전체' 탭이고, 옷 목록이 비어있을 때만 말풍선을 보여줍니다.
            if (categoryToObserve == "전체" && items.isEmpty()) {
                binding.emptyViewContainer.visibility = View.VISIBLE
                binding.recyclerViewClothingList.visibility = View.GONE
            } else {
                binding.emptyViewContainer.visibility = View.GONE
                binding.recyclerViewClothingList.visibility = View.VISIBLE
            }
            // ▲▲▲▲▲ 핵심 추가 로직 ▲▲▲▲▲
        }
    }

    fun scrollToTop() {
        if (isAdded && _binding != null) {
            binding.recyclerViewClothingList.smoothScrollToPosition(0)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
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