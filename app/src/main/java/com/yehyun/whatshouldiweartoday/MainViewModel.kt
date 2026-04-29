package com.yehyun.whatshouldiweartoday

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.yehyun.whatshouldiweartoday.ai.AiModelProvider
import com.yehyun.whatshouldiweartoday.data.preference.SettingsManager
import com.yehyun.whatshouldiweartoday.util.Event
import com.yehyun.whatshouldiweartoday.util.isNetworkAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BodyAnalysis(
    val is_person: Boolean,
    val estimated_height: Double? = null,
    val estimated_weight: Double? = null,
    val estimated_waist: Double? = null,
    val failure_reason: String? = null
)

sealed class BodyAnalysisState {
    data class Success(val height: Float, val weight: Float, val waist: Float) : BodyAnalysisState()
    data class Error(val message: String) : BodyAnalysisState()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsManager = SettingsManager(application)

    private val _resetAllEvent = MutableLiveData<Boolean>()
    val resetAllEvent: LiveData<Boolean> = _resetAllEvent

    private val _settingsChangedEvent = MutableLiveData<Event<Unit>>()
    val settingsChangedEvent: LiveData<Event<Unit>> = _settingsChangedEvent

    private val _isBodyAnalyzing = MutableLiveData(false)
    val isBodyAnalyzing: LiveData<Boolean> = _isBodyAnalyzing

    private val _bodyAnalysisResult = MutableLiveData<Event<BodyAnalysisState>>()
    val bodyAnalysisResult: LiveData<Event<BodyAnalysisState>> = _bodyAnalysisResult

    fun triggerResetAll() {
        _resetAllEvent.value = true
    }

    fun onResetAllEventHandled() {
        _resetAllEvent.value = false
    }

    fun notifySettingsChanged() {
        _settingsChangedEvent.value = Event(Unit)
    }

    fun analyzeBodyPhoto(bitmap: Bitmap, apiKey: String) {
        if (_isBodyAnalyzing.value == true) return

        _isBodyAnalyzing.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!isNetworkAvailable(getApplication())) {
                    _bodyAnalysisResult.postValue(Event(BodyAnalysisState.Error("인터넷에 연결되어 있지 않습니다. 와이파이 또는 모바일 데이터를 확인해주세요.")))
                    _isBodyAnalyzing.postValue(false)
                    return@launch
                }
                val model = AiModelProvider.getModel(getApplication(), apiKey)
                val resizedBitmap = resizeBitmap(bitmap)
                val inputContent = com.google.ai.client.generativeai.type.content {
                    image(resizedBitmap)
                    text("""
                        You are a body type analyzer. Analyze the person in the image and estimate their height, weight, and waist circumference.
                        Your JSON response MUST contain ONLY: "is_person", "estimated_height", "estimated_weight", "estimated_waist", "failure_reason".
                        - "is_person": (boolean) True if a single person is clearly visible in a full-body shot and body proportions can be estimated. False otherwise.
                        - "estimated_height": (double) Estimated height in cm (e.g., 167.5, 173.0). null if is_person is false.
                        - "estimated_weight": (double) Estimated weight in kg (e.g., 62.0, 78.5). null if is_person is false.
                        - "estimated_waist": (double) Estimated waist circumference in cm (e.g., 72.0, 84.5). Use visual proportions at natural waistline. null if is_person is false or waistline cannot be seen clearly.
                        - "failure_reason": (string or null) If "is_person" is false, provide exactly one of these values:
                          "not_person" - no person is visible in the image
                          "not_full_body" - a person is visible but the image is not a full-body shot (e.g., only upper body, face close-up)
                          "thick_clothing" - a person is visible in full body, but wearing very thick/bulky/layered clothing that makes accurate body estimation unreliable
                          "multiple_people" - multiple people are visible in the image
                          If "is_person" is true, "failure_reason" must be null.
                        Provide realistic estimates based on visual proportions, build, and body composition.
                    """.trimIndent())
                }
                val response = model.generateContent(inputContent)
                val analysis = Json { ignoreUnknownKeys = true }.decodeFromString<BodyAnalysis>(response.text!!)

                if (analysis.is_person && analysis.estimated_height != null && analysis.estimated_weight != null) {
                    settingsManager.estimatedHeight = analysis.estimated_height.toFloat()
                    settingsManager.estimatedWeight = analysis.estimated_weight.toFloat()
                    // Waist is optional — only overwrite if AI actually provided a value.
                    val waistValue = analysis.estimated_waist?.toFloat() ?: 0f
                    if (waistValue > 0f) {
                        settingsManager.estimatedWaist = waistValue
                    }
                    _bodyAnalysisResult.postValue(
                        Event(BodyAnalysisState.Success(
                            analysis.estimated_height.toFloat(),
                            analysis.estimated_weight.toFloat(),
                            waistValue
                        ))
                    )
                    _settingsChangedEvent.postValue(Event(Unit))
                } else {
                    val errorMessage = when (analysis.failure_reason) {
                        "not_person" -> "사람이 감지되지 않았습니다. 사람이 나온 사진을 사용해주세요."
                        "not_full_body" -> "전신이 나오지 않아 체형을 판단할 수 없습니다. 전신 사진을 사용해주세요."
                        "thick_clothing" -> "두꺼운 옷을 입고 있어 정확한 체형 측정이 어렵습니다. 얇은 옷을 입은 사진을 사용해주세요."
                        "multiple_people" -> "여러 명이 감지되었습니다. 한 명만 나온 사진을 사용해주세요."
                        else -> "체형을 파악할 수 없는 사진입니다."
                    }
                    _bodyAnalysisResult.postValue(Event(BodyAnalysisState.Error(errorMessage)))
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Body analysis failed", e)
                _bodyAnalysisResult.postValue(
                    Event(BodyAnalysisState.Error("체형 분석 중 오류가 발생했습니다"))
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
}