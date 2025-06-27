package com.yehyun.whatshouldiweartoday.data.repository

import com.yehyun.whatshouldiweartoday.data.api.WeatherApiService
import com.yehyun.whatshouldiweartoday.data.api.WeatherResponse
import retrofit2.Response

class WeatherRepository(private val weatherApiService: WeatherApiService) {
    suspend fun getFiveDayForecast(lat: Double, lon: Double, apiKey: String): Response<WeatherResponse> {
        return weatherApiService.getFiveDayForecast(lat, lon, apiKey)
    }
}