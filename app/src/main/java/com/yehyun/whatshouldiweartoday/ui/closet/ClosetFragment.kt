package com.yehyun.whatshouldiweartoday.ui.closet

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
import com.google.android.material.tabs.TabLayoutMediator
import com.yehyun.whatshouldiweartoday.R
import com.yehyun.whatshouldiweartoday.databinding.FragmentClosetBinding
import com.yehyun.whatshouldiweartoday.ui.OnTabReselectedListener

class ClosetFragment : Fragment(), OnTabReselectedListener {

    private var _binding: FragmentClosetBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ClosetViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentClosetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViewPager()
        setupSearch()
        setupSortSpinner()

        binding.fabAddClothing.setOnClickListener {
            findNavController().navigate(R.id.action_navigation_closet_to_addClothingFragment)
        }
    }

    override fun onResume() {
        super.onResume()
        // [핵심 수정] 설정 화면에서 돌아왔을 때, ViewModel의 데이터를 강제로 새로고침하여
        // 모든 옷 목록이 최신 설정 값으로 다시 그려지도록 합니다.
        viewModel.refreshData()
    }

    private fun setupViewPager() {
        val categories = listOf("전체", "상의", "하의", "아우터", "신발", "가방", "모자", "기타")
        val viewPagerAdapter = ClosetViewPagerAdapter(this, categories)
        binding.viewPagerCloset.adapter = viewPagerAdapter

        TabLayoutMediator(binding.tabLayoutCategory, binding.viewPagerCloset) { tab, position ->
            tab.text = categories[position]
        }.attach()
    }

    private fun setupSearch() {
        binding.searchViewCloset.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.setSearchQuery(newText.orEmpty())
                return true
            }
        })
    }

    private fun setupSortSpinner() {
        val spinner: Spinner = binding.spinnerSort
        val sortOptions = listOf("최신순", "오래된 순", "이름 오름차순", "이름 내림차순", "온도 오름차순", "온도 내림차순")
        val arrayAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, sortOptions)
        arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = arrayAdapter

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                viewModel.setSortType(sortOptions[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    override fun onTabReselected() {
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.navigation_closet) {
            navController.popBackStack(R.id.navigation_closet, false)
            return
        }

        if (binding.viewPagerCloset.currentItem != 0) {
            binding.viewPagerCloset.currentItem = 0
        } else {
            val currentFragment = childFragmentManager.findFragmentByTag("f0")
            (currentFragment as? ClothingListFragment)?.scrollToTop()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}