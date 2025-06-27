package com.yehyun.whatshouldiweartoday.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.viewpager2.widget.ViewPager2
import com.google.android.gms.location.LocationServices
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.yehyun.whatshouldiweartoday.R

class HomeFragment : Fragment(R.layout.fragment_home) {

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViewPager(view)
        checkLocationPermission()

        homeViewModel.error.observe(viewLifecycleOwner) { errorMessage ->
            Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
        }
    }

    private fun setupViewPager(view: View) {
        val viewPager = view.findViewById<ViewPager2>(R.id.view_pager_home)
        val tabLayout = view.findViewById<TabLayout>(R.id.tab_layout_home)

        viewPager.adapter = HomeViewPagerAdapter(this)

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = if (position == 0) "오늘" else "내일"
        }.attach()
    }

    private fun checkLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                getCurrentLocation()
            }
            else -> {
                locationPermissionRequest.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }
    }

    private fun getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    val apiKey = getString(R.string.openweathermap_api_key)
                    // API Key가 입력되었는지 확인
                    if (apiKey.isBlank() || apiKey == "YOUR_OPENWEATHERMAP_API_KEY") {
                        Toast.makeText(requireContext(), "secrets.xml 파일에 날씨 API 키를 입력해주세요.", Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }
                    homeViewModel.fetchWeatherData(location.latitude, location.longitude, apiKey)
                } else {
                    Toast.makeText(requireContext(), "위치 정보를 가져올 수 없습니다. 위치 설정을 확인해주세요.", Toast.LENGTH_LONG).show()
                }
            }
    }
}
