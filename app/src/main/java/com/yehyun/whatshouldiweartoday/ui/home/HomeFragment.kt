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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
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
            try {
                checkAndRefresh()
            }finally{
                homeViewModel.stopLoading()
            }
        } else {
            if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
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

        setupSwipeRefreshLayout()
        setupCustomTabs()
        setupViewPager()

        if (args.targetTab != 0) { homeViewModel.requestTabSwitch(args.targetTab) }

        observeViewModel()
    }

    private fun setupSwipeRefreshLayout() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            checkAndRefresh()
        }

        binding.swipeRefreshLayout.setOnChildScrollUpCallback(object : SwipeRefreshLayout.OnChildScrollUpCallback {
            override fun canChildScrollUp(parent: SwipeRefreshLayout, child: View?): Boolean {
                val currentPosition = binding.viewPagerHome.currentItem
                val currentFragment = childFragmentManager.findFragmentByTag("f$currentPosition") as? RecommendationFragment

                return currentFragment?.canScrollUp() ?: false
            }
        })
    }

    override fun onResume() {
        super.onResume()
        checkAndRefresh()

    }

    override fun onDestroyView() {
        super.onDestroyView()
        locationDialog?.dismiss()
        locationDialog = null
        _binding = null
    }

    private fun observeViewModel() {
        homeViewModel.error.observe(viewLifecycleOwner) { errorMessage ->
            errorMessage?.let {
                showToast(it)
                homeViewModel.onErrorShown()
            }
        }

        homeViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.swipeRefreshLayout.isRefreshing = isLoading
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

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
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
        if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
            view?.post {
                if (!isAdded) return@post

                AlertDialog.Builder(requireContext())
                    .setTitle("위치 권한 안내")
                    .setMessage("현재 위치의 날씨 정보를 가져오기 위해 위치 권한이 필요합니다.")
                    .setPositiveButton("권한 허용") { _, _ ->
                        homeViewModel.permissionRequestedThisSession = true
                        locationPermissionRequest.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                    .setNegativeButton("거부") { _, _ ->
                        homeViewModel.stopLoading()
                        showToast("위치 권한이 거부되어 날씨 정보를 자동으로 가져올 수 없습니다.")
                    }
                    .setOnDismissListener {
                        if (_binding != null && binding.swipeRefreshLayout.isRefreshing){
                            homeViewModel.stopLoading()
                        }
                    }
                    .show()
            }

        } else {
            if (!homeViewModel.permissionRequestedThisSession) {
                homeViewModel.permissionRequestedThisSession = true
                locationPermissionRequest.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            } else {
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

        if (selectedPosition == 0) {
            binding.tvTabToday.setBackgroundResource(R.drawable.bg_tab_pill_selected)
            binding.tvTabToday.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            binding.tvTabToday.elevation = 4f
            binding.tvTabTomorrow.setBackgroundResource(R.drawable.bg_tab_pill_unselected)
            binding.tvTabTomorrow.setTextColor(ContextCompat.getColor(requireContext(), R.color.tab_unselected_text))
            binding.tvTabTomorrow.elevation = 0f
        } else {
            binding.tvTabTomorrow.setBackgroundResource(R.drawable.bg_tab_pill_selected)
            binding.tvTabTomorrow.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            binding.tvTabTomorrow.elevation = 4f
            binding.tvTabToday.setBackgroundResource(R.drawable.bg_tab_pill_unselected)
            binding.tvTabToday.setTextColor(ContextCompat.getColor(requireContext(), R.color.tab_unselected_text))
            binding.tvTabToday.elevation = 0f
        }
    }

    override fun onTabReselected() {
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.navigation_home) {
            navController.popBackStack(R.id.navigation_home, false)
            return
        }

        if (_binding == null) return
        binding.viewPagerHome.currentItem = 0
        binding.viewPagerHome.post {
            if (isAdded) {
                (childFragmentManager.findFragmentByTag("f0") as? RecommendationFragment)?.scrollToTop()
            }
        }
    }
}