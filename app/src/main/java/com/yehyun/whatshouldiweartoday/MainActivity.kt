package com.yehyun.whatshouldiweartoday

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.yehyun.whatshouldiweartoday.ui.OnTabReselectedListener

class MainActivity : AppCompatActivity() {

    private lateinit var navController: NavController
    private lateinit var navView: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        navView = findViewById(R.id.nav_view)
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        navController = navHostFragment.navController

        // [핵심 수정] 탭 선택/재선택 리스너를 분리하여 설정
        setupNav()
    }

    private fun setupNav() {
        navView.setupWithNavController(navController)

        // 탭이 '선택'되었을 때의 리스너
        navView.setOnItemSelectedListener { item ->
            // 기본 네비게이션 동작을 수행
            NavigationUI.onNavDestinationSelected(item, navController)
            return@setOnItemSelectedListener true
        }

        // 탭이 '재선택'되었을 때의 리스너
        navView.setOnItemReselectedListener { item ->
            val currentFragment = (supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment)
                .childFragmentManager.fragments.firstOrNull()

            if (currentFragment is OnTabReselectedListener) {
                // 현재 프래그먼트가 onTabReselected를 처리하도록 함
                (currentFragment as OnTabReselectedListener).onTabReselected()
            } else {
                // 처리 로직이 없는 프래그먼트의 경우, 해당 탭의 시작점으로 돌아감
                navController.popBackStack(item.itemId, inclusive = false)
            }
        }
    }
}