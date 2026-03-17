package com.yehyun.whatshouldiweartoday.ui.closet

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.type.content
import com.yehyun.whatshouldiweartoday.R
import com.yehyun.whatshouldiweartoday.ai.AiModelProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ShoppingWebViewViewModel(application: Application) : AndroidViewModel(application) {

    private val apiKey = application.getString(R.string.gemini_api_key)

    private val _isProcessing = MutableLiveData(false)
    val isProcessing: LiveData<Boolean> = _isProcessing

    private val _processingMessage = MutableLiveData<String>()
    val processingMessage: LiveData<String> = _processingMessage

    private val _captureResult = MutableLiveData<CaptureResult>()
    val captureResult: LiveData<CaptureResult> = _captureResult

    private val _extractResult = MutableLiveData<CaptureResult>()
    val extractResult: LiveData<CaptureResult> = _extractResult

    @Serializable
    data class BoundingBoxResponse(
        val clothing_detected: Boolean = false,
        val items: List<BoundingBox> = emptyList()
    )

    @Serializable
    data class BoundingBox(
        val x: Double = 0.0,
        val y: Double = 0.0,
        val width: Double = 0.0,
        val height: Double = 0.0
    )

    @Serializable
    data class ClothingCheckResponse(
        val is_clothing: Boolean = false
    )

    data class CaptureResult(
        val success: Boolean,
        val imagePaths: List<String> = emptyList(),
        val message: String = ""
    )

    fun detectClothingFromScreenshot(bitmap: Bitmap) {
        if (_isProcessing.value == true) return
        _isProcessing.value = true
        _processingMessage.value = "화면에서 옷을 찾고 있습니다..."

        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    detectAndCropClothing(bitmap)
                }
                _captureResult.value = result
            } catch (e: Exception) {
                Log.e("ShoppingWebViewVM", "Screenshot detection failed", e)
                _captureResult.value = CaptureResult(false, message = "옷 감지 중 오류가 발생했습니다.")
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun extractAndFilterClothingImages(imageUrls: List<String>) {
        if (_isProcessing.value == true) return
        _isProcessing.value = true
        _processingMessage.value = "이미지를 다운로드하고 있습니다..."

        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    downloadAndFilterClothing(imageUrls)
                }
                _extractResult.value = result
            } catch (e: Exception) {
                Log.e("ShoppingWebViewVM", "Image extraction failed", e)
                _extractResult.value = CaptureResult(false, message = "이미지 처리 중 오류가 발생했습니다.")
            } finally {
                _isProcessing.value = false
            }
        }
    }

    private suspend fun detectAndCropClothing(screenshot: Bitmap): CaptureResult {
        val model = AiModelProvider.getModel(getApplication(), apiKey)
        val resized = resizeBitmap(screenshot, 1024)

        val inputContent = content {
            image(resized)
            text("""
                Analyze this screenshot from a mobile shopping app or website product listing page.
                The screen shows a product grid where each product card typically contains: a product PHOTO, product name text, and price text.

                YOUR TASK: Find ONLY the product PHOTO/IMAGE area within each card for clothing/wearable items (clothes, shoes, bags, hats, accessories).

                CRITICAL BOUNDING BOX RULES:
                1. The bounding box MUST contain ONLY the actual photograph/image of the clothing item
                2. Do NOT include any text below or above the photo (product names, prices, sizes, discounts, percentages, ratings)
                3. Do NOT include any UI elements (buttons, borders, card backgrounds, icons, navigation bars)
                4. Do NOT include whitespace or padding outside the actual photo area
                5. Crop TIGHTLY to just the clothing product photo boundaries
                6. Each bounding box should capture the FULL clothing item visible in the photo, not just a portion

                Return JSON with:
                - "clothing_detected": boolean (true if any clothing product photos found)
                - "items": array of objects with "x", "y", "width", "height" as PERCENTAGE values (0-100) of the full screenshot dimensions

                Example: If a product card spans y:20-80% but the photo is only y:20-60% with text at y:60-80%, the bounding box height should cover only y:20-60%.

                If no clothing items found, return {"clothing_detected": false, "items": []}.
            """.trimIndent())
        }

        val response = model.generateContent(inputContent)
        val json = Json { ignoreUnknownKeys = true }
        val result = json.decodeFromString<BoundingBoxResponse>(response.text ?: return CaptureResult(false, message = "AI 응답을 받지 못했습니다."))

        if (!result.clothing_detected || result.items.isEmpty()) {
            return CaptureResult(false, message = "옷사진이 감지되지 않았습니다.")
        }

        val cacheDir = getApplication<Application>().cacheDir
        val formatter = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.KOREA)
        val paths = mutableListOf<String>()

        result.items.forEachIndexed { index, box ->
            try {
                val imgWidth = screenshot.width
                val imgHeight = screenshot.height
                val x = (box.x / 100.0 * imgWidth).toInt().coerceIn(0, imgWidth - 1)
                val y = (box.y / 100.0 * imgHeight).toInt().coerceIn(0, imgHeight - 1)
                val w = (box.width / 100.0 * imgWidth).toInt().coerceIn(1, imgWidth - x)
                val h = (box.height / 100.0 * imgHeight).toInt().coerceIn(1, imgHeight - y)

                if (w > 20 && h > 20) {
                    val cropped = Bitmap.createBitmap(screenshot, x, y, w, h)
                    val timestamp = formatter.format(Date())
                    val file = File(cacheDir, "shopping_capture_${timestamp}_$index.jpg")
                    FileOutputStream(file).use { stream ->
                        cropped.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                    }
                    paths.add(file.absolutePath)
                    cropped.recycle()
                }
            } catch (e: Exception) {
                Log.e("ShoppingWebViewVM", "Failed to crop item $index", e)
            }
        }

        return if (paths.isNotEmpty()) {
            CaptureResult(true, paths)
        } else {
            CaptureResult(false, message = "옷사진이 감지되지 않았습니다.")
        }
    }

    private suspend fun downloadAndFilterClothing(imageUrls: List<String>): CaptureResult {
        _processingMessage.postValue("이미지를 다운로드하고 있습니다... (0/${imageUrls.size})")

        val cacheDir = getApplication<Application>().cacheDir
        val formatter = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.KOREA)
        val downloadedImages = mutableListOf<Pair<String, Bitmap>>()

        // Download all images
        imageUrls.forEachIndexed { index, url ->
            try {
                val bitmap = downloadImage(url)
                if (bitmap != null && bitmap.width > 50 && bitmap.height > 50) {
                    val timestamp = formatter.format(Date())
                    val filePath = File(cacheDir, "shopping_extract_${timestamp}_$index.jpg").absolutePath
                    downloadedImages.add(filePath to bitmap)
                }
            } catch (e: Exception) {
                Log.e("ShoppingWebViewVM", "Failed to download: $url", e)
            }
            _processingMessage.postValue("이미지를 다운로드하고 있습니다... (${index + 1}/${imageUrls.size})")
        }

        if (downloadedImages.isEmpty()) {
            return CaptureResult(false, message = "다운로드할 수 있는 이미지가 없습니다.")
        }

        _processingMessage.postValue("AI가 옷을 분류하고 있습니다...")

        // Filter clothing images using AI (batch of 5 at a time)
        val model = AiModelProvider.getModel(getApplication(), apiKey)
        val clothingPaths = mutableListOf<String>()

        downloadedImages.chunked(5).forEach { chunk ->
            val results = chunk.map { (path, bitmap) ->
                viewModelScope.async(Dispatchers.IO) {
                    try {
                        val resized = resizeBitmap(bitmap, 256)
                        val inputContent = content {
                            image(resized)
                            text("""
                                Is this image a clothing/wearable item? (clothes, shoes, bags, hats, accessories - anything a person can wear, carry, or put on their body)
                                Return JSON: {"is_clothing": true} or {"is_clothing": false}
                                Return false for: logos, icons, banners, UI elements, people without clear clothing focus, text images, non-fashion items.
                            """.trimIndent())
                        }
                        val response = model.generateContent(inputContent)
                        val json = Json { ignoreUnknownKeys = true }
                        val check = json.decodeFromString<ClothingCheckResponse>(response.text ?: "{}")
                        if (check.is_clothing) path else null
                    } catch (e: Exception) {
                        Log.e("ShoppingWebViewVM", "AI check failed for $path", e)
                        null
                    }
                }
            }.awaitAll()

            results.filterNotNull().forEach { path ->
                clothingPaths.add(path)
            }
        }

        // Save the clothing bitmaps to cache files
        val savedPaths = mutableListOf<String>()
        downloadedImages.forEach { (path, bitmap) ->
            if (path in clothingPaths) {
                try {
                    val file = File(path)
                    FileOutputStream(file).use { stream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                    }
                    savedPaths.add(path)
                } catch (e: Exception) {
                    Log.e("ShoppingWebViewVM", "Failed to save: $path", e)
                }
            }
            bitmap.recycle()
        }

        return if (savedPaths.isNotEmpty()) {
            CaptureResult(true, savedPaths, "${savedPaths.size}개의 옷 이미지를 찾았습니다.")
        } else {
            CaptureResult(false, message = "옷사진이 감지되지 않았습니다.")
        }
    }

    private fun downloadImage(urlString: String): Bitmap? {
        return try {
            val url = URL(urlString)
            val connection = url.openConnection()
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36")
            val inputStream = connection.getInputStream()
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            null
        }
    }

    private fun resizeBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap

        val ratio = if (width > height) {
            maxDimension.toFloat() / width
        } else {
            maxDimension.toFloat() / height
        }
        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
