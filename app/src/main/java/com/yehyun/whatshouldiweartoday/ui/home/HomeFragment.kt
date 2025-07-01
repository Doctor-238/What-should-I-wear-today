package com.yehyun.whatshouldiweartoday.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import com.google.android.gms.location.LocationServices
import com.google.android.material.tabs.TabLayoutMediator
import com.yehyun.whatshouldiweartoday.R
import com.yehyun.whatshouldiweartoday.databinding.FragmentHomeBinding
import com.yehyun.whatshouldiweartoday.ui.OnTabReselectedListener

class HomeFragment : Fragment(), OnTabReselectedListener {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val homeViewModel: HomeViewModel by activityViewModels()
    private val args: HomeFragmentArgs by navArgs()

    private var toast: Toast? = null
    private var isRequestingPermission = false

    // [수정] 포그라운드 위치 권한 요청 런처만 남김
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        isRequestingPermission = false
        if (isGranted) {
            getCurrentLocation() // 성공 시 바로 위치 가져오기
        } else {
            homeViewModel.locationPermissionGranted.value = false
            showToast("위치 권한이 거부되어 날씨 정보를 가져올 수 없습니다.")
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
        binding.ivSettings.setOnClickListener {
            findNavController().navigate(R.id.action_navigation_home_to_settingsFragment)
        }
        setupViewPager()
        binding.swipeRefreshLayout.setOnRefreshListener {
            checkLocationPermission(isTriggeredByUser = true)
        }
        if (args.targetTab != 0) {
            homeViewModel.requestTabSwitch(args.targetTab)
        }
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        if (!isRequestingPermission) {
            checkLocationPermission(isTriggeredByUser = false)
        }
    }

    private fun observeViewModel() {
        homeViewModel.error.observe(viewLifecycleOwner) {
            Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
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
        homeViewModel.locationPermissionGranted.observe(viewLifecycleOwner) { isGranted ->
            if (isGranted == false) {
                if (binding.swipeRefreshLayout.isRefreshing) {
                    binding.swipeRefreshLayout.isRefreshing = false
                }
            }
        }
    }

    // [수정] 권한 확인 로직을 '앱 사용 중 허용'만 체크하도록 대폭 단순화
    private fun checkLocationPermission(isTriggeredByUser: Boolean) {
        if (isRequestingPermission) return

        val coarsePermission = Manifest.permission.ACCESS_COARSE_LOCATION
        val isCoarseGranted = ContextCompat.checkSelfPermission(requireContext(), coarsePermission) == PackageManager.PERMISSION_GRANTED

        if (isCoarseGranted) {
            getCurrentLocation()
        } else {
            if (isTriggeredByUser || homeViewModel.todayWeatherSummary.value == null) {
                homeViewModel.locationPermissionGranted.value = false
                if (homeViewModel.locationPermissionGranted.value == false && !shouldShowRequestPermissionRationale(coarsePermission)) {
                    showGoToSettingsDialog()
                } else {
                    isRequestingPermission = true
                    locationPermissionRequest.launch(coarsePermission)
                }
            } else {
                homeViewModel.locationPermissionGranted.value = true
                showToast("위치 권한이 없어 이전에 불러온 날씨 정보를 표시합니다.")
            }
        }
    }

    private fun showGoToSettingsDialog() {
        isRequestingPermission = true
        AlertDialog.Builder(requireContext())
            .setTitle("위치 권한이 필요합니다")
            .setMessage("날씨 정보 조회를 위해 위치 권한이 반드시 필요합니다. '예'를 눌러 설정 화면으로 이동한 후, '권한' 메뉴에서 '위치' 권한을 허용해주세요.")
            .setPositiveButton("예") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", requireContext().packageName, null)
                intent.data = uri
                startActivity(intent)
                isRequestingPermission = false
            }
            .setNegativeButton("아니오") { _, _ ->
                showToast("위치 권한이 거부되어 기능을 실행할 수 없습니다.")
                homeViewModel.locationPermissionGranted.value = false
                isRequestingPermission = false
            }
            .setOnCancelListener { isRequestingPermission = false }
            .show()
    }

    private fun getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            homeViewModel.locationPermissionGranted.value = false
            return
        }
        homeViewModel.locationPermissionGranted.value = true

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val apiKey = getString(R.string.openweathermap_api_key)
                if (apiKey.isBlank() || apiKey == "YOUR_OPENWEATHERMAP_API_KEY") {
                    showToast("secrets.xml 파일에 날씨 API 키를 입력해주세요.")
                    return@addOnSuccessListener
                }
                homeViewModel.fetchWeatherData(location.latitude, location.longitude, apiKey)
            } else {
                showToast("위치 정보를 가져올 수 없습니다. 위치 설정을 확인해주세요.")
            }
        }
    }

    private fun showToast(message: String) {
        toast?.cancel()
        toast = Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT)
        toast?.show()
    }

    private fun setupViewPager() {
        binding.viewPagerHome.adapter = HomeViewPagerAdapter(this)
        binding.viewPagerHome.isUserInputEnabled = false
        TabLayoutMediator(binding.tabLayoutHome, binding.viewPagerHome) { tab, position ->
            tab.text = if (position == 0) "오늘" else "내일"
        }.attach()
    }

    override fun onTabReselected() {
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.navigation_home) {
            navController.popBackStack(R.id.navigation_home, false)
            return
        }
        if (binding.viewPagerHome.currentItem != 0) {
            binding.viewPagerHome.currentItem = 0
        } else {
            val currentFragment = childFragmentManager.findFragmentByTag("f0")
            (currentFragment as? RecommendationFragment)?.scrollToTop()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        toast?.cancel()
        _binding = null
    }
}