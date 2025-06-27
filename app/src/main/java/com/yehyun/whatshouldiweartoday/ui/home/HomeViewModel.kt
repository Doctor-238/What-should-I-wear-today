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
import com.yehyun.whatshouldiweartoday.data.repository.ClothingRepository
import com.yehyun.whatshouldiweartoday.data.repository.WeatherRepository
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

// 추천 결과를 담을 데이터 클래스
data class RecommendationResult(
    val recommendedTops: List<ClothingItem>,
    val recommendedBottoms: List<ClothingItem>,
    val recommendedOuters: List<ClothingItem>,
    val bestCombination: List<ClothingItem>,
    val packableOuter: ClothingItem?,
    val umbrellaRecommendation: String
)

// 하루의 날씨 요약 정보를 담을 데이터 클래스
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

    companion object {
        private const val TEMPERATURE_TOLERANCE = 4
        private const val SIGNIFICANT_TEMP_DIFFERENCE = 10
    }

    init {
        weatherRepository = WeatherRepository(WeatherApiService.create())
        val clothingDao = AppDatabase.getDatabase(application).clothingDao()
        clothingRepository = ClothingRepository(clothingDao)
    }

    fun fetchWeatherData(latitude: Double, longitude: Double, apiKey: String) {
        viewModelScope.launch {
            try {
                val response = weatherRepository.getFiveDayForecast(latitude, longitude, apiKey)
                if (response.isSuccessful && response.body() != null) {
                    processAndRecommend(response.body()!!)
                } else {
                    // [개선] 서버가 보내준 상세 오류 메시지를 로그로 출력
                    val errorBody = response.errorBody()?.string() ?: "알 수 없는 오류"
                    Log.e("HomeViewModel", "API Error: ${response.code()} - $errorBody")
                    _error.postValue("날씨 정보를 가져오는 데 실패했습니다. (에러 코드: ${response.code()})")
                }
            } catch (e: Exception) {
                _error.postValue("네트워크 오류가 발생했습니다: ${e.message}")
            }
        }
    }

    private suspend fun processAndRecommend(weatherResponse: com.yehyun.whatshouldiweartoday.data.api.WeatherResponse) {
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        val todayForecasts = weatherResponse.list.filter { LocalDate.parse(it.dt_txt, formatter).isEqual(today) }
        val tomorrowForecasts = weatherResponse.list.filter { LocalDate.parse(it.dt_txt, formatter).isEqual(tomorrow) }

        if (todayForecasts.isNotEmpty()) {
            _todayWeatherSummary.postValue(createDailySummary(todayForecasts, "오늘"))
        }
        if (tomorrowForecasts.isNotEmpty()) {
            _tomorrowWeatherSummary.postValue(createDailySummary(tomorrowForecasts, "내일"))
        }

        // DB에서 옷 목록을 안전하게 가져옵니다.
        val allClothes = clothingRepository.getAllItemsList()

        if (todayForecasts.isNotEmpty()) {
            val summary = createDailySummary(todayForecasts, "오늘")
            _todayRecommendation.postValue(generateRecommendation(summary, allClothes))
        }
        if (tomorrowForecasts.isNotEmpty()) {
            val summary = createDailySummary(tomorrowForecasts, "내일")
            _tomorrowRecommendation.postValue(generateRecommendation(summary, allClothes))
        }
    }

    private fun createDailySummary(forecasts: List<Forecast>, dateLabel: String): DailyWeatherSummary {
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

        return DailyWeatherSummary(dateLabel, maxTemp, minTemp, maxFeelsLike, minFeelsLike, weatherCondition, pop)
    }

    private fun generateRecommendation(summary: DailyWeatherSummary, allClothes: List<ClothingItem>): RecommendationResult {
        // [수정] Double 타입의 체감 온도를 반올림하여 Int로 변환합니다.
        val roundedMaxFeelsLike = summary.maxFeelsLike.roundToInt()
        val roundedMinFeelsLike = summary.minFeelsLike.roundToInt()

        val recommendedTops = allClothes.filter {
            it.category == "상의" && it.suitableTemperature in (roundedMaxFeelsLike - TEMPERATURE_TOLERANCE)..(roundedMaxFeelsLike + TEMPERATURE_TOLERANCE)
        }
        val recommendedBottoms = allClothes.filter {
            it.category == "하의" && it.suitableTemperature in (roundedMaxFeelsLike - TEMPERATURE_TOLERANCE)..(roundedMaxFeelsLike + TEMPERATURE_TOLERANCE)
        }
        val recommendedOuters = allClothes.filter {
            it.category == "아우터" && it.suitableTemperature in (roundedMaxFeelsLike - TEMPERATURE_TOLERANCE)..(roundedMaxFeelsLike + TEMPERATURE_TOLERANCE)
        }

        val packableOuter = if ((summary.maxTemp - summary.minTemp) >= SIGNIFICANT_TEMP_DIFFERENCE) {
            allClothes
                .filter { it.category == "아우터" }
                .minByOrNull { abs(it.suitableTemperature - roundedMinFeelsLike) }
        } else {
            null
        }

        val bestTop = recommendedTops.minByOrNull { abs(it.suitableTemperature - roundedMaxFeelsLike) }
        val bestBottom = recommendedBottoms.minByOrNull { abs(it.suitableTemperature - roundedMaxFeelsLike) }
        val bestOuter = recommendedOuters.minByOrNull { abs(it.suitableTemperature - roundedMaxFeelsLike) }
        val bestCombination = listOfNotNull(bestTop, bestBottom, bestOuter)

        val umbrellaRecommendation = when {
            summary.weatherCondition == "비" && summary.precipitationProbability > 70 -> "비가 많이 와요. 큰 우산은 필수!"
            summary.weatherCondition == "비" && summary.precipitationProbability > 40 -> "비가 올 수 있으니, 우산을 챙기세요."
            else -> ""
        }

        return RecommendationResult(
            recommendedTops,
            recommendedBottoms,
            recommendedOuters,
            bestCombination,
            packableOuter,
            umbrellaRecommendation
        )
    }
}
