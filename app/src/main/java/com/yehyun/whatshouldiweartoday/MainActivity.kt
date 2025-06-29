package com.yehyun.whatshouldiweartoday

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.yehyun.whatshouldiweartoday.ui.OnTabReselectedListener

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val navView: BottomNavigationView = findViewById(R.id.nav_view)
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        val navController = navHostFragment.navController

        // [핵심 수정] 탭 선택과 재선택 로직을 분리하여 안정성 확보
        setupBottomNav(navView, navController)
    }

    private fun setupBottomNav(navView: BottomNavigationView, navController: NavController) {
        // 1. 탭 '선택'(전환) 리스너: 다른 탭으로 이동하는 것을 처리합니다.
        navView.setOnItemSelectedListener { item ->
            NavigationUI.onNavDestinationSelected(item, navController)
            true
        }

        // 2. 탭 '재선택'(더블클릭) 리스너: 이미 선택된 탭을 다시 누르는 것을 처리합니다.
        navView.setOnItemReselectedListener {
            // 현재 화면의 프래그먼트를 찾아서 이벤트를 전달합니다.
            val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
            val currentFragment = navHostFragment.childFragmentManager.fragments.firstOrNull()
            if (currentFragment is OnTabReselectedListener) {
                currentFragment.onTabReselected()
            }
        }

        // 3. 네비게이션 상태가 바뀔 때마다 하단 탭의 아이콘이 올바르게 선택되도록 동기화합니다.
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val menu = navView.menu
            for (i in 0 until menu.size()) {
                val menuItem = menu.getItem(i)
                if (menuItem.itemId == destination.id) {
                    menuItem.isChecked = true
                    break
                }
            }
        }
    }
}