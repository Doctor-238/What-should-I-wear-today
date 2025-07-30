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
import com.yehyun.whatshouldiweartoday.R
import com.yehyun.whatshouldiweartoday.databinding.FragmentStyleListBinding

class StyleListFragment : Fragment() {

    private var _binding: FragmentStyleListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: StyleViewModel by viewModels({ requireParentFragment() })
    private lateinit var adapter: SavedStylesAdapter
    private var season: String? = null

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
            onItemClicked = { clickedStyle ->
                if (viewModel.isDeleteMode.value == true) {
                    viewModel.toggleItemSelection(clickedStyle.style.styleId)
                } else {
                    // ▼▼▼▼▼ 핵심 수정: requireParentFragment()로 NavController를 찾도록 수정 ▼▼▼▼▼
                    val navController = requireParentFragment().findNavController()
                    if (navController.currentDestination?.id == R.id.navigation_style) {
                        val action = StyleFragmentDirections.actionNavigationStyleToEditStyleFragment(clickedStyle.style.styleId)
                        navController.navigate(action)
                    }
                }
            },
            onItemLongClicked = { longClickedStyle ->
                viewModel.enterDeleteMode(longClickedStyle.style.styleId)
            },
            isDeleteMode = { viewModel.isDeleteMode.value ?: false },
            isItemSelected = { styleId -> viewModel.selectedItems.value?.contains(styleId) ?: false }
        )
        binding.recyclerViewStyleList.layoutManager = LinearLayoutManager(context)
        binding.recyclerViewStyleList.adapter = adapter
        binding.recyclerViewStyleList.itemAnimator = DefaultItemAnimator()
    }

    private fun observeViewModel() {
        val seasonToObserve = season ?: "전체"

        viewModel.getStylesForSeason(seasonToObserve).observe(viewLifecycleOwner) { styles ->
            adapter.submitList(styles)

            // ▼▼▼▼▼ 핵심 수정: 오류가 발생한 부분을 원래의 올바른 로직으로 복원 ▼▼▼▼▼
            if (seasonToObserve == "전체" && styles.isEmpty() && viewModel.isDeleteMode.value == false) {
                binding.emptyStyleContainer.visibility = View.VISIBLE
                binding.recyclerViewStyleList.visibility = View.GONE
            } else {
                binding.emptyStyleContainer.visibility = View.GONE
                binding.recyclerViewStyleList.visibility = View.VISIBLE
            }
            // ▲▲▲▲▲ 핵심 수정 ▲▲▲▲▲
        }
    }

    fun notifyAdapter(payload: String) {
        if (::adapter.isInitialized) {
            adapter.notifyItemRangeChanged(0, adapter.itemCount, payload)
        }
    }

    fun scrollToTop() {
        if (isAdded && _binding != null) {
            binding.recyclerViewStyleList.smoothScrollToPosition(0)
        }
    }

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