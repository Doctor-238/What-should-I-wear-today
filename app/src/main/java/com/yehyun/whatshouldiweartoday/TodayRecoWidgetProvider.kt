package com.yehyun.whatshouldiweartoday

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews
import androidx.navigation.NavDeepLinkBuilder
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

class TodayRecoWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            schedulePeriodicWork(context, appWidgetId)
            startOneTimeWork(context, appWidgetId, true)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.MY_PACKAGE_REPLACED") {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisAppWidget = ComponentName(context.packageName, javaClass.name)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget)
            for (appWidgetId in appWidgetIds) {
                startOneTimeWork(context, appWidgetId, true)
            }
            return
        }
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            if (intent.action in listOf("WIDGET_TAB_CLICK", "WIDGET_REFRESH_CLICK")) {
                val isToday = intent.getBooleanExtra("IS_TODAY", true)
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
            ExistingPeriodicWorkPolicy.REPLACE,
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
        WorkManager.getInstance(context).enqueueUniqueWork("one_time_widget_update_$appWidgetId", ExistingWorkPolicy.REPLACE, workRequest)
    }

    companion object {
        fun setupClickIntents(context: Context, appWidgetId: Int, remoteViews: RemoteViews, isToday: Boolean) {
            val pendingIntentFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val primaryColor = Color.parseColor("#0EB4FC"); val secondaryColor = Color.DKGRAY
            remoteViews.setTextColor(R.id.tv_widget_today, if (isToday) primaryColor else secondaryColor)
            remoteViews.setTextColor(R.id.tv_widget_tomorrow, if (isToday) secondaryColor else primaryColor)

            val args = Bundle().apply { putInt("target_tab", if (isToday) 0 else 1) }
            val mainPendingIntent = NavDeepLinkBuilder(context)
                .setComponentName(MainActivity::class.java).setGraph(R.navigation.mobile_navigation)
                .setDestination(R.id.navigation_home).setArguments(args).createPendingIntent()
            remoteViews.setOnClickPendingIntent(R.id.widget_root, mainPendingIntent)

            val todayIntent = Intent(context, TodayRecoWidgetProvider::class.java).apply {
                action = "WIDGET_TAB_CLICK"; putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId); putExtra("IS_TODAY", true)
                data = Uri.withAppendedPath(Uri.parse("mywidget://widget/id/"), "$appWidgetId/today")
            }
            val todayPendingIntent = PendingIntent.getBroadcast(context, appWidgetId * 10 + 1, todayIntent, pendingIntentFlag)
            remoteViews.setOnClickPendingIntent(R.id.tv_widget_today, todayPendingIntent)

            val tomorrowIntent = Intent(context, TodayRecoWidgetProvider::class.java).apply {
                action = "WIDGET_TAB_CLICK"; putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId); putExtra("IS_TODAY", false)
                data = Uri.withAppendedPath(Uri.parse("mywidget://widget/id/"), "$appWidgetId/tomorrow")
            }
            val tomorrowPendingIntent = PendingIntent.getBroadcast(context, appWidgetId * 10 + 2, tomorrowIntent, pendingIntentFlag)
            remoteViews.setOnClickPendingIntent(R.id.tv_widget_tomorrow, tomorrowPendingIntent)

            val refreshIntent = Intent(context, TodayRecoWidgetProvider::class.java).apply {
                action = "WIDGET_REFRESH_CLICK"; putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId); putExtra("IS_TODAY", isToday)
                data = Uri.withAppendedPath(Uri.parse("mywidget://widget/id/"), "$appWidgetId/refresh")
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(context, appWidgetId * 10 + 3, refreshIntent, pendingIntentFlag)
            remoteViews.setOnClickPendingIntent(R.id.iv_widget_refresh, refreshPendingIntent)
        }
    }
}