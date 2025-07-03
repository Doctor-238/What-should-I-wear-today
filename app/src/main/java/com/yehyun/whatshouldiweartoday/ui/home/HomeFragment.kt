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

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        homeViewModel.permissionRequestedThisSession = false
        if (isGranted) {
            checkAndRefresh()
        } else {
            homeViewModel.stopLoading()
            if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                showToast("위치 권한이 거부되어 날씨 정보를 가져올 수 없습니다.")
            } else {
                showGoToSettingsDialog("이 앱은 위치 권한이 반드시 필요합니다. 앱을 사용하려면 '설정'으로 이동하여 위치 권한을 허용해주세요.")
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

        binding.swipeRefreshLayout.setOnRefreshListener {
            checkAndRefresh()
        }

        if (args.targetTab != 0) { homeViewModel.requestTabSwitch(args.targetTab) }

        observeViewModel()

        if (savedInstanceState == null) {
            checkAndRefresh()
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        toast?.cancel()
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
        if (homeViewModel.permissionRequestedThisSession) {
            homeViewModel.stopLoading()
            return
        }

        if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)) {
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
                    if (binding.swipeRefreshLayout.isRefreshing){
                        homeViewModel.stopLoading()
                    }
                }
                .show()
        } else {
            homeViewModel.permissionRequestedThisSession = true
            locationPermissionRequest.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
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
        AlertDialog.Builder(requireContext())
            .setTitle("위치 서비스 비활성화")
            .setMessage("날씨 정보를 가져오려면 기기의 위치 서비스를 켜야 합니다. 설정으로 이동하시겠습니까?")
            .setPositiveButton("설정으로 이동") { _, _ ->
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .setNegativeButton("취소") { _, _ ->
                showToast("위치 서비스가 꺼져있어 날씨를 조회할 수 없습니다.")
            }
            .setOnDismissListener {
                if(_binding != null && binding.swipeRefreshLayout.isRefreshing) {
                    homeViewModel.stopLoading()
                }
            }
            .show()
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