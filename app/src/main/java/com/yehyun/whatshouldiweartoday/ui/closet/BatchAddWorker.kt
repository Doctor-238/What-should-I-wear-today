package com.yehyun.whatshouldiweartoday.ui.closet

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.GenerationConfig
import com.google.ai.client.generativeai.type.content
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import com.yehyun.whatshouldiweartoday.R
import com.yehyun.whatshouldiweartoday.data.database.AppDatabase
import com.yehyun.whatshouldiweartoday.data.database.ClothingItem
import com.yehyun.whatshouldiweartoday.data.preference.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class BatchAddWorker(private val context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    private val clothingDao = AppDatabase.getDatabase(context).clothingDao()
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val settingsManager = SettingsManager(context)

    companion object {
        const val KEY_IMAGE_URIS = "image_uris"
        const val KEY_API = "api_key"
        const val PROGRESS_CURRENT = "current"
        const val PROGRESS_TOTAL = "total"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "batch_add_channel"
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("새 옷 추가 중")
            .setContentText("AI가 옷을 분석하고 있습니다...")
            .setSmallIcon(R.drawable.ic_infinity)
            .setOngoing(true)
            .setProgress(100, 0, true)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val uriStrings = inputData.getStringArray(KEY_IMAGE_URIS) ?: return@withContext Result.failure()
        val apiKey = inputData.getString(KEY_API) ?: return@withContext Result.failure()
        val totalItems = uriStrings.size

        val generationConfig = GenerationConfig.Builder().apply { responseMimeType = "application/json" }.build()
        // [수정] AddClothingViewModel과 모델 버전 통일
        val generativeModel = GenerativeModel("gemini-2.5-flash", apiKey, generationConfig)

        for (i in uriStrings.indices) {
            val uri = Uri.parse(uriStrings[i])
            try {
                val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                analyzeAndSave(bitmap, generativeModel)
            } catch (e: Exception) {
                Log.e("BatchAddWorker", "Failed to process image URI: $uri", e)
            }
            val progressData = workDataOf(PROGRESS_CURRENT to i + 1, PROGRESS_TOTAL to totalItems)
            setProgress(progressData)
            updateNotification(i + 1, totalItems)
        }

        notificationManager.cancel(NOTIFICATION_ID)
        return@withContext Result.success()
    }

    private suspend fun analyzeAndSave(bitmap: Bitmap, model: GenerativeModel) {
        try {
            // [수정] AddClothingViewModel과 동일한 재시도 로직 사용
            val analysisResult = analyzeImageWithRetry(bitmap, model)

            // 분석에 성공하고, 입을 수 있는 옷일 경우에만 저장
            if (analysisResult?.is_wearable == true) {
                // [수정] 배경 제거 로직 복원
                val processedBitmap = try {
                    val segmenter = Segmentation.getClient(SelfieSegmenterOptions.Builder().setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE).build())
                    val mask = segmenter.process(InputImage.fromBitmap(bitmap, 0)).await()
                    createBitmapWithMask(bitmap, mask)
                } catch (e: Exception) {
                    Log.e("BatchAddWorker", "Segmentation failed.", e)
                    null
                }

                val originalPath = saveBitmapToInternalStorage(bitmap, "original_")
                val processedPath = processedBitmap?.let { saveBitmapToInternalStorage(it, "processed_") }

                if (originalPath != null) {
                    val finalTemp = if (analysisResult.category == "아우터") analysisResult.suitable_temperature - 3.0 else analysisResult.suitable_temperature
                    val newItem = ClothingItem(
                        name = analysisResult.category, // [수정] 이름은 카테고리명으로 자동 설정
                        imageUri = originalPath,
                        processedImageUri = processedPath, // [수정] 배경 제거된 이미지 경로 저장
                        useProcessedImage = false, // [수정] 배경 제거는 기본적으로 꺼둠
                        category = analysisResult.category,
                        suitableTemperature = finalTemp,
                        colorHex = analysisResult.color_hex
                    )
                    clothingDao.insert(newItem)
                }
            }
        } catch (e: Exception) {
            Log.e("BatchAddWorker", "Analysis or save failed for an image", e)
        }
    }

    private suspend fun analyzeImageWithRetry(bitmap: Bitmap, model: GenerativeModel, maxRetries: Int = 2): ClothingAnalysis? {
        var attempt = 0
        var successfulAnalysis: ClothingAnalysis? = null

        while (attempt < maxRetries) {
            try {
                val resizedBitmap = resizeBitmap(bitmap)
                // [수정] AddClothingViewModel과 프롬프트 완전 통일
                val inputContent = content {
                    image(resizedBitmap)
                    text("""
                            You are a Precise Climate & Fashion Analyst for Korean weather.
                            Your task is to analyze the clothing item in the image and provide a detailed analysis in a strict JSON format, without any additional text or explanations.

                            Your JSON response MUST contain ONLY the following keys: "is_wearable", "category", "suitable_temperature", and "color_hex".

                            - "is_wearable": (boolean) True if the item is wearable clothing.
                            - "category": (string) One of '상의', '하의', '아우터', '신발', '가방', '모자', '기타'.
                            - "color_hex": (string) The dominant color of the item as a hex string.
                            - "suitable_temperature": (double) This is the most important. Estimate the MAXIMUM comfortable temperature for this item. The value can be negative for winter clothing. You MUST provide a specific, non-round number with one decimal place (e.g., 23.5, 8.0, -2.5). A generic integer like 15.0 is a bad response. Base your judgment on the visual evidence of material, thickness, and design.
                        """.trimIndent())
                }
                val response = model.generateContent(inputContent)
                val analysisResult = Json { ignoreUnknownKeys = true }.decodeFromString<ClothingAnalysis>(response.text!!)

                if (isValidHexCode(analysisResult.color_hex)) {
                    successfulAnalysis = analysisResult
                    break
                } else {
                    successfulAnalysis = analysisResult
                }
            } catch (e: Exception) {
                Log.e("AI_ERROR_WORKER", "Attempt ${attempt + 1} failed", e)
                if (attempt == maxRetries - 1) {
                    return null // 최종 실패 시 null 반환
                }
            }
            attempt++
        }
        return successfulAnalysis
    }

    private fun isValidHexCode(hexCode: String): Boolean {
        return try {
            Color.parseColor(hexCode)
            true
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    private fun createBitmapWithMask(original: Bitmap, mask: SegmentationMask): Bitmap {
        val maskedBitmap = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
        val maskBuffer = mask.buffer
        val maskWidth = mask.width
        val maskHeight = mask.height
        val sensitivity = settingsManager.getBackgroundSensitivityValue()
        for (y in 0 until maskHeight) {
            for (x in 0 until maskWidth) {
                if (maskBuffer.float > sensitivity) {
                    maskedBitmap.setPixel(x, y, original.getPixel(x, y))
                }
            }
        }
        maskBuffer.rewind()
        return maskedBitmap
    }

    private fun resizeBitmap(bitmap: Bitmap, maxDimension: Int = 512): Bitmap {
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height
        var resizedWidth = originalWidth
        var resizedHeight = originalHeight
        if (originalHeight > maxDimension || originalWidth > maxDimension) {
            if (originalWidth > originalHeight) {
                resizedWidth = maxDimension
                resizedHeight = (resizedWidth * originalHeight) / originalWidth
            } else {
                resizedHeight = maxDimension
                resizedWidth = (resizedHeight * originalWidth) / originalHeight
            }
        }
        return Bitmap.createScaledBitmap(bitmap, resizedWidth, resizedHeight, false)
    }

    private fun saveBitmapToInternalStorage(bitmap: Bitmap, prefix: String): String? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "$prefix$timeStamp.png"
        return try {
            val file = File(context.filesDir, fileName)
            FileOutputStream(file).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
            file.absolutePath
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "옷 일괄 추가"
            val descriptionText = "여러 개의 옷을 백그라운드에서 추가할 때 사용됩니다."
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(current: Int, total: Int) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("새 옷 추가 중")
            .setContentText("$current / $total 완료")
            .setSmallIcon(R.drawable.ic_infinity)
            .setOngoing(true)
            .setProgress(total, current, false)
            .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}