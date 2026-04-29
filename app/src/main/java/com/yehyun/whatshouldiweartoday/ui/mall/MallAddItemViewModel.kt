package com.yehyun.whatshouldiweartoday.ui.mall

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.yehyun.whatshouldiweartoday.ai.AiModelProvider
import com.yehyun.whatshouldiweartoday.data.database.mall.MallDatabase
import com.yehyun.whatshouldiweartoday.data.database.mall.MallItem
import com.yehyun.whatshouldiweartoday.data.preference.SettingsManager
import com.yehyun.whatshouldiweartoday.util.isNetworkAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
data class MallItemAnalysis(
    val is_wearable: Boolean,
    val category: String? = null,
    val suitable_min_temp: Double? = null,
    val suitable_max_temp: Double? = null,
    val color_hex: String? = null,
    val fit_min_height: Double? = null,
    val fit_max_height: Double? = null,
    val fit_min_weight: Double? = null,
    val fit_max_weight: Double? = null,
    val fit_min_waist: Double? = null,
    val fit_max_waist: Double? = null,
    val purposes: List<String>? = null,
    val product_name: String? = null,
    val brand: String? = null,
    val price: Int? = null,
    val material: String? = null,
    val description: String? = null,
    val tags: List<String>? = null,
    val season: List<String>? = null
)

class MallAddItemViewModel(application: Application) : AndroidViewModel(application) {

    data class PendingItem(val bitmap: Bitmap, val analysis: MallItemAnalysis)

    private val mallDao = MallDatabase.getDatabase(application).mallDao()
    private val settingsManager = SettingsManager(application)

    private val _isProcessing = MutableLiveData(false)
    val isProcessing: LiveData<Boolean> = _isProcessing

    private val _progress = MutableLiveData(0 to 0)
    val progress: LiveData<Pair<Int, Int>> = _progress

    private val _pendingItems = MutableLiveData<List<PendingItem>>(emptyList())
    val pendingItems: LiveData<List<PendingItem>> = _pendingItems

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _saveCompleted = MutableLiveData(false)
    val saveCompleted: LiveData<Boolean> = _saveCompleted

    fun analyzeImages(bitmaps: List<Bitmap>, apiKey: String) {
        if (!isNetworkAvailable(getApplication())) {
            _errorMessage.value = "인터넷 연결이 필요합니다."
            return
        }
        viewModelScope.launch {
            _isProcessing.value = true
            val results = mutableListOf<PendingItem>()
            val model = AiModelProvider.getModel(getApplication(), apiKey)
            val allPurposes = settingsManager.getAllPurposes()
            val purposeListStr = allPurposes.joinToString("', '", "'", "'")

            bitmaps.forEachIndexed { index, bitmap ->
                _progress.value = index + 1 to bitmaps.size
                try {
                    val analysis = analyzeWithRetry(bitmap, model, purposeListStr)
                    if (analysis != null && analysis.is_wearable && analysis.category in listOf("상의", "하의", "아우터")) {
                        results.add(PendingItem(bitmap, analysis))
                    }
                } catch (e: Exception) {
                    Log.e("MallAddItemVM", "Analysis failed for image $index", e)
                }
            }
            _pendingItems.value = results
            _isProcessing.value = false
            if (results.isEmpty()) _errorMessage.value = "분석 가능한 상의/하의/아우터 이미지가 없습니다."
        }
    }

    private suspend fun analyzeWithRetry(bitmap: Bitmap, model: com.google.ai.client.generativeai.GenerativeModel, purposeListStr: String, maxRetries: Int = 2): MallItemAnalysis? {
        val resized = resizeBitmap(bitmap)
        repeat(maxRetries) { attempt ->
            try {
                val content = com.google.ai.client.generativeai.type.content {
                    image(resized)
                    text("""
                        You are a Korean online shopping mall product analyst and fashion AI.
                        Analyze the clothing item in the image and return ONLY valid JSON with these exact keys:
                        - "is_wearable": boolean (true if clothing item)
                        - "category": one of "상의","하의","아우터" only (null if not these)
                        - "suitable_min_temp": minimum comfortable temperature (double, e.g. 5.0)
                        - "suitable_max_temp": maximum comfortable temperature (double, e.g. 22.0)
                        - "color_hex": dominant color hex string (e.g. "#FF8C69")
                        - "fit_min_height": min height in cm (double)
                        - "fit_max_height": max height in cm (double)
                        - "fit_min_weight": min weight in kg (double)
                        - "fit_max_weight": max weight in kg (double)
                        - "fit_min_waist": min waist cm (double or null)
                        - "fit_max_waist": max waist cm (double or null)
                        - "purposes": array, pick 1-2 from [$purposeListStr]
                        - "product_name": creative Korean product name (string, e.g. "오버핏 코튼 티셔츠")
                        - "brand": fictional Korean brand name (string, e.g. "데일리웨어")
                        - "price": realistic Korean online price in KRW (integer, e.g. 29900)
                        - "material": material description (string, e.g. "면 100%")
                        - "description": 1-2 sentence Korean product description (string)
                        - "tags": array of 3-5 Korean fashion tags (e.g. ["오버핏","캐주얼","베이직"])
                        - "season": array of applicable seasons from ["봄","여름","가을","겨울"]
                    """.trimIndent())
                }
                val response = model.generateContent(content)
                val analysis = Json { ignoreUnknownKeys = true }.decodeFromString<MallItemAnalysis>(response.text!!)
                if (!analysis.color_hex.isNullOrBlank() && analysis.product_name != null) return analysis
            } catch (e: Exception) {
                Log.e("MallAddItemVM", "Retry $attempt failed", e)
                if (attempt == maxRetries - 1) return null
            }
        }
        return null
    }

    fun saveAllItems() {
        viewModelScope.launch {
            _isProcessing.value = true
            val items = _pendingItems.value ?: return@launch
            withContext(Dispatchers.IO) {
                items.forEach { pending ->
                    val analysis = pending.analysis
                    val imagePath = saveBitmap(pending.bitmap)
                    val mallItem = MallItem(
                        name = analysis.product_name ?: analysis.category ?: "상품",
                        brand = analysis.brand ?: "브랜드",
                        price = analysis.price ?: 29900,
                        category = analysis.category ?: "상의",
                        colorHex = analysis.color_hex?.let { if (isValidHex(it)) it else "#CCCCCC" } ?: "#CCCCCC",
                        imageUri = imagePath ?: "",
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
                    mallDao.insert(mallItem)
                }
            }
            _isProcessing.value = false
            _saveCompleted.value = true
        }
    }

    private fun saveBitmap(bitmap: Bitmap): String? {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())
        val file = File(getApplication<Application>().filesDir, "mall_$ts.jpg")
        return try {
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            file.absolutePath
        } catch (e: IOException) { null }
    }

    private fun resizeBitmap(bitmap: Bitmap, max: Int = 512): Bitmap {
        val w = bitmap.width; val h = bitmap.height
        if (w <= max && h <= max) return bitmap
        return if (w > h) Bitmap.createScaledBitmap(bitmap, max, max * h / w, false)
        else Bitmap.createScaledBitmap(bitmap, max * w / h, max, false)
    }

    private fun isValidHex(hex: String?): Boolean {
        if (hex.isNullOrBlank()) return false
        return try { Color.parseColor(hex); true } catch (e: Exception) { false }
    }

    fun clearError() { _errorMessage.value = null }
    fun clearPending() { _pendingItems.value = emptyList() }
}
