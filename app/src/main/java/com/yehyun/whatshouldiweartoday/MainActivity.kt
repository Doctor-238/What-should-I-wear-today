// 파일 경로: app/src/main/java/com/yehyun/whatshouldiweartoday/MainActivity.kt

package com.yehyun.whatshouldiweartoday

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.yehyun.whatshouldiweartoday.ui.OnTabReselectedListener

class MainActivity : AppCompatActivity() {

    private val mainViewModel: MainViewModel by viewModels()
    private lateinit var navController: NavController
    private lateinit var navView: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        // ▼▼▼▼▼ 핵심 수정 부분 ▼▼▼▼▼
        // 스플래시 화면 API를 호출합니다. 반드시 super.onCreate() 및 setContentView() 보다 먼저 와야 합니다.
        installSplashScreen()
        // ▲▲▲▲▲ 핵심 수정 부분 ▲▲▲▲▲

        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        setContentView(R.layout.activity_main)


        navView = findViewById(R.id.nav_view)
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        navController = navHostFragment.navController

        setupBottomNav()

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
        // [수정] 앱 실행 시 특정 화면으로 이동시키는 로직을 제거합니다.
        // handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // [수정] 앱 실행 시 특정 화면으로 이동시키는 로직을 제거합니다.
        // handleIntent(intent)
    }

    /*
    [수정] 특정 탭으로 이동하는 기능 자체를 제거했으므로, 이 함수는 더 이상 필요하지 않습니다.
    private fun handleIntent(intent: Intent) {
        if (intent.hasExtra("destination")) {
            val destinationId = intent.getIntExtra("destination", R.id.navigation_closet)
            navView.post {
                navController.navigate(destinationId)
            }
        }
    }
    */

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
            val builder = NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setRestoreState(true)
                .setPopUpTo(navController.graph.startDestinationId, false, true)

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