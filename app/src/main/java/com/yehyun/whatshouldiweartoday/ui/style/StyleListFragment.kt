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
        adapter = SavedStylesAdapter { clickedStyle ->
            val action = StyleFragmentDirections.actionNavigationStyleToEditStyleFragment(clickedStyle.style.styleId)
            requireParentFragment().findNavController().navigate(action)
        }
        // ▼▼▼▼▼ 핵심 수정: 올바른 ID(rvStyleList)로 변경 ▼▼▼▼▼
        binding.rvStyleList.layoutManager = LinearLayoutManager(context)
        binding.rvStyleList.adapter = adapter
    }

    private fun observeViewModel() {
        val seasonToObserve = season ?: "전체"
        viewModel.getStylesForSeason(seasonToObserve).observe(viewLifecycleOwner) { styles ->
            adapter.submitList(styles)
        }
    }

    fun scrollToTop() {
        if (isAdded && _binding != null) {
            binding.rvStyleList.smoothScrollToPosition(0)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvStyleList.adapter = null
        // ▲▲▲▲▲ 핵심 수정 ▲▲▲▲▲
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