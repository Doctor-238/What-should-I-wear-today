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
    private var permissionDialog: AlertDialog? = null
    private var isRequestingPermission = false

    // [핵심] 권한 요청 결과 처리를 안드로이드 정석대로 재구성
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        isRequestingPermission = false // 요청 프로세스 종료
        if (isGranted) {
            // 1. 사용자가 권한을 허용한 경우
            getCurrentLocation()
        } else {
            // 2. 사용자가 권한을 거부한 경우
            if (!shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                // 2a. '다시 묻지 않음'을 선택하여 영구적으로 거부한 경우에만 안내창 표시
                showPermissionSettingsDialog()
            } else {
                // 2b. 이번 한 번만 거부한 경우에는 토스트 메시지만 표시
                showToast("위치 권한이 거부되어 날씨 정보를 가져올 수 없습니다.")
            }
            homeViewModel.locationPermissionGranted.value = false
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
    }

    override fun onResume() {
        super.onResume()
        // 다른 화면에 갔다 돌아왔을 때, 진행 중인 요청이 없다면 다시 확인
        if (!isRequestingPermission) {
            checkLocationPermission()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        permissionDialog?.dismiss()
        permissionDialog = null
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
        homeViewModel.locationPermissionGranted.observe(viewLifecycleOwner) { isGranted ->
            if (isGranted == false && _binding != null) {
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun checkLocationPermission() {
        if (isRequestingPermission) return

        when {
            // 권한이 이미 있는 경우
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                getCurrentLocation()
            }
            // 이전에 거부했지만 '다시 묻지 않음'은 선택하지 않은 경우, 다시 요청하기 전에 왜 필요한지 설명 (선택사항, 여기서는 생략)
            // shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION) -> { ... }

            // 권한이 없고, 요청한 적이 없거나 영구 거부된 경우
            else -> {
                isRequestingPermission = true // 시스템 팝업을 띄우기 직전에만 잠금
                locationPermissionRequest.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }
    }

    private fun showPermissionSettingsDialog() {
        if (permissionDialog?.isShowing == true || !isAdded) return

        permissionDialog = AlertDialog.Builder(requireContext())
            .setTitle("위치 권한이 필요합니다")
            .setMessage("날씨 정보 조회를 위해 위치 권한이 반드시 필요합니다. '예'를 눌러 설정 화면으로 이동한 후, '권한' 메뉴에서 '위치' 권한을 허용해주세요.")
            .setPositiveButton("예") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", requireContext().packageName, null)
                intent.data = uri
                startActivity(intent)
            }
            .setNegativeButton("아니오") { _, _ ->
                showToast("위치 권한이 없어 기능을 실행할 수 없습니다.")
                homeViewModel.locationPermissionGranted.value = false
            }
            .setCancelable(false)
            .setOnDismissListener { permissionDialog = null }
            .show()
    }

    private fun showTurnOnLocationDialog() {
        if (permissionDialog?.isShowing == true || !isAdded) return

        permissionDialog = AlertDialog.Builder(requireContext())
            .setTitle("위치 서비스 비활성화")
            .setMessage("날씨 정보를 가져오려면 기기의 위치 서비스를 켜야 합니다. 설정으로 이동하시겠습니까?")
            .setPositiveButton("설정으로 이동") { _, _ ->
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .setNegativeButton("취소") { _, _ ->
                showToast("위치 서비스가 꺼져있어 날씨를 조회할 수 없습니다.")
                if(_binding != null) binding.swipeRefreshLayout.isRefreshing = false
            }
            .setCancelable(false)
            .setOnDismissListener { permissionDialog = null }
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