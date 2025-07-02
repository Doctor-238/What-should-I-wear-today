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
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.ActivityCompat
import androidx.navigation.NavDeepLinkBuilder
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.yehyun.whatshouldiweartoday.data.api.WeatherApiService
import com.yehyun.whatshouldiweartoday.data.api.WeatherResponse
import com.yehyun.whatshouldiweartoday.data.database.AppDatabase
import com.yehyun.whatshouldiweartoday.data.database.ClothingItem
import com.yehyun.whatshouldiweartoday.data.preference.SettingsManager
import com.yehyun.whatshouldiweartoday.data.repository.ClothingRepository
import com.yehyun.whatshouldiweartoday.data.repository.WeatherRepository
import com.yehyun.whatshouldiweartoday.ui.home.DailyWeatherSummary
import com.yehyun.whatshouldiweartoday.ui.home.RecommendationResult
import kotlinx.coroutines.tasks.await
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

class WidgetUpdateWorker(private val appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val appWidgetId = inputData.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val isToday = inputData.getBoolean("IS_TODAY", true)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return Result.failure()

        val appWidgetManager = AppWidgetManager.getInstance(appContext)
        val remoteViews = RemoteViews(appContext.packageName, R.layout.today_reco_widget)
        var errorMessage: String? = null
        var resultPair: Pair<DailyWeatherSummary, RecommendationResult>? = null

        try {
            showLoadingState(appWidgetId, remoteViews, isToday)

            if (ActivityCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                errorMessage = "위치 권한을 허용해주세요!"
                return Result.success()
            }

            val location = LocationServices.getFusedLocationProviderClient(appContext)
                .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token).await()

            if (location == null) {
                errorMessage = "위치 정보를 가져올 수 없습니다. GPS를 켜주세요."
                return Result.success()
            }

            val weatherRepo = WeatherRepository(WeatherApiService.create())
            val clothingRepo = ClothingRepository(AppDatabase.getDatabase(appContext).clothingDao())
            val apiKey = appContext.getString(R.string.openweathermap_api_key)
            val weatherResponse = weatherRepo.getFiveDayForecast(location.latitude, location.longitude, apiKey)

            if (weatherResponse.isSuccessful && weatherResponse.body() != null) {
                val allClothes = clothingRepo.getAllItemsList()
                resultPair = processAndRecommend(weatherResponse.body()!!, allClothes, isToday)
                if (resultPair == null) {
                    errorMessage = "날씨 정보가 없습니다."
                }
            } else {
                errorMessage = "날씨 정보 업데이트 실패"
            }
        } catch (e: Exception) {
            Log.e("WidgetUpdateWorker", "Error updating widget", e)
            errorMessage = "업데이트 중 오류 발생"
        } finally {
            when {
                errorMessage != null -> {
                    if (errorMessage.contains("권한")) {
                        showPermissionError(appWidgetId, remoteViews, isToday)
                    } else {
                        showError(appWidgetId, remoteViews, isToday, errorMessage!!)
                    }
                }
                resultPair != null -> {
                    showSuccessState(appWidgetId, remoteViews, isToday, resultPair!!.first, resultPair!!.second)
                }
                else -> {
                    showError(appWidgetId, remoteViews, isToday, "알 수 없는 오류")
                }
            }
        }
        return Result.success()
    }

    private fun showLoadingState(appWidgetId: Int, remoteViews: RemoteViews, isToday: Boolean) {
        remoteViews.setTextViewText(R.id.tv_widget_weather_summary, "업데이트 중...")
        remoteViews.setViewVisibility(R.id.ll_widget_clothing_images, View.INVISIBLE)
        remoteViews.setViewVisibility(R.id.tv_widget_no_reco, View.GONE)
        finalizeWidgetUpdate(appWidgetId, remoteViews, isToday)
    }

    private fun showPermissionError(appWidgetId: Int, remoteViews: RemoteViews, isToday: Boolean) {
        remoteViews.setTextViewText(R.id.tv_widget_weather_summary, "위치 권한을 허용해주세요!")
        remoteViews.setViewVisibility(R.id.ll_widget_clothing_images, View.GONE)
        remoteViews.setViewVisibility(R.id.tv_widget_no_reco, View.GONE)
        setupPermissionClickIntent(appWidgetId, remoteViews)
        finalizeWidgetUpdate(appWidgetId, remoteViews, isToday)
    }

    private fun showError(appWidgetId: Int, remoteViews: RemoteViews, isToday: Boolean, message: String) {
        remoteViews.setTextViewText(R.id.tv_widget_weather_summary, message)
        remoteViews.setViewVisibility(R.id.ll_widget_clothing_images, View.GONE)
        remoteViews.setViewVisibility(R.id.tv_widget_no_reco, View.GONE)
        finalizeWidgetUpdate(appWidgetId, remoteViews, isToday)
    }

    private fun showSuccessState(appWidgetId: Int, remoteViews: RemoteViews, isToday: Boolean, summary: DailyWeatherSummary, result: RecommendationResult) {
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
        finalizeWidgetUpdate(appWidgetId, remoteViews, isToday)
    }

    private fun finalizeWidgetUpdate(appWidgetId: Int, remoteViews: RemoteViews, isToday: Boolean) {
        // [핵심 수정] TodayRecoWidgetProvider 클래스를 명시하여 함수를 정확히 호출합니다.
        TodayRecoWidgetProvider.setupClickIntents(appContext, appWidgetId, remoteViews, isToday)
        AppWidgetManager.getInstance(appContext).updateAppWidget(appWidgetId, remoteViews)
    }

    private fun setupPermissionClickIntent(appWidgetId: Int, remoteViews: RemoteViews) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val uri = Uri.fromParts("package", appContext.packageName, null)
        intent.data = uri
        val pendingIntentFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(appContext, appWidgetId, intent, pendingIntentFlag)
        remoteViews.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
    }

    private fun processAndRecommend(weatherResponse: WeatherResponse, allClothes: List<ClothingItem>, isToday: Boolean): Pair<DailyWeatherSummary, RecommendationResult>? {
        val targetDate = if (isToday) LocalDate.now() else LocalDate.now().plusDays(1)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val forecastsByDate = weatherResponse.list.groupBy { LocalDate.parse(it.dt_txt, formatter) }
        val targetForecasts = forecastsByDate[targetDate] ?: return null
        val summary = DailyWeatherSummary(date = "", maxTemp = targetForecasts.maxOf { it.main.temp_max }, minTemp = targetForecasts.minOf { it.main.temp_min }, maxFeelsLike = targetForecasts.maxOf { it.main.feels_like }, minFeelsLike = targetForecasts.minOf { it.main.feels_like }, weatherCondition = targetForecasts.firstOrNull()?.weather?.firstOrNull()?.description ?: "", precipitationProbability = (targetForecasts.maxOfOrNull { it.pop }?.times(100))?.toInt() ?: 0)
        val recommendation = generateRecommendationForWidget(appContext, summary, allClothes)
        return Pair(summary, recommendation)
    }

    private fun generateRecommendationForWidget(context: Context, summary: DailyWeatherSummary, allClothes: List<ClothingItem>): RecommendationResult {
        val settingsManager = SettingsManager(context)
        val maxTempCriteria = (summary.maxTemp + summary.maxFeelsLike) / 2
        val minTempCriteria = (summary.minTemp + summary.minFeelsLike) / 2
        val temperatureTolerance = settingsManager.getTemperatureTolerance()
        val packableOuterTolerance = settingsManager.getPackableOuterTolerance()
        val constitutionAdjustment = settingsManager.getConstitutionAdjustment()
        val significantTempDifference = 12.0
        val recommendedClothes = allClothes.filter {
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
                val tempRangeForMin = (it.suitableTemperature + packableOuterTolerance)..it.suitableTemperature
                tempRangeForMin.contains(minTempCriteria)
            }.minByOrNull { abs(it.suitableTemperature - minTempCriteria) }
        } else { null }
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