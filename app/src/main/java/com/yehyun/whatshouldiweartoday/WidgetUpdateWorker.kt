package com.yehyun.whatshouldiweartoday

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
import kotlin.math.min

class WidgetUpdateWorker(private val appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val FINAL_IMAGE_SIZE = 256
    }

    // ▼▼▼▼▼ 핵심 수정 1: 추천 로직을 별도 클래스로 분리하여 홈 화면과 100% 동일한 로직 보장 ▼▼▼▼▼
    private class RecommendationGenerator(private val context: Context) {
        companion object {
            private const val SIGNIFICANT_TEMP_DIFFERENCE = 12.0
        }

        private val settingsManager = SettingsManager(context)

        fun generate(summary: DailyWeatherSummary, allClothes: List<ClothingItem>): RecommendationResult {
            val maxTempCriteria = (summary.maxTemp + summary.maxFeelsLike) / 2
            val minTempCriteria = (summary.minTemp + summary.minFeelsLike) / 2
            val temperatureTolerance = settingsManager.getTemperatureTolerance()
            val packableOuterTolerance = settingsManager.getPackableOuterTolerance()
            val constitutionAdjustment = settingsManager.getConstitutionAdjustment()

            val recommendedClothes = allClothes.filter {
                val adjustedTemp = it.suitableTemperature + constitutionAdjustment
                val itemMinTemp = adjustedTemp - temperatureTolerance
                val itemMaxTemp = adjustedTemp + temperatureTolerance
                val isFitForMaxTemp = maxTempCriteria in itemMinTemp + 1..itemMaxTemp + 2.5
                val isFitForHotDay = maxTempCriteria > 33 && itemMaxTemp + 2.5 >= 33
                val isFitForFreezingDay = minTempCriteria < -3 && itemMinTemp + 1 <= -3
                isFitForMaxTemp || isFitForHotDay || isFitForFreezingDay
            }

            val recommendedTops = recommendedClothes.filter { it.category == "상의" }
            val recommendedBottoms = recommendedClothes.filter { it.category == "하의" }
            val recommendedOuters = recommendedClothes.filter { it.category == "아우터" }.toMutableList()

            val isTempDifferenceSignificant = (maxTempCriteria - minTempCriteria) >= SIGNIFICANT_TEMP_DIFFERENCE

            val packableOuters = if (isTempDifferenceSignificant) {
                allClothes.filter { it.category == "아우터" }
                    .filter {
                        val suitableTemp = it.suitableTemperature
                        val minRange = minTempCriteria
                        val maxRange = minTempCriteria + abs(packableOuterTolerance)
                        suitableTemp in minRange..maxRange
                    }
            } else {
                emptyList()
            }

            packableOuters.forEach { po ->
                recommendedOuters.add(po)
            }

            val bestTop = recommendedTops.minByOrNull { abs(it.suitableTemperature - maxTempCriteria) }
            val bestBottom = recommendedBottoms.minByOrNull { abs(it.suitableTemperature - maxTempCriteria) }
            val bestOuter = recommendedOuters.minByOrNull { abs(it.suitableTemperature - maxTempCriteria) }
            val bestCombination = listOfNotNull(bestTop, bestBottom, bestOuter)

            return RecommendationResult(
                emptyList(), emptyList(), emptyList(),
                bestCombination,
                packableOuters,
                "",
                isTempDifferenceSignificant
            )
        }
    }
    // ▲▲▲▲▲ 핵심 수정 1 끝 ▲▲▲▲▲

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
                errorMessage != null -> showError(appWidgetId, remoteViews, isToday, errorMessage)
                resultPair != null -> showSuccessState(appWidgetId, remoteViews, isToday, resultPair.first, resultPair.second)
                else -> showError(appWidgetId, remoteViews, isToday, "알 수 없는 오류")
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

    private fun showError(appWidgetId: Int, remoteViews: RemoteViews, isToday: Boolean, message: String) {
        remoteViews.setTextViewText(R.id.tv_widget_weather_summary, message)
        remoteViews.setViewVisibility(R.id.ll_widget_clothing_images, View.GONE)
        remoteViews.setViewVisibility(R.id.tv_widget_no_reco, View.GONE)
        finalizeWidgetUpdate(appWidgetId, remoteViews, isToday)
    }

    private fun showSuccessState(appWidgetId: Int, remoteViews: RemoteViews, isToday: Boolean, summary: DailyWeatherSummary, result: RecommendationResult) {
        val summaryText = "최고:%.0f°(%.0f°) | 최저:%.0f°(%.0f°)".format(summary.maxTemp, summary.maxFeelsLike, summary.minTemp, summary.minFeelsLike)
        remoteViews.setTextViewText(R.id.tv_widget_weather_summary, summaryText)

        // ▼▼▼▼▼ 핵심 수정 2: 홈 화면과 동일하게 bestCombination을 기준으로 위젯 UI 업데이트 ▼▼▼▼▼
        if (result.bestCombination.isNotEmpty()) {
            remoteViews.setViewVisibility(R.id.ll_widget_clothing_images, View.VISIBLE)
            remoteViews.setViewVisibility(R.id.tv_widget_no_reco, View.GONE)
            updateWidgetImages(remoteViews, result.bestCombination, result.packableOuters)
        } else {
            remoteViews.setViewVisibility(R.id.ll_widget_clothing_images, View.GONE)
            remoteViews.setViewVisibility(R.id.tv_widget_no_reco, View.VISIBLE)
            remoteViews.setTextViewText(R.id.tv_widget_no_reco, "추천할 옷이 없습니다.")
        }
        // ▲▲▲▲▲ 핵심 수정 2 끝 ▲▲▲▲▲
        finalizeWidgetUpdate(appWidgetId, remoteViews, isToday)
    }

    private fun finalizeWidgetUpdate(appWidgetId: Int, remoteViews: RemoteViews, isToday: Boolean) {
        TodayRecoWidgetProvider.setupClickIntents(appContext, appWidgetId, remoteViews, isToday)
        AppWidgetManager.getInstance(appContext).updateAppWidget(appWidgetId, remoteViews)
    }

    private fun processAndRecommend(weatherResponse: WeatherResponse, allClothes: List<ClothingItem>, isToday: Boolean): Pair<DailyWeatherSummary, RecommendationResult>? {
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
        // ▼▼▼▼▼ 핵심 수정 3: 분리된 RecommendationGenerator를 사용하여 추천 결과 생성 ▼▼▼▼▼
        val recommendationGenerator = RecommendationGenerator(appContext)
        val recommendation = recommendationGenerator.generate(summary, allClothes)
        // ▲▲▲▲▲ 핵심 수정 3 끝 ▲▲▲▲▲
        return Pair(summary, recommendation)
    }

    private fun getResizedBitmap(path: String, reqWidth: Int = 256, reqHeight: Int = 256): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, options)
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            BitmapFactory.decodeFile(path, options)
        } catch (e: Exception) {
            Log.e("WidgetUpdateWorker", "Failed to create resized bitmap", e)
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun createCenterCroppedSquareBitmap(source: Bitmap): Bitmap {
        val size = min(source.width, source.height)
        val x = (source.width - size) / 2
        val y = (source.height - size) / 2
        return Bitmap.createBitmap(source, x, y, size, size)
    }

    private fun createFramedBitmap(bitmap: Bitmap, isPackable: Boolean): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val imagePaint = Paint().apply {
            isAntiAlias = true
            shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        val strokeWidth = 8f
        val cornerRadius = 24f
        val borderPaint = Paint().apply {
            isAntiAlias = true
            color = ContextCompat.getColor(appContext, R.color.clothing_item_border)
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
        }
        val imageRect = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
        canvas.drawRoundRect(imageRect, cornerRadius, cornerRadius, imagePaint)
        val borderRect = RectF(strokeWidth / 2, strokeWidth / 2, bitmap.width - strokeWidth / 2, bitmap.height - strokeWidth / 2)
        canvas.drawRoundRect(borderRect, cornerRadius, cornerRadius, borderPaint)
        if (isPackable) {
            val iconBitmap = BitmapFactory.decodeResource(appContext.resources, R.drawable.ic_packable_bag)
            if (iconBitmap != null) {
                val iconSize = (bitmap.width * 0.25f).toInt()
                val scaledIcon = Bitmap.createScaledBitmap(iconBitmap, iconSize, iconSize, true)
                val margin = bitmap.width * 0.08f
                val left = bitmap.width - scaledIcon.width - margin
                val top = bitmap.height - scaledIcon.height - margin
                canvas.drawBitmap(scaledIcon, left, top, null)
                iconBitmap.recycle()
            }
        }
        return output
    }

    // ▼▼▼▼▼ 핵심 수정 4: 아이콘 표시를 위해 packableOuters 목록을 정확히 활용 ▼▼▼▼▼
    private fun updateWidgetImages(remoteViews: RemoteViews, items: List<ClothingItem>, packableOuters: List<ClothingItem>) {
        val imageViews = listOf(R.id.iv_widget_item1, R.id.iv_widget_item2, R.id.iv_widget_item3)
        imageViews.forEach { viewId -> remoteViews.setViewVisibility(viewId, View.GONE) }

        items.forEachIndexed { index, item ->
            if (index < imageViews.size) {
                val imagePath = if (item.useProcessedImage && item.processedImageUri != null) item.processedImageUri else item.imageUri
                try {
                    val file = File(imagePath)
                    if (file.exists()) {
                        val originalBitmap = getResizedBitmap(file.absolutePath)
                        if (originalBitmap != null) {
                            val croppedBitmap = createCenterCroppedSquareBitmap(originalBitmap)
                            val scaledBitmap = Bitmap.createScaledBitmap(croppedBitmap, FINAL_IMAGE_SIZE, FINAL_IMAGE_SIZE, true)
                            // '최적 조합'의 아이템이 '챙겨갈 아우터' 목록에 있는지 확인하여 아이콘 표시
                            val isPackable = packableOuters.any { it.id == item.id }
                            val framedBitmap = createFramedBitmap(scaledBitmap, isPackable)
                            remoteViews.setImageViewBitmap(imageViews[index], framedBitmap)
                            remoteViews.setViewVisibility(imageViews[index], View.VISIBLE)
                            if (!originalBitmap.isRecycled) originalBitmap.recycle()
                            if (!croppedBitmap.isRecycled) croppedBitmap.recycle()
                            if (!scaledBitmap.isRecycled) scaledBitmap.recycle()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("WidgetUpdateWorker", "Image loading failed for widget", e)
                }
            }
        }
    }
    // ▲▲▲▲▲ 핵심 수정 4 끝 ▲▲▲▲▲
}