package com.yehyun.whatshouldiweartoday.ui.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.yehyun.whatshouldiweartoday.data.api.Forecast
import com.yehyun.whatshouldiweartoday.data.api.WeatherApiService
import com.yehyun.whatshouldiweartoday.data.database.AppDatabase
import com.yehyun.whatshouldiweartoday.data.database.ClothingItem
import com.yehyun.whatshouldiweartoday.data.preference.SettingsManager
import com.yehyun.whatshouldiweartoday.data.repository.ClothingRepository
import com.yehyun.whatshouldiweartoday.data.repository.WeatherRepository
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

data class RecommendationResult(
    val recommendedTops: List<ClothingItem>,
    val recommendedBottoms: List<ClothingItem>,
    val recommendedOuters: List<ClothingItem>,
    val bestCombination: List<ClothingItem>,
    val packableOuter: ClothingItem?,
    val umbrellaRecommendation: String,
    val isTempDifferenceSignificant: Boolean
)

data class DailyWeatherSummary(
    val date: String,
    val maxTemp: Double,
    val minTemp: Double,
    val maxFeelsLike: Double,
    val minFeelsLike: Double,
    val weatherCondition: String,
    val precipitationProbability: Int
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val weatherRepository: WeatherRepository
    private val clothingRepository: ClothingRepository

    private val _todayWeatherSummary = MutableLiveData<DailyWeatherSummary>()
    val todayWeatherSummary: LiveData<DailyWeatherSummary> = _todayWeatherSummary
    private val _tomorrowWeatherSummary = MutableLiveData<DailyWeatherSummary>()
    val tomorrowWeatherSummary: LiveData<DailyWeatherSummary> = _tomorrowWeatherSummary
    private val _todayRecommendation = MutableLiveData<RecommendationResult>()
    val todayRecommendation: LiveData<RecommendationResult> = _todayRecommendation
    private val _tomorrowRecommendation = MutableLiveData<RecommendationResult>()
    val tomorrowRecommendation: LiveData<RecommendationResult> = _tomorrowRecommendation
    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    private val _isRecommendationScrolledToTop = MutableLiveData(true)
    val isRecommendationScrolledToTop: LiveData<Boolean> = _isRecommendationScrolledToTop
    private val settingsManager = SettingsManager(application)

    companion object {
        private const val SIGNIFICANT_TEMP_DIFFERENCE = 12.0
    }

    fun setScrollState(isAtTop: Boolean) {
        if (_isRecommendationScrolledToTop.value != isAtTop) {
            _isRecommendationScrolledToTop.value = isAtTop
        }
    }

    init {
        weatherRepository = WeatherRepository(WeatherApiService.create())
        val clothingDao = AppDatabase.getDatabase(application).clothingDao()
        clothingRepository = ClothingRepository(clothingDao)
    }

    fun fetchWeatherData(latitude: Double, longitude: Double, apiKey: String) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val response = weatherRepository.getFiveDayForecast(latitude, longitude, apiKey)
                if (response.isSuccessful && response.body() != null) {
                    processAndRecommend(response.body()!!)
                } else {
                    val errorBody = response.errorBody()?.string() ?: "알 수 없는 오류"
                    Log.e("HomeViewModel", "API Error: ${response.code()} - $errorBody")
                    _error.postValue("날씨 정보를 가져오는 데 실패했습니다. (코드: ${response.code()})")
                }
            } catch (e: Exception) {
                _error.postValue("네트워크 오류가 발생했습니다: ${e.message}")
            } finally {
                _isLoading.postValue(false)
            }
        }
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
        }
        if (tomorrowForecasts.isNotEmpty()) {
            val summary = createDailySummary(tomorrowForecasts)
            _tomorrowWeatherSummary.postValue(summary)
            _tomorrowRecommendation.postValue(generateRecommendation(summary, allClothes))
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

    fun generateRecommendation(summary: DailyWeatherSummary, allClothes: List<ClothingItem>): RecommendationResult {
        val maxTempCriteria = (summary.maxTemp + summary.maxFeelsLike) / 2
        val minTempCriteria = (summary.minTemp + summary.minFeelsLike) / 2
        val temperatureTolerance = settingsManager.getTemperatureTolerance()
        val packableOuterTolerance = settingsManager.getPackableOuterTolerance()


        val recommendedClothes = allClothes.filter {
            val itemMinTemp = it.suitableTemperature - temperatureTolerance
            val itemMaxTemp = it.suitableTemperature + temperatureTolerance
            val isFitForMaxTemp = maxTempCriteria in itemMinTemp..itemMaxTemp
            val isFitForHotDay = maxTempCriteria > 30 && itemMaxTemp >= 30
            val isFitForFreezingDay = minTempCriteria < 0 && itemMinTemp <= 0
            isFitForMaxTemp || isFitForHotDay || isFitForFreezingDay
        }


        val recommendedTops = recommendedClothes.filter { it.category == "상의" }
        val recommendedBottoms = recommendedClothes.filter { it.category == "하의" }
        val recommendedOuters = recommendedClothes.filter { it.category == "아우터" }

        val isTempDifferenceSignificant = (maxTempCriteria - minTempCriteria) >= SIGNIFICANT_TEMP_DIFFERENCE
        val packableOuter = if (isTempDifferenceSignificant) {
            allClothes.filter { it.category == "아우터" }
                .filter {
                    // [수정] 설정에서 가져온 챙겨갈 아우터 범위 적용
                    val tempRangeForMin = (it.suitableTemperature + packableOuterTolerance)..it.suitableTemperature
                    tempRangeForMin.contains(minTempCriteria)
                }
                .minByOrNull { abs(it.suitableTemperature - minTempCriteria) }
        } else {
            null
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

        return RecommendationResult(recommendedTops, recommendedBottoms, recommendedOuters, bestCombination, packableOuter, umbrellaRecommendation, isTempDifferenceSignificant)
    }
}