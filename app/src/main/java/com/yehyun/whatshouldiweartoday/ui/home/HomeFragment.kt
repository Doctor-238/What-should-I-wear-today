package com.yehyun.whatshouldiweartoday.ui.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.viewpager2.widget.ViewPager2
import com.yehyun.whatshouldiweartoday.R
import com.yehyun.whatshouldiweartoday.databinding.FragmentHomeBinding
import com.yehyun.whatshouldiweartoday.ui.OnTabReselectedListener

class HomeFragment : Fragment(), OnTabReselectedListener {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val homeViewModel: HomeViewModel by activityViewModels()
    private val args: HomeFragmentArgs by navArgs()

    private var toast: Toast? = null

    private var locationDialog: AlertDialog? = null

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        homeViewModel.permissionRequestedThisSession = false
        if (isGranted) {
            checkAndRefresh()
        } else {
            if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                showToast("위치 권한이 거부되어 날씨 정보를 가져올 수 없습니다.")
                homeViewModel.stopLoading()
            } else {
                showGoToSettingsDialog("이 앱은 위치 권한이 반드시 필요합니다. 앱을 사용하려면 '설정'으로 이동하여 위치 권한을 허용해주세요.")
                homeViewModel.stopLoading()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.ivSettings.setOnClickListener { findNavController().navigate(R.id.action_navigation_home_to_settingsFragment) }
        setupCustomTabs()
        setupViewPager()

        binding.swipeRefreshLayout.setOnRefreshListener {
            checkAndRefresh()
        }

        if (args.targetTab != 0) { homeViewModel.requestTabSwitch(args.targetTab) }

        observeViewModel()

//        if (savedInstanceState == null) {
//            checkAndRefresh()
//        }
    }

    override fun onResume() {
        super.onResume()
        checkAndRefresh()

    }


    override fun onDestroyView() {
        super.onDestroyView()
        // Fragment의 View가 파괴될 때, 다이얼로그가 살아있으면 반드시 dismiss() 호출
        locationDialog?.dismiss()
        locationDialog = null // 참조 해제
        _binding = null
    }

    private fun observeViewModel() {
        homeViewModel.error.observe(viewLifecycleOwner) { errorMessage ->
            errorMessage?.let {
                showToast(it)
                homeViewModel.onErrorShown()
            }
        }

        homeViewModel.isLoading.observe(viewLifecycleOwner) {
            binding.swipeRefreshLayout.isRefreshing = it
        }
        homeViewModel.isRecommendationScrolledToTop.observe(viewLifecycleOwner) {
            binding.swipeRefreshLayout.isEnabled = it
        }
        homeViewModel.switchToTab.observe(viewLifecycleOwner) { tabIndex ->
            tabIndex?.let {
                binding.viewPagerHome.currentItem = it
                homeViewModel.onTabSwitchHandled()
            }
        }
    }

    private fun checkAndRefresh() {
        if (!isAdded) return

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermission()
            return
        }

        val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) && !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            homeViewModel.stopLoading()
            showTurnOnLocationDialog()
            return
        }

        val apiKey = getString(R.string.openweathermap_api_key)
        if (apiKey.isBlank() || apiKey == "YOUR_OPENWEATHERMAP_API_KEY") {
            showToast("날씨 API 키가 설정되지 않았습니다.")
            homeViewModel.stopLoading()
            return
        }

        homeViewModel.refreshWeatherData(apiKey)
    }

    private fun requestLocationPermission() {
        // 사용자가 이전에 권한을 거부했는지 확인
        if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            // ▼▼▼ 핵심 수정 부분 ▼▼▼
            // UI가 완전히 준비된 후 다이얼로그를 띄우도록 하여 타이밍 문제 해결
            view?.post {
                // 프래그먼트가 여전히 화면에 있는지 다시 한번 확인 (안정성 강화)
                if (!isAdded) return@post

                AlertDialog.Builder(requireContext())
                    .setTitle("위치 권한 안내")
                    .setMessage("현재 위치의 날씨 정보를 가져오기 위해 위치 권한이 필요합니다.")
                    .setPositiveButton("권한 허용") { _, _ ->
                        homeViewModel.permissionRequestedThisSession = true
                        locationPermissionRequest.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    }
                    .setNegativeButton("거부") { _, _ ->
                        homeViewModel.stopLoading()
                        showToast("위치 권한이 거부되어 날씨 정보를 자동으로 가져올 수 없습니다.")
                    }
                    .setOnDismissListener {
                        // 다이얼로그가 닫힐 때, 만약 새로고침 중이었다면 멈춤
                        if (_binding != null && binding.swipeRefreshLayout.isRefreshing){
                            homeViewModel.stopLoading()
                        }
                    }
                    .show()
            }
            // ▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲

        } else {
            // 처음 권한을 요청하거나, '다시 묻지 않음'을 선택했을 경우
            if (!homeViewModel.permissionRequestedThisSession) {
                homeViewModel.permissionRequestedThisSession = true
                locationPermissionRequest.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            } else {
                // '다시 묻지 않음' 상태에서 권한이 필요함을 안내
                homeViewModel.stopLoading()
                showToast("위치 권한이 거부되었습니다. 앱 설정에서 권한을 허용해주세요.")
            }
        }
    }

    private fun showGoToSettingsDialog(message: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("권한 필요")
            .setMessage(message)
            .setPositiveButton("설정으로 이동") { _, _ ->
                homeViewModel.stopLoading()
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", requireContext().packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton("닫기") { _, _ ->
                homeViewModel.stopLoading()
            }
            .setCancelable(false)
            .show()
    }

    private fun showTurnOnLocationDialog() {
        // 다이얼로그가 이미 떠 있다면 중복으로 띄우지 않음
        if (locationDialog != null && locationDialog!!.isShowing) {
            return
        }

        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("위치 서비스 비활성화")
        builder.setMessage("날씨 정보를 가져오려면 위치 서비스가 필요합니다. 설정에서 위치를 켜주세요.")
        builder.setPositiveButton("설정으로 이동") { _, _ ->
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }
        builder.setNegativeButton("취소") { dialog, _ ->
            dialog.dismiss()
        }
        // 생성된 다이얼로그를 멤버 변수에 할당
        locationDialog = builder.create()
        locationDialog?.show()
    }

    private fun showToast(message: String) {
        toast?.cancel()
        toast = Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT)
        toast?.show()
    }

    private fun setupViewPager() {
        binding.viewPagerHome.adapter = HomeViewPagerAdapter(this)
        binding.viewPagerHome.isUserInputEnabled = false
    }

    private fun setupCustomTabs() {
        binding.tvTabToday.setOnClickListener {
            if (binding.viewPagerHome.currentItem == 0) {
                (childFragmentManager.findFragmentByTag("f0") as? RecommendationFragment)?.scrollToTop()
            } else {
                binding.viewPagerHome.currentItem = 0
            }
        }
        binding.tvTabTomorrow.setOnClickListener {
            if (binding.viewPagerHome.currentItem == 1) {
                (childFragmentManager.findFragmentByTag("f1") as? RecommendationFragment)?.scrollToTop()
            } else {
                binding.viewPagerHome.currentItem = 1
            }
        }

        binding.viewPagerHome.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            private var isInitialSelection = true

            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                if (isInitialSelection) {
                    updateTabAppearance(position, animate = false)
                    isInitialSelection = false
                } else {
                    updateTabAppearance(position, animate = true)
                }
            }
        })
    }

    private fun updateTabAppearance(selectedPosition: Int, animate: Boolean) {
        if (_binding == null) return

        val todayColor = if (selectedPosition == 0) R.color.tab_selected_text else R.color.tab_unselected_text
        val tomorrowColor = if (selectedPosition == 1) R.color.tab_selected_text else R.color.tab_unselected_text

        binding.tvTabToday.setTextColor(ContextCompat.getColor(requireContext(), todayColor))
        binding.tvTabTomorrow.setTextColor(ContextCompat.getColor(requireContext(), tomorrowColor))

        val indicatorTarget = if (selectedPosition == 0) binding.tvTabToday else binding.tvTabTomorrow

        indicatorTarget.post {
            if (_binding == null) return@post

            val indicatorWidth = indicatorTarget.width / 2
            val targetCenter = binding.tabsContainer.left + indicatorTarget.left + (indicatorTarget.width / 2)
            val indicatorStart = targetCenter - (indicatorWidth / 2)

            val params = binding.viewTabIndicator.layoutParams
            params.width = indicatorWidth
            binding.viewTabIndicator.layoutParams = params

            if (animate) {
                binding.viewTabIndicator.animate()
                    .x(indicatorStart.toFloat())
                    .setDuration(250)
                    .start()
            } else {
                binding.viewTabIndicator.x = indicatorStart.toFloat()
            }
        }
    }


    override fun onTabReselected() {
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.navigation_home) {
            navController.popBackStack(R.id.navigation_home, false)
            return
        }

        // ▼▼▼▼▼ 핵심 수정: '오늘' 탭으로 이동하고 맨 위로 스크롤 ▼▼▼▼▼
        if (_binding == null) return
        binding.viewPagerHome.currentItem = 0
        binding.viewPagerHome.post {
            if (isAdded) {
                (childFragmentManager.findFragmentByTag("f0") as? RecommendationFragment)?.scrollToTop()
            }
        }
        // ▲▲▲▲▲ 핵심 수정 ▲▲▲▲▲
    }
}