package com.yehyun.whatshouldiweartoday

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.yehyun.whatshouldiweartoday.util.CrashLogger

class WhatShouldIWearTodayApplication : Application() {

    companion object {
        const val PROGRESS_CHANNEL_ID = "batch_add_progress_channel"
        const val COMPLETE_CHANNEL_ID = "batch_add_complete_channel"
    }

    override fun onCreate() {
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler(CrashLogger(this))
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager: NotificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            // 진행 중 알림 채널
            val progressChannel = NotificationChannel(
                PROGRESS_CHANNEL_ID,
                "옷 일괄 추가 진행률",
                NotificationManager.IMPORTANCE_LOW // 소리 없는 알림
            ).apply {
                description = "여러 개의 옷을 추가할 때 진행률을 표시합니다."
            }
            notificationManager.createNotificationChannel(progressChannel)

            // 완료 알림 채널
            val completeChannel = NotificationChannel(
                COMPLETE_CHANNEL_ID,
                "옷 일괄 추가 완료",
                NotificationManager.IMPORTANCE_DEFAULT // 소리가 있는 일반 알림
            ).apply {
                description = "옷 추가 작업의 완료/실패 여부를 알립니다."
            }
            notificationManager.createNotificationChannel(completeChannel)
        }
    }
}