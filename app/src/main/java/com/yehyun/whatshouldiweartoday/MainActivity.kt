// app/src/main/java/com/yehyun/whatshouldiweartoday/MainActivity.kt

package com.yehyun.whatshouldiweartoday

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.yehyun.whatshouldiweartoday.MainViewModel
import com.yehyun.whatshouldiweartoday.ui.OnTabReselectedListener

class MainActivity : AppCompatActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val navView: BottomNavigationView = findViewById(R.id.nav_view)
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        val navController = navHostFragment.navController

        setupBottomNav(navView, navController)

        // [수정] MainViewModel의 이벤트를 관찰하여 내비게이션을 리셋합니다.
        mainViewModel.resetAllEvent.observe(this) { shouldReset ->
            if (shouldReset) {
                // 현재까지 쌓인 모든 화면 이동 기록을 삭제하고,
                // 홈 화면(startDestination)으로 이동합니다.
                val navOptions = NavOptions.Builder()
                    .setPopUpTo(navController.graph.startDestinationId, true)
                    .build()
                navController.navigate(R.id.navigation_home, null, navOptions)

                // 이벤트 처리가 완료되었음을 알립니다.
                mainViewModel.onResetAllEventHandled()
            }
        }
    }

    private fun setupBottomNav(navView: BottomNavigationView, navController: NavController) {
        navView.setOnItemSelectedListener { item ->
            NavigationUI.onNavDestinationSelected(item, navController)
            true
        }

        navView.setOnItemReselectedListener {
            val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
            val currentFragment = navHostFragment.childFragmentManager.fragments.firstOrNull()
            if (currentFragment is OnTabReselectedListener) {
                currentFragment.onTabReselected()
            }
        }

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