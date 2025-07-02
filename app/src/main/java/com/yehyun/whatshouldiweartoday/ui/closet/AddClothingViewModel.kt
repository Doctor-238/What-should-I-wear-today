package com.yehyun.whatshouldiweartoday.ui.closet

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
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
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
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

    private val _isImageProcessing = MutableLiveData(false)
    val isImageProcessing: LiveData<Boolean> = _isImageProcessing

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
    val hasChanges = MutableLiveData(false)

    private val _analysisResultText = MutableLiveData<String>()
    val analysisResultText: LiveData<String> = _analysisResultText

    private val _viewColor = MutableLiveData<Int?>()
    val viewColor: LiveData<Int?> = _viewColor

    private val _segmentationSucceeded = MutableLiveData<Boolean>()
    val segmentationSucceeded: LiveData<Boolean> = _segmentationSucceeded

    private val settingsManager = SettingsManager(application)
    private var generativeModel: GenerativeModel? = null

    private var analysisResult: ClothingAnalysis? = null
    private var imageProcessingJob: Deferred<Pair<String, String?>>? = null

    fun onImageSelected(bitmap: Bitmap, apiKey: String) {
        resetAllState()
        hasChanges.value = true
        _originalBitmap.value = bitmap
        generativeModel = AiModelProvider.getModel(getApplication(), apiKey)

        _isAiAnalyzing.value = true
        _isImageProcessing.value = true

        viewModelScope.launch {
            imageProcessingJob = async(Dispatchers.IO) {
                val originalPath = saveBitmapToInternalStorage(bitmap, "original_", getApplication<Application>().filesDir)
                    ?: throw IOException("원본 이미지 저장 실패")
                val processedBitmap = try {
                    val segmenter = Segmentation.getClient(SelfieSegmenterOptions.Builder().setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE).build())
                    val mask = segmenter.process(InputImage.fromBitmap(bitmap, 0)).await()
                    createBitmapWithMask(bitmap, mask)
                } catch (e: Exception) { null }

                withContext(Dispatchers.Main) {
                    _processedBitmap.value = processedBitmap ?: bitmap
                    _segmentationSucceeded.value = (processedBitmap != null)
                }
                val processedPath = processedBitmap?.let { savePngToInternalStorage(it, "processed_", getApplication<Application>().filesDir) }
                _isImageProcessing.postValue(false)
                Pair(originalPath, processedPath)
            }

            val analysisJob = async(Dispatchers.IO) { analyzeImageWithRetry(bitmap) }
            analysisResult = analysisJob.await()

            withContext(Dispatchers.Main) {
                _isAiAnalyzing.value = false
                if (analysisResult == null || analysisResult?.is_wearable == false) {
                    _errorMessage.value = "올바른 사진을 입력해주세요."
                    resetAllState()
                } else {
                    processAnalysisResult(analysisResult!!)
                }
            }
        }
    }

    fun onUseProcessedImageToggled(isChecked: Boolean) {
        viewModelScope.launch {
            if (_isImageProcessing.value == true) {
                _isSaving.value = true
                imageProcessingJob?.await()
                _isSaving.value = false
            }
            _useProcessedImage.value = isChecked
            hasChanges.value = true
        }
    }

    fun saveClothingItem(name: String) {
        viewModelScope.launch {
            if (_isAiAnalyzing.value == true || imageProcessingJob == null) {
                _errorMessage.value = "아직 분석이 진행중입니다."
                return@launch
            }
            if (analysisResult == null) {
                _errorMessage.value = "AI 분석 결과가 없습니다."
                return@launch
            }
            _isSaving.value = true
            try {
                val (originalPath, processedPath) = imageProcessingJob!!.await()
                val finalTemperature = if (analysisResult!!.category == "아우터") {
                    analysisResult!!.suitable_temperature!! - 3.0
                } else {
                    analysisResult!!.suitable_temperature!!
                }
                val newClothingItem = ClothingItem(
                    name = name,
                    imageUri = originalPath,
                    processedImageUri = processedPath,
                    useProcessedImage = _useProcessedImage.value ?: false,
                    category = analysisResult!!.category!!,
                    suitableTemperature = finalTemperature,
                    colorHex = analysisResult!!.color_hex!!
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
                        Your JSON response MUST contain ONLY the following keys: "is_wearable", "category", "suitable_temperature", and "color_hex".
                        - "is_wearable": (boolean) If the image contains a single primary clothing item (or a single person's outfit), this is True. If the image contains multiple people or multiple separate clothing items laid out, this MUST be False.
                        - "category": (string) If wearable, one of '상의', '하의', '아우터', '신발', '가방', '모자', '기타'.
                        - "color_hex": (string) If wearable, the dominant color of the item as a hex string.
                        - "suitable_temperature": (double) If wearable, this is the most important. Estimate the MAXIMUM comfortable temperature for this item. The value can be negative for winter clothing. You MUST provide a specific, non-round number with one decimal place (e.g., 23.5, 8.0, -2.5). A generic integer like 15.0 is a bad response. Base your judgment on the visual evidence of material, thickness, and design.
                    """.trimIndent())
                }
                val response = generativeModel!!.generateContent(inputContent)
                successfulAnalysis = Json { ignoreUnknownKeys = true }.decodeFromString<ClothingAnalysis>(response.text!!)
                break
            } catch (e: Exception) {
                Log.e("AI_ERROR", "Attempt ${attempt + 1} failed", e)
                if (attempt == maxRetries - 1) return null
            }
            attempt++
        }
        return successfulAnalysis
    }

    fun processAnalysisResult(result: ClothingAnalysis) {
        clothingName.value = result.category ?: "새 옷"
        val category = result.category ?: "기타"
        val temp = result.suitable_temperature
        if (category in listOf("상의", "하의", "아우터") && temp != null) {
            val baseTemp = if (category == "아우터") temp - 3.0 else temp
            val tolerance = settingsManager.getTemperatureTolerance()
            val constitutionAdjustment = settingsManager.getConstitutionAdjustment()
            val adjustedTemp = baseTemp + constitutionAdjustment
            val minTemp = adjustedTemp - tolerance
            val maxTemp = adjustedTemp + tolerance
            updateAnalysisResultText(category, minTemp, maxTemp)
        } else {
            updateAnalysisResultText(category, null, null)
        }
        if (isValidHexCode(result.color_hex)) {
            setViewColor(Color.parseColor(result.color_hex!!))
        } else {
            setViewColor(null)
        }
    }

    private fun isValidHexCode(hexCode: String?): Boolean {
        if (hexCode.isNullOrBlank()) return false
        return try { Color.parseColor(hexCode); true } catch (e: IllegalArgumentException) { false }
    }

    fun setClothingName(name: String) {
        if (clothingName.value != name) {
            clothingName.value = name
            hasChanges.value = true
        }
    }

    private fun updateAnalysisResultText(category: String, minTemp: Double?, maxTemp: Double?) {
        val temperatureText = if (minTemp != null && maxTemp != null) ", 적정 온도:%.1f°C ~ %.1f°C".format(minTemp, maxTemp) else ""
        _analysisResultText.value = "분류:$category$temperatureText"
    }

    private fun setViewColor(color: Int?) { _viewColor.value = color }
    fun clearErrorMessage() { _errorMessage.value = null }

    fun resetAllState() {
        imageProcessingJob?.cancel()
        _originalBitmap.value = null
        _processedBitmap.value = null
        analysisResult = null
        imageProcessingJob = null
        _isAiAnalyzing.value = false
        _isImageProcessing.value = false
        _isSaving.value = false
        _isSaveCompleted.value = false
        hasChanges.value = false
        _segmentationSucceeded.value = false
        clothingName.value = ""
        _analysisResultText.value = ""
        _viewColor.value = null
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