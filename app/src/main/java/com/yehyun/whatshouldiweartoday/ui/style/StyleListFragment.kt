package com.yehyun.whatshouldiweartoday.ui.style

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yehyun.whatshouldiweartoday.R
import com.yehyun.whatshouldiweartoday.databinding.FragmentStyleListBinding

class StyleListFragment : Fragment() {

    private var _binding: FragmentStyleListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: StyleViewModel by viewModels({ requireParentFragment() })
    private lateinit var adapter: SavedStylesAdapter
    private var season: String? = null

    // ▼▼▼▼▼ 핵심 수정 1: itemClickListener 멤버 변수 제거 ▼▼▼▼▼
    // private lateinit var itemClickListener: RecyclerItemClickListener
    // ▲▲▲▲▲ 핵심 수정 끝 ▲▲▲▲▲

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
        adapter = SavedStylesAdapter(
            isDeleteMode = { viewModel.isDeleteMode.value ?: false },
            isItemSelected = { styleId -> viewModel.selectedItems.value?.contains(styleId) ?: false }
        )
        binding.recyclerViewStyleList.layoutManager = LinearLayoutManager(context)
        binding.recyclerViewStyleList.adapter = adapter
        binding.recyclerViewStyleList.itemAnimator = DefaultItemAnimator()

        // ▼▼▼▼▼ 핵심 수정 2: itemClickListener를 다시 지역 변수로 생성 ▼▼▼▼▼
        val itemClickListener = RecyclerItemClickListener(
            context = requireContext(),
            recyclerView = binding.recyclerViewStyleList,
            onItemClick = { _, position ->
                adapter.getStyleAt(position)?.let { clickedStyle ->
                    if (viewModel.isDeleteMode.value == true) {
                        viewModel.toggleItemSelection(clickedStyle.style.styleId)
                    } else {
                        val navController = requireParentFragment().findNavController()
                        if (navController.currentDestination?.id == R.id.navigation_style) {
                            val action = StyleFragmentDirections.actionNavigationStyleToEditStyleFragment(clickedStyle.style.styleId)
                            navController.navigate(action)
                        }
                    }
                }
            },
            onItemLongClick = { _, position ->
                adapter.getStyleAt(position)?.let { longClickedStyle ->
                    viewModel.enterDeleteMode(longClickedStyle.style.styleId)
                }
            }
        )
        binding.recyclerViewStyleList.addOnItemTouchListener(itemClickListener)
        // ▲▲▲▲▲ 핵심 수정 끝 ▲▲▲▲▲
    }

    private fun observeViewModel() {
        val seasonToObserve = season ?: "전체"

        viewModel.getStylesForSeason(seasonToObserve).observe(viewLifecycleOwner) { styles ->
            adapter.submitList(styles)

            if (seasonToObserve == "전체" && styles.isEmpty() && viewModel.searchQuery.value.isNullOrEmpty()) {
                binding.emptyStyleContainer.visibility = View.VISIBLE
                binding.recyclerViewStyleList.visibility = View.GONE
            } else {
                binding.emptyStyleContainer.visibility = View.GONE
                binding.recyclerViewStyleList.visibility = View.VISIBLE
            }
        }
    }

    fun notifyAdapter(payload: String) {
        if (::adapter.isInitialized) {
            adapter.notifyItemRangeChanged(0, adapter.itemCount, payload)
        }
    }

    // ▼▼▼▼▼ 핵심 수정 3: scrollToTop 함수에서 isDragging 관련 코드 제거 ▼▼▼▼▼
    fun scrollToTop() {
        if (isAdded && _binding != null) {
            binding.recyclerViewStyleList.smoothScrollToPosition(0)

            (binding.recyclerViewStyleList.layoutManager as? LinearLayoutManager)?.let { layoutManager ->
                val firstVisible = layoutManager.findFirstVisibleItemPosition()
                val lastVisible = layoutManager.findLastVisibleItemPosition()

                if (firstVisible != RecyclerView.NO_POSITION) {
                    for (i in firstVisible..lastVisible) {
                        val holder = binding.recyclerViewStyleList.findViewHolderForAdapterPosition(i)
                        val nestedRv = holder?.itemView?.findViewById<RecyclerView>(R.id.rv_style_items)
                        nestedRv?.smoothScrollToPosition(0)
                    }
                }
            }
        }
    }
    // ▲▲▲▲▲ 핵심 수정 끝 ▲▲▲▲▲

    override fun onDestroyView() {
        super.onDestroyView()
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