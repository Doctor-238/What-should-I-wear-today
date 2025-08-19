package com.yehyun.whatshouldiweartoday

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
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
import java.util.concurrent.CancellationException
import kotlin.math.abs
import kotlin.math.min

class WidgetUpdateWorker(private val appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val FINAL_IMAGE_SIZE = 256
    }

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
                val isFitForHotDay = maxTempCriteria > 31 && itemMaxTemp + 2.5 >= 31
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

            val bestTop = recommendedTops.minByOrNull { abs(it.suitableTemperature + settingsManager.getTemperatureTolerance() - 1 - maxTempCriteria) }
            val bestBottom = recommendedBottoms.minByOrNull { abs(it.suitableTemperature + settingsManager.getTemperatureTolerance() - 1 - maxTempCriteria) }
            val bestOuter = recommendedOuters.minByOrNull { abs(it.suitableTemperature + settingsManager.getTemperatureTolerance() - 1 - maxTempCriteria) }
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
            } else {
                val location = LocationServices.getFusedLocationProviderClient(appContext)
                    .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token).await()

                if (location == null) {
                    errorMessage = "위치 정보를 가져올 수 없습니다. GPS를 켜주세요."
                } else {
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
                }
            }
        } catch (e: CancellationException) {
            return Result.success()
        } catch (e: Exception) {
            Log.e("WidgetUpdateWorker", "Error updating widget", e)
            errorMessage = "업데이트 중 오류 발생"
        } finally {
            when {
                errorMessage != null -> showError(appWidgetId, remoteViews, isToday, errorMessage)
                resultPair != null -> showSuccessState(appWidgetId, remoteViews, isToday, resultPair.first, resultPair.second)
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

        if (result.bestCombination.isNotEmpty()) {
            remoteViews.setViewVisibility(R.id.ll_widget_clothing_images, View.VISIBLE)
            remoteViews.setViewVisibility(R.id.tv_widget_no_reco, View.GONE)
            updateWidgetImages(remoteViews, result.bestCombination, result.packableOuters)
        } else {
            remoteViews.setViewVisibility(R.id.ll_widget_clothing_images, View.GONE)
            remoteViews.setViewVisibility(R.id.tv_widget_no_reco, View.VISIBLE)
            remoteViews.setTextViewText(R.id.tv_widget_no_reco, "추천할 옷이 없습니다.")
        }
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
        val recommendationGenerator = RecommendationGenerator(appContext)
        val recommendation = recommendationGenerator.generate(summary, allClothes)
        return Pair(summary, recommendation)
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

    // ▼▼▼▼▼ 핵심 수정: 여러 이미지 처리 함수를 하나로 통합하여 최적화 ▼▼▼▼▼
    private fun createWidgetBitmap(path: String, isPackable: Boolean): Bitmap? {
        try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, options)

            options.inSampleSize = calculateInSampleSize(options, FINAL_IMAGE_SIZE, FINAL_IMAGE_SIZE)
            options.inJustDecodeBounds = false

            val decodedBitmap = BitmapFactory.decodeFile(path, options) ?: return null

            val finalBitmap = Bitmap.createBitmap(FINAL_IMAGE_SIZE, FINAL_IMAGE_SIZE, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(finalBitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

            val srcWidth = decodedBitmap.width
            val srcHeight = decodedBitmap.height
            val dstSize = FINAL_IMAGE_SIZE.toFloat()

            val srcRect: Rect
            val scale: Float

            if (srcWidth > srcHeight) {
                scale = dstSize / srcHeight
                val newWidth = srcHeight
                val xOffset = (srcWidth - newWidth) / 2
                srcRect = Rect(xOffset, 0, xOffset + newWidth, srcHeight)
            } else {
                scale = dstSize / srcWidth
                val newHeight = srcWidth
                val yOffset = (srcHeight - newHeight) / 2
                srcRect = Rect(0, yOffset, srcWidth, yOffset + newHeight)
            }

            val shader = BitmapShader(decodedBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val matrix = Matrix()
            matrix.setScale(scale, scale)
            matrix.postTranslate(-srcRect.left * scale, -srcRect.top * scale)
            shader.setLocalMatrix(matrix)
            paint.shader = shader

            val cornerRadius = 24f
            canvas.drawRoundRect(RectF(0f, 0f, dstSize, dstSize), cornerRadius, cornerRadius, paint)

            decodedBitmap.recycle()

            val borderPaint = Paint().apply {
                isAntiAlias = true
                color = ContextCompat.getColor(appContext, R.color.clothing_item_border)
                style = Paint.Style.STROKE
                strokeWidth = 8f
            }
            val borderRect = RectF(borderPaint.strokeWidth / 2, borderPaint.strokeWidth / 2, dstSize - borderPaint.strokeWidth / 2, dstSize - borderPaint.strokeWidth / 2)
            canvas.drawRoundRect(borderRect, cornerRadius, cornerRadius, borderPaint)

            if (isPackable) {
                val iconDrawable = ContextCompat.getDrawable(appContext, R.drawable.ic_packable_bag)
                if (iconDrawable != null) {
                    val iconSize = (dstSize * 0.25f).toInt()
                    val margin = (dstSize * 0.08f).toInt()
                    val left = (dstSize - iconSize - margin).toInt()
                    val top = (dstSize - iconSize - margin).toInt()
                    val right = (dstSize - margin).toInt()
                    val bottom = (dstSize - margin).toInt()
                    iconDrawable.setBounds(left, top, right, bottom)
                    iconDrawable.draw(canvas)
                }
            }

            return finalBitmap
        } catch (e: Exception) {
            Log.e("WidgetUpdateWorker", "Failed to create widget bitmap", e)
            return null
        }
    }

    private fun updateWidgetImages(remoteViews: RemoteViews, items: List<ClothingItem>, packableOuters: List<ClothingItem>) {
        val imageViews = listOf(R.id.iv_widget_item1, R.id.iv_widget_item2, R.id.iv_widget_item3)
        imageViews.forEach { viewId -> remoteViews.setViewVisibility(viewId, View.GONE) }

        items.forEachIndexed { index, item ->
            if (index < imageViews.size) {
                val imagePath = if (item.useProcessedImage && item.processedImageUri != null) item.processedImageUri else item.imageUri
                try {
                    val isPackable = packableOuters.any { it.id == item.id }
                    val widgetBitmap = createWidgetBitmap(imagePath, isPackable)
                    if (widgetBitmap != null) {
                        remoteViews.setImageViewBitmap(imageViews[index], widgetBitmap)
                        remoteViews.setViewVisibility(imageViews[index], View.VISIBLE)
                    }
                } catch (e: Exception) {
                    Log.e("WidgetUpdateWorker", "Image loading failed for widget", e)
                }
            }
        }
    }
}