package com.yehyun.whatshouldiweartoday.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.gms.location.LocationServices
import com.google.android.material.tabs.TabLayoutMediator
import com.yehyun.whatshouldiweartoday.R
import com.yehyun.whatshouldiweartoday.databinding.FragmentHomeBinding
import com.yehyun.whatshouldiweartoday.ui.OnTabReselectedListener

class HomeFragment : Fragment(), OnTabReselectedListener {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val homeViewModel: HomeViewModel by activityViewModels()

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            getCurrentLocation()
        } else {
            Toast.makeText(requireContext(), "위치 권한이 거부되어 날씨 정보를 가져올 수 없습니다.", Toast.LENGTH_LONG).show()
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
        checkLocationPermission()
        binding.swipeRefreshLayout.setOnRefreshListener { checkLocationPermission() }
        homeViewModel.error.observe(viewLifecycleOwner) {
            Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
        }
        homeViewModel.isLoading.observe(viewLifecycleOwner) {
            binding.swipeRefreshLayout.isRefreshing = it
        }
        homeViewModel.isRecommendationScrolledToTop.observe(viewLifecycleOwner) {
            binding.swipeRefreshLayout.isEnabled = it
        }
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
        // 홈 탭의 시작점(HomeFragment)이 아닌 다른 화면(예: 옷 수정)에 있을 경우
        if (navController.currentDestination?.id != R.id.navigation_home) {
            // 홈 탭의 시작점으로 돌아갑니다 (뒤로가기).
            navController.popBackStack(R.id.navigation_home, false)
            return
        }

        // 이미 홈 화면일 경우,
        // '내일' 탭을 보고 있었다면 '오늘' 탭으로 이동
        if (binding.viewPagerHome.currentItem != 0) {
            binding.viewPagerHome.currentItem = 0
        } else {
            // 이미 '오늘' 탭이라면, 스크롤을 맨 위로
            val currentFragment = childFragmentManager.findFragmentByTag("f0")
            (currentFragment as? RecommendationFragment)?.scrollToTop()
        }
    }

    // ... (나머지 코드는 이전과 동일)
    private fun checkLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                getCurrentLocation()
            }
            else -> locationPermissionRequest.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    private fun getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    val apiKey = getString(com.yehyun.whatshouldiweartoday.R.string.openweathermap_api_key)
                    if (apiKey.isBlank() || apiKey == "YOUR_OPENWEATHERMAP_API_KEY") {
                        Toast.makeText(context, "secrets.xml 파일에 날씨 API 키를 입력해주세요.", Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }
                    homeViewModel.fetchWeatherData(location.latitude, location.longitude, apiKey)
                } else {
                    Toast.makeText(context, "위치 정보를 가져올 수 없습니다. 위치 설정을 확인해주세요.", Toast.LENGTH_LONG).show()
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}