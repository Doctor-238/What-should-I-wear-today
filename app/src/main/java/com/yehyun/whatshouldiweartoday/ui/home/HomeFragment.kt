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
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
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

    // [핵심] 권한 요청 결과 처리를 새 로직으로 변경
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // 1. 권한 허용 시: 위치 정보 가져오기 실행
            getCurrentLocation()
        }  else {
            if (!shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                showAppTerminationDialog()
            } else {
                Toast.makeText(requireContext(), "위치 권한이 거부되어 일부 기능에 제한이 있을 수 있습니다.", Toast.LENGTH_SHORT).show()
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
        setupViewPager()
        binding.swipeRefreshLayout.setOnRefreshListener { checkLocationPermission() }
        if (args.targetTab != 0) { homeViewModel.requestTabSwitch(args.targetTab) }
        observeViewModel()

        // 화면이 처음 생성될 때만 권한을 확인하고 요청합니다.
        checkLocationPermission()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        toast?.cancel()
        _binding = null
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
    }

    private fun checkLocationPermission() {
        when {
            // 권한이 이미 있는 경우
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                getCurrentLocation()
            }
            // 권한이 없는 경우, 시스템에 권한을 요청합니다.
            else -> {
                locationPermissionRequest.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }
    }



    // [핵심] 권한 거부 시 앱을 종료시키는 안내 팝업
    private fun showAppTerminationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("권한 필요")
            .setMessage("이 앱은 위치 권한이 반드시 필요합니다. 권한을 허용하지 않으면 앱을 사용할 수 없습니다.")
            .setPositiveButton("확인") { _, _ ->
                requireActivity().finish()
            }
            .setNegativeButton("설정으로 이동") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", requireContext().packageName, null)
                intent.data = uri
                startActivity(intent)
            }
            .setCancelable(false) // 뒤로가기 버튼으로 팝업을 닫을 수 없게 설정
            .show()
    }

    private fun showTurnOnLocationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("위치 서비스 비활성화")
            .setMessage("날씨 정보를 가져오려면 기기의 위치 서비스를 켜야 합니다. 설정으로 이동하시겠습니까?")
            .setPositiveButton("설정으로 이동") { _, _ ->
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .setNegativeButton("취소") { _, _ ->
                showToast("위치 서비스가 꺼져있어 날씨를 조회할 수 없습니다.")
                if(_binding != null) binding.swipeRefreshLayout.isRefreshing = false
            }
            .show()
    }


    private fun getCurrentLocation() {
        if (!isAdded) return
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            homeViewModel.locationPermissionGranted.value = false
            return
        }

        val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) && !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            showTurnOnLocationDialog()
            return
        }

        homeViewModel.locationPermissionGranted.value = true
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token)
            .addOnSuccessListener { location ->
                if (!isAdded) return@addOnSuccessListener
                if (location != null) {
                    val apiKey = getString(R.string.openweathermap_api_key)
                    if (apiKey.isBlank() || apiKey == "YOUR_OPENWEATHERMAP_API_KEY") {
                        showToast("secrets.xml 파일에 날씨 API 키를 입력해주세요.")
                        return@addOnSuccessListener
                    }
                    homeViewModel.fetchWeatherData(location.latitude, location.longitude, apiKey)
                } else {
                    showToast("위치 정보를 가져올 수 없습니다. 잠시 후 다시 시도해주세요.")
                    if(_binding != null) binding.swipeRefreshLayout.isRefreshing = false
                }
            }.addOnFailureListener {
                if (!isAdded) return@addOnFailureListener
                showToast("위치 정보를 가져오는 데 실패했습니다.")
                if(_binding != null) binding.swipeRefreshLayout.isRefreshing = false
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
}