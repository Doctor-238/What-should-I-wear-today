package com.yehyun.whatshouldiweartoday

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.yehyun.whatshouldiweartoday.ui.OnTabReselectedListener
import androidx.navigation.ui.NavigationUI

class MainActivity : AppCompatActivity() {

    private val mainViewModel: MainViewModel by viewModels()
    private lateinit var navController: NavController
    private lateinit var navView: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        setContentView(R.layout.activity_main)


        navView = findViewById(R.id.nav_view)
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        navController = navHostFragment.navController

        // ▼▼▼▼▼ 핵심 수정 부분 ▼▼▼▼▼
        // 기존의 간단한 setupWithNavController 대신, 각 탭의 상태와 백스택을
        // 독립적으로 저장하고 복원하도록 커스텀 리스너를 설정합니다.
        // 이 방식이 여러 탭의 내비게이션 상태를 관리하는 현대적인 표준 방식입니다.
        navView.setOnItemSelectedListener { item ->
            // NavOptions를 사용하여 내비게이션 동작을 커스터마이징합니다.
            val builder = NavOptions.Builder().setLaunchSingleTop(true).setRestoreState(true)

            // 현재 탭의 백스택을 '저장'하고, 다른 탭의 백스택을 '복원'하는 핵심 로직
            val destination = navController.graph.findNode(item.itemId)
            val currentDestination = navController.currentDestination
            if (destination != null && currentDestination != null) {
                val popUpToId = navController.graph.startDestinationId
                builder.setPopUpTo(popUpToId, false, true)
            }

            val options = builder.build()
            try {
                // 설정된 옵션으로 선택된 탭의 목적지로 이동합니다.
                NavigationUI.onNavDestinationSelected(item, navController)
                navController.navigate(item.itemId, null, options)
                true
            } catch (e: IllegalArgumentException) {
                // 사용자가 매우 빠르게 탭을 연속으로 누를 때 발생할 수 있는 오류 방지
                false
            }
        }

        // 뒤로가기 버튼 등으로 화면이 전환될 때, 하단 네비게이션 뷰의 선택된 아이콘이
        // 현재 화면과 일치하도록 동기화해주는 리스너입니다.
        navController.addOnDestinationChangedListener { _, destination, _ ->
            navView.menu.findItem(destination.id)?.isChecked = true
        }
        // ▲▲▲▲▲ 핵심 수정 부분 ▲▲▲▲▲

        // 같은 탭을 다시 눌렀을 때의 동작은 그대로 유지합니다.
        navView.setOnItemReselectedListener {
            val currentFragment = navHostFragment.childFragmentManager.fragments.firstOrNull()
            if (currentFragment is OnTabReselectedListener) {
                currentFragment.onTabReselected()
            }
        }

        mainViewModel.resetAllEvent.observe(this) { shouldReset ->
            if (shouldReset) {
                val navOptions = NavOptions.Builder()
                    .setPopUpTo(navController.graph.startDestinationId, true)
                    .build()
                navController.navigate(R.id.navigation_home, null, navOptions)
                mainViewModel.onResetAllEventHandled()
            }
        }

        updateAllWidgets()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.hasExtra("destination")) {
            val destinationId = intent.getIntExtra("destination", R.id.navigation_closet)
            // Postpone a little for the nav controller to be ready
            navView.post {
                navController.navigate(destinationId)
            }
        }
    }

    private fun updateAllWidgets() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, TodayRecoWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        if (appWidgetIds.isNotEmpty()) {
            val intent = Intent(this, TodayRecoWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
            }
            sendBroadcast(intent)
        }
    }
        private fun setupBottomNav() {
            navView.setOnItemSelectedListener { item ->
                val builder = NavOptions.Builder().setLaunchSingleTop(true).setRestoreState(true)

                val destination = navController.graph.findNode(item.itemId)
                if (destination != null) {
                    // 백스택의 가장 처음(루트) 목적지를 popUpTo 대상으로 설정
                    val popUpToId = navController.graph.startDestinationId
                    builder.setPopUpTo(popUpToId, false, true)
                }

                val options = builder.build()
                try {
                    navController.navigate(item.itemId, null, options)
                    true
                } catch (e: IllegalArgumentException) {
                    false
                }
            }

            navController.addOnDestinationChangedListener { _, destination, _ ->
                navView.menu.findItem(destination.id)?.isChecked = true
            }

            navView.setOnItemReselectedListener {
                val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
                val currentFragment = navHostFragment.childFragmentManager.fragments.firstOrNull()
                if (currentFragment is OnTabReselectedListener) {
                    currentFragment.onTabReselected()
                }
            }
        }
    }
