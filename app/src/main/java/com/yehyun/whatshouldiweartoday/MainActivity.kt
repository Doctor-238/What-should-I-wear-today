package com.yehyun.whatshouldiweartoday

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.yehyun.whatshouldiweartoday.ui.OnTabReselectedListener

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val navView: BottomNavigationView = findViewById(R.id.nav_view)
        val navController = findNavController(R.id.nav_host_fragment_activity_main)

        navView.setupWithNavController(navController)

        // [핵심 추가] 탭 재선택 리스너 설정
        navView.setOnItemReselectedListener { item ->
            val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
            val currentFragment = navHostFragment.childFragmentManager.fragments.firstOrNull()

            // 현재 화면(Fragment)이 우리가 만든 규칙(OnTabReselectedListener)을 따르는지 확인
            if (currentFragment is OnTabReselectedListener) {
                // 규칙을 따른다면, 해당 Fragment의 onTabReselected() 함수를 호출
                currentFragment.onTabReselected()
            } else {
                // 그렇지 않다면, 해당 탭의 최상위 화면으로 스택을 모두 비우고 이동
                // (예: 수정 화면 -> 목록 화면)
                val startDestinationId = navController.graph.findNode(item.itemId)!!.id
                navController.popBackStack(startDestinationId, false)
            }
        }
    }
}