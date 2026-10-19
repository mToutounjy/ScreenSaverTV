package com.example.alepposcreensaver

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate

data class ForecastDay(
    val date: LocalDate,
    val highCelsius: Int,
    val lowCelsius: Int,
    val weatherCode: Int
)

object WeatherClient {
    fun fetchWeek(): List<ForecastDay> {
        val timezone = URLEncoder.encode("Asia/Aleppo", Charsets.UTF_8.name())
        val url = URL(
            "https://api.open-meteo.com/v1/forecast" +
                "?latitude=36.2021&longitude=37.1343" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
                "&timezone=$timezone&forecast_days=7"
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            requestMethod = "GET"
        }
        try {
            if (connection.responseCode !in 200..299) {
                error("Weather service returned HTTP ${connection.responseCode}")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val daily = JSONObject(body).getJSONObject("daily")
            val dates = daily.getJSONArray("time")
            val highs = daily.getJSONArray("temperature_2m_max")
            val lows = daily.getJSONArray("temperature_2m_min")
            val codes = daily.getJSONArray("weather_code")
            return (0 until dates.length()).map { index ->
                ForecastDay(
                    date = LocalDate.parse(dates.getString(index)),
                    highCelsius = highs.getDouble(index).toInt(),
                    lowCelsius = lows.getDouble(index).toInt(),
                    weatherCode = codes.getInt(index)
                )
            }
        } finally {
            connection.disconnect()
        }
    }
}
