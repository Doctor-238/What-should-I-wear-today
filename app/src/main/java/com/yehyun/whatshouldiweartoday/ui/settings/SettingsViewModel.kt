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

    private val _isApiKeyValidating = MutableLiveData(false)
    val isApiKeyValidating: LiveData<Boolean> = _isApiKeyValidating

    private val _isOpenWeatherApiKeyValidating = MutableLiveData(false)
    val isOpenWeatherApiKeyValidating: LiveData<Boolean> = _isOpenWeatherApiKeyValidating

    sealed class ApiKeyResult {
        data class Success(val isCustomKey: Boolean) : ApiKeyResult()
        data class Error(val message: String) : ApiKeyResult()
    }

    private val _apiKeyResult = MutableLiveData<Event<ApiKeyResult>>()
    val apiKeyResult: LiveData<Event<ApiKeyResult>> = _apiKeyResult

    private val _openWeatherApiKeyResult = MutableLiveData<Event<ApiKeyResult>>()
    val openWeatherApiKeyResult: LiveData<Event<ApiKeyResult>> = _openWeatherApiKeyResult

    fun validateAndSaveApiKey(apiKey: String) {
        if (_isApiKeyValidating.value == true) return
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isEmpty()) {
            _apiKeyResult.value = Event(ApiKeyResult.Error("API 키를 입력해주세요."))
            return
        }
        _isApiKeyValidating.value = true
        viewModelScope.launch {
            try {
                val model = GenerativeModel(
                    modelName = settingsManager.getAiModelName(),
                    apiKey = trimmedKey
                )
                withContext(Dispatchers.IO) {
                    val content = com.google.ai.client.generativeai.type.content { text("hi") }
                    model.generateContent(content)
                }
                settingsManager.customGeminiApiKey = trimmedKey
                _apiKeyResult.postValue(Event(ApiKeyResult.Success(true)))
            } catch (e: Exception) {
                _apiKeyResult.postValue(Event(ApiKeyResult.Error(categorizeApiKeyError(e))))
            } finally {
                _isApiKeyValidating.postValue(false)
            }
        }
    }

    fun resetApiKeyToDefault() {
        settingsManager.customGeminiApiKey = ""
        _apiKeyResult.value = Event(ApiKeyResult.Success(false))
    }

    fun validateAndSaveOpenWeatherApiKey(apiKey: String) {
        if (_isOpenWeatherApiKeyValidating.value == true) return
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isEmpty()) {
            _openWeatherApiKeyResult.value = Event(ApiKeyResult.Error("API 키를 입력해주세요."))
            return
        }
        _isOpenWeatherApiKeyValidating.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val url = java.net.URL("https://api.openweathermap.org/data/2.5/weather?q=Seoul&appid=\$trimmedKey")
                    val connection = url.openConnection() as java.net.HttpURLConnection
                    connection.requestMethod = "GET"
                    val responseCode = connection.responseCode
                    if (responseCode == 200) {
                        settingsManager.customOpenWeatherApiKey = trimmedKey
                        _openWeatherApiKeyResult.postValue(Event(ApiKeyResult.Success(true)))
                    } else if (responseCode == 401) {
                         _openWeatherApiKeyResult.postValue(Event(ApiKeyResult.Error("유효하지 않은 API 키입니다.")))
                    } else {
                        _openWeatherApiKeyResult.postValue(Event(ApiKeyResult.Error("서버 오류: \$responseCode")))
                    }
                }
            } catch (e: Exception) {
                _openWeatherApiKeyResult.postValue(Event(ApiKeyResult.Error("네트워크 또는 검증 실패: \${e.message}")))
            } finally {
                _isOpenWeatherApiKeyValidating.postValue(false)
            }
        }
    }

    fun resetOpenWeatherApiKeyToDefault() {
        settingsManager.customOpenWeatherApiKey = ""
        _openWeatherApiKeyResult.value = Event(ApiKeyResult.Success(false))
    }

    private fun categorizeApiKeyError(e: Exception): String {
        val msg = (e.message ?: e.toString()).lowercase()
        return when {
            msg.contains("api_key_invalid") || msg.contains("api key not valid") ||
            msg.contains("invalid api key") || msg.contains("invalid_argument") && msg.contains("key") ->
                "유효하지 않은 API 키입니다. Gemini API 키가 맞는지 확인해주세요."
            msg.contains("resource_exhausted") || msg.contains("quota") || msg.contains("429") ->
                "API 할당량(quota)이 초과된 키입니다. 잠시 후 다시 시도해주세요."
            msg.contains("permission_denied") || msg.contains("403") ->
                "API 키 접근 권한이 없습니다. Google AI Studio에서 Gemini API 활성화 여부를 확인해주세요."
            msg.contains("404") || msg.contains("not_found") ->
                "모델을 찾을 수 없습니다. API 키가 유효한지 다시 확인해주세요."
            msg.contains("unknownhostexception") || msg.contains("timeout") || msg.contains("network") ->
                "네트워크 오류입니다. 인터넷 연결을 확인해주세요."
            else -> "키 검증 실패: ${e.javaClass.simpleName}"
        }
    }

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
