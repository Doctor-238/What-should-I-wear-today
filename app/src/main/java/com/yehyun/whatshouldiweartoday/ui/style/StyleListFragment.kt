package com.yehyun.whatshouldiweartoday.ui.style

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yehyun.whatshouldiweartoday.databinding.FragmentStyleListBinding

class StyleListFragment : Fragment() {

    private var _binding: FragmentStyleListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: StyleViewModel by viewModels({ requireParentFragment() })
    private lateinit var adapter: SavedStylesAdapter // 스타일 어댑터라고 가정합니다.
    private var season: String = "전체"
    private var isViewJustCreated = false

    // ▼▼▼ 스크롤 관련 변수 추가 (이전과 동일) ▼▼▼
    private var scrollOnNextDataUpdate = false
    private lateinit var dataObserver: RecyclerView.AdapterDataObserver
    // ▲▲▲ 스크롤 관련 변수 추가 ▲▲▲

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.getString("season")?.let {
            season = it
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
        isViewJustCreated = true

        setupRecyclerView()
        observeViewModel()

        // ▼▼▼ viewModel.sortTypeChanged.observe 블록 수정: scrollToTop() 직접 호출 제거 ▼▼▼
        viewModel.sortTypeChanged.observe(viewLifecycleOwner) {
            if (!isViewJustCreated) {
                // 스크롤을 바로 실행하는 대신, 플래그를 true로 설정하여
                // 다음 데이터 업데이트가 완료될 때 스크롤하도록 '예약'만 합니다.
                // 데이터 업데이트가 발생했을 때 onChanged()가 호출되지 않으면 스크롤되지 않습니다.
                scrollOnNextDataUpdate = true
            }
            isViewJustCreated = false
        }
        // ▲▲▲ 수정된 viewModel.sortTypeChanged.observe 블록 ▲▲▲
    }

    private fun setupRecyclerView() {
        adapter = SavedStylesAdapter { clickedStyle ->
            // 스타일 클릭 시 동작 정의 (예: EditStyleFragment로 이동)
            // val action = StyleFragmentDirections.actionNavigationStyleToEditStyleFragment(clickedStyle.id)
            // findNavController().navigate(action)
        }

        // ▼▼▼ 어댑터 데이터 옵저버 추가 (이전과 동일) ▼▼▼
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
        // ▲▲▲ 어댑터 데이터 옵저버 추가 ▲▲▲

        binding.recyclerViewStyleList.layoutManager = LinearLayoutManager(context)
        binding.recyclerViewStyleList.adapter = adapter
    }

    private fun observeViewModel() {
        val seasonToObserve = season ?: "전체"

        viewModel.getStylesForSeason(seasonToObserve).observe(viewLifecycleOwner) { styles ->
            // SavedStylesAdapter가 submitList를 지원한다면 사용 (DiffUtil 사용 가정)
            adapter.submitList(styles)
            // 만약 SavedStylesAdapter가 notifyDataSetChanged()를 사용한다면:
            // adapter.setStyles(styles)
            // adapter.notifyDataSetChanged() // 이 경우 onChanged()가 항상 호출될 가능성이 높습니다.

            // 현재 탭이 '전체' 탭이고, 스타일 목록이 비어있을 때만 말풍선을 보여줍니다.
            if (seasonToObserve == "전체" && styles.isEmpty()) {
                binding.emptyStyleContainer.visibility = View.VISIBLE
                binding.recyclerViewStyleList.visibility = View.GONE
            } else {
                binding.emptyStyleContainer.visibility = View.GONE
                binding.recyclerViewStyleList.visibility = View.VISIBLE
            }
        }
    }

    // ▼▼▼ scrollToTop 함수 추가 (이전과 동일) ▼▼▼
    fun scrollToTop() {
        binding.recyclerViewStyleList.smoothScrollToPosition(0)
    }
    // ▲▲▲ scrollToTop 함수 추가 ▲▲▲

    override fun onDestroyView() {
        super.onDestroyView()
        // ▼▼▼ 어댑터 데이터 변경 감시자 해제 로직 추가 (이전과 동일) ▼▼▼
        if (::adapter.isInitialized && ::dataObserver.isInitialized) {
            adapter.unregisterAdapterDataObserver(dataObserver)
        }
        // ▲▲▲ 어댑터 데이터 변경 감시자 해제 로직 추가 ▲▲▲
        binding.recyclerViewStyleList.adapter = null
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