package com.yehyun.whatshouldiweartoday

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.yehyun.whatshouldiweartoday.util.CrashLogger

class WhatShouldIWearTodayApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // 앱의 기본 예외 처리 핸들러를 우리가 만든 CrashLogger로 설정합니다.
        Thread.setDefaultUncaughtExceptionHandler(CrashLogger(this))

        // 앱이 시작될 때 알림 채널을 미리 생성합니다.
        // 이렇게 하면 Worker가 채널을 사용하기 전에 항상 채널이 준비되어 있도록 보장하여
        // 알림이 누락되는 타이밍 이슈를 해결할 수 있습니다.
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        // Android 8.0 (Oreo) 이상에서만 NotificationChannel이 필요합니다.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "batch_add_channel"
            val name = "옷 일괄 추가"
            val descriptionText = "여러 개의 옷을 백그라운드에서 추가할 때 사용됩니다."
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }

            // 시스템에 채널을 등록합니다. 앱 실행 시 한 번만 호출하면 됩니다.
            val notificationManager: NotificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}