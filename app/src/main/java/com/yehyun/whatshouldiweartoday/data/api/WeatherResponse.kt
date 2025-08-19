package com.yehyun.whatshouldiweartoday.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WeatherResponse(
    @SerialName("list")
    val list: List<Forecast>
)

@Serializable
data class Forecast(
    @SerialName("dt_txt")
    val dt_txt: String,
    @SerialName("main")
    val main: Main,
    @SerialName("weather")
    val weather: List<Weather>,
    @SerialName("pop")
    val pop: Double // 강수 확률
)

@Serializable
data class Main(
    @SerialName("temp")
    val temp: Double,
    @SerialName("feels_like")
    val feels_like: Double,
    @SerialName("temp_min")
    val temp_min: Double,
    @SerialName("temp_max")
    val temp_max: Double
)

@Serializable
data class Weather(
    @SerialName("main")
    val main: String,
    @SerialName("description")
    val description: String,
    @SerialName("icon")
    val icon: String
)
