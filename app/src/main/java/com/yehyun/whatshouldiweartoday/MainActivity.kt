package com.yehyun.whatshouldiweartoday

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // 우리가 수정한 activity_main.xml을 화면으로 설정

        // 레이아웃에 있는 하단 네비게이션 뷰를 찾습니다.
        val navView: BottomNavigationView = findViewById(R.id.nav_view)

        // Fragment들의 길잡이(NavController)를 찾습니다.
        val navController = findNavController(R.id.nav_host_fragment_activity_main)

        // 하단 네비게이션 뷰와 길잡이를 연결합니다.
        // 이제 탭을 누르면 자동으로 해당 Fragment로 이동합니다.
        navView.setupWithNavController(navController)
    }
}