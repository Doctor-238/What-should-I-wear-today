package com.yehyun.whatshouldiweartoday.ui.closet

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.workDataOf
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

    // [수정] 권한 거부 시의 동작을 더 상세하게 변경
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            pickMultipleImagesLauncher.launch("image/*")
        } else {
            // 권한 요청이 거부된 직후, '다시 묻지 않음'을 선택했는지 확인합니다.
            // shouldShowRequestPermissionRationale이 false라면 영구 거부된 상태입니다.
            if (!shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                // 설정으로 이동할지 묻는 대화상자를 띄웁니다.
                showGoToSettingsDialog()
            } else {
                // '다시 묻지 않음'을 선택하지 않은 일반 거부일 경우, 토스트 메시지를 보여줍니다.
                Toast.makeText(requireContext(), "알림 권한이 거부되어 기능을 실행할 수 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

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

        setupViewPager()
        setupSearch()
        setupSortSpinner()

        binding.fabAddClothing.setOnClickListener {
            findNavController().navigate(R.id.action_navigation_closet_to_addClothingFragment)
        }

        binding.fabBatchAdd.setOnClickListener {
            checkNotificationPermission()
        }

        observeViewModel()
    }

    // [추가] 설정 화면으로 이동을 안내하는 대화상자 함수
    private fun showGoToSettingsDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("알림 권한이 필요합니다")
            .setMessage("진행률을 표시하려면 알림 권한이 반드시 필요합니다. '예'를 눌러 설정 화면으로 이동한 후, '알림' 권한을 허용해주세요.")
            .setPositiveButton("예") { _, _ ->
                // [수정] 앱의 '알림 설정' 화면으로 바로 이동하도록 수정
                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // Android 8.0 (Oreo) 이상
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
                    }
                } else {
                    // Android 8.0 미만
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


    private fun observeViewModel() {
        viewModel.batchAddWorkInfo.observe(viewLifecycleOwner) { workInfos ->
            val workInfo = workInfos.firstOrNull()

            if (workInfo == null || workInfo.state.isFinished) {
                lifecycleScope.launch {
                    delay(500)
                    binding.fabBatchAdd.hideProgress()
                }
                return@observe
            }

            val progress = workInfo.progress
            val current = progress.getInt(BatchAddWorker.PROGRESS_CURRENT, 0)
            val total = progress.getInt(BatchAddWorker.PROGRESS_TOTAL, 1)
            val percentage = if (total > 0) (current * 100 / total) else 0

            binding.fabBatchAdd.showProgress(percentage)
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