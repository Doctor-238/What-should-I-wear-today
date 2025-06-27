package com.yehyun.whatshouldiweartoday.ui.home

import android.app.Application
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
    val precipitationProbability: Int // 강수 확률 (%)
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    // --- Repositories ---
    private val weatherRepository: WeatherRepository
    private val clothingRepository: ClothingRepository

    // --- LiveData ---
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

    // --- 추천 로직을 위한 상수 ---
    companion object {
        private const val TEMPERATURE_TOLERANCE = 4 // 추천 온도 범위 (±4도)
        private const val SIGNIFICANT_TEMP_DIFFERENCE = 10 // 일교차 기준 (10도 이상)
    }

    init {
        // Repository 초기화
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
                    _error.postValue("날씨 정보를 가져오는 데 실패했습니다. (에러 코드: ${response.code()})")
                }
            } catch (e: Exception) {
                _error.postValue("네트워크 오류가 발생했습니다: ${e.message}")
            }
        }
    }

    // [구현] 날씨 정보를 가공하고, 옷 추천 로직을 실행하는 메인 함수
    private fun processAndRecommend(weatherResponse: com.yehyun.whatshouldiweartoday.data.api.WeatherResponse) {
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        // 오늘과 내일의 예보를 분리
        val todayForecasts = weatherResponse.list.filter { LocalDate.parse(it.dt_txt, formatter).isEqual(today) }
        val tomorrowForecasts = weatherResponse.list.filter { LocalDate.parse(it.dt_txt, formatter).isEqual(tomorrow) }

        // 오늘과 내일의 날씨 요약 정보 생성 및 LiveData 업데이트
        if (todayForecasts.isNotEmpty()) {
            _todayWeatherSummary.postValue(createDailySummary(todayForecasts, "오늘"))
        }
        if (tomorrowForecasts.isNotEmpty()) {
            _tomorrowWeatherSummary.postValue(createDailySummary(tomorrowForecasts, "내일"))
        }

        // 전체 옷 목록을 DB에서 가져옴 (한 번만)
        val allClothes = clothingRepository.getItems("전체", "", "최신순").value ?: emptyList()

        // 오늘과 내일의 추천 생성
        if (todayForecasts.isNotEmpty()) {
            val summary = createDailySummary(todayForecasts, "오늘")
            _todayRecommendation.postValue(generateRecommendation(summary, allClothes))
        }
        if (tomorrowForecasts.isNotEmpty()) {
            val summary = createDailySummary(tomorrowForecasts, "내일")
            _tomorrowRecommendation.postValue(generateRecommendation(summary, allClothes))
        }
    }

    // 3시간 단위 예보 목록으로 하루 날씨 요약을 만드는 함수
    private fun createDailySummary(forecasts: List<Forecast>, dateLabel: String): DailyWeatherSummary {
        val maxTemp = forecasts.maxOf { it.main.temp_max }
        val minTemp = forecasts.minOf { it.main.temp_min }
        val maxFeelsLike = forecasts.maxOf { it.main.feels_like }
        val minFeelsLike = forecasts.minOf { it.main.feels_like }
        val pop = (forecasts.maxOf { it.pop } * 100).toInt()

        // 날씨 상태 결정 (비나 눈이 한 번이라도 오면 우선)
        val weatherCondition = when {
            forecasts.any { it.weather.any { w -> w.main.equals("Rain", true) } } -> "비"
            forecasts.any { it.weather.any { w -> w.main.equals("Snow", true) } } -> "눈"
            else -> forecasts.first().weather.first().description // 그 외에는 첫 시간의 날씨로
        }

        return DailyWeatherSummary(dateLabel, maxTemp, minTemp, maxFeelsLike, minFeelsLike, weatherCondition, pop)
    }

    // 하루 날씨 요약과 전체 옷 목록으로 추천 결과를 생성하는 함수
    private fun generateRecommendation(summary: DailyWeatherSummary, allClothes: List<ClothingItem>): RecommendationResult {
        // 1. 분류별 옷 추천
        val recommendedTops = allClothes.filter {
            it.category == "상의" && it.suitableTemperature in (summary.maxFeelsLike - TEMPERATURE_TOLERANCE)..(summary.maxFeelsLike + TEMPERATURE_TOLERANCE)
        }
        val recommendedBottoms = allClothes.filter {
            it.category == "하의" && it.suitableTemperature in (summary.maxFeelsLike - TEMPERATURE_TOLERANCE)..(summary.maxFeelsLike + TEMPERATURE_TOLERANCE)
        }
        val recommendedOuters = allClothes.filter {
            it.category == "아우터" && it.suitableTemperature in (summary.maxFeelsLike - TEMPERATURE_TOLERANCE)..(summary.maxFeelsLike + TEMPERATURE_TOLERANCE)
        }

        // 2. 챙겨갈 아우터 추천
        val packableOuter = if ((summary.maxTemp - summary.minTemp) >= SIGNIFICANT_TEMP_DIFFERENCE) {
            allClothes
                .filter { it.category == "아우터" }
                .minByOrNull { abs(it.suitableTemperature - summary.minFeelsLike) }
        } else {
            null
        }

        // 3. 최적의 코디 조합
        val bestTop = recommendedTops.minByOrNull { abs(it.suitableTemperature - summary.maxFeelsLike) }
        val bestBottom = recommendedBottoms.minByOrNull { abs(it.suitableTemperature - summary.maxFeelsLike) }
        val bestOuter = recommendedOuters.minByOrNull { abs(it.suitableTemperature - summary.maxFeelsLike) }
        val bestCombination = listOfNotNull(bestTop, bestBottom, bestOuter)

        // 4. 우산 추천
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