package com.yehyun.whatshouldiweartoday

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.ActivityCompat
import androidx.navigation.NavDeepLinkBuilder
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.location.LocationServices
import com.yehyun.whatshouldiweartoday.data.api.WeatherApiService
import com.yehyun.whatshouldiweartoday.data.api.WeatherResponse
import com.yehyun.whatshouldiweartoday.data.database.AppDatabase
import com.yehyun.whatshouldiweartoday.data.database.ClothingItem
import com.yehyun.whatshouldiweartoday.data.repository.ClothingRepository
import com.yehyun.whatshouldiweartoday.data.repository.WeatherRepository
import com.yehyun.whatshouldiweartoday.ui.home.DailyWeatherSummary
import com.yehyun.whatshouldiweartoday.ui.home.RecommendationResult
import kotlinx.coroutines.tasks.await
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import com.yehyun.whatshouldiweartoday.data.preference.SettingsManager

class WidgetUpdateWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val appWidgetId = inputData.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val isToday = inputData.getBoolean("IS_TODAY", true)

        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            updateWidget(applicationContext, appWidgetId, isToday)
        }

        return Result.success()
    }

    private suspend fun updateWidget(context: Context, appWidgetId: Int, isToday: Boolean) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val remoteViews = RemoteViews(context.packageName, R.layout.today_reco_widget)

        // 이 부분은 Provider에서 미리 처리하므로, 여기서는 데이터 로딩에 집중합니다.
        // remoteViews.setTextViewText(R.id.tv_widget_weather_summary, "업데이트 중...")
        // ...

        try {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                remoteViews.setTextViewText(R.id.tv_widget_weather_summary, "앱에서 위치 권한을 허용해주세요.")
                finalizeWidgetUpdate(context, appWidgetManager, appWidgetId, remoteViews, isToday)
                return
            }

            val location = LocationServices.getFusedLocationProviderClient(context).lastLocation.await()
            if (location == null) {
                remoteViews.setTextViewText(R.id.tv_widget_weather_summary, "위치 정보 없음. 앱을 열어주세요.")
                finalizeWidgetUpdate(context, appWidgetManager, appWidgetId, remoteViews, isToday)
                return
            }

            val weatherRepo = WeatherRepository(WeatherApiService.create())
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
            Log.e("WidgetUpdateWorker", "Error updating widget", e)
            remoteViews.setTextViewText(R.id.tv_widget_weather_summary, "업데이트 중 오류 발생")
        } finally {
            finalizeWidgetUpdate(context, appWidgetManager, appWidgetId, remoteViews, isToday)
        }
    }

    private fun finalizeWidgetUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, remoteViews: RemoteViews, isToday: Boolean) {
        setupClickIntents(context, appWidgetId, remoteViews, isToday)
        appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
    }

    private fun setupClickIntents(context: Context, appWidgetId: Int, remoteViews: RemoteViews, isToday: Boolean) {
        val pendingIntentFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        // 탭 색상은 Provider에서 미리 처리하지만, 최종 업데이트 시 한 번 더 확인
        val primaryColor = Color.parseColor("#0EB4FC")
        val secondaryColor = Color.DKGRAY
        remoteViews.setTextColor(R.id.tv_widget_today, if (isToday) primaryColor else secondaryColor)
        remoteViews.setTextColor(R.id.tv_widget_tomorrow, if (isToday) secondaryColor else primaryColor)

        // [수정] NavDeepLinkBuilder를 사용하여 탭 정보를 정확하게 전달
        val args = Bundle()
        args.putInt("target_tab", if (isToday) 0 else 1)
        val mainPendingIntent = NavDeepLinkBuilder(context)
            .setComponentName(MainActivity::class.java)
            .setGraph(R.navigation.mobile_navigation)
            .setDestination(R.id.navigation_home)
            .setArguments(args)
            .createPendingIntent()
        remoteViews.setOnClickPendingIntent(R.id.widget_root, mainPendingIntent)

        // '오늘' 탭 클릭 Intent
        val todayIntent = Intent(context, TodayRecoWidgetProvider::class.java).apply {
            action = "WIDGET_TAB_CLICK"
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra("IS_TODAY", true)
            data = Uri.withAppendedPath(Uri.parse("mywidget://widget/id/"), "$appWidgetId/today")
        }
        val todayPendingIntent = PendingIntent.getBroadcast(context, appWidgetId * 10 + 1, todayIntent, pendingIntentFlag)
        remoteViews.setOnClickPendingIntent(R.id.tv_widget_today, todayPendingIntent)

        // '내일' 탭 클릭 Intent
        val tomorrowIntent = Intent(context, TodayRecoWidgetProvider::class.java).apply {
            action = "WIDGET_TAB_CLICK"
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra("IS_TODAY", false)
            data = Uri.withAppendedPath(Uri.parse("mywidget://widget/id/"), "$appWidgetId/tomorrow")
        }
        val tomorrowPendingIntent = PendingIntent.getBroadcast(context, appWidgetId * 10 + 2, tomorrowIntent, pendingIntentFlag)
        remoteViews.setOnClickPendingIntent(R.id.tv_widget_tomorrow, tomorrowPendingIntent)

        // '새로고침' 클릭 Intent
        val refreshIntent = Intent(context, TodayRecoWidgetProvider::class.java).apply {
            action = "WIDGET_REFRESH_CLICK"
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra("IS_TODAY", isToday)
            data = Uri.withAppendedPath(Uri.parse("mywidget://widget/id/"), "$appWidgetId/refresh")
        }
        val refreshPendingIntent = PendingIntent.getBroadcast(context, appWidgetId * 10 + 3, refreshIntent, pendingIntentFlag)
        remoteViews.setOnClickPendingIntent(R.id.iv_widget_refresh, refreshPendingIntent)
    }

    private fun processAndRecommend(weatherResponse: WeatherResponse, allClothes: List<ClothingItem>, isToday: Boolean): Pair<DailyWeatherSummary, RecommendationResult>? {
        val targetDate = if (isToday) LocalDate.now() else LocalDate.now().plusDays(1)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val forecastsByDate = weatherResponse.list.groupBy { LocalDate.parse(it.dt_txt, formatter) }
        val targetForecasts = forecastsByDate[targetDate] ?: return null
        val summary = DailyWeatherSummary(date = "", maxTemp = targetForecasts.maxOf { it.main.temp_max }, minTemp = targetForecasts.minOf { it.main.temp_min }, maxFeelsLike = targetForecasts.maxOf { it.main.feels_like }, minFeelsLike = targetForecasts.minOf { it.main.feels_like }, weatherCondition = targetForecasts.firstOrNull()?.weather?.firstOrNull()?.description ?: "", precipitationProbability = (targetForecasts.maxOfOrNull { it.pop }?.times(100))?.toInt() ?: 0)
        val recommendation = generateRecommendationForWidget(applicationContext, summary, allClothes)
        return Pair(summary, recommendation)
    }

    private fun generateRecommendationForWidget(context: Context, summary: DailyWeatherSummary, allClothes: List<ClothingItem>): RecommendationResult {
        // 설정 값을 가져오기 위해 SettingsManager를 생성합니다.
        val settingsManager = SettingsManager(context)

        val maxTempCriteria = (summary.maxTemp + summary.maxFeelsLike) / 2
        val minTempCriteria = (summary.minTemp + summary.minFeelsLike) / 2

        // 설정 값을 불러옵니다.
        val temperatureTolerance = settingsManager.getTemperatureTolerance()
        val packableOuterTolerance = settingsManager.getPackableOuterTolerance()
        val constitutionAdjustment = settingsManager.getConstitutionAdjustment()

        val significantTempDifference = 12.0

        val recommendedClothes = allClothes.filter {
            // 체질 설정을 반영합니다.
            val adjustedTemp = it.suitableTemperature + constitutionAdjustment
            val itemMinTemp = adjustedTemp - temperatureTolerance
            val itemMaxTemp = adjustedTemp + temperatureTolerance

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
            allClothes.filter { it.category == "아우터" }.filter {
                // 챙겨갈 아우터 로직에도 설정 값을 반영합니다.
                val tempRangeForMin = (it.suitableTemperature + packableOuterTolerance)..it.suitableTemperature
                tempRangeForMin.contains(minTempCriteria)
            }.minByOrNull { abs(it.suitableTemperature - minTempCriteria) }
        } else {
            null
        }

        val bestTop = recommendedTops.minByOrNull { abs(it.suitableTemperature - maxTempCriteria) }
        val bestBottom = recommendedBottoms.minByOrNull { abs(it.suitableTemperature - maxTempCriteria) }
        val bestOuter = recommendedOuters.minByOrNull { abs(it.suitableTemperature - maxTempCriteria) }
        val bestCombination = listOfNotNull(bestTop, bestBottom, bestOuter)

        val umbrellaRecommendation = ""
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
                    Log.e("WidgetUpdateWorker", "Image loading failed for widget", e)
                }
            }
        }
    }
}