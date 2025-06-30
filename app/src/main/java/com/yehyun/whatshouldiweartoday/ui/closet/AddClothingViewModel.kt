// app/src/main/java/com/yehyun/whatshouldiweartoday/ui/closet/AddClothingViewModel.kt

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
import com.google.ai.client.generativeai.type.GenerationConfig
import com.google.ai.client.generativeai.type.content
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import com.yehyun.whatshouldiweartoday.data.database.AppDatabase
import com.yehyun.whatshouldiweartoday.data.database.ClothingItem
import com.yehyun.whatshouldiweartoday.data.preference.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AddClothingViewModel(application: Application) : AndroidViewModel(application) {

    enum class UiState { IDLE, ANALYZING, ANALYZED }

    private val _uiState = MutableLiveData<UiState>(UiState.IDLE)
    val uiState: LiveData<UiState> = _uiState

    private val _originalBitmap = MutableLiveData<Bitmap?>()
    val originalBitmap: LiveData<Bitmap?> = _originalBitmap

    private val _processedBitmap = MutableLiveData<Bitmap?>()
    val processedBitmap: LiveData<Bitmap?> = _processedBitmap

    private val _clothingAnalysisResult = MutableLiveData<ClothingAnalysis?>()
    val clothingAnalysisResult: LiveData<ClothingAnalysis?> = _clothingAnalysisResult

    private val _segmentationSucceeded = MutableLiveData<Boolean>()
    val segmentationSucceeded: LiveData<Boolean> = _segmentationSucceeded

    private val _useProcessedImage = MutableLiveData(false)
    val useProcessedImage: LiveData<Boolean> = _useProcessedImage

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _analysisResultText = MutableLiveData<String>()
    val analysisResultText: LiveData<String> = _analysisResultText

    private val _viewColor = MutableLiveData<Int?>()
    val viewColor: LiveData<Int?> = _viewColor

    private val _isSaveCompleted = MutableLiveData<Boolean>(false)
    val isSaveCompleted: LiveData<Boolean> = _isSaveCompleted

    val clothingName = MutableLiveData<String>()

    val hasChanges = MutableLiveData(false)

    private var generativeModel: GenerativeModel? = null
    private val settingsManager = SettingsManager(application)

    fun onImageSelected(bitmap: Bitmap, apiKey: String) {
        hasChanges.value = true
        _uiState.value = UiState.ANALYZING
        _originalBitmap.value = bitmap
        _processedBitmap.value = null
        _clothingAnalysisResult.value = null
        clothingName.value = ""
        _useProcessedImage.value = false

        initializeGenerativeModel(apiKey)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resizedBitmap = resizeBitmap(bitmap)
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

                val response = generativeModel!!.generateContent(inputContent)
                val analysisResult = Json { ignoreUnknownKeys = true }.decodeFromString<ClothingAnalysis>(response.text!!)

                withContext(Dispatchers.Main) {
                    if (!analysisResult.is_wearable) {
                        _errorMessage.value = "올바른 사진을 입력해주세요"
                        _uiState.value = UiState.IDLE
                    } else {
                        _clothingAnalysisResult.value = analysisResult
                        processAnalysisResult(analysisResult)
                        segmentImage(bitmap)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("AI_ERROR", "Error during AI analysis", e)
                    _errorMessage.value = "분석 실패: ${e.message}"
                    _uiState.value = UiState.IDLE
                }
            }
        }
    }

    private fun segmentImage(originalBitmap: Bitmap) {
        val segmenter = Segmentation.getClient(SelfieSegmenterOptions.Builder().setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE).build())
        segmenter.process(InputImage.fromBitmap(originalBitmap, 0))
            .addOnSuccessListener { mask ->
                viewModelScope.launch(Dispatchers.Default) {
                    val processed = createBitmapWithMask(originalBitmap, mask)
                    withContext(Dispatchers.Main) {
                        _processedBitmap.value = processed
                        _segmentationSucceeded.value = true
                        _uiState.value = UiState.ANALYZED
                    }
                }
            }
            .addOnFailureListener {
                _processedBitmap.value = originalBitmap
                _segmentationSucceeded.value = false
                _uiState.value = UiState.ANALYZED
            }
    }

    fun processAnalysisResult(result: ClothingAnalysis) {
        if (result.category in listOf("상의", "하의", "아우터")) {
            val baseTemp = if (result.category == "아우터") result.suitable_temperature - 3.0 else result.suitable_temperature
            val tolerance = settingsManager.getTemperatureTolerance()
            val constitutionAdjustment = settingsManager.getConstitutionAdjustment()
            val adjustedTemp = baseTemp + constitutionAdjustment

            val minTemp = adjustedTemp - tolerance
            val maxTemp = adjustedTemp + tolerance

            updateAnalysisResultText(result.category, minTemp, maxTemp)
        } else {
            updateAnalysisResultText(result.category, null, null)
        }

        try {
            setViewColor(Color.parseColor(result.color_hex))
        } catch (e: IllegalArgumentException) {
            setViewColor(null)
        }
    }

    fun setUseProcessedImage(use: Boolean) {
        _useProcessedImage.value = use
        hasChanges.value = true
    }

    fun setClothingName(name: String) {
        clothingName.value = name
        hasChanges.value = true
    }

    private fun initializeGenerativeModel(apiKey: String) {
        if (generativeModel == null) {
            val config = GenerationConfig.Builder().apply {
                responseMimeType = "application/json"
            }.build()
            generativeModel = GenerativeModel(
                modelName = "gemini-2.5-flash",
                apiKey = apiKey,
                generationConfig = config
            )
        }
    }

    fun updateAnalysisResultText(category: String, minTemp: Double?, maxTemp: Double?) {
        val temperatureText = if (minTemp != null && maxTemp != null) {
            ", 적정 온도:%.1f°C ~ %.1f°C".format(minTemp, maxTemp)
        } else {
            ""
        }
        _analysisResultText.value = "분류:$category$temperatureText"
    }

    fun setViewColor(color: Int?) {
        _viewColor.value = color
    }

    fun saveClothingItem(filesDir: File, name: String) {
        val bitmapToSave = originalBitmap.value
        val analysis = clothingAnalysisResult.value

        if (bitmapToSave == null || analysis == null) {
            _errorMessage.value = "모든 정보를 입력해주세요."
            return
        }

        val finalTemperature = if (analysis.category == "아우터") {
            analysis.suitable_temperature - 3.0
        } else {
            analysis.suitable_temperature
        }

        viewModelScope.launch(Dispatchers.IO) {
            val originalImagePath = saveBitmapToInternalStorage(bitmapToSave, "original_", filesDir)
            val processedImagePath = processedBitmap.value?.let { saveBitmapToInternalStorage(it, "processed_", filesDir) }

            if (originalImagePath != null) {
                val newClothingItem = ClothingItem(
                    name = name,
                    imageUri = originalImagePath,
                    processedImageUri = processedImagePath,
                    useProcessedImage = useProcessedImage.value ?: false,
                    category = analysis.category,
                    suitableTemperature = finalTemperature,
                    colorHex = analysis.color_hex
                )
                AppDatabase.getDatabase(getApplication()).clothingDao().insert(newClothingItem)
                withContext(Dispatchers.Main) {
                    _isSaveCompleted.value = true
                }
            }
        }
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

    private fun saveBitmapToInternalStorage(bitmap: Bitmap, prefix: String, directory: File): String? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "$prefix$timeStamp.png"
        try {
            val file = File(directory, fileName)
            val stream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.flush()
            stream.close()
            return file.absolutePath
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        }
    }
    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    fun resetSaveState() {
        _isSaveCompleted.value = false
        resetAllState()
    }

    fun resetAllState() {
        _uiState.value = UiState.IDLE
        _originalBitmap.value = null
        _processedBitmap.value = null
        _clothingAnalysisResult.value = null
        _segmentationSucceeded.value = false
        _useProcessedImage.value = false
        clothingName.value = ""
        _errorMessage.value = null
        hasChanges.value = false
    }
}