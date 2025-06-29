package com.yehyun.whatshouldiweartoday.ui.style

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.yehyun.whatshouldiweartoday.databinding.FragmentStyleListBinding

class StyleListFragment : Fragment() {

    private var _binding: FragmentStyleListBinding? = null
    private val binding get() = _binding!!

    // 부모 프래그먼트인 StyleFragment의 ViewModel을 공유합니다.
    private val viewModel: StyleViewModel by viewModels({requireParentFragment()})
    private lateinit var adapter: SavedStylesAdapter
    private var season: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // newInstance를 통해 전달받은 계절(season) 값을 저장합니다.
        arguments?.let {
            season = it.getString(ARG_SEASON)
        }
    }

    // [오류 해결] 프래그먼트의 뷰를 생성하는 표준적인 방식입니다.
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStyleListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()

        // ViewModel의 스타일 목록(styles)에 변화가 생길 때마다 호출됩니다.
        viewModel.styles.observe(viewLifecycleOwner) { styles ->
            // 현재 프래그먼트의 계절에 맞는 스타일만 필터링합니다.
            val filteredList = if (season == "전체") {
                styles
            } else {
                styles.filter { it.style.season == season }
            }
            // 필터링된 리스트를 어댑터에 전달하여 화면에 표시합니다.
            adapter.submitList(filteredList)
        }
    }

    private fun setupRecyclerView() {
        adapter = SavedStylesAdapter { clickedStyle ->
            val action = StyleFragmentDirections.actionNavigationStyleToEditStyleFragment(clickedStyle.style.styleId)
            requireParentFragment().findNavController().navigate(action)
        }
        binding.rvStyleList.layoutManager = LinearLayoutManager(requireContext())
        binding.rvStyleList.adapter = adapter
    }

    fun scrollToTop() {
        if (this::adapter.isInitialized && adapter.itemCount > 0) {
            binding.rvStyleList.smoothScrollToPosition(0)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_SEASON = "season"
        @JvmStatic
        fun newInstance(season: String) =
            StyleListFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_SEASON, season)
                }
            }
    }
}