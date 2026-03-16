package com.yehyun.whatshouldiweartoday.ui.closet

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import com.yehyun.whatshouldiweartoday.ai.AiModelProvider
import com.yehyun.whatshouldiweartoday.data.database.AppDatabase
import com.yehyun.whatshouldiweartoday.data.database.ClothingItem
import com.yehyun.whatshouldiweartoday.data.preference.SettingsManager
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AddClothingViewModel(application: Application) : AndroidViewModel(application) {

    private val _isAiAnalyzing = MutableLiveData(false)
    val isAiAnalyzing: LiveData<Boolean> = _isAiAnalyzing

    private val _isSaving = MutableLiveData(false)
    val isSaving: LiveData<Boolean> = _isSaving

    private val _isSaveCompleted = MutableLiveData(false)
    val isSaveCompleted: LiveData<Boolean> = _isSaveCompleted

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _originalBitmap = MutableLiveData<Bitmap?>()
    val originalBitmap: LiveData<Bitmap?> = _originalBitmap

    private val _processedBitmap = MutableLiveData<Bitmap?>()
    val processedBitmap: LiveData<Bitmap?> = _processedBitmap

    private val _useProcessedImage = MutableLiveData(false)
    val useProcessedImage: LiveData<Boolean> = _useProcessedImage

    val clothingName = MutableLiveData<String>()

    val hasChanges = MediatorLiveData<Boolean>().apply {
        value = false
        addSource(_originalBitmap) { bitmap ->
            value = bitmap != null || !clothingName.value.isNullOrEmpty()
        }
        addSource(clothingName) { name ->
            value = _originalBitmap.value != null || !name.isNullOrEmpty()
        }
    }

    val categoryText = MutableLiveData<String>()
    val temperatureText = MutableLiveData<String>()
    val isTemperatureVisible = MutableLiveData<Boolean>()

    private val _viewColor = MutableLiveData<Int?>()
    val viewColor: LiveData<Int?> = _viewColor

    private val _segmentationSucceeded = MutableLiveData<Boolean>()
    val segmentationSucceeded: LiveData<Boolean> = _segmentationSucceeded

    val fitLevelText = MutableLiveData<String>()

    private val settingsManager = SettingsManager(application)
    private var generativeModel: GenerativeModel? = null

    private val _analysisResult = MutableLiveData<ClothingAnalysis?>()
    val analysisResult: LiveData<ClothingAnalysis?> = _analysisResult

    private var processedImageJob: Deferred<Bitmap?>? = null
    private var originalImageJob: Deferred<Bitmap>? = null
    private var isImageProcessing = false

    init {
        _analysisResult.observeForever { result ->
            processAnalysisResult(result)
        }
    }

    override fun onCleared() {
        super.onCleared()
        _analysisResult.removeObserver { }
    }


    fun onImageSelected(bitmap: Bitmap, apiKey: String) {
        resetAllState()
        _originalBitmap.value = bitmap
        generativeModel = AiModelProvider.getModel(getApplication(), apiKey)
        isImageProcessing = true

        _isAiAnalyzing.value = true

        viewModelScope.launch {
            val analysisJob = async(Dispatchers.IO) { analyzeImageWithRetry(bitmap) }
            processedImageJob = async(Dispatchers.IO) { createProcessedBitmap(bitmap) }
            originalImageJob = async(Dispatchers.Default) { bitmap }

            val result = analysisJob.await()
            _isAiAnalyzing.postValue(false)

            if (result == null || !result.is_wearable) {
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "올바른 사진을 입력해주세요."
                    resetAllState()
                }
            } else {
                _analysisResult.postValue(result)
                val processed = processedImageJob?.await()
                withContext(Dispatchers.Main) {
                    _processedBitmap.value = processed
                    _segmentationSucceeded.value = (processed != null)
                    isImageProcessing = false
                }
            }
        }
    }

    private suspend fun createProcessedBitmap(bitmap: Bitmap): Bitmap? {
        return try {
            val segmenter = Segmentation.getClient(SelfieSegmenterOptions.Builder().setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE).build())
            val mask = segmenter.process(InputImage.fromBitmap(bitmap, 0)).await()
            if (mask != null) createBitmapWithMask(bitmap, mask) else null
        } catch (e: Exception) {
            null
        }
    }

    fun onUseProcessedImageToggled(isChecked: Boolean) {
        viewModelScope.launch {
            if (_processedBitmap.value == null && isImageProcessing) {
                _isSaving.value = true
                _processedBitmap.value = processedImageJob?.await()
                _isSaving.value = false
            }
            _useProcessedImage.value = isChecked
        }
    }

    fun saveClothingItem(name: String) {
        viewModelScope.launch {
            if (_analysisResult.value == null) {
                _errorMessage.value = "AI 분석 결과가 없습니다."
                return@launch
            }
            _isSaving.value = true
            try {
                val original = originalImageJob?.await()
                val processed = processedImageJob?.await()

                if (original == null) {
                    throw IOException("원본 이미지가 없습니다.")
                }

                val originalPathJob = async(Dispatchers.IO) { saveBitmapToInternalStorage(original, "original_", getApplication<Application>().filesDir) }
                val processedPathJob = async(Dispatchers.IO) { processed?.let { savePngToInternalStorage(it, "processed_", getApplication<Application>().filesDir) } }

                val originalPath = originalPathJob.await()
                val processedPath = processedPathJob.await()

                if (originalPath == null) {
                    throw IOException("원본 이미지 저장 실패")
                }

                val analysis = _analysisResult.value!!
                val baseTemp = analysis.suitable_temperature!!
                val finalTemp = when (analysis.category) {
                    "아우터" -> baseTemp - 3.0
                    "상의", "하의" -> baseTemp + 2.0
                    else -> baseTemp
                }

                val newClothingItem = ClothingItem(
                    name = name,
                    imageUri = originalPath,
                    processedImageUri = processedPath,
                    useProcessedImage = _useProcessedImage.value ?: false,
                    category = analysis.category!!,
                    suitableTemperature = finalTemp,
                    baseTemperature = baseTemp,
                    colorHex = analysis.color_hex!!,
                    fitMinHeight = analysis.fit_min_height,
                    fitMaxHeight = analysis.fit_max_height,
                    fitMinWeight = analysis.fit_min_weight,
                    fitMaxWeight = analysis.fit_max_weight
                )
                withContext(Dispatchers.IO) {
                    AppDatabase.getDatabase(getApplication()).clothingDao().insert(newClothingItem)
                }
                _isSaveCompleted.value = true
            } catch (e: Exception) {
                _errorMessage.value = "저장 중 오류 발생: ${e.message}"
            } finally {
                _isSaving.value = false
            }
        }
    }

    private suspend fun analyzeImageWithRetry(bitmap: Bitmap, maxRetries: Int = 2): ClothingAnalysis? {
        var attempt = 0
        var successfulAnalysis: ClothingAnalysis? = null
        while (attempt < maxRetries) {
            try {
                val resizedBitmap = resizeBitmap(bitmap)
                val inputContent = com.google.ai.client.generativeai.type.content {
                    image(resizedBitmap)
                    text("""
                        You are a Precise Climate & Fashion Analyst for Korean weather.
                        Your task is to analyze the clothing item in the image and provide a detailed analysis in a strict JSON format, without any additional text or explanations.
                        Your JSON response MUST contain ONLY the following keys: "is_wearable", "category", "suitable_temperature", "color_hex", "fit_min_height", "fit_max_height", "fit_min_weight", "fit_max_weight".
                        - "is_wearable": (boolean) If the image contains a single primary clothing item (or a single person's outfit), this is True. If the image contains multiple people or multiple separate clothing items laid out, this MUST be False.
                        - "category": (string) If wearable, one of '상의', '하의', '아우터', '신발', '가방', '모자', '기타'.
                        - "color_hex": (string) If wearable, the dominant color of the item as a hex string.
                        - "suitable_temperature": (double) If wearable, this is the most important. Estimate the MAXIMUM comfortable temperature for this item. The value can be negative for winter clothing. You MUST provide a specific, non-round number with one decimal place (e.g., 23.5, 8.0, -2.5). A generic integer like 15.0 is a bad response. Base your judgment on the visual evidence of material, thickness, and design.
                        - "fit_min_height": (double) Minimum height in cm for wearing this item (e.g., 155.0).
                        - "fit_max_height": (double) Maximum height in cm for wearing this item (e.g., 180.0).
                        - "fit_min_weight": (double) Minimum weight in kg for wearing this item (e.g., 45.0).
                        - "fit_max_weight": (double) Maximum weight in kg for wearing this item (e.g., 75.0).
                        Estimate the body size range this clothing would fit. For free-size/stretchy items, use wider ranges. Base on visual cues: size labels, proportions, material stretch.
                    """.trimIndent())
                }
                val response = generativeModel!!.generateContent(inputContent)
                val currentAnalysis = Json { ignoreUnknownKeys = true }.decodeFromString<ClothingAnalysis>(response.text!!)
                if (!currentAnalysis.color_hex.isNullOrBlank()) {
                    successfulAnalysis = currentAnalysis
                    break
                } else {
                    Log.w("AI_RETRY", "Attempt ${attempt + 1} succeeded but color_hex is missing. Retrying...")
                }
            } catch (e: Exception) {
                Log.e("AI_ERROR", "Attempt ${attempt + 1} failed", e)
                if (attempt == maxRetries - 1) return null
            }
            attempt++
        }
        return successfulAnalysis
    }

    fun processAnalysisResult(result: ClothingAnalysis?) {
        if (result == null) {
            categoryText.postValue("")
            temperatureText.postValue("상의, 하의, 아우터에만 표시됩니다.")
            isTemperatureVisible.postValue(false)
            setViewColor(null)
            fitLevelText.postValue("")
            return
        }

        val category = result.category ?: "기타"
        categoryText.postValue(category)

        if (settingsManager.bodyFitEnabled) {
            if (settingsManager.isBodyRegistered) {
                val level = calculateFitLevel(
                    settingsManager.estimatedHeight, settingsManager.estimatedWeight,
                    result.fit_min_height, result.fit_max_height,
                    result.fit_min_weight, result.fit_max_weight
                )
                fitLevelText.postValue(level)
            } else {
                fitLevelText.postValue("설정에서 체형을 등록해주세요")
            }
        } else {
            fitLevelText.postValue("")
        }

        val isTempCategory = category in listOf("상의", "하의", "아우터")
        isTemperatureVisible.postValue(isTempCategory)

        val temp = result.suitable_temperature
        if (isTempCategory && temp != null) {
            val baseTemp = temp
            val finalTemp = when (category) {
                "아우터" -> baseTemp - 3.0
                "상의", "하의" -> baseTemp + 2.0
                else -> baseTemp
            }
            val tolerance = settingsManager.getTemperatureTolerance()
            val constitutionAdjustment = settingsManager.getConstitutionAdjustment()
            val adjustedTemp = finalTemp + constitutionAdjustment
            val minTemp = adjustedTemp - tolerance
            val maxTemp = adjustedTemp + tolerance
            temperatureText.postValue("%.1f°C ~ %.1f°C".format(minTemp, maxTemp))
        } else {
            temperatureText.postValue("상의, 하의, 아우터에만 표시됩니다.")
        }

        if (isValidHexCode(result.color_hex)) {
            setViewColor(Color.parseColor(result.color_hex!!))
        } else {
            setViewColor(null)
        }
    }

    fun increaseTemp() {
        _analysisResult.value?.let { currentResult ->
            currentResult.suitable_temperature?.let { temp ->
                val newResult = currentResult.copy(suitable_temperature = temp + 0.5)
                _analysisResult.value = newResult
            }
        }
    }

    fun decreaseTemp() {
        _analysisResult.value?.let { currentResult ->
            currentResult.suitable_temperature?.let { temp ->
                val newResult = currentResult.copy(suitable_temperature = temp - 0.5)
                _analysisResult.value = newResult
            }
        }
    }

    private fun isValidHexCode(hexCode: String?): Boolean {
        if (hexCode.isNullOrBlank()) return false
        return try { Color.parseColor(hexCode); true } catch (e: IllegalArgumentException) { false }
    }

    fun setClothingName(name: String) {
        if (clothingName.value != name) {
            clothingName.value = name
        }
    }

    private fun setViewColor(color: Int?) { _viewColor.value = color }
    fun clearErrorMessage() { _errorMessage.value = null }

    fun resetAllState() {
        processedImageJob?.cancel()
        originalImageJob?.cancel()
        isImageProcessing = false

        _originalBitmap.value = null
        _processedBitmap.value = null
        _analysisResult.value = null
        processedImageJob = null
        originalImageJob = null
        _isAiAnalyzing.value = false
        _isSaving.value = false
        _isSaveCompleted.value = false
        clothingName.value = ""
        _segmentationSucceeded.value = false
        categoryText.value = ""
        temperatureText.value = ""
        _viewColor.value = null
        fitLevelText.value = ""
    }

    companion object {
        const val FIT_VERY_GOOD = "매우 적합"
        const val FIT_GOOD = "적합"
        const val FIT_NORMAL = "보통"
        const val FIT_BAD = "맞지않음"
        const val FIT_VERY_BAD = "매우 맞지않음"
        const val FIT_NO_INFO = "정보 없음"

        fun calculateFitLevel(
            userHeight: Float, userWeight: Float,
            minH: Double?, maxH: Double?,
            minW: Double?, maxW: Double?
        ): String {
            if (minH == null || maxH == null || minW == null || maxW == null) return FIT_NO_INFO

            val hRange = maxH - minH
            val wRange = maxW - minW
            val hShrink = hRange * 0.15
            val wShrink = wRange * 0.15

            val heightPerfect = userHeight.toDouble() in (minH + hShrink)..(maxH - hShrink)
            val weightPerfect = userWeight.toDouble() in (minW + wShrink)..(maxW - wShrink)
            val heightInRange = userHeight.toDouble() in minH..maxH
            val weightInRange = userWeight.toDouble() in minW..maxW
            val heightClose = userHeight.toDouble() in (minH - 5)..(maxH + 5)
            val weightClose = userWeight.toDouble() in (minW - 5)..(maxW + 5)
            val heightFar = userHeight.toDouble() in (minH - 10)..(maxH + 10)
            val weightFar = userWeight.toDouble() in (minW - 10)..(maxW + 10)

            return when {
                heightPerfect && weightPerfect -> FIT_VERY_GOOD
                heightInRange && weightInRange -> FIT_GOOD
                heightClose && weightClose -> FIT_NORMAL
                heightFar && weightFar -> FIT_BAD
                else -> FIT_VERY_BAD
            }
        }

        fun fitLevelToOrder(level: String): Int {
            return when (level) {
                FIT_VERY_GOOD -> 0
                FIT_GOOD -> 1
                FIT_NORMAL -> 2
                FIT_BAD -> 3
                FIT_VERY_BAD -> 4
                else -> 5
            }
        }
    }

    fun refreshDisplayWithNewSettings() {
        if (_analysisResult.value != null) {
            processAnalysisResult(_analysisResult.value)
        }
    }

    private fun resizeBitmap(bitmap: Bitmap, maxDimension: Int = 512): Bitmap {
        val originalWidth = bitmap.width; val originalHeight = bitmap.height
        var resizedWidth = originalWidth; var resizedHeight = originalHeight
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

    private fun createBitmapWithMask(original: Bitmap, mask: SegmentationMask): Bitmap {
        val maskedBitmap = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
        val maskBuffer = mask.buffer; val maskWidth = mask.width; val maskHeight = mask.height
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

    private fun saveBitmapToInternalStorage(bitmap: Bitmap, prefix: String, directory: File): String? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "$prefix$timeStamp.jpg"
        return try {
            val file = File(directory, fileName)
            FileOutputStream(file).use { stream -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream) }
            file.absolutePath
        } catch (e: IOException) { e.printStackTrace(); null }
    }

    private fun savePngToInternalStorage(bitmap: Bitmap, prefix: String, directory: File): String? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "$prefix$timeStamp.png"
        return try {
            val file = File(directory, fileName)
            FileOutputStream(file).use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) }
            file.absolutePath
        } catch (e: IOException) { e.printStackTrace(); null }
    }
}