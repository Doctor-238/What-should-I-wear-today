package com.yehyun.whatshouldiweartoday

import android.Manifest
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import com.yehyun.whatshouldiweartoday.data.api.WeatherResponse
import com.yehyun.whatshouldiweartoday.data.database.AppDatabase
import com.yehyun.whatshouldiweartoday.data.database.ClothingItem
import com.yehyun.whatshouldiweartoday.data.repository.ClothingRepository
import com.yehyun.whatshouldiweartoday.data.repository.WeatherRepository
import com.yehyun.whatshouldiweartoday.ui.home.DailyWeatherSummary
import com.yehyun.whatshouldiweartoday.ui.home.HomeViewModel
import com.yehyun.whatshouldiweartoday.ui.home.RecommendationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

class WidgetUpdateService : Service() {

    private val job = CoroutineScope(Dispatchers.IO)

    private val pendingIntentFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    } else {
        PendingIntent.FLAG_UPDATE_CURRENT
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val isToday = intent?.getBooleanExtra("IS_TODAY", true) ?: true

        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            job.launch {
                updateWidget(applicationContext, appWidgetId, isToday)
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun updateWidget(context: Context, appWidgetId: Int, isToday: Boolean) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val remoteViews = RemoteViews(context.packageName, R.layout.today_reco_widget)

        // [핵심 수정] 1. 업데이트 시작 시, GONE 대신 INVISIBLE을 사용하여 레이아웃 크기를 유지합니다.
        remoteViews.setTextViewText(R.id.tv_widget_weather_summary, "업데이트 중...")
        remoteViews.setViewVisibility(R.id.ll_widget_clothing_images, View.INVISIBLE) // 공간은 차지하되 보이지 않게
        remoteViews.setViewVisibility(R.id.tv_widget_no_reco, View.GONE)
        updateTabColors(remoteViews, isToday)
        appWidgetManager.updateAppWidget(appWidgetId, remoteViews)


        try {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                remoteViews.setTextViewText(R.id.tv_widget_weather_summary, "앱에서 위치 권한을 허용해주세요.")
                finalizeWidgetUpdate(appWidgetManager, appWidgetId, remoteViews, isToday)
                return
            }

            val location = LocationServices.getFusedLocationProviderClient(context).lastLocation.await()
            if (location == null) {
                remoteViews.setTextViewText(R.id.tv_widget_weather_summary, "위치 정보 없음. 앱을 열어주세요.")
                finalizeWidgetUpdate(appWidgetManager, appWidgetId, remoteViews, isToday)
                return
            }

            val weatherRepo = WeatherRepository(com.yehyun.whatshouldiweartoday.data.api.WeatherApiService.create())
            val clothingRepo = ClothingRepository(AppDatabase.getDatabase(context).clothingDao())
            val apiKey = context.getString(R.string.openweathermap_api_key)
            val weatherResponse = weatherRepo.getFiveDayForecast(location.latitude, location.longitude, apiKey)

            if (weatherResponse.isSuccessful && weatherResponse.body() != null) {
                val allClothes = clothingRepo.getAllItemsList()
                val recommendationPair = processAndRecommend(weatherResponse.body()!!, allClothes, isToday)

                if (recommendationPair != null) {
                    val summary = recommendationPair.first
                    val result = recommendationPair.second
                    val summaryText = "최고:%.0f°(%.0f°) | 최저:%.0f°(%.0f°)".format(summary.maxTemp, summary.maxFeelsLike, summary.minTemp, summary.minFeelsLike)
                    remoteViews.setTextViewText(R.id.tv_widget_weather_summary, summaryText)

                    if (result.bestCombination.isNotEmpty()) {
                        remoteViews.setViewVisibility(R.id.ll_widget_clothing_images, View.VISIBLE)
                        remoteViews.setViewVisibility(R.id.tv_widget_no_reco, View.GONE)
                        updateWidgetImages(remoteViews, result.bestCombination)
                    } else {
                        remoteViews.setViewVisibility(R.id.ll_widget_clothing_images, View.GONE)
                        remoteViews.setViewVisibility(R.id.tv_widget_no_reco, View.VISIBLE)
                        remoteViews.setTextViewText(R.id.tv_widget_no_reco, "추천할 옷이 없습니다.")
                    }
                } else {
                    remoteViews.setTextViewText(R.id.tv_widget_weather_summary, "날씨 정보가 없습니다.")
                }
            } else {
                remoteViews.setTextViewText(R.id.tv_widget_weather_summary, "날씨 정보 업데이트 실패")
            }
        } catch (e: Exception) {
            Log.e("WidgetUpdateService", "Error updating widget", e)
            remoteViews.setTextViewText(R.id.tv_widget_weather_summary, "업데이트 중 오류 발생")
        } finally {
            finalizeWidgetUpdate(appWidgetManager, appWidgetId, remoteViews, isToday)
        }
    }

    private fun finalizeWidgetUpdate(appWidgetManager: AppWidgetManager, appWidgetId: Int, remoteViews: RemoteViews, isToday: Boolean) {
        updateTabColors(remoteViews, isToday)
        setupClickIntents(applicationContext, appWidgetId, remoteViews, isToday)
        appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
        stopSelf() // 작업 완료 후 서비스 종료
    }

    private fun processAndRecommend(
        weatherResponse: WeatherResponse,
        allClothes: List<ClothingItem>,
        isToday: Boolean
    ): Pair<DailyWeatherSummary, RecommendationResult>? {
        val targetDate = if (isToday) LocalDate.now() else LocalDate.now().plusDays(1)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val forecastsByDate = weatherResponse.list.groupBy { LocalDate.parse(it.dt_txt, formatter) }
        val targetForecasts = forecastsByDate[targetDate] ?: return null

        val summary = DailyWeatherSummary(
            date = "",
            maxTemp = targetForecasts.maxOf { it.main.temp_max },
            minTemp = targetForecasts.minOf { it.main.temp_min },
            maxFeelsLike = targetForecasts.maxOf { it.main.feels_like },
            minFeelsLike = targetForecasts.minOf { it.main.feels_like },
            weatherCondition = targetForecasts.firstOrNull()?.weather?.firstOrNull()?.description ?: "",
            precipitationProbability = (targetForecasts.maxOfOrNull { it.pop }?.times(100))?.toInt() ?: 0
        )

        val recommendation = generateRecommendationForWidget(summary, allClothes)
        return Pair(summary, recommendation)
    }

    private fun generateRecommendationForWidget(summary: DailyWeatherSummary, allClothes: List<ClothingItem>): RecommendationResult {
        val maxTempCriteria = (summary.maxTemp + summary.maxFeelsLike) / 2
        val minTempCriteria = (summary.minTemp + summary.minFeelsLike) / 2
        val temperatureTolerance = 3.0
        val significantTempDifference = 12.0

        val recommendedClothes = allClothes.filter {
            val itemMinTemp = it.suitableTemperature - temperatureTolerance
            val itemMaxTemp = it.suitableTemperature + temperatureTolerance
            val isFitForMaxTemp = maxTempCriteria in itemMinTemp..itemMaxTemp
            val isFitForHotDay = maxTempCriteria > 30 && itemMaxTemp >= 30
            val isFitForFreezingDay = minTempCriteria < 0 && itemMinTemp <= 0
            isFitForMaxTemp || isFitForHotDay || isFitForFreezingDay
        }

        val recommendedTops = recommendedClothes.filter { it.category == "상의" }
        val recommendedBottoms = recommendedClothes.filter { it.category == "하의" }
        val recommendedOuters = recommendedClothes.filter { it.category == "아우터" }

        val isTempDifferenceSignificant = (maxTempCriteria - minTempCriteria) >= significantTempDifference
        val packableOuter = if (isTempDifferenceSignificant) {
            allClothes.filter { it.category == "아우터" }
                .filter {
                    val tempRangeForMin = (it.suitableTemperature - temperatureTolerance)..it.suitableTemperature
                    tempRangeForMin.contains(minTempCriteria)
                }
                .minByOrNull { abs(it.suitableTemperature - minTempCriteria) }
        } else {
            null
        }

        val bestTop = recommendedTops.minByOrNull { abs(it.suitableTemperature - maxTempCriteria) }
        val bestBottom = recommendedBottoms.minByOrNull { abs(it.suitableTemperature - maxTempCriteria) }
        val bestOuter = recommendedOuters.minByOrNull { abs(it.suitableTemperature - maxTempCriteria) }
        val bestCombination = listOfNotNull(bestTop, bestBottom, bestOuter)

        val umbrellaRecommendation = "" // 위젯에서는 표시하지 않음

        return RecommendationResult(recommendedTops, recommendedBottoms, recommendedOuters, bestCombination, packableOuter, umbrellaRecommendation, isTempDifferenceSignificant)
    }

    private fun updateWidgetImages(remoteViews: RemoteViews, items: List<ClothingItem>) {
        val imageViews = listOf(R.id.iv_widget_item1, R.id.iv_widget_item2, R.id.iv_widget_item3)
        imageViews.forEach { viewId -> remoteViews.setViewVisibility(viewId, View.GONE) }

        items.forEachIndexed { index, item ->
            if (index < imageViews.size) {
                val imagePath = if (item.useProcessedImage && item.processedImageUri != null) item.processedImageUri else item.imageUri
                try {
                    val file = File(imagePath)
                    if (file.exists()) {
                        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                        remoteViews.setImageViewBitmap(imageViews[index], bitmap)
                        remoteViews.setViewVisibility(imageViews[index], View.VISIBLE)
                    }
                } catch (e: Exception) {
                    Log.e("WidgetUpdateService", "Image loading failed for widget", e)
                }
            }
        }
    }

    private fun updateTabColors(remoteViews: RemoteViews, isToday: Boolean) {
        val primaryColor = Color.parseColor("#0EB4FC")
        val secondaryColor = Color.DKGRAY
        remoteViews.setTextColor(R.id.tv_widget_today, if (isToday) primaryColor else secondaryColor)
        remoteViews.setTextColor(R.id.tv_widget_tomorrow, if (isToday) secondaryColor else primaryColor)
    }

    private fun setupClickIntents(context: Context, appWidgetId: Int, remoteViews: RemoteViews, isToday: Boolean) {
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val mainPendingIntent = PendingIntent.getActivity(context, appWidgetId, mainIntent, pendingIntentFlag)
        remoteViews.setOnClickPendingIntent(R.id.widget_root, mainPendingIntent)

        val todayIntent = Intent(context, TodayRecoWidgetProvider::class.java).apply {
            action = "WIDGET_TAB_CLICK"
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra("IS_TODAY", true)
            data = Uri.withAppendedPath(Uri.parse("mywidget://widget/id/"), "$appWidgetId/today")
        }
        val todayPendingIntent = PendingIntent.getBroadcast(context, appWidgetId * 10 + 1, todayIntent, pendingIntentFlag)
        remoteViews.setOnClickPendingIntent(R.id.tv_widget_today, todayPendingIntent)

        val tomorrowIntent = Intent(context, TodayRecoWidgetProvider::class.java).apply {
            action = "WIDGET_TAB_CLICK"
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra("IS_TODAY", false)
            data = Uri.withAppendedPath(Uri.parse("mywidget://widget/id/"), "$appWidgetId/tomorrow")
        }
        val tomorrowPendingIntent = PendingIntent.getBroadcast(context, appWidgetId * 10 + 2, tomorrowIntent, pendingIntentFlag)
        remoteViews.setOnClickPendingIntent(R.id.tv_widget_tomorrow, tomorrowPendingIntent)

        val refreshIntent = Intent(context, TodayRecoWidgetProvider::class.java).apply {
            action = "WIDGET_REFRESH_CLICK"
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra("IS_TODAY", isToday)
            data = Uri.withAppendedPath(Uri.parse("mywidget://widget/id/"), "$appWidgetId/refresh")
        }
        val refreshPendingIntent = PendingIntent.getBroadcast(context, appWidgetId * 10 + 3, refreshIntent, pendingIntentFlag)
        remoteViews.setOnClickPendingIntent(R.id.iv_widget_refresh, refreshPendingIntent)
    }
}