package com.yehyun.whatshouldiweartoday.ui.style

import android.os.Bundle
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.yehyun.whatshouldiweartoday.R
import com.yehyun.whatshouldiweartoday.databinding.FragmentStyleBinding
import com.yehyun.whatshouldiweartoday.ui.OnTabReselectedListener
import com.yehyun.whatshouldiweartoday.ui.closet.ClothingListFragment
import kotlinx.coroutines.launch

class StyleFragment : Fragment(), OnTabReselectedListener {

    private var _binding: FragmentStyleBinding? = null
    private val binding get() = _binding!!

    private val viewModel: StyleViewModel by viewModels()
    private lateinit var onBackPressedCallback: OnBackPressedCallback



    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStyleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViewPagerAndTabs()
        setupSearch()
        setupSortSpinner()
        setupBackButtonHandler()
        setupListeners()
        setupObservers()
    }

    private fun setupListeners() {
        binding.fabAddStyle.setOnClickListener {
            findNavController().navigate(R.id.action_global_saveStyleFragment)
        }
        binding.ivBackDeleteModeStyle.setOnClickListener {
            viewModel.exitDeleteMode()
        }
        binding.ivSelectAllStyle.setOnClickListener {
            val currentState = viewModel.currentTabState.value ?: return@setOnClickListener
            if (currentState.items.isEmpty()) return@setOnClickListener

            val areAllSelected = currentState.items.all { it.style.styleId in currentState.selectedItemIds }
            if (areAllSelected) {
                viewModel.deselectAll(currentState.items)
            } else {
                viewModel.selectAll(currentState.items)
            }
        }
        binding.btnDeleteStyle.setOnClickListener {
            val count = viewModel.selectedItems.value?.size ?: 0
            if (count > 0) {
                AlertDialog.Builder(requireContext())
                    .setTitle("삭제 확인")
                    .setMessage("${count}개의 스타일을 정말 삭제하시겠습니까?")
                    .setPositiveButton("예") { _, _ -> viewModel.deleteSelectedItems() }
                    .setNegativeButton("아니오", null)
                    .show()
            }
        }
    }

    private fun setupObservers() {
        viewModel.currentTabState.observe(viewLifecycleOwner) { state ->
            if (_binding == null) return@observe

            updateToolbarVisibility(state.isDeleteMode)
            onBackPressedCallback.isEnabled = state.isDeleteMode

            binding.btnDeleteStyle.isEnabled = state.selectedItemIds.isNotEmpty()

            if (state.isDeleteMode) {
                if (state.items.isEmpty()) {
                    binding.ivSelectAllStyle.isEnabled = false
                    updateSelectAllIcon(false)
                } else {
                    binding.ivSelectAllStyle.isEnabled = true
                    val areAllSelected = state.items.all { it.style.styleId in state.selectedItemIds }
                    updateSelectAllIcon(areAllSelected)
                }
            }
        }

        viewModel.isDeleteMode.observe(viewLifecycleOwner) { notifyAdapterDeleteModeChanged() }
        viewModel.selectedItems.observe(viewLifecycleOwner) { notifyAdapterSelectionChanged() }


        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.resetSearchEvent.collect {
                    resetsearchViewStyle() // 신호가 오면 스스로 함수 호출
                }
            }
        }
    }

    private fun updateToolbarVisibility(isDeleteMode: Boolean) {
        if (_binding == null) return
        TransitionManager.beginDelayedTransition(binding.toolbarContainerStyle)
        if (isDeleteMode) {
            binding.toolbarNormalStyle.visibility = View.GONE
            binding.toolbarDeleteStyle.visibility = View.VISIBLE
            binding.fabAddStyle.hide()
        } else {
            binding.toolbarNormalStyle.visibility = View.VISIBLE
            binding.toolbarDeleteStyle.visibility = View.GONE
            binding.fabAddStyle.show()
        }
    }

    private fun updateSelectAllIcon(isChecked: Boolean) {
        if (_binding == null) return
        if (isChecked) {
            binding.ivSelectAllStyle.setImageResource(R.drawable.ic_checkbox_checked_custom)
        } else {
            binding.ivSelectAllStyle.setImageResource(R.drawable.ic_checkbox_unchecked_custom)
        }
    }


    private fun setupViewPagerAndTabs() {
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

        binding.viewPagerStyle.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                viewModel.setCurrentTabIndex(position)
            }
        })
    }

    private fun notifyAdapterDeleteModeChanged() {
        if (binding.viewPagerStyle.adapter == null) return
        for (i in 0 until binding.viewPagerStyle.adapter!!.itemCount) {
            val fragment = childFragmentManager.findFragmentByTag("f$i") as? StyleListFragment
            fragment?.notifyAdapter("DELETE_MODE_CHANGED")
        }
    }

    private fun notifyAdapterSelectionChanged() {
        if (binding.viewPagerStyle.adapter == null) return
        for (i in 0 until binding.viewPagerStyle.adapter!!.itemCount) {
            val fragment = childFragmentManager.findFragmentByTag("f$i") as? StyleListFragment
            fragment?.notifyAdapter("SELECTION_CHANGED")
        }
    }

    private fun setupSearch() {
        binding.searchViewStyle.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.setSearchQuery(newText.orEmpty())
                return true
            }
        })
        binding.searchViewDelete.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.setSearchQuery(newText.orEmpty())
                return true
            }
        })
    }

    private fun resetsearchViewStyle(){
        var query=""
        binding.searchViewDelete.setQuery(query, false)
        binding.searchViewStyle.setQuery(query, false)
    }

    private fun setupSortSpinner() {
        val spinner: Spinner = binding.spinnerSortStyle
        val sortOptions = listOf("최신순", "오래된 순", "이름 오름차순", "이름 내림차순")
        val adapter = ArrayAdapter(requireContext(), R.layout.spinner_item_centered_normal, sortOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        val currentSortType = viewModel.getCurrentSortType()
        val currentPosition = sortOptions.indexOf(currentSortType)
        if (currentPosition >= 0) {
            spinner.setSelection(currentPosition)
        }

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if(position!=currentPosition){
                    viewModel.setSortType(sortOptions[position])
                    val current = binding.viewPagerStyle.currentItem
                    val fragment =
                        childFragmentManager.findFragmentByTag("f$current") as? StyleListFragment
                    fragment?.scrollToTop()
                }else{viewModel.setSortType(sortOptions[position])}
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {

            }


        }
    }

    private fun setupBackButtonHandler() {
        onBackPressedCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                viewModel.exitDeleteMode()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, onBackPressedCallback)
    }

    override fun onTabReselected() {
        if (viewModel.isDeleteMode.value == true) {
            viewModel.exitDeleteMode()
            return
        }

        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.navigation_style) {
            navController.popBackStack(R.id.navigation_style, false)
            return
        }

        if (_binding == null) return
        if (binding.viewPagerStyle.currentItem == 0) {
            val fragment = childFragmentManager.findFragmentByTag("f0") as? StyleListFragment
            fragment?.scrollToTop()
        } else {
            binding.viewPagerStyle.currentItem = 0
            binding.viewPagerStyle.post { // ViewPager 애니메이션 완료 후 스크롤을 위해 post 사용
                val fragment = childFragmentManager.findFragmentByTag("f0") as? StyleListFragment
                fragment?.scrollToTop()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        onBackPressedCallback.remove()
        _binding = null
    }
}