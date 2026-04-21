package com.yehyun.whatshouldiweartoday.ui.closet

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.exifinterface.media.ExifInterface
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.ai.client.generativeai.GenerativeModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import com.yehyun.whatshouldiweartoday.MainActivity
import com.yehyun.whatshouldiweartoday.R
import com.yehyun.whatshouldiweartoday.TodayRecoWidgetProvider
import com.yehyun.whatshouldiweartoday.WhatShouldIWearTodayApplication
import com.yehyun.whatshouldiweartoday.ai.AiModelProvider
import com.yehyun.whatshouldiweartoday.data.database.AppDatabase
import com.yehyun.whatshouldiweartoday.data.database.ClothingItem
import com.yehyun.whatshouldiweartoday.data.preference.SettingsManager
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BatchAddWorker(private val context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    private val clothingDao = AppDatabase.getDatabase(context).clothingDao()
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val settingsManager = SettingsManager(context)
    private val prefs = context.getSharedPreferences("batch_add_progress", Context.MODE_PRIVATE)


    companion object {
        const val KEY_BATCH_ID = "batch_id"
        const val KEY_IMAGE_PATHS = "image_paths"
        const val KEY_PATHS_FILE = "paths_file"
        const val KEY_API = "api_key"
        const val PROGRESS_CURRENT = "current"
        const val PROGRESS_TOTAL = "total"
        const val OUTPUT_SUCCESS_COUNT = "success_count"
        const val OUTPUT_FAILURE_COUNT = "failure_count"
        private const val FOREGROUND_NOTIFICATION_ID = 1
        private const val COMPLETE_NOTIFICATION_ID = 2
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = createProgressNotification(0, 0).build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(FOREGROUND_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    override suspend fun doWork(): Result {
        val batchId = inputData.getString(KEY_BATCH_ID) ?: return Result.failure()
        val apiKey = inputData.getString(KEY_API) ?: return Result.failure()

        // Support both direct paths (small batches) and file-based paths (large batches)
        val imagePaths = inputData.getStringArray(KEY_IMAGE_PATHS)
            ?: run {
                val pathsFilePath = inputData.getString(KEY_PATHS_FILE) ?: return Result.failure()
                val pathsFile = File(pathsFilePath)
                if (!pathsFile.exists()) return Result.failure()
                pathsFile.readLines().filter { it.isNotBlank() }.toTypedArray()
            }

        val completedPaths = prefs.getStringSet(batchId, emptySet())?.toMutableSet() ?: mutableSetOf()
        var successCount = completedPaths.size
        var failureCount = 0
        var currentProgress = completedPaths.size


        try {
            setForeground(getForegroundInfo())

            val totalItems = imagePaths.size
            val generativeModel = AiModelProvider.getModel(context, apiKey)

            for (i in imagePaths.indices) {
                if (isStopped) break

                val path = imagePaths[i]
                if (completedPaths.contains(path)) {
                    continue
                }


                try {
                    val bitmap = getCorrectlyOrientedBitmap(path)
                    if (bitmap != null) {
                        val saved = analyzeAndSave(bitmap, generativeModel)
                        if (saved) {
                            successCount++
                            completedPaths.add(path)
                            prefs.edit().putStringSet(batchId, completedPaths).apply()
                        } else {
                            failureCount++
                        }
                    } else {
                        failureCount++
                    }
                } catch (e: Exception) {
                    Log.e("BatchAddWorker", "Failed to process image: ${imagePaths[i]}", e)
                    failureCount++
                }

                currentProgress++

                if (isStopped) break

                val progressData = workDataOf(PROGRESS_CURRENT to currentProgress, PROGRESS_TOTAL to totalItems)
                setProgress(progressData)

                val notification = createProgressNotification(currentProgress, totalItems).build()
                notificationManager.notify(FOREGROUND_NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e("BatchAddWorker", "Major failure in doWork", e)
            showFinalNotification("작업 실패", "옷 추가 중 오류가 발생했습니다.")
            return Result.failure()
        } finally {
            cleanup(imagePaths, batchId)
        }

        val outputData = workDataOf(
            OUTPUT_SUCCESS_COUNT to successCount,
            OUTPUT_FAILURE_COUNT to failureCount
        )

        val message = if (isStopped) {
            "이미 추가된 옷은 자동으로 삭제되지 않습니다"

        } else {
            "$successCount 개의 옷을 성공적으로 추가했습니다.\n$failureCount 개의 옷이 추가되지 않았습니다."
        }
        val title = if(isStopped) "작업 취소됨" else "작업 완료"
        showFinalNotification(title, message)

        return Result.success(outputData)
    }

    private fun cleanup(imagePaths: Array<String>, batchId: String) {
        notificationManager.cancel(FOREGROUND_NOTIFICATION_ID)

        if (!isStopped) {
            prefs.edit().remove(batchId).apply()
            imagePaths.forEach { path ->
                try {
                    val file = File(path)
                    if (file.exists()) file.delete()
                } catch (e: Exception) {
                    Log.e("BatchAddWorker", "Failed to delete cache file: $path", e)
                }
            }
            // Clean up paths file if used
            val pathsFilePath = inputData.getString(KEY_PATHS_FILE)
            if (pathsFilePath != null) {
                try { File(pathsFilePath).delete() } catch (_: Exception) {}
            }
        }
    }


    private fun createProgressNotification(current: Int, total: Int): NotificationCompat.Builder {
        val title = if (total > 0) "새 옷 추가 중 ($current/$total)" else "새 옷 추가 중"
        val isIndeterminate = total == 0

        return NotificationCompat.Builder(context, WhatShouldIWearTodayApplication.PROGRESS_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("AI가 옷을 분석하고 있습니다...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(total, current, isIndeterminate)
            .setContentIntent(createMainActivityPendingIntent())
            .addAction(R.drawable.ic_arrow_back, "취소", createCancelPendingIntent())
            .setAutoCancel(false)
    }

    private fun showFinalNotification(title: String, message: String) {
        val notification = NotificationCompat.Builder(context, WhatShouldIWearTodayApplication.COMPLETE_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(createMainActivityPendingIntent())
            .setAutoCancel(true)
            .build()
        notificationManager.notify(COMPLETE_NOTIFICATION_ID, notification)
    }

    private suspend fun analyzeAndSave(bitmap: Bitmap, model: GenerativeModel): Boolean {
        var saved = false
        coroutineScope {
            if (isStopped) return@coroutineScope

            val analysisJob = async { analyzeImageWithRetry(bitmap, model) }
            val processedImageJob = async {
                try {
                    val segmenter = Segmentation.getClient(SelfieSegmenterOptions.Builder().setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE).build())
                    val mask = segmenter.process(InputImage.fromBitmap(bitmap, 0)).await()
                    if (mask != null) createBitmapWithMask(bitmap, mask) else null
                } catch (e: Exception) { null }
            }
            val originalImageJob = async { bitmap }

            val analysisResult = analysisJob.await()
            if (isStopped) return@coroutineScope

            if (analysisResult == null || !analysisResult.is_wearable) {
                return@coroutineScope
            }

            val processedBitmap = processedImageJob.await()
            val originalBitmap = originalImageJob.await()

            val temp = analysisResult.suitable_temperature
            if (analysisResult.category != null && temp != null && analysisResult.color_hex != null) {
                val originalPath = saveBitmapToInternalStorage(originalBitmap, "original_")
                val processedPath = processedBitmap?.let { savePngToInternalStorage(it, "processed_") }

                if (originalPath != null) {
                    val baseTemp = temp
                    val finalTemp = when (analysisResult.category) {
                        "아우터" -> baseTemp - 3.0
                        "상의", "하의" -> baseTemp + 2.0
                        else -> baseTemp
                    }

                    val purposeStr = analysisResult.purposes?.take(2)?.joinToString(",") ?: ""

                    val newItem = ClothingItem(
                        name = analysisResult.category,
                        imageUri = originalPath,
                        processedImageUri = processedPath,
                        useProcessedImage = false,
                        category = analysisResult.category,
                        suitableTemperature = finalTemp,
                        baseTemperature = baseTemp,
                        colorHex = analysisResult.color_hex,
                        fitMinHeight = analysisResult.fit_min_height,
                        fitMaxHeight = analysisResult.fit_max_height,
                        fitMinWeight = analysisResult.fit_min_weight,
                        fitMaxWeight = analysisResult.fit_max_weight,
                        fitMinWaist = analysisResult.fit_min_waist,
                        fitMaxWaist = analysisResult.fit_max_waist,
                        purpose = purposeStr
                    )
                    clothingDao.insert(newItem)
                    saved = true
                }
            }
        }
        return saved
    }

    private fun createMainActivityPendingIntent(): PendingIntent {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT
        return PendingIntent.getActivity(applicationContext, 1001, intent, flags)
    }

    private fun createCancelPendingIntent(): PendingIntent {
        val intent = Intent(applicationContext, TodayRecoWidgetProvider::class.java).apply {
            action = "ACTION_CANCEL_BATCH_ADD"
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT
        return PendingIntent.getBroadcast(applicationContext, 1002, intent, flags)
    }

    private fun getCorrectlyOrientedBitmap(path: String): Bitmap? {
        return try {
            val originalBitmap = BitmapFactory.decodeFile(path)
            val exifInterface = ExifInterface(path)
            val orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            }
            Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun analyzeImageWithRetry(bitmap: Bitmap, model: GenerativeModel, maxRetries: Int = 2): ClothingAnalysis? {
        var attempt = 0
        var successfulAnalysis: ClothingAnalysis? = null
        val allPurposes = settingsManager.getAllPurposes()
        val purposeListStr = allPurposes.joinToString("', '", "'", "'")
        while (attempt < maxRetries) {
            if(isStopped) return null
            try {
                val resizedBitmap = resizeBitmap(bitmap)
                val inputContent = com.google.ai.client.generativeai.type.content {
                    image(resizedBitmap)
                    text("""
                        You are a Precise Climate & Fashion Analyst for Korean weather.
                        Your task is to analyze the clothing item in the image and provide a detailed analysis in a strict JSON format, without any additional text or explanations.
                        Your JSON response MUST contain ONLY the following keys: "is_wearable", "category", "suitable_temperature", "color_hex", "fit_min_height", "fit_max_height", "fit_min_weight", "fit_max_weight", "fit_min_waist", "fit_max_waist", "purposes".
                        - "is_wearable": (boolean) If the image contains at least one wearable clothing item, this is True. If the image contains multiple clothing items, focus on the PRIMARY/MAIN item that appears to be the subject or focus of the photo (NOT necessarily the largest one). Only return False if the image contains no clothing at all (e.g. food, scenery, etc.).
                        - "category": (string) If wearable, one of '상의', '하의', '아우터', '신발', '가방', '모자', '기타'.
                        - "color_hex": (string) If wearable, the dominant color of the item as a hex string.
                        - "suitable_temperature": (double) If wearable, this is the most important. Estimate the MAXIMUM comfortable temperature for this item. The value can be negative for winter clothing. You MUST provide a specific, non-round number with one decimal place (e.g., 23.5, 8.0, -2.5). A generic integer like 15.0 is a bad response. Base your judgment on the visual evidence of material, thickness, and design.
                        - "fit_min_height": (double) Minimum height in cm for wearing this item (e.g., 155.0).
                        - "fit_max_height": (double) Maximum height in cm for wearing this item (e.g., 180.0).
                        - "fit_min_weight": (double) Minimum weight in kg for wearing this item (e.g., 45.0).
                        - "fit_max_weight": (double) Maximum weight in kg for wearing this item (e.g., 75.0).
                        - "fit_min_waist": (double or null) Minimum waist circumference in cm that this item fits (e.g., 68.0). Use null ONLY for items where the waistline is irrelevant (oversized tops, shoes, bags, hats, loose jackets). Bottoms and fitted tops MUST have a value.
                        - "fit_max_waist": (double or null) Maximum waist circumference in cm that this item fits (e.g., 88.0). Follow the same rule as fit_min_waist.
                        - "purposes": (array of strings) If wearable, select the 1 or 2 MOST suitable purposes from this list: [$purposeListStr]. Pick only the best fitting ones. Maximum 2 purposes per item.
                        Estimate the body size range this clothing would fit. For free-size/stretchy items, use wider ranges. Base on visual cues: size labels, proportions, material stretch. Waist range should be the user's waist circumference, not the garment's flat measurement.
                    """.trimIndent())
                }
                val response = model.generateContent(inputContent)
                val currentAnalysis = Json { ignoreUnknownKeys = true }.decodeFromString<ClothingAnalysis>(response.text!!)
                if (!currentAnalysis.color_hex.isNullOrBlank()) {
                    successfulAnalysis = currentAnalysis
                    break
                } else {
                    Log.w("AI_RETRY_WORKER", "Attempt ${attempt + 1} succeeded but color_hex is missing. Retrying...")
                }
            } catch (e: Exception) {
                Log.e("AI_ERROR_WORKER", "Attempt ${attempt + 1} failed", e)
                if (attempt == maxRetries - 1) return null
            }
            attempt++
        }
        return successfulAnalysis
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

    private fun uniqueSuffix(): String {
        // Milliseconds + random tail avoids collisions when multiple images
        // are saved within the same second in a batch.
        val millis = System.currentTimeMillis()
        val rand = (Math.random() * 0x10000).toInt().toString(16).padStart(4, '0')
        return "${millis}_$rand"
    }

    private fun saveBitmapToInternalStorage(bitmap: Bitmap, prefix: String): String? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "$prefix${timeStamp}_${uniqueSuffix()}.jpg"
        return try {
            val file = File(context.filesDir, fileName)
            FileOutputStream(file).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            }
            file.absolutePath
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    private fun savePngToInternalStorage(bitmap: Bitmap, prefix: String): String? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "$prefix${timeStamp}_${uniqueSuffix()}.png"
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
}