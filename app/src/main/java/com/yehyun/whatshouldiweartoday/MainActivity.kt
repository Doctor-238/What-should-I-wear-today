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
        handleIntent(intent)
    }

    // ▼▼▼▼▼ 핵심 수정 부분 ▼▼▼▼▼
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        // intent가 null이 아닌지 확인하고, "destination" 정보가 있을 때만 처리합니다.
        intent?.let {
            if (it.hasExtra("destination")) {
                val destinationId = it.getIntExtra("destination", R.id.navigation_closet)
                navView.post {
                    navController.navigate(destinationId)
                }
            }
        }
    }
    // ▲▲▲▲▲ 핵심 수정 부분 ▲▲▲▲▲

    private fun setupBottomNav() {
        navView.setOnItemSelectedListener { item ->
            val builder = NavOptions.Builder().setLaunchSingleTop(true).setRestoreState(true)
            val popUpToId = navController.graph.startDestinationId
            builder.setPopUpTo(popUpToId, false, true)
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
}