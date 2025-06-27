package com.yehyun.whatshouldiweartoday.data.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherApiService {

    // 5일/3시간 단위의 날씨 예보를 요청하는 함수
    @GET("data/2.5/forecast")
    suspend fun getFiveDayForecast(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric", // 섭씨 온도 사용
        @Query("lang") lang: String = "kr" // 한국어 설명
    ): Response<WeatherResponse>

    companion object {
        private const val BASE_URL = "https://api.openweathermap.org/"

        fun create(): WeatherApiService {
            val json = Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            }
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(WeatherApiService::class.java)
        }
    }
}
