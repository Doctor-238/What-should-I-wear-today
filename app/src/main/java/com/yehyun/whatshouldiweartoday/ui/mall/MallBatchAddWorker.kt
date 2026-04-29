package com.yehyun.whatshouldiweartoday.ui.mall

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.yehyun.whatshouldiweartoday.MainActivity
import com.yehyun.whatshouldiweartoday.R
import com.yehyun.whatshouldiweartoday.WhatShouldIWearTodayApplication
import com.yehyun.whatshouldiweartoday.ai.AiModelProvider
import com.yehyun.whatshouldiweartoday.data.database.mall.MallDatabase
import com.yehyun.whatshouldiweartoday.data.database.mall.MallItem
import com.yehyun.whatshouldiweartoday.data.preference.SettingsManager
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MallBatchAddWorker(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val mallDao = MallDatabase.getDatabase(context).mallDao()
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val settingsManager = SettingsManager(context)
    private val prefs = context.getSharedPreferences("mall_batch_progress", Context.MODE_PRIVATE)

    companion object {
        const val KEY_BATCH_ID = "mall_batch_id"
        const val KEY_PATHS_FILE = "mall_paths_file"
        const val KEY_API = "api_key"
        const val PROGRESS_CURRENT = "current"
        const val PROGRESS_TOTAL = "total"
        const val OUTPUT_SUCCESS_COUNT = "success_count"
        const val TAG = "mall_batch_add"
        private const val FOREGROUND_NOTIFICATION_ID = 11
        private const val COMPLETE_NOTIFICATION_ID = 12
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = createProgressNotification(0, 0).build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                FOREGROUND_NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    override suspend fun doWork(): Result {
        val batchId = inputData.getString(KEY_BATCH_ID) ?: return Result.failure()
        val apiKey = inputData.getString(KEY_API) ?: return Result.failure()
        val pathsFilePath = inputData.getString(KEY_PATHS_FILE) ?: return Result.failure()
        val pathsFile = File(pathsFilePath)
        if (!pathsFile.exists()) return Result.failure()
        val imagePaths = pathsFile.readLines().filter { it.isNotBlank() }

        val completedPaths = prefs.getStringSet(batchId, emptySet())?.toMutableSet() ?: mutableSetOf()
        var successCount = completedPaths.size
        var currentProgress = completedPaths.size
        val totalItems = imagePaths.size

        try {
            setForeground(getForegroundInfo())
            val model = AiModelProvider.getModel(context, apiKey)
            val allPurposes = settingsManager.getAllPurposes()
            val purposeListStr = allPurposes.joinToString("', '", "'", "'")

            for (path in imagePaths) {
                if (isStopped) break
                if (completedPaths.contains(path)) {
                    currentProgress++
                    continue
                }

                try {
                    val bitmap = BitmapFactory.decodeFile(path)
                    if (bitmap != null) {
                        val mallItem = analyzeAndCreateItem(bitmap, model, purposeListStr)
                        if (mallItem != null) {
                            mallDao.insert(mallItem)
                            successCount++
                            completedPaths.add(path)
                            prefs.edit().putStringSet(batchId, completedPaths).apply()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MallBatchAddWorker", "Failed: $path", e)
                }

                currentProgress++
                setProgress(workDataOf(PROGRESS_CURRENT to currentProgress, PROGRESS_TOTAL to totalItems))
                notificationManager.notify(
                    FOREGROUND_NOTIFICATION_ID,
                    createProgressNotification(currentProgress, totalItems).build()
                )
            }
        } catch (e: Exception) {
            Log.e("MallBatchAddWorker", "Major failure", e)
            notificationManager.cancel(FOREGROUND_NOTIFICATION_ID)
            return Result.failure()
        } finally {
            notificationManager.cancel(FOREGROUND_NOTIFICATION_ID)
            if (!isStopped) {
                prefs.edit().remove(batchId).apply()
                imagePaths.forEach { try { File(it).delete() } catch (_: Exception) {} }
                try { pathsFile.delete() } catch (_: Exception) {}
            }
        }

        showFinalNotification(successCount)
        return Result.success(workDataOf(OUTPUT_SUCCESS_COUNT to successCount))
    }

    private suspend fun analyzeAndCreateItem(
        bitmap: Bitmap,
        model: com.google.ai.client.generativeai.GenerativeModel,
        purposeListStr: String
    ): MallItem? {
        val resized = resizeBitmap(bitmap)
        for (attempt in 0..1) {
            try {
                val content = com.google.ai.client.generativeai.type.content {
                    image(resized)
                    text(
                        """
                        You are a Korean online shopping mall product analyst.
                        Analyze the clothing item in the image and return ONLY valid JSON with these exact keys:
                        - "is_wearable": boolean (true if clothing item)
                        - "category": one of "상의","하의","아우터" only (null if not these)
                        - "suitable_min_temp": minimum comfortable temperature (double)
                        - "suitable_max_temp": maximum comfortable temperature (double)
                        - "color_hex": dominant color hex string (e.g. "#FF8C69")
                        - "fit_min_height": min height in cm (double)
                        - "fit_max_height": max height in cm (double)
                        - "fit_min_weight": min weight in kg (double)
                        - "fit_max_weight": max weight in kg (double)
                        - "fit_min_waist": min waist cm (double or null)
                        - "fit_max_waist": max waist cm (double or null)
                        - "purposes": array, pick 1-2 from [$purposeListStr]
                        - "product_name": creative Korean product name (string)
                        - "brand": fictional Korean brand name (string)
                        - "price": realistic Korean online price in KRW (integer)
                        - "material": material description (string)
                        - "description": 1-2 sentence Korean product description (string)
                        - "tags": array of 3-5 Korean fashion tags
                        - "season": array from ["봄","여름","가을","겨울"]
                        """.trimIndent()
                    )
                }
                val response = model.generateContent(content)
                val analysis = Json { ignoreUnknownKeys = true }
                    .decodeFromString<MallItemAnalysis>(response.text!!)

                if (!analysis.is_wearable || analysis.category !in listOf("상의", "하의", "아우터")) {
                    return null
                }
                val imagePath = saveBitmap(bitmap) ?: return null
                val colorHex = analysis.color_hex?.takeIf { isValidHex(it) } ?: "#CCCCCC"
                return MallItem(
                    name = analysis.product_name ?: analysis.category ?: "상품",
                    brand = analysis.brand ?: "브랜드",
                    price = analysis.price ?: 29900,
                    category = analysis.category ?: "상의",
                    colorHex = colorHex,
                    imageUri = imagePath,
                    suitableMinTemp = analysis.suitable_min_temp ?: 10.0,
                    suitableMaxTemp = analysis.suitable_max_temp ?: 25.0,
                    fitMinHeight = analysis.fit_min_height,
                    fitMaxHeight = analysis.fit_max_height,
                    fitMinWeight = analysis.fit_min_weight,
                    fitMaxWeight = analysis.fit_max_weight,
                    fitMinWaist = analysis.fit_min_waist,
                    fitMaxWaist = analysis.fit_max_waist,
                    purposes = analysis.purposes?.joinToString(",") ?: "",
                    season = analysis.season?.joinToString(",") ?: "",
                    material = analysis.material ?: "",
                    description = analysis.description ?: "",
                    tags = analysis.tags?.joinToString(",") ?: ""
                )
            } catch (e: Exception) {
                Log.e("MallBatchAddWorker", "Attempt $attempt failed", e)
                if (attempt == 1) return null
            }
        }
        return null
    }

    private fun saveBitmap(bitmap: Bitmap): String? {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())
        val file = File(context.filesDir, "mall_${ts}.jpg")
        return try {
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            file.absolutePath
        } catch (e: IOException) {
            null
        }
    }

    private fun resizeBitmap(bitmap: Bitmap, max: Int = 512): Bitmap {
        val w = bitmap.width; val h = bitmap.height
        if (w <= max && h <= max) return bitmap
        return if (w > h) Bitmap.createScaledBitmap(bitmap, max, max * h / w, false)
        else Bitmap.createScaledBitmap(bitmap, max * w / h, max, false)
    }

    private fun isValidHex(hex: String?): Boolean {
        if (hex.isNullOrBlank()) return false
        return try {
            android.graphics.Color.parseColor(hex); true
        } catch (e: Exception) {
            false
        }
    }

    private fun createProgressNotification(current: Int, total: Int): NotificationCompat.Builder {
        val title = if (total > 0) "쇼핑몰 상품 추가 중 ($current/$total)" else "쇼핑몰 상품 추가 중"
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        else PendingIntent.FLAG_UPDATE_CURRENT
        val intent = Intent(context, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(context, 2001, intent, flags)
        return NotificationCompat.Builder(context, WhatShouldIWearTodayApplication.PROGRESS_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("AI가 상품을 분석하고 있습니다...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true).setOnlyAlertOnce(true)
            .setProgress(total, current, total == 0)
            .setContentIntent(pi).setAutoCancel(false)
    }

    private fun showFinalNotification(count: Int) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        else PendingIntent.FLAG_UPDATE_CURRENT
        val intent = Intent(context, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(context, 2002, intent, flags)
        val notification = NotificationCompat.Builder(context, WhatShouldIWearTodayApplication.COMPLETE_CHANNEL_ID)
            .setContentTitle("상품 추가 완료")
            .setContentText("${count}개의 상품이 쇼핑몰에 추가되었습니다.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pi).setAutoCancel(true).build()
        notificationManager.notify(COMPLETE_NOTIFICATION_ID, notification)
    }
}
