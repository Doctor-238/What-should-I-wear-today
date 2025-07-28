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

    // ▼▼▼ 추가된 변수 ▼▼▼
    // 다음 데이터 업데이트 시 스크롤을 실행할지 여부를 결정하는 플래그
    private var scrollOnNextDataUpdate = false
    // 메모리 누수 방지를 위해 AdapterDataObserver 참조를 저장
    private lateinit var dataObserver: RecyclerView.AdapterDataObserver
    // ▲▲▲ 추가된 변수 ▲▲▲

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.getString("category")?.let {
            category = it // null 아님이 보장됨
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

        // ▼▼▼ 수정된 정렬 변경 리스너 ▼▼▼
        viewModel.sortChangedEvent.observe(viewLifecycleOwner, Observer {
            if (!isViewJustCreated) {
                // 스크롤을 바로 실행하는 대신, 플래그를 true로 설정하여
                // 다음 데이터 업데이트가 완료될 때 스크롤하도록 예약합니다.
                scrollOnNextDataUpdate = true
            }
            isViewJustCreated = false
        })
        // ▲▲▲ 수정된 정렬 변경 리스너 ▲▲▲
    }

    private fun setupRecyclerView() {
        adapter = ClothingAdapter { clickedItem ->
            val action =
                ClosetFragmentDirections.actionNavigationClosetToEditClothingFragment(clickedItem.id)
            requireParentFragment().findNavController().navigate(action)
        }

        // ▼▼▼ 핵심 추가 로직 ▼▼▼
        // 어댑터의 데이터 변경을 감지하는 '감시자(Observer)'를 만듭니다.
        dataObserver = object : RecyclerView.AdapterDataObserver() {
            // 목록 전체가 갱신되었다는 신호('onChanged')를 받으면 실행됩니다.
            override fun onChanged() {
                super.onChanged()
                // 스크롤 예약 플래그(scrollOnNextDataUpdate)가 true이면,
                // 바로 이 시점에 스크롤을 실행하고 플래그를 다시 false로 바꿉니다.
                if (scrollOnNextDataUpdate) {
                    scrollToTop()
                    scrollOnNextDataUpdate = false
                }
            }
        }
        // 어댑터에 위에서 만든 '감시자'를 등록합니다.
        adapter.registerAdapterDataObserver(dataObserver)
        // ▲▲▲ 핵심 추가 로직 ▲▲▲

        binding.recyclerViewClothingList.layoutManager = GridLayoutManager(context, 2)
        binding.recyclerViewClothingList.adapter = adapter
    }

    private fun observeViewModel() {
        val categoryToObserve = category ?: "전체"
        viewModel.getClothesForCategory(categoryToObserve).observe(viewLifecycleOwner) { items ->
            adapter.submitList(items)

            // '전체' 탭이고, 옷 목록이 비어있을 때만 말풍선을 보여줍니다.
            if (categoryToObserve == "전체" && items.isEmpty()) {
                binding.emptyViewContainer.visibility = View.VISIBLE
                binding.recyclerViewClothingList.visibility = View.GONE
            } else {
                binding.emptyViewContainer.visibility = View.GONE
                binding.recyclerViewClothingList.visibility = View.VISIBLE
            }
        }
    }

    fun scrollToTop() {
        // smoothScrollToPosition(0) 대신 scrollToPosition(0)을 사용하여
        // 애니메이션 없이 즉시 맨 위로 이동시켜 더 안정적인 느낌을 줍니다.
        binding.recyclerViewClothingList.smoothScrollToPosition(0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // ▼▼▼ 추가된 로직 ▼▼▼
        // 화면이 사라질 때, 등록했던 '감시자'를 반드시 해제하여 메모리 누수를 방지합니다.
        if (::adapter.isInitialized && ::dataObserver.isInitialized) {
            adapter.unregisterAdapterDataObserver(dataObserver)
        }
        // ▲▲▲ 추가된 로직 ▲▲▲
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