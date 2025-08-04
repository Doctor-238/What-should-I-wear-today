package com.yehyun.whatshouldiweartoday

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color // Color를 사용하기 위해 import
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
import kotlin.math.max // max 함수를 사용하기 위해 import

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
                errorMessage != null -> showError(appWidgetId, remoteViews, isToday, errorMessage)
                resultPair != null -> showSuccessState(appWidgetId, remoteViews, isToday, resultPair!!.first, resultPair!!.second)
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
            val isFitForMaxTemp = maxTempCriteria in itemMinTemp+1..itemMaxTemp+2.5
            val isFitForHotDay = maxTempCriteria > 33 && itemMaxTemp+2.5 >= 33
            val isFitForFreezingDay = minTempCriteria < -3 && itemMinTemp+1 <= -3
            isFitForMaxTemp || isFitForHotDay || isFitForFreezingDay
        }
        val recommendedTops = recommendedClothes.filter { it.category == "상의" }
        val recommendedBottoms = recommendedClothes.filter { it.category == "하의" }
        val recommendedOuters = recommendedClothes.filter { it.category == "아우터" }

        val bestTop = recommendedTops.minByOrNull { abs(it.suitableTemperature - maxTempCriteria) }
        val bestBottom = recommendedBottoms.minByOrNull { abs(it.suitableTemperature - maxTempCriteria) }
        var bestOuter = recommendedOuters.minByOrNull { abs(it.suitableTemperature - maxTempCriteria) }

        var packableOuterForWidget: ClothingItem? = null
        val isTempDifferenceSignificant = (maxTempCriteria - minTempCriteria) >= significantTempDifference

        if (bestOuter == null && isTempDifferenceSignificant) {
            packableOuterForWidget = allClothes.filter { it.category == "아우터" }
                .filter {
                    val suitableTemp = it.suitableTemperature
                    val minRange = minTempCriteria
                    val maxRange = minTempCriteria + abs(packableOuterTolerance)
                    suitableTemp in minRange..maxRange
                }
                .minByOrNull { abs(it.suitableTemperature - minTempCriteria) }

            if(packableOuterForWidget != null) {
                bestOuter = packableOuterForWidget
            }
        }

        val bestCombination = listOfNotNull(bestTop, bestBottom, bestOuter)

        return RecommendationResult(
            emptyList(),
            emptyList(),
            emptyList(),
            bestCombination,
            if(packableOuterForWidget != null) listOf(packableOuterForWidget) else emptyList(),
            "",
            isTempDifferenceSignificant
        )
    }

    private fun getResizedBitmap(path: String, reqWidth: Int = 256, reqHeight: Int = 256): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
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

    // ▼▼▼▼▼ 핵심 수정 1: 흰색 배경을 추가하는 함수 ▼▼▼▼▼
    private fun createBitmapWithWhiteBackground(source: Bitmap): Bitmap {
        // 원본 이미지의 가로/세로 중 더 긴 쪽을 기준으로 정사각형 비트맵 생성
        val size = max(source.width, source.height)
        val newBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(newBitmap)

        // 캔버스를 흰색으로 채움
        canvas.drawColor(Color.WHITE)

        // 원본 이미지를 중앙에 그림
        val left = (size - source.width) / 2f
        val top = (size - source.height) / 2f
        canvas.drawBitmap(source, left, top, null)

        return newBitmap
    }
    // ▲▲▲▲▲ 핵심 수정 끝 ▲▲▲▲▲

    private fun createFramedBitmap(bitmap: Bitmap, isPackable: Boolean): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val imagePaint = Paint().apply {
            isAntiAlias = true
            shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }

        val strokeWidth = 10f
        val borderPaint = Paint().apply {
            isAntiAlias = true
            color = ContextCompat.getColor(appContext, R.color.clothing_item_border)
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
        }

        val imageRect = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
        val cornerRadius = 30f

        canvas.drawRoundRect(imageRect, cornerRadius, cornerRadius, imagePaint)

        val borderRect = RectF(
            strokeWidth / 2,
            strokeWidth / 2,
            bitmap.width - strokeWidth / 2,
            bitmap.height - strokeWidth / 2
        )
        canvas.drawRoundRect(borderRect, cornerRadius, cornerRadius, borderPaint)

        if (isPackable) {
            val iconBitmap = BitmapFactory.decodeResource(appContext.resources, R.drawable.ic_packable_bag)
            val iconSize = (bitmap.width * 0.25f).toInt()
            val scaledIcon = Bitmap.createScaledBitmap(iconBitmap, iconSize, iconSize, true)

            val margin = bitmap.width * 0.08f
            val left = bitmap.width - scaledIcon.width - margin
            val top = bitmap.height - scaledIcon.height - margin

            canvas.drawBitmap(scaledIcon, left, top, null)
            iconBitmap.recycle()
        }

        return output
    }

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
                            // ▼▼▼▼▼ 핵심 수정 2: 이미지를 프레이밍하기 전에 배경 처리 ▼▼▼▼▼
                            // 1. 어떤 이미지든 흰색 배경을 가진 정사각형 이미지로 변환
                            val cleanBitmap = createBitmapWithWhiteBackground(originalBitmap)

                            // 2. '챙겨갈 아우터' 여부 확인
                            val isPackable = packableOuters.any { it.id == item.id }

                            // 3. 배경 처리된 깔끔한 이미지에 테두리와 아이콘을 그림
                            val framedBitmap = createFramedBitmap(cleanBitmap, isPackable)
                            remoteViews.setImageViewBitmap(imageViews[index], framedBitmap)
                            remoteViews.setViewVisibility(imageViews[index], View.VISIBLE)

                            // 메모리 관리
                            if (!originalBitmap.isRecycled) originalBitmap.recycle()
                            if (!cleanBitmap.isRecycled) cleanBitmap.recycle()
                            // ▲▲▲▲▲ 핵심 수정 끝 ▲▲▲▲▲
                        }
                    }
                } catch (e: Exception) {
                    Log.e("WidgetUpdateWorker", "Image loading failed for widget", e)
                }
            }
        }
    }
}