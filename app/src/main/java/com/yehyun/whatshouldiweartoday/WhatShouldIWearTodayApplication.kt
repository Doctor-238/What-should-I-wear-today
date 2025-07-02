package com.yehyun.whatshouldiweartoday

import android.app.Application
import com.yehyun.whatshouldiweartoday.util.CrashLogger

class WhatShouldIWearTodayApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // 앱의 기본 예외 처리 핸들러를 우리가 만든 CrashLogger로 설정합니다.
        Thread.setDefaultUncaughtExceptionHandler(CrashLogger(this))
    }
}