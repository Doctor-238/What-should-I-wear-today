package com.yehyun.whatshouldiweartoday.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationTokenSource
import com.yehyun.whatshouldiweartoday.data.api.Forecast
import com.yehyun.whatshouldiweartoday.data.api.WeatherApiService
import com.yehyun.whatshouldiweartoday.data.database.AppDatabase
import com.yehyun.whatshouldiweartoday.data.database.ClothingItem
import com.yehyun.whatshouldiweartoday.data.preference.SettingsManager
import com.yehyun.whatshouldiweartoday.data.repository.ClothingRepository
import com.yehyun.whatshouldiweartoday.data.repository.WeatherRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

// ▼▼▼▼▼ 핵심 수정 1: RecommendationResult 구조 변경 ▼▼▼▼▼
data class RecommendationResult(
    val recommendedTops: List<ClothingItem>,
    val recommendedBottoms: List<ClothingItem>,
    val recommendedOuters: List<ClothingItem>,
    val bestCombination: List<ClothingItem>,
    val packableOuters: List<ClothingItem>, // 변경: 단일 객체 -> 객체 리스트
    val umbrellaRecommendation: String,
    val isTempDifferenceSignificant: Boolean
)
// ▲▲▲▲▲ 핵심 수정 끝 ▲▲▲▲▲

data class DailyWeatherSummary(
    val date: String,
    val maxTemp: Double,
    val minTemp: Double,
    val maxFeelsLike: Double,
    val minFeelsLike: Double,
    val weatherCondition: String,
    val precipitationProbability: Int
)

@SuppressLint("MissingPermission")
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val weatherRepository: WeatherRepository
    private val clothingRepository: ClothingRepository
    private val settingsManager = SettingsManager(application)
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(application)
    private var fetchJob: Job? = null
    private var cancellationTokenSource = CancellationTokenSource()

    private val _todayWeatherSummary = MutableLiveData<DailyWeatherSummary?>()
    val todayWeatherSummary: LiveData<DailyWeatherSummary?> = _todayWeatherSummary
    private val _tomorrowWeatherSummary = MutableLiveData<DailyWeatherSummary?>()
    val tomorrowWeatherSummary: LiveData<DailyWeatherSummary?> = _tomorrowWeatherSummary
    private val _todayRecommendation = MutableLiveData<RecommendationResult?>()
    val todayRecommendation: LiveData<RecommendationResult?> = _todayRecommendation
    private val _tomorrowRecommendation = MutableLiveData<RecommendationResult?>()
    val tomorrowRecommendation: LiveData<RecommendationResult?> = _tomorrowRecommendation
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _switchToTab = MutableLiveData<Int?>()
    val switchToTab: LiveData<Int?> = _switchToTab

    var permissionRequestedThisSession = false

    companion object {
        private const val SIGNIFICANT_TEMP_DIFFERENCE = 12.0
    }

    init {
        weatherRepository = WeatherRepository(WeatherApiService.create())
        val clothingDao = AppDatabase.getDatabase(application).clothingDao()
        clothingRepository = ClothingRepository(clothingDao)
    }

    fun startLoading() {
        if (_isLoading.value != true) _isLoading.value = true
    }

    fun stopLoading() {
        _isLoading.value = false
    }

    fun refreshWeatherData(apiKey: String) {
        cancellationTokenSource.cancel()
        cancellationTokenSource = CancellationTokenSource()

        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            startLoading()
            try {
                if (ActivityCompat.checkSelfPermission(getApplication(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    throw SecurityException("위치 권한이 없습니다.")
                }
                val location = getFreshLocation()
                fetchWeatherForLocation(location, apiKey)

            } catch (e: Exception) {
                handleFetchError(e)
            } finally {
                stopLoading()
            }
        }
    }

    private suspend fun getFreshLocation(): Location {
        try {
            val lastLocation = fusedLocationClient.lastLocation.await()
            if (lastLocation != null && (System.currentTimeMillis() - lastLocation.time) < 600000) {
                return lastLocation
            }
        } catch (e: Exception) {
            Log.w("HomeViewModel", "마지막 위치 가져오기 실패", e)
        }

        return fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            cancellationTokenSource.token
        ).await()
    }

    private suspend fun fetchWeatherForLocation(location: Location, apiKey: String) {
        val response = weatherRepository.getFiveDayForecast(location.latitude, location.longitude, apiKey)
        if (response.isSuccessful && response.body() != null) {
            processAndRecommend(response.body()!!)
        } else {
            val errorBody = response.errorBody()?.string() ?: "알 수 없는 오류"
            throw IOException("날씨 정보를 가져오는 데 실패했습니다. (코드: ${response.code()}) - $errorBody")
        }
    }

    private fun handleFetchError(e: Exception) {
        Log.e("HomeViewModel", "데이터 가져오기 오류", e)
        val errorMessage = when (e) {
            is SecurityException -> "날씨 정보를 보려면 위치 권한을 허용해주세요."
            is IOException -> e.message
            is com.google.android.gms.tasks.RuntimeExecutionException -> "위치를 가져올 수 없습니다. GPS를 켜고 잠시 후 다시 시도해주세요."
            else -> return
        }
        _error.postValue(errorMessage)
    }

    suspend fun processAndRecommend(weatherResponse: com.yehyun.whatshouldiweartoday.data.api.WeatherResponse) {
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val forecastsByDate = weatherResponse.list.groupBy {
            LocalDate.parse(it.dt_txt, formatter)
        }
        val todayForecasts = forecastsByDate[today] ?: emptyList()
        val tomorrowForecasts = forecastsByDate[tomorrow] ?: emptyList()
        val allClothes = clothingRepository.getAllItemsList()
        if (todayForecasts.isNotEmpty()) {
            val summary = createDailySummary(todayForecasts)
            _todayWeatherSummary.postValue(summary)
            _todayRecommendation.postValue(generateRecommendation(summary, allClothes))
        } else {
            _todayWeatherSummary.postValue(null)
            _todayRecommendation.postValue(null)
        }
        if (tomorrowForecasts.isNotEmpty()) {
            val summary = createDailySummary(tomorrowForecasts)
            _tomorrowWeatherSummary.postValue(summary)
            _tomorrowRecommendation.postValue(generateRecommendation(summary, allClothes))
        } else {
            _tomorrowWeatherSummary.postValue(null)
            _tomorrowRecommendation.postValue(null)
        }
    }

    private fun createDailySummary(forecasts: List<Forecast>): DailyWeatherSummary {
        val maxTemp = forecasts.maxOf { it.main.temp_max }
        val minTemp = forecasts.minOf { it.main.temp_min }
        val maxFeelsLike = forecasts.maxOf { it.main.feels_like }
        val minFeelsLike = forecasts.minOf { it.main.feels_like }
        val pop = (forecasts.maxOf { it.pop } * 100).toInt()
        val weatherCondition = when {
            forecasts.any { it.weather.any { w -> w.main.equals("Rain", true) } } -> "비"
            forecasts.any { it.weather.any { w -> w.main.equals("Snow", true) } } -> "눈"
            else -> forecasts.first().weather.first().description
        }
        return DailyWeatherSummary("", maxTemp, minTemp, maxFeelsLike, minFeelsLike, weatherCondition, pop)
    }

    // ▼▼▼▼▼ 핵심 수정 2: 홈 화면을 위한 추천 로직 ▼▼▼▼▼
    fun generateRecommendation(summary: DailyWeatherSummary, allClothes: List<ClothingItem>): RecommendationResult {
        val maxTempCriteria = (summary.maxTemp + summary.maxFeelsLike) / 2
        val minTempCriteria = (summary.minTemp + summary.minFeelsLike) / 2
        val temperatureTolerance = settingsManager.getTemperatureTolerance()
        val packableOuterTolerance = settingsManager.getPackableOuterTolerance()
        val constitutionAdjustment = settingsManager.getConstitutionAdjustment()

        val recommendedClothes = allClothes.filter {
            val adjustedTemp = it.suitableTemperature + constitutionAdjustment
            val itemMinTemp = adjustedTemp - temperatureTolerance
            val itemMaxTemp = adjustedTemp + temperatureTolerance
            val isFitForMaxTemp = maxTempCriteria in itemMinTemp+1..itemMaxTemp+2.5
            val isFitForHotDay = maxTempCriteria > 33 && itemMaxTemp+2.5 >= 33
            val isFitForFreezingDay = minTempCriteria < -3 && itemMinTemp+1 <= -3
            isFitForMaxTemp || isFitForHotDay || isFitForFreezingDay
        }

        val recommendedTops = recommendedClothes.filter { it.category == "상의" }
        val recommendedBottoms = recommendedClothes.filter { it.category == "하의" }
        val recommendedOuters = recommendedClothes.filter { it.category == "아우터" }.toMutableList()

        val isTempDifferenceSignificant = (maxTempCriteria - minTempCriteria) >= SIGNIFICANT_TEMP_DIFFERENCE

        // '챙겨갈 아우터'를 여러 개 찾습니다.
        val packableOuters = if (isTempDifferenceSignificant) {
            allClothes.filter { it.category == "아우터" }
                .filter {
                    val suitableTemp = it.suitableTemperature
                    val minRange = minTempCriteria
                    val maxRange = minTempCriteria + abs(packableOuterTolerance)
                    suitableTemp in minRange..maxRange
                }
        } else {
            emptyList()
        }

        // '챙겨갈 아우터'들을 기존 추천 목록에 중복되지 않게 추가합니다.
        packableOuters.forEach { po ->
            if (recommendedOuters.none { it.id == po.id }) {
                recommendedOuters.add(po)
            }
        }

        val bestTop = recommendedTops.minByOrNull { abs(it.suitableTemperature - maxTempCriteria) }
        val bestBottom = recommendedBottoms.minByOrNull { abs(it.suitableTemperature - maxTempCriteria) }
        val bestOuter = recommendedOuters.minByOrNull { abs(it.suitableTemperature - maxTempCriteria) }
        val bestCombination = listOfNotNull(bestTop, bestBottom, bestOuter)

        val umbrellaRecommendation = when {
            summary.precipitationProbability >= 70 -> "비가 올 예정이니 우산을 꼭 챙겨주세요!"
            summary.precipitationProbability >= 40 -> "비올 확률이 있어요. 우산을 챙겨주세요!"
            else -> ""
        }

        return RecommendationResult(
            recommendedTops,
            recommendedBottoms,
            recommendedOuters.sortedBy { it.suitableTemperature },
            bestCombination,
            packableOuters, // 찾은 '챙겨갈 아우터' 목록 전체를 전달
            umbrellaRecommendation,
            isTempDifferenceSignificant
        )
    }
    // ▲▲▲▲▲ 핵심 수정 끝 ▲▲▲▲▲

    fun requestTabSwitch(tabIndex: Int) {
        _switchToTab.value = tabIndex
    }

    fun onTabSwitchHandled() {
        _switchToTab.value = null
    }

    fun onErrorShown() {
        _error.value = null
    }

    override fun onCleared() {
        super.onCleared()
        cancellationTokenSource.cancel()
    }
}