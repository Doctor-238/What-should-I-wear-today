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
import com.yehyun.whatshouldiweartoday.util.PERCEPTUAL_HASH_THRESHOLD
import com.yehyun.whatshouldiweartoday.util.computePerceptualHash
import com.yehyun.whatshouldiweartoday.util.hammingDistance
import com.yehyun.whatshouldiweartoday.util.isNetworkAvailable
import com.yehyun.whatshouldiweartoday.util.trimBorders
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
    val purposeList = MutableLiveData<List<String>>(emptyList())
    val sizeLabelText = MutableLiveData<String>()

    private val settingsManager = SettingsManager(application)
    private var generativeModel: GenerativeModel? = null

    private val _analysisResult = MutableLiveData<ClothingAnalysis?>()
    val analysisResult: LiveData<ClothingAnalysis?> = _analysisResult
    private var initialAnalysisResult: ClothingAnalysis? = null

    private var processedImageJob: Deferred<Bitmap?>? = null
    private var originalImageJob: Deferred<Bitmap>? = null
    private var isImageProcessing = false
    private var currentImageHash: String? = null

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
        if (!isNetworkAvailable(getApplication())) {
            _errorMessage.value = "인터넷에 연결되어 있지 않습니다. 와이파이 또는 모바일 데이터를 확인해주세요."
            return
        }
        resetAllState()
        val trimmed = trimBorders(bitmap)
        _originalBitmap.value = trimmed
        generativeModel = AiModelProvider.getModel(getApplication(), apiKey)
        isImageProcessing = true

        _isAiAnalyzing.value = true

        viewModelScope.launch {
            val analysisJob = async(Dispatchers.IO) { analyzeImageWithRetry(trimmed) }
            val hashJob = async(Dispatchers.IO) { computePerceptualHash(trimmed) }
            processedImageJob = async(Dispatchers.IO) { createProcessedBitmap(trimmed) }
            originalImageJob = async(Dispatchers.Default) { trimmed }

            currentImageHash = hashJob.await()

            val existingHashes = withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(getApplication()).clothingDao().getAllImageHashes()
            }
            if (existingHashes.any { hammingDistance(currentImageHash!!, it) <= PERCEPTUAL_HASH_THRESHOLD }) {
                analysisJob.cancel()
                withContext(Dispatchers.Main) {
                    _isAiAnalyzing.value = false
                    _errorMessage.value = "이미 옷장에 등록된 사진입니다."
                    resetAllState()
                }
                return@launch
            }

            val result = analysisJob.await()
            _isAiAnalyzing.postValue(false)

            if (result == null) {
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "분석 중 오류가 발생했습니다. 다시 시도해주세요."
                    resetAllState()
                }
            } else if (!result.is_wearable) {
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "의류가 감지되지 않았습니다. 의류 사진을 사용해주세요."
                    resetAllState()
                }
            } else if ((result.clothing_area_ratio ?: 1.0) < 0.1) {
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "의류가 너무 작거나 일부만 보입니다.\n의류 전체가 잘 보이는 사진을 사용해주세요."
                    resetAllState()
                }
            } else if ((result.clothing_completeness_ratio ?: 1.0) < 0.3) {
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "옷의 일부분만 보입니다.\n옷 전체가 나온 사진을 사용해주세요."
                    resetAllState()
                }
            } else {
                initialAnalysisResult = result.copy()
                purposeList.postValue(result.purposes?.take(2) ?: emptyList())
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

    fun saveClothingItem(name: String, selectedSize: String? = null) {
        viewModelScope.launch {
            if (_analysisResult.value == null) {
                _errorMessage.value = "AI 분석 결과가 없습니다."
                return@launch
            }
            val hash = currentImageHash
            if (hash != null) {
                val isDuplicate = withContext(Dispatchers.IO) {
                    val existing = AppDatabase.getDatabase(getApplication()).clothingDao().getAllImageHashes()
                    existing.any { hammingDistance(hash, it) <= PERCEPTUAL_HASH_THRESHOLD }
                }
                if (isDuplicate) {
                    _errorMessage.value = "이미 옷장에 등록된 사진입니다."
                    return@launch
                }
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
                val initialAnalysis = initialAnalysisResult ?: analysis
                val currentBaseTemp = analysis.suitable_temperature!!
                val initialBaseTemp = initialAnalysis.suitable_temperature ?: currentBaseTemp
                val currentCategory = analysis.category ?: initialAnalysis.category ?: "기타"
                val initialCategory = initialAnalysis.category ?: currentCategory
                val finalTemp = defaultTemperatureForCategory(currentCategory, currentBaseTemp)

                val purposeStr = purposeList.value?.joinToString(",") ?: ""
                val savedSize = selectedSize.takeUnless { it == sizeLabelText.value }

                val safeColorHex = analysis.color_hex?.takeIf { isValidHexCode(it) } ?: "#000000"
                val newClothingItem = ClothingItem(
                    name = name,
                    imageUri = originalPath,
                    processedImageUri = processedPath,
                    useProcessedImage = _useProcessedImage.value ?: false,
                    category = currentCategory,
                    suitableTemperature = finalTemp,
                    baseTemperature = initialBaseTemp,
                    colorHex = safeColorHex,
                    fitMinHeight = analysis.fit_min_height,
                    fitMaxHeight = analysis.fit_max_height,
                    fitMinWeight = analysis.fit_min_weight,
                    fitMaxWeight = analysis.fit_max_weight,
                    fitMinWaist = analysis.fit_min_waist,
                    fitMaxWaist = analysis.fit_max_waist,
                    purpose = purposeStr,
                    size = savedSize,
                    aiCategory = initialCategory,
                    imageHash = currentImageHash
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
        val allPurposes = settingsManager.getAllPurposes()
        val purposeListStr = allPurposes.joinToString("', '", "'", "'")
        while (attempt < maxRetries) {
            try {
                val resizedBitmap = resizeBitmap(bitmap)
                val inputContent = com.google.ai.client.generativeai.type.content {
                    image(resizedBitmap)
                    text("""
                        You are a Precise Climate & Fashion Analyst for Korean weather.
                        Your task is to analyze the clothing item in the image and provide a detailed analysis in a strict JSON format, without any additional text or explanations.
                        Your JSON response MUST contain ONLY the following keys: "is_wearable", "category", "suitable_temperature", "color_hex", "fit_min_height", "fit_max_height", "fit_min_weight", "fit_max_weight", "fit_min_waist", "fit_max_waist", "purposes", "clothing_area_ratio", "clothing_completeness_ratio".
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
                        - "clothing_area_ratio": (double) Estimate the fraction (0.0 to 1.0) of the total image area occupied by the main clothing item. A close-up shot filling the frame = 0.7-0.9. A small item in a wider scene = 0.1-0.3. Only a tiny corner or fragment visible = 0.05 or less.
                        - "clothing_completeness_ratio": (double) Estimate the fraction (0.0 to 1.0) of the FULL clothing item that is actually visible in the image, regardless of how large it appears. A fully visible garment = 0.9-1.0. Heavily cropped but most is visible = 0.5-0.8. Only a collar, sleeve, hem, or small detail = 0.1-0.2. Only an extreme fragment = 0.05 or less.
                        Estimate the body size range this clothing would fit. For free-size/stretchy items, use wider ranges. Base on visual cues: size labels, proportions, material stretch. Waist range should be the user's waist circumference, not the garment's flat measurement.
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
            sizeLabelText.postValue("")
            return
        }

        val category = result.category ?: "기타"
        categoryText.postValue(category)

        if (settingsManager.bodyFitEnabled) {
            if (settingsManager.isBodyRegistered) {
                val level = calculateFitLevel(
                    settingsManager.estimatedHeight, settingsManager.estimatedWeight, settingsManager.estimatedWaist,
                    result.fit_min_height, result.fit_max_height,
                    result.fit_min_weight, result.fit_max_weight,
                    result.fit_min_waist, result.fit_max_waist
                )
                fitLevelText.postValue(level)
            } else {
                fitLevelText.postValue("설정에서 사이즈를 등록해주세요")
            }
        } else {
            fitLevelText.postValue("")
        }

        if (category in SIZE_CATEGORIES) {
            val sizeLabel = calculateItemSizeLabel(
                category,
                result.fit_min_height, result.fit_max_height,
                result.fit_min_weight, result.fit_max_weight,
                result.fit_min_waist, result.fit_max_waist,
                settingsManager.sizeNotationType
            )
            sizeLabelText.postValue(sizeLabel ?: "")
        } else {
            sizeLabelText.postValue("")
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

        // purposeList is set only during onImageSelected; refreshDisplayWithNewSettings does not reset it
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

    fun updatePurposes(list: List<String>) { purposeList.value = list }

    fun getAvailablePurposes(): List<String> = settingsManager.getAllPurposes()

    fun resetAllState() {
        processedImageJob?.cancel()
        originalImageJob?.cancel()
        isImageProcessing = false
        currentImageHash = null

        _originalBitmap.value = null
        _processedBitmap.value = null
        _analysisResult.value = null
        initialAnalysisResult = null
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
        purposeList.value = emptyList()
        sizeLabelText.value = ""
    }

    companion object {
        const val FIT_VERY_GOOD = "매우 적합"
        const val FIT_GOOD = "적합"
        const val FIT_NORMAL = "보통"
        const val FIT_BAD = "맞지않음"
        const val FIT_VERY_BAD = "매우 맞지않음"
        const val FIT_NO_INFO = "정보 없음"

        // Score a single body axis: 4 = inner band (매우 적합), 3 = within range,
        // 2 = within ~5 units of range, 1 = within ~10 units, 0 = far off.
        // Returns null if the clothing does not define this axis (treat as neutral).
        private fun axisScore(userValue: Double, min: Double?, max: Double?, nearMargin: Double, farMargin: Double): Int? {
            if (min == null || max == null) return null
            val range = max - min
            val shrink = range * 0.15
            return when {
                userValue in (min + shrink)..(max - shrink) -> 4
                userValue in min..max -> 3
                userValue in (min - nearMargin)..(max + nearMargin) -> 2
                userValue in (min - farMargin)..(max + farMargin) -> 1
                else -> 0
            }
        }

        // Weighted fit level. Height and weight are primary (weight 0.4 each),
        // waist is supplementary (0.2) and only counts when both the user has
        // registered a waist AND the clothing defines a waist range.
        // If only one of those holds, waist is skipped and height/weight renormalize to 0.5 each.
        fun calculateFitLevel(
            userHeight: Float, userWeight: Float, userWaist: Float,
            minH: Double?, maxH: Double?,
            minW: Double?, maxW: Double?,
            minWaist: Double?, maxWaist: Double?
        ): String {
            val hScore = axisScore(userHeight.toDouble(), minH, maxH, 5.0, 10.0)
            val wScore = axisScore(userWeight.toDouble(), minW, maxW, 5.0, 10.0)
            if (hScore == null || wScore == null) return FIT_NO_INFO

            val waistKnown = userWaist > 0f
            val waistScore = if (waistKnown) axisScore(userWaist.toDouble(), minWaist, maxWaist, 4.0, 8.0) else null

            val combined = if (waistScore != null) {
                hScore * 0.4 + wScore * 0.4 + waistScore * 0.2
            } else {
                hScore * 0.5 + wScore * 0.5
            }

            // Gate the top band so that both primary axes are at least within-range.
            val primaryInRange = hScore >= 3 && wScore >= 3
            // Waist being very wrong should pull down "매우 적합" but not dominate.
            val waistBad = waistScore != null && waistScore <= 1

        return when {
            combined >= 3.7 && primaryInRange && !waistBad -> FIT_VERY_GOOD
                combined >= 3.0 && primaryInRange -> FIT_GOOD
                combined >= 2.0 -> FIT_NORMAL
                combined >= 1.0 -> FIT_BAD
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

        fun defaultTemperatureForCategory(category: String, baseTemperature: Double): Double {
            return when (category) {
                "아우터" -> baseTemperature - 3.0
                "상의", "하의" -> baseTemperature + 2.0
                else -> baseTemperature
            }
        }

        // 상의/아우터: (height-155)/5 + (weight-45)/8 로 사이즈 스코어 계산
        private fun topSizeScore(height: Double, weight: Double) =
            (height - 155.0) / 5.0 + (weight - 45.0) / 8.0

        private fun topLetterSize(height: Double, weight: Double): String {
            val s = topSizeScore(height, weight)
            return when { s < 1.1 -> "XS"; s < 2.9 -> "S"; s < 4.8 -> "M"; s < 6.7 -> "L"; s < 8.7 -> "XL"; else -> "XXL" }
        }

        private fun topNumericSize(height: Double, weight: Double): String {
            val s = topSizeScore(height, weight)
            return when { s < 1.1 -> "85"; s < 2.9 -> "90"; s < 4.8 -> "95"; s < 6.7 -> "100"; s < 8.7 -> "105"; else -> "110" }
        }

        // 하의: 허리 우선, 없으면 체중으로 추정
        private fun bottomWaistEstimate(weight: Double) = weight * 1.0 + 12.0

        private fun bottomLetterSize(weight: Double, waist: Double?): String {
            val w = waist ?: bottomWaistEstimate(weight)
            return when { w < 65.0 -> "XS"; w < 70.0 -> "S"; w < 77.0 -> "M"; w < 84.0 -> "L"; w < 91.0 -> "XL"; else -> "XXL" }
        }

        private fun bottomNumericSize(weight: Double, waist: Double?): String {
            val w = waist ?: bottomWaistEstimate(weight)
            return when {
                w < 67.0 -> "26"; w < 70.0 -> "27"; w < 73.0 -> "28"; w < 76.0 -> "29"
                w < 79.0 -> "30"; w < 82.0 -> "31"; w < 85.0 -> "32"; w < 88.0 -> "33"; else -> "34"
            }
        }

        /**
         * 카테고리·신체 치수·표기 방식으로 사이즈 레이블 반환.
         * 상의/아우터 → letter(XS~XXL) 또는 numeric(85~110)
         * 하의 → letter(XS~XXL) 또는 numeric(26~34)
         * 그 외 → null (표시하지 않음)
         */
        fun calculateSizeLabel(
            category: String,
            userHeight: Float,
            userWeight: Float,
            userWaist: Float,
            notationType: String
        ): String? {
            val h = userHeight.toDouble()
            val w = userWeight.toDouble()
            val waist = if (userWaist > 0f) userWaist.toDouble() else null
            return when {
                category in listOf("상의", "아우터") ->
                    if (notationType == com.yehyun.whatshouldiweartoday.data.preference.SettingsManager.SIZE_NOTATION_NUMERIC)
                        topNumericSize(h, w) else topLetterSize(h, w)
                category == "하의" ->
                    if (notationType == com.yehyun.whatshouldiweartoday.data.preference.SettingsManager.SIZE_NOTATION_NUMERIC)
                        bottomNumericSize(w, waist) else bottomLetterSize(w, waist)
                else -> null
            }
        }

        /**
         * 옷 자체의 fit range 중간값으로 사이즈 레이블 계산.
         * 사용자 신체 측정값과 무관하게 각 옷마다 고유한 사이즈를 반환.
         */
        fun calculateItemSizeLabel(
            category: String,
            fitMinHeight: Double?, fitMaxHeight: Double?,
            fitMinWeight: Double?, fitMaxWeight: Double?,
            fitMinWaist: Double?, fitMaxWaist: Double?,
            notationType: String
        ): String? {
            if (category !in SIZE_CATEGORIES) return null
            // 20분위수(하위 20%) 사용: AI fit range는 실제 레이블보다 넓게 잡히는 경향이 있어
            // 단순 중간값은 한 치수 크게 나옴. 하위 20% 지점이 해당 옷의 실제 사이즈에 가장 근접.
            return when {
                category in listOf("상의", "아우터") -> {
                    val minH = fitMinHeight ?: return null
                    val maxH = fitMaxHeight ?: return null
                    val minW = fitMinWeight ?: return null
                    val maxW = fitMaxWeight ?: return null
                    val h = minH + (maxH - minH) * 0.20
                    val w = minW + (maxW - minW) * 0.20
                    if (notationType == com.yehyun.whatshouldiweartoday.data.preference.SettingsManager.SIZE_NOTATION_NUMERIC)
                        topNumericSize(h, w) else topLetterSize(h, w)
                }
                category == "하의" -> {
                    val waist = if (fitMinWaist != null && fitMaxWaist != null)
                        fitMinWaist + (fitMaxWaist - fitMinWaist) * 0.20 else null
                    val minW = fitMinWeight ?: return null
                    val maxW = fitMaxWeight ?: return null
                    val w = minW + (maxW - minW) * 0.20
                    if (notationType == com.yehyun.whatshouldiweartoday.data.preference.SettingsManager.SIZE_NOTATION_NUMERIC)
                        bottomNumericSize(w, waist) else bottomLetterSize(w, waist)
                }
                else -> null
            }
        }

        fun getSizeList(category: String, notationType: String): List<String> {
            return when {
                category in listOf("상의", "아우터") ->
                    if (notationType == com.yehyun.whatshouldiweartoday.data.preference.SettingsManager.SIZE_NOTATION_NUMERIC)
                        listOf("85", "90", "95", "100", "105", "110")
                    else listOf("XS", "S", "M", "L", "XL", "XXL")
                category == "하의" ->
                    if (notationType == com.yehyun.whatshouldiweartoday.data.preference.SettingsManager.SIZE_NOTATION_NUMERIC)
                        listOf("26", "27", "28", "29", "30", "31", "32", "33", "34")
                    else listOf("XS", "S", "M", "L", "XL", "XXL")
                else -> emptyList()
            }
        }

        val SIZE_CATEGORIES = setOf("상의", "하의", "아우터")
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
        val maskWidth = mask.width
        val maskHeight = mask.height
        val n = maskWidth * maskHeight
        val sensitivity = settingsManager.getBackgroundSensitivityValue()

        // Build foreground boolean mask
        val fg = BooleanArray(n)
        val buf = mask.buffer
        for (i in 0 until n) fg[i] = buf.float > sensitivity
        buf.rewind()

        // BFS pass 1: find start index of largest connected component
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

        // BFS pass 2: mark only the largest component pixels
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

        // Apply: keep only pixels belonging to the largest component
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
