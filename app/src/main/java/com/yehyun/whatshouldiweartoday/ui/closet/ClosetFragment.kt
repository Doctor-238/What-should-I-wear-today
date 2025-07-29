// 파일 경로: app/src/main/java/com/yehyun/whatshouldiweartoday/ui/closet/ClosetFragment.kt
package com.yehyun.whatshouldiweartoday.ui.closet

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.workDataOf
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.yehyun.whatshouldiweartoday.R
import com.yehyun.whatshouldiweartoday.databinding.FragmentClosetBinding
import com.yehyun.whatshouldiweartoday.ui.OnTabReselectedListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class ClosetFragment : Fragment(), OnTabReselectedListener {

    private var _binding: FragmentClosetBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ClosetViewModel by viewModels()

    private var lastFabClickTime = 0L
    private lateinit var onBackPressedCallback: OnBackPressedCallback


    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            pickMultipleImagesLauncher.launch("image/*")
        } else {
            if (!shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                showGoToSettingsDialog()
            } else {
                Toast.makeText(requireContext(), "알림 권한이 거부되어 기능을 실행할 수 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val pickMultipleImagesLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewLifecycleOwner.lifecycleScope.launch {
                binding.fabBatchAdd.showProgress(0)
                val copiedImagePaths = copyUrisToCache(uris)
                if (copiedImagePaths.isNotEmpty()) {
                    startBatchAddWorker(copiedImagePaths.toTypedArray())
                } else {
                    Toast.makeText(requireContext(), "이미지를 처리하는 데 실패했습니다.", Toast.LENGTH_SHORT).show()
                    binding.fabBatchAdd.hideProgress()
                }
            }
        }
    }

    private suspend fun copyUrisToCache(uris: List<Uri>): List<String> = withContext(Dispatchers.IO) {
        val pathList = mutableListOf<String>()
        val cacheDir = requireContext().cacheDir
        val formatter = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.KOREA)

        uris.forEachIndexed { index, uri ->
            try {
                val timestamp = formatter.format(Date())
                val tempFile = File(cacheDir, "batch_add_${timestamp}_$index.jpg")

                requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                    FileOutputStream(tempFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                pathList.add(tempFile.absolutePath)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        pathList
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

        setupFabs()
        setupSearch()
        setupSortSpinner()
        setupBackButtonHandler()
        setupViewPagerAndTabs()
        setupObservers()
    }

    private fun setupFabs() {
        binding.fabAddClothing.setOnClickListener {
            findNavController().navigate(R.id.action_navigation_closet_to_addClothingFragment)
        }

        binding.fabBatchAdd.setOnClickListener {
            if (System.currentTimeMillis() - lastFabClickTime < 700) {
                return@setOnClickListener
            }
            lastFabClickTime = System.currentTimeMillis()
            checkNotificationPermission()
        }
    }

    private fun setupObservers() {
        // UI 상태를 결정하는 핵심 Observer
        viewModel.currentTabState.observe(viewLifecycleOwner) { state ->
            if (_binding == null) return@observe

            // 1. 툴바 가시성 업데이트
            updateToolbarVisibility(state.isDeleteMode)
            onBackPressedCallback.isEnabled = state.isDeleteMode

            // 2. 삭제 버튼 상태 업데이트
            binding.btnDelete.isEnabled = state.selectedItemIds.isNotEmpty()

            // 3. '전체 선택' 아이콘 상태 업데이트
            if (state.isDeleteMode) {
                if (state.items.isEmpty()) {
                    binding.ivSelectAll.isEnabled = false
                    updateSelectAllIcon(false)
                } else {
                    binding.ivSelectAll.isEnabled = true
                    val areAllSelected = state.items.all { it.id in state.selectedItemIds }
                    updateSelectAllIcon(areAllSelected)
                }
            }
        }

        // 어댑터 업데이트를 위한 Observer는 별도로 유지
        viewModel.isDeleteMode.observe(viewLifecycleOwner) { notifyAdapterDeleteModeChanged() }
        viewModel.selectedItems.observe(viewLifecycleOwner) { notifyAdapterSelectionChanged() }

        // 일괄 추가 진행 상태 Observer
        viewModel.batchAddWorkInfo.observe(viewLifecycleOwner) { workInfos ->
            val workInfo = workInfos.firstOrNull() ?: run {
                binding.fabBatchAdd.hideProgress()
                return@observe
            }

            if (workInfo.state.isFinished) {
                lifecycleScope.launch {
                    delay(500)
                    _binding?.fabBatchAdd?.hideProgress()
                    viewModel.workManager.pruneWork()
                }
            } else {
                val progress = workInfo.progress
                val current = progress.getInt(BatchAddWorker.PROGRESS_CURRENT, 0)
                val total = progress.getInt(BatchAddWorker.PROGRESS_TOTAL, 1)
                val percentage = if (total > 0) (current * 100 / total) else 0
                binding.fabBatchAdd.showProgress(percentage)
            }
        }
    }

    private fun setupViewPagerAndTabs() {
        val categories = listOf("전체", "상의", "하의", "아우터", "신발", "가방", "모자", "기타")
        val viewPagerAdapter = ClosetViewPagerAdapter(this, categories)
        binding.viewPagerCloset.adapter = viewPagerAdapter

        TabLayoutMediator(binding.tabLayoutCategory, binding.viewPagerCloset) { tab, position ->
            tab.text = categories[position]
        }.attach()

        // 탭 레이아웃 리스너 대신 ViewPager2의 콜백을 사용
        binding.viewPagerCloset.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                viewModel.setCurrentTabIndex(position)
            }
        })

        // '전체 선택' 아이콘 클릭 리스너
        binding.ivSelectAll.setOnClickListener {
            val currentState = viewModel.currentTabState.value ?: return@setOnClickListener
            if (currentState.items.isEmpty()) return@setOnClickListener

            val areAllSelected = currentState.items.all { it.id in currentState.selectedItemIds }
            if (areAllSelected) {
                viewModel.deselectAll(currentState.items)
            } else {
                viewModel.selectAll(currentState.items)
            }
        }

        // 삭제 버튼 클릭 리스너
        binding.btnDelete.setOnClickListener {
            val count = viewModel.selectedItems.value?.size ?: 0
            if (count > 0) {
                AlertDialog.Builder(requireContext())
                    .setTitle("삭제 확인")
                    .setMessage("${count}개의 옷을 정말 삭제하시겠습니까?")
                    .setPositiveButton("예") { _, _ -> viewModel.deleteSelectedItems() }
                    .setNegativeButton("아니오", null)
                    .show()
            }
        }

        // 뒤로가기 버튼 클릭 리스너
        binding.ivBackDeleteMode.setOnClickListener { viewModel.exitDeleteMode() }
    }

    private fun updateSelectAllIcon(isChecked: Boolean) {
        if (_binding == null) return
        if (isChecked) {
            binding.ivSelectAll.setImageResource(R.drawable.ic_checkbox_checked_custom)
        } else {
            binding.ivSelectAll.setImageResource(R.drawable.ic_checkbox_unchecked_custom)
        }
    }

    private fun showGoToSettingsDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("알림 권한이 필요합니다")
            .setMessage("진행률을 표시하려면 알림 권한이 반드시 필요합니다. '예'를 눌러 설정 화면으로 이동한 후, '알림' 권한을 허용해주세요.")
            .setPositiveButton("예") { _, _ ->
                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
                    }
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", requireContext().packageName, null)
                    }
                }
                startActivity(intent)
            }
            .setNegativeButton("아니오") { _, _ ->
                Toast.makeText(requireContext(), "알림 권한이 거부되어 기능을 실행할 수 없습니다.", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                pickMultipleImagesLauncher.launch("image/*")
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            pickMultipleImagesLauncher.launch("image/*")
        }
    }

    private fun updateToolbarVisibility(isDeleteMode: Boolean) {
        if (_binding == null) return
        TransitionManager.beginDelayedTransition(binding.toolbarContainer)
        if (isDeleteMode) {
            binding.toolbarNormal.visibility = View.GONE
            binding.toolbarDelete.visibility = View.VISIBLE
        } else {
            binding.toolbarNormal.visibility = View.VISIBLE
            binding.toolbarDelete.visibility = View.GONE
        }
    }

    private fun notifyAdapterDeleteModeChanged() {
        if (binding.viewPagerCloset.adapter == null) return
        for (i in 0 until binding.viewPagerCloset.adapter!!.itemCount) {
            val fragment = childFragmentManager.findFragmentByTag("f$i") as? ClothingListFragment
            fragment?.notifyAdapter("DELETE_MODE_CHANGED")
        }
    }

    private fun notifyAdapterSelectionChanged() {
        if (binding.viewPagerCloset.adapter == null) return
        for (i in 0 until binding.viewPagerCloset.adapter!!.itemCount) {
            val fragment = childFragmentManager.findFragmentByTag("f$i") as? ClothingListFragment
            fragment?.notifyAdapter("SELECTION_CHANGED")
        }
    }

    private fun startBatchAddWorker(imagePaths: Array<String>) {
        val workRequest = OneTimeWorkRequestBuilder<BatchAddWorker>()
            .setInputData(workDataOf(
                BatchAddWorker.KEY_IMAGE_PATHS to imagePaths,
                BatchAddWorker.KEY_API to getString(R.string.gemini_api_key)
            ))
            .build()
        viewModel.workManager.enqueueUniqueWork("batch_add", ExistingWorkPolicy.REPLACE, workRequest)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshData()
    }

    private fun setupSearch() {
        binding.searchViewCloset.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
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

    private fun setupSortSpinner() {
        val spinner: Spinner = binding.spinnerSort
        val sortOptions = listOf("최신순", "오래된 순", "이름 오름차순", "이름 내림차순", "온도 오름차순", "온도 내림차순")
        val arrayAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, sortOptions)
        arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = arrayAdapter

        val currentSortType = viewModel.getCurrentSortType()
        val currentPosition = sortOptions.indexOf(currentSortType)
        if (currentPosition >= 0) {
            spinner.setSelection(currentPosition)
        }

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (viewModel.isDeleteMode.value == false) {
                    viewModel.setSortType(sortOptions[position])
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
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
        if (navController.currentDestination?.id != R.id.navigation_closet) {
            navController.popBackStack(R.id.navigation_closet, false)
            return
        }
        if (_binding == null) return
        binding.viewPagerCloset.currentItem = 0
        binding.viewPagerCloset.post {
            if (isAdded) {
                (childFragmentManager.findFragmentByTag("f0") as? ClothingListFragment)?.scrollToTop()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        onBackPressedCallback.remove()
        _binding = null
    }
}