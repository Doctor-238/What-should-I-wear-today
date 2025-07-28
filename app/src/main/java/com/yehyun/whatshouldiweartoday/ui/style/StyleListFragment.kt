package com.yehyun.whatshouldiweartoday.ui.style

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yehyun.whatshouldiweartoday.databinding.FragmentStyleListBinding

class StyleListFragment : Fragment() {

    private var _binding: FragmentStyleListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: StyleViewModel by viewModels({ requireParentFragment() })
    private lateinit var adapter: SavedStylesAdapter
    private var season: String? = null

    private var scrollOnNextDataUpdate = false
    private lateinit var dataObserver: RecyclerView.AdapterDataObserver
    private var isViewJustCreated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            season = it.getString(ARG_SEASON)
        }
    }

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
        observeViewModel()
    }

    private fun setupRecyclerView() {
        adapter = SavedStylesAdapter { clickedStyle ->
            val action = StyleFragmentDirections.actionNavigationStyleToEditStyleFragment(clickedStyle.style.styleId)
            requireParentFragment().findNavController().navigate(action)
        }
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
        // ▼▼▼▼▼ 핵심 수정 부분 ▼▼▼▼▼
        binding.recyclerViewStyleList.layoutManager = LinearLayoutManager(context)
        binding.recyclerViewStyleList.adapter = adapter
        // ▲▲▲▲▲ 핵심 수정 부분 ▲▲▲▲▲
    }

    private fun observeViewModel() {
        // newInstance를 통해 전달받은 season 값 (탭 이름)
        val seasonToObserve = season ?: "전체"

        viewModel.getStylesForSeason(seasonToObserve).observe(viewLifecycleOwner) { styles ->
            adapter.submitList(styles)

            // ▼▼▼▼▼ 핵심 수정 로직 ▼▼▼▼▼
            // 현재 탭이 '전체' 탭이고, 스타일 목록이 비어있을 때만 말풍선을 보여줍니다.
            if (seasonToObserve == "전체" && styles.isEmpty()) {
                binding.emptyStyleContainer.visibility = View.VISIBLE
                binding.recyclerViewStyleList.visibility = View.GONE
            } else {
                // 그 외의 경우(다른 탭이거나, 전체 탭에 스타일이 있거나)에는 말풍선을 숨깁니다.
                binding.emptyStyleContainer.visibility = View.GONE
                binding.recyclerViewStyleList.visibility = View.VISIBLE
            }
            viewModel.sortTypeChanged.observe(viewLifecycleOwner) {
                if (!isViewJustCreated) {
                    scrollOnNextDataUpdate = true
                }
                isViewJustCreated = false
            }
            // ▲▲▲▲▲ 핵심 수정 로직 ▲▲▲▲▲
        }
    }

    fun scrollToTop() {
        if (isAdded && _binding != null) {
            // ▼▼▼▼▼ 핵심 수정 부분 ▼▼▼▼▼
            binding.recyclerViewStyleList.smoothScrollToPosition(0)
            // ▲▲▲▲▲ 핵심 수정 부분 ▲▲▲▲▲
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // ▼▼▼▼▼ 핵심 수정 부분 ▼▼▼▼▼
        binding.recyclerViewStyleList.adapter = null
        // ▲▲▲▲▲ 핵심 수정 부분 ▲▲▲▲▲
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