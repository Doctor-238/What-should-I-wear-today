package com.yehyun.whatshouldiweartoday.data.preference

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var temperatureRange: String
        get() = prefs.getString(KEY_TEMP_RANGE, TEMP_RANGE_NORMAL) ?: TEMP_RANGE_NORMAL
        set(value) = prefs.edit().putString(KEY_TEMP_RANGE, value).apply()

    fun getTemperatureTolerance(): Double {
        return when (temperatureRange) {
            TEMP_RANGE_NARROW -> 1.5
            TEMP_RANGE_WIDE -> 4.5
            else -> 3.0 // NORMAL
        }
    }

    fun getPackableOuterTolerance(): Double {
        return when (temperatureRange) {
            TEMP_RANGE_NARROW -> -3.0
            TEMP_RANGE_WIDE -> -9.0
            else -> -6.0 // NORMAL
        }
    }

    fun resetToDefaults() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "app_settings"
        private const val KEY_TEMP_RANGE = "temperature_range"

        const val TEMP_RANGE_NARROW = "좁게"
        const val TEMP_RANGE_NORMAL = "보통"
        const val TEMP_RANGE_WIDE = "넓게"
    }
}