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
import com.yehyun.whatshouldiweartoday.util.PERCEPTUAL_HASH_THRESHOLD
import com.yehyun.whatshouldiweartoday.util.computePerceptualHash
import com.yehyun.whatshouldiweartoday.util.hammingDistance
import com.yehyun.whatshouldiweartoday.util.trimBorders
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
        const val KEY_PURCHASE_SOURCE = "purchase_source"
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
        val purchaseSource = inputData.getString(KEY_PURCHASE_SOURCE)

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

        val existingHashes = clothingDao.getAllImageHashes().toHashSet()

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
                    val raw = getCorrectlyOrientedBitmap(path)
                    if (raw != null) {
                        val bitmap = trimBorders(raw).also { if (it !== raw) raw.recycle() }
                        val hash = computePerceptualHash(bitmap)
                        if (existingHashes.any { hammingDistance(hash, it) <= PERCEPTUAL_HASH_THRESHOLD }) {
                            failureCount++
                        } else {
                            val saved = analyzeAndSave(bitmap, hash, generativeModel, purchaseSource)
                            if (saved) {
                                existingHashes.add(hash)
                                successCount++
                                completedPaths.add(path)
                                prefs.edit().putStringSet(batchId, completedPaths).apply()
                            } else {
                                failureCount++
                            }
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

    private suspend fun analyzeAndSave(bitmap: Bitmap, imageHash: String, model: GenerativeModel, purchaseSource: String? = null): Boolean {
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

            if (analysisResult == null
                || analysisResult.rejection_reason != null
                || !analysisResult.is_wearable
                || (analysisResult.clothing_area_ratio ?: 1.0) < 0.1
                || (analysisResult.clothing_completeness_ratio ?: 1.0) < 0.3
            ) {
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
                        purpose = purposeStr,
                        purchaseSource = purchaseSource,
                        aiCategory = analysisResult.category,
                        imageHash = imageHash
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
                        Your JSON response MUST contain ONLY the following keys: "is_wearable", "rejection_reason", "category", "suitable_temperature", "color_hex", "fit_min_height", "fit_max_height", "fit_min_weight", "fit_max_weight", "fit_min_waist", "fit_max_waist", "purposes", "clothing_area_ratio", "clothing_completeness_ratio".
                        - "rejection_reason": (string or null) This is the MOST IMPORTANT field. Critically evaluate whether the image is suitable for clothing registration. If it should be REJECTED, provide EXACTLY one of these values (be strict — when in doubt, reject): "not_wearable" (image does not clearly show a wearable item as its main subject — includes: people photos where clothing is not the focus, food, animals, scenery, furniture, screenshots, memes, backgrounds, documents, body parts without clothing, accessories like jewelry that are not worn items), "too_small" (a clothing item is visible but occupies less than 10% of the image area — e.g. tiny item in background, person standing far away), "too_cropped" (a clothing item is visible but less than 30% of the full garment is shown — e.g. only a collar, one sleeve, or just the hem). Set to null ONLY when: the image clearly and unambiguously shows a single wearable clothing item or accessory as its main subject, it fills a reasonable portion of the frame, and most of the item is visible.
                        - "is_wearable": (boolean) True only if the image clearly shows a wearable clothing item or accessory as its main subject. False for people photos, food, scenery, or any non-clothing content.
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
                        - "clothing_area_ratio": (double) Estimate the fraction (0.0 to 1.0) of the total image area occupied by the main clothing item.
                        - "clothing_completeness_ratio": (double) Estimate the fraction (0.0 to 1.0) of the FULL clothing item that is actually visible in the image.
                        Estimate the body size range this clothing would fit. For free-size/stretchy items, use wider ranges. Base on visual cues: size labels, proportions, material stretch. Waist range should be the user's waist circumference, not the garment's flat measurement.
                    """.trimIndent())
                }
                val response = model.generateContent(inputContent)
                val rawText = response.text ?: throw IllegalStateException("빈 응답")
                val start = rawText.indexOf('{')
                val end = rawText.lastIndexOf('}')
                val jsonText = if (start >= 0 && end > start) rawText.substring(start, end + 1) else rawText
                val currentAnalysis = Json { ignoreUnknownKeys = true }.decodeFromString<ClothingAnalysis>(jsonText)
                successfulAnalysis = currentAnalysis
                break
            } catch (e: Exception) {
                Log.e("AI_ERROR_WORKER", "Attempt ${attempt + 1} failed", e)
                if (attempt == maxRetries - 1) return null
            }
            attempt++
        }
        return successfulAnalysis
    }

    private fun createBitmapWithMask(original: Bitmap, mask: SegmentationMask): Bitmap {
        val maskWidth = mask.width
        val maskHeight = mask.height
        val n = maskWidth * maskHeight
        val sensitivity = settingsManager.getBackgroundSensitivityValue()

        val fg = BooleanArray(n)
        val buf = mask.buffer
        for (i in 0 until n) fg[i] = buf.float > sensitivity
        buf.rewind()

        val visited = BooleanArray(n)
        val queue = IntArray(n)
        var bestStart = -1
        var bestSize = 0
        for (seed in 0 until n) {
            if (!fg[seed] || visited[seed]) continue
            var head = 0; var tail = 0
            queue[tail++] = seed; visited[seed] = true
            while (head < tail) {
                val idx = queue[head++]
                val x = idx % maskWidth; val y = idx / maskWidth
                if (x > 0 && fg[idx - 1] && !visited[idx - 1])                       { visited[idx - 1] = true; queue[tail++] = idx - 1 }
                if (x < maskWidth - 1 && fg[idx + 1] && !visited[idx + 1])            { visited[idx + 1] = true; queue[tail++] = idx + 1 }
                if (y > 0 && fg[idx - maskWidth] && !visited[idx - maskWidth])        { visited[idx - maskWidth] = true; queue[tail++] = idx - maskWidth }
                if (y < maskHeight - 1 && fg[idx + maskWidth] && !visited[idx + maskWidth]) { visited[idx + maskWidth] = true; queue[tail++] = idx + maskWidth }
            }
            if (tail > bestSize) { bestSize = tail; bestStart = seed }
        }

        visited.fill(false)
        if (bestStart >= 0) {
            var head = 0; var tail = 0
            queue[tail++] = bestStart; visited[bestStart] = true
            while (head < tail) {
                val idx = queue[head++]
                val x = idx % maskWidth; val y = idx / maskWidth
                if (x > 0 && fg[idx - 1] && !visited[idx - 1])                       { visited[idx - 1] = true; queue[tail++] = idx - 1 }
                if (x < maskWidth - 1 && fg[idx + 1] && !visited[idx + 1])            { visited[idx + 1] = true; queue[tail++] = idx + 1 }
                if (y > 0 && fg[idx - maskWidth] && !visited[idx - maskWidth])        { visited[idx - maskWidth] = true; queue[tail++] = idx - maskWidth }
                if (y < maskHeight - 1 && fg[idx + maskWidth] && !visited[idx + maskWidth]) { visited[idx + maskWidth] = true; queue[tail++] = idx + maskWidth }
            }
        }

        val maskedBitmap = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
        for (y in 0 until maskHeight) {
            for (x in 0 until maskWidth) {
                if (visited[y * maskWidth + x]) {
                    maskedBitmap.setPixel(x, y, original.getPixel(x, y))
                }
            }
        }
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
