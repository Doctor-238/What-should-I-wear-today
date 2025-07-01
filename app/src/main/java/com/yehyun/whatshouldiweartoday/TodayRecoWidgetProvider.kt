package com.yehyun.whatshouldiweartoday

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import androidx.work.*
import java.util.concurrent.TimeUnit

class TodayRecoWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            schedulePeriodicWork(context, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)

        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            if (intent.action in listOf("WIDGET_TAB_CLICK", "WIDGET_REFRESH_CLICK")) {
                val isToday = intent.getBooleanExtra("IS_TODAY", true)

                // [추가] 즉각적인 UI 피드백을 위한 코드
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val remoteViews = RemoteViews(context.packageName, R.layout.today_reco_widget)

                // 1. 탭 색상을 즉시 변경
                val primaryColor = Color.parseColor("#0EB4FC")
                val secondaryColor = Color.DKGRAY
                remoteViews.setTextColor(R.id.tv_widget_today, if (isToday) primaryColor else secondaryColor)
                remoteViews.setTextColor(R.id.tv_widget_tomorrow, if (isToday) secondaryColor else primaryColor)

                // 2. "업데이트 중..." 문구를 바로 표시
                remoteViews.setTextViewText(R.id.tv_widget_weather_summary, "업데이트 중...")
                remoteViews.setViewVisibility(R.id.ll_widget_clothing_images, View.INVISIBLE) // 기존 옷 이미지를 숨김
                remoteViews.setViewVisibility(R.id.tv_widget_no_reco, View.GONE)

                // 3. 변경된 UI를 위젯에 즉시 적용
                appWidgetManager.updateAppWidget(appWidgetId, remoteViews)

                // 4. UI 변경 후, 백그라운드에서 데이터 업데이트 작업 시작
                startOneTimeWork(context, appWidgetId, isToday)
            }
        }
    }

    private fun schedulePeriodicWork(context: Context, appWidgetId: Int) {
        val workRequest = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(30, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf(AppWidgetManager.EXTRA_APPWIDGET_ID to appWidgetId))
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "widget_update_$appWidgetId",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    private fun startOneTimeWork(context: Context, appWidgetId: Int, isToday: Boolean) {
        val workRequest = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
            .setInputData(workDataOf(
                AppWidgetManager.EXTRA_APPWIDGET_ID to appWidgetId,
                "IS_TODAY" to isToday
            ))
            .build()
        WorkManager.getInstance(context).enqueue(workRequest)
    }
}