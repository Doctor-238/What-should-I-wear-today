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
        viewModel.clothes.observe(viewLifecycleOwner) { items ->
            val filteredList = if (category == "전체") {
                items
            } else {
                items.filter { it.category == category }
            }
            // ▼▼▼▼▼ 핵심 수정 부분 ▼▼▼▼▼
            // 어댑터에 새 리스트를 전달하고, UI 업데이트가 완료된 후 실행될 콜백을 추가합니다.
            // 이 콜백에서 리스트의 스크롤을 맨 위(0번 위치)로 이동시킵니다.
            // 이렇게 하면 정렬 순서를 바꿀 때마다 항상 스크롤이 최상단으로 이동합니다.
            adapter.submitList(filteredList) {
                if (filteredList.isNotEmpty()) {
                    binding.recyclerViewClothingList.scrollToPosition(0)
                }
            }
            // ▲▲▲▲▲ 핵심 수정 부분 ▲▲▲▲▲
        }
    }

    fun scrollToTop() {
        if (::adapter.isInitialized && adapter.itemCount > 0) {
            binding.recyclerViewClothingList.smoothScrollToPosition(0)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
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