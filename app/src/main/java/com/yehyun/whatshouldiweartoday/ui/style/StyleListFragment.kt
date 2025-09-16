package com.yehyun.whatshouldiweartoday.ui.style

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yehyun.whatshouldiweartoday.R
import com.yehyun.whatshouldiweartoday.databinding.FragmentStyleListBinding
import com.yehyun.whatshouldiweartoday.ui.home.HomeViewModel

class StyleListFragment : Fragment() {

    private var _binding: FragmentStyleListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: StyleViewModel by viewModels({ requireParentFragment() })
    private val homeViewModel: HomeViewModel by activityViewModels() // HomeViewModel 추가
    private lateinit var adapter: SavedStylesAdapter
    private var season: String? = null
    private var itemClickListener: RecyclerItemClickListener? = null
    private var tabIndex: Int = 0

    fun resetDragState() {
        itemClickListener?.resetDragState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            season = it.getString(ARG_SEASON)
            tabIndex = when (season) {
                "전체" -> 0
                "봄" -> 1
                "여름" -> 2
                "가을" -> 3
                "겨울" -> 4
                else -> 0
            }
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

        itemClickListener = RecyclerItemClickListener(
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
                    if (viewModel.isDeleteMode.value != true) {
                        viewModel.enterDeleteMode(longClickedStyle.style.styleId)
                    }
                }
            },
            onLongDragStateChanged = { isLongDragging ->
                viewModel.setLongDragStateForTab(tabIndex, isLongDragging)
            }
        )

        binding.recyclerViewStyleList.addOnItemTouchListener(itemClickListener!!)
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

        homeViewModel.todayRecommendedClothingIds.observe(viewLifecycleOwner) { ids ->
            adapter.setRecommendedIds(ids)
        }

        homeViewModel.todayRecommendation.observe(viewLifecycleOwner) { result ->
            result?.let { adapter.setPackableOuters(it.packableOuters) }
        }
    }

    fun notifyAdapter(payload: String) {
        if (::adapter.isInitialized) {
            adapter.notifyItemRangeChanged(0, adapter.itemCount, payload)
        }
    }
    fun notifyAdapterRefresh() {
        if (::adapter.isInitialized) {
            adapter.notifyDataSetChanged()
        }
    }

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


    override fun onDestroyView() {
        super.onDestroyView()
        itemClickListener?.let { binding.recyclerViewStyleList.removeOnItemTouchListener(it) }
        itemClickListener = null
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