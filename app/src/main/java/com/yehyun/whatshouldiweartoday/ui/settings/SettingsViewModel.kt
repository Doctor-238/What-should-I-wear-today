package com.yehyun.whatshouldiweartoday.ui.settings

import android.app.Application
import android.webkit.CookieManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.google.ai.client.generativeai.GenerativeModel
import com.yehyun.whatshouldiweartoday.data.database.AppDatabase
import com.yehyun.whatshouldiweartoday.data.preference.SettingsManager
import com.yehyun.whatshouldiweartoday.ui.closet.ShoppingWebViewFragment
import com.yehyun.whatshouldiweartoday.util.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsManager = SettingsManager(application)
    private val db = AppDatabase.getDatabase(application)
    private val workManager = WorkManager.getInstance(application)

    private val _isProcessing = MutableLiveData(false)
    val isProcessing: LiveData<Boolean> = _isProcessing

    private val _resetComplete = MutableLiveData(false)
    val resetComplete: LiveData<Boolean> = _resetComplete

    private val _isPurposeValidating = MutableLiveData(false)
    val isPurposeValidating: LiveData<Boolean> = _isPurposeValidating

    private val _purposeValidationResult = MutableLiveData<Event<PurposeValidationResult>>()
    val purposeValidationResult: LiveData<Event<PurposeValidationResult>> = _purposeValidationResult

    data class PurposeValidationResult(val purpose: String, val isValid: Boolean, val message: String)

    fun validateAndAddPurpose(purpose: String, apiKey: String) {
        if (_isPurposeValidating.value == true) return

        val trimmed = purpose.trim()
        if (trimmed.isEmpty()) {
            _purposeValidationResult.value = Event(PurposeValidationResult(trimmed, false, "용도를 입력해주세요."))
            return
        }

        if (settingsManager.getAllPurposes().any { it == trimmed }) {
            _purposeValidationResult.value = Event(PurposeValidationResult(trimmed, false, "이미 존재하는 용도입니다."))
            return
        }

        _isPurposeValidating.value = true
        viewModelScope.launch {
            try {
                val model = GenerativeModel(
                    modelName = settingsManager.getAiModelName(),
                    apiKey = apiKey
                )
                val result = withContext(Dispatchers.IO) {
                    val inputContent = com.google.ai.client.generativeai.type.content {
                        text("""
                            You are a clothing purpose/occasion validator.
                            Determine if the following text describes a valid clothing occasion, purpose, or situation where specific types of clothes would be worn.
                            Valid examples: "운동용", "등산용", "출근용", "파티용", "캠핑용", "여행용", "면접용"
                            Invalid examples: "ㅋㅋㅋ", "hello", "아무거나", "12345", "맛있는음식"

                            Text to validate: "$trimmed"

                            Respond with ONLY "valid" or "invalid". Nothing else.
                        """.trimIndent())
                    }
                    val response = model.generateContent(inputContent)
                    response.text?.trim()?.lowercase()
                }

                if (result?.contains("valid") == true && !result.contains("invalid")) {
                    settingsManager.addCustomPurpose(trimmed)
                    _purposeValidationResult.postValue(Event(PurposeValidationResult(trimmed, true, "'$trimmed' 용도가 추가되었습니다.")))
                } else {
                    _purposeValidationResult.postValue(Event(PurposeValidationResult(trimmed, false, "옷을 입는 용도/상황과 관련 없는 텍스트입니다.")))
                }
            } catch (e: Exception) {
                _purposeValidationResult.postValue(Event(PurposeValidationResult(trimmed, false, "AI 검증 중 오류가 발생했습니다.")))
            } finally {
                _isPurposeValidating.postValue(false)
            }
        }
    }

    fun resetAllData() {
        if (_isProcessing.value == true) return

        _isProcessing.value = true
        viewModelScope.launch {
            try {
                workManager.cancelUniqueWork("batch_add")
                db.clearAllData()
                settingsManager.resetToDefaults()

                // Clear WebView login cookies and saved page states
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                ShoppingWebViewFragment.clearWebViewStates()

                _resetComplete.postValue(true)
            } finally {
                _isProcessing.postValue(false)
            }
        }
    }
}
