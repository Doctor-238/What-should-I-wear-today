package com.yehyun.whatshouldiweartoday.ui.closet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.workDataOf
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayoutMediator
import com.yehyun.whatshouldiweartoday.R
import com.yehyun.whatshouldiweartoday.databinding.FragmentClosetBinding
import com.yehyun.whatshouldiweartoday.ui.OnTabReselectedListener
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ClosetFragment : Fragment(), OnTabReselectedListener {

    private var _binding: FragmentClosetBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ClosetViewModel by viewModels()

    private lateinit var fabBatchAdd: FloatingActionButton
    private lateinit var fabProgressBar: ProgressBar
    private lateinit var fabProgressText: TextView

    private val pickMultipleImagesLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val uriStrings = uris.map { it.toString() }.toTypedArray()
            startBatchAddWorker(uriStrings)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentClosetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fabBatchAdd = view.findViewById(R.id.fab_batch_add)
        fabProgressBar = view.findViewById(R.id.fab_progress_bar)
        fabProgressText = view.findViewById(R.id.fab_progress_text)

        setupViewPager()
        setupSearch()
        setupSortSpinner()

        binding.fabAddClothing.setOnClickListener {
            findNavController().navigate(R.id.action_navigation_closet_to_addClothingFragment)
        }
        fabBatchAdd.setOnClickListener {
            pickMultipleImagesLauncher.launch("image/*")
        }

        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.batchAddWorkInfo.observe(viewLifecycleOwner) { workInfos ->
            val workInfo = workInfos.firstOrNull()

            // 작업이 없거나 끝났을 때
            if (workInfo == null || workInfo.state.isFinished) {
                if (fabProgressBar.visibility == View.VISIBLE) {
                    lifecycleScope.launch {
                        delay(500)
                        setFabProgressUi(false)
                    }
                }
                return@observe
            }

            // 작업이 진행 중일 때
            setFabProgressUi(true)
            val progress = workInfo.progress
            val current = progress.getInt(BatchAddWorker.PROGRESS_CURRENT, 0)
            val total = progress.getInt(BatchAddWorker.PROGRESS_TOTAL, 1)
            val percentage = if (total > 0) (current * 100 / total) else 0
            fabProgressBar.progress = percentage
            fabProgressText.text = "$percentage%"
        }
    }

    private fun setFabProgressUi(isProcessing: Boolean) {
        if (isProcessing) {
            fabBatchAdd.setImageResource(0) // 아이콘 숨기기
            fabBatchAdd.isEnabled = false
            fabProgressBar.visibility = View.VISIBLE
            fabProgressText.visibility = View.VISIBLE
        } else {
            fabBatchAdd.setImageResource(R.drawable.ic_infinity) // 아이콘 보이기
            fabBatchAdd.isEnabled = true
            fabProgressBar.visibility = View.GONE
            fabProgressText.visibility = View.GONE
        }
    }

    private fun startBatchAddWorker(uriStrings: Array<String>) {
        val workRequest = OneTimeWorkRequestBuilder<BatchAddWorker>()
            .setInputData(workDataOf(
                BatchAddWorker.KEY_IMAGE_URIS to uriStrings,
                BatchAddWorker.KEY_API to getString(R.string.gemini_api_key)
            ))
            .build()

        viewModel.workManager.enqueueUniqueWork("batch_add", ExistingWorkPolicy.REPLACE, workRequest)
    }


    override fun onResume() {
        super.onResume()
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