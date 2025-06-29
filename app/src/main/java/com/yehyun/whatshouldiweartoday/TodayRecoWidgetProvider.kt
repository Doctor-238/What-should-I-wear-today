package com.yehyun.whatshouldiweartoday

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri

class TodayRecoWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            startUpdateService(context, appWidgetId, true)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)

        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            when (intent.action) {
                "WIDGET_TAB_CLICK", "WIDGET_REFRESH_CLICK" -> {
                    val isToday = intent.getBooleanExtra("IS_TODAY", true)
                    startUpdateService(context, appWidgetId, isToday)
                }
            }
        }
    }

    private fun startUpdateService(context: Context, appWidgetId: Int, isToday: Boolean) {
        val serviceIntent = Intent(context, WidgetUpdateService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra("IS_TODAY", isToday)
            data = Uri.withAppendedPath(Uri.parse("mywidget://widget/id/"), "$appWidgetId/${System.currentTimeMillis()}")
        }
        context.startService(serviceIntent)
    }
}