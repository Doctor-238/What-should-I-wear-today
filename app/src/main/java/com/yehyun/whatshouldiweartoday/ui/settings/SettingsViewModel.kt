package com.yehyun.whatshouldiweartoday.ui.settings

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.yehyun.whatshouldiweartoday.ai.AiModelProvider
import com.yehyun.whatshouldiweartoday.data.database.AppDatabase
import com.yehyun.whatshouldiweartoday.data.preference.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BodyAnalysis(
    val is_person: Boolean,
    val estimated_height: Double? = null,
    val estimated_weight: Double? = null
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsManager = SettingsManager(application)
    private val db = AppDatabase.getDatabase(application)
    private val workManager = WorkManager.getInstance(application)

    private val _isProcessing = MutableLiveData(false)
    val isProcessing: LiveData<Boolean> = _isProcessing

    private val _resetComplete = MutableLiveData(false)
    val resetComplete: LiveData<Boolean> = _resetComplete

    private val _isBodyAnalyzing = MutableLiveData(false)
    val isBodyAnalyzing: LiveData<Boolean> = _isBodyAnalyzing

    private val _bodyAnalysisResult = MutableLiveData<BodyAnalysisState>()
    val bodyAnalysisResult: LiveData<BodyAnalysisState> = _bodyAnalysisResult

    sealed class BodyAnalysisState {
        data class Success(val height: Float, val weight: Float) : BodyAnalysisState()
        data class Error(val message: String) : BodyAnalysisState()
    }

    fun analyzeBodyPhoto(bitmap: Bitmap, apiKey: String) {
        if (_isBodyAnalyzing.value == true) return

        _isBodyAnalyzing.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val model = AiModelProvider.getModel(getApplication(), apiKey)
                val resizedBitmap = resizeBitmap(bitmap)
                val inputContent = com.google.ai.client.generativeai.type.content {
                    image(resizedBitmap)
                    text("""
                        You are a body type analyzer. Analyze the person in the image and estimate their height and weight.
                        Your JSON response MUST contain ONLY: "is_person", "estimated_height", "estimated_weight".
                        - "is_person": (boolean) True if a single person is clearly visible and body proportions can be estimated. False if no person, multiple people, or body type cannot be determined.
                        - "estimated_height": (double) Estimated height in cm (e.g., 167.5, 173.0).
                        - "estimated_weight": (double) Estimated weight in kg (e.g., 62.0, 78.5).
                        Provide realistic estimates based on visual proportions, build, and body composition.
                    """.trimIndent())
                }
                val response = model.generateContent(inputContent)
                val analysis = Json { ignoreUnknownKeys = true }.decodeFromString<BodyAnalysis>(response.text!!)

                if (analysis.is_person && analysis.estimated_height != null && analysis.estimated_weight != null) {
                    settingsManager.estimatedHeight = analysis.estimated_height.toFloat()
                    settingsManager.estimatedWeight = analysis.estimated_weight.toFloat()
                    _bodyAnalysisResult.postValue(
                        BodyAnalysisState.Success(
                            analysis.estimated_height.toFloat(),
                            analysis.estimated_weight.toFloat()
                        )
                    )
                } else {
                    _bodyAnalysisResult.postValue(
                        BodyAnalysisState.Error("사람이 아니거나 체형을 파악할 수 없는 사진입니다")
                    )
                }
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Body analysis failed", e)
                _bodyAnalysisResult.postValue(
                    BodyAnalysisState.Error("체형 분석 중 오류가 발생했습니다")
                )
            } finally {
                _isBodyAnalyzing.postValue(false)
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

    fun resetAllData() {
        if (_isProcessing.value == true) return

        _isProcessing.value = true
        viewModelScope.launch {
            try {
                workManager.cancelUniqueWork("batch_add")
                db.clearAllData(getApplication())
                settingsManager.resetToDefaults()

                _resetComplete.postValue(true)
            } finally {
                _isProcessing.postValue(false)
            }
        }
    }
}
