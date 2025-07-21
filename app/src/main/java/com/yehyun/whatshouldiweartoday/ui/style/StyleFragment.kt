package com.yehyun.whatshouldiweartoday.ui.style

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.yehyun.whatshouldiweartoday.R
import com.yehyun.whatshouldiweartoday.databinding.FragmentStyleBinding
import com.yehyun.whatshouldiweartoday.ui.OnTabReselectedListener

class StyleFragment : Fragment(), OnTabReselectedListener {

    private var _binding: FragmentStyleBinding? = null
    private val binding get() = _binding!!

    private val viewModel: StyleViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStyleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViewPager()
        setupSearch()
        setupSortSpinner()

        binding.fabAddStyle.setOnClickListener {
            findNavController().navigate(R.id.action_global_saveStyleFragment)
        }
    }

    private fun setupViewPager() {
        val seasons = listOf("전체", "봄", "여름", "가을", "겨울")
        val adapter = StyleViewPagerAdapter(this, seasons)
        binding.viewPagerStyle.adapter = adapter
        binding.viewPagerStyle.isUserInputEnabled = false

        TabLayoutMediator(binding.tabLayoutStyleSeason, binding.viewPagerStyle) { tab, position ->
            tab.text = seasons[position]
        }.attach()

        binding.tabLayoutStyleSeason.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {}
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {
                tab?.position?.let { position ->
                    (childFragmentManager.findFragmentByTag("f$position") as? StyleListFragment)?.scrollToTop()
                }
            }
        })
    }

    private fun setupSearch() {
        binding.searchViewStyle.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.setSearchQuery(newText.orEmpty())
                return true
            }
        })
    }

    private fun setupSortSpinner() {
        val spinner: Spinner = binding.spinnerSortStyle
        val sortOptions = listOf("최신순", "오래된 순", "이름 오름차순", "이름 내림차순")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, sortOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        val currentSortType = viewModel.getCurrentSortType()
        val currentPosition = sortOptions.indexOf(currentSortType)
        if (currentPosition >= 0) {
            spinner.setSelection(currentPosition)
        }

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                viewModel.setSortType(sortOptions[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    override fun onTabReselected() {
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.navigation_style) {
            navController.popBackStack(R.id.navigation_style, false)
            return
        }
        // ▼▼▼▼▼ 핵심 수정: '전체' 탭으로 이동하고 맨 위로 스크롤 ▼▼▼▼▼
        if (_binding == null) return
        binding.viewPagerStyle.currentItem = 0
        binding.viewPagerStyle.post {
            if (isAdded) {
                (childFragmentManager.findFragmentByTag("f0") as? StyleListFragment)?.scrollToTop()
            }
        }
        // ▲▲▲▲▲ 핵심 수정 ▲▲▲▲▲
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}