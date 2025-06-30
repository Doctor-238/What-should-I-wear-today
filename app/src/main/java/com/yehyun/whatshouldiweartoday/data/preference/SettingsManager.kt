// app/src/main/java/com/yehyun/whatshouldiweartoday/data/preference/SettingsManager.kt

package com.yehyun.whatshouldiweartoday.data.preference

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var temperatureRange: String
        get() = prefs.getString(KEY_TEMP_RANGE, TEMP_RANGE_NORMAL) ?: TEMP_RANGE_NORMAL
        set(value) = prefs.edit().putString(KEY_TEMP_RANGE, value).apply()

    var constitutionLevel: Int
        get() = prefs.getInt(KEY_CONSTITUTION, 3)
        set(value) = prefs.edit().putInt(KEY_CONSTITUTION, value).apply()

    var sensitivityLevel: Int
        get() = prefs.getInt(KEY_SENSITIVITY, 3)
        set(value) = prefs.edit().putInt(KEY_SENSITIVITY, value).apply()


    fun getTemperatureTolerance(): Double {
        // [수정] 요청에 따라 적정온도 범위 값 변경
        return when (temperatureRange) {
            TEMP_RANGE_NARROW -> 1.5
            TEMP_RANGE_WIDE -> 3.5
            else -> 2.5 // NORMAL
        }
    }

    fun getConstitutionAdjustment(): Double {
        // [수정] 요청에 따라 체질별 온도 보정 값 변경
        return when (constitutionLevel) {
            1 -> -2.0  // 더위 많이 탐
            2 -> -1.0  // 더위 조금 탐
            4 -> 1.0   // 추위 조금 탐
            5 -> 2.0   // 추위 많이 탐
            else -> 0.0 // 보통
        }
    }

    fun getBackgroundSensitivityValue(): Float {
        return when (sensitivityLevel) {
            5 -> 0.3f
            4 -> 0.01f
            2 -> 0.0000000001f
            1 -> 0.00000000000001f
            else -> 0.000001f
        }
    }

    // '챙겨갈 아우터' 로직은 '적정온도 범위' 설정과 독립적으로 운영되므로 이 부분은 수정하지 않습니다.
    // 만약 이 값도 변경을 원하시면 알려주세요.
    fun getPackableOuterTolerance(): Double {
        return when (temperatureRange) {
            TEMP_RANGE_NARROW -> -3.0
            TEMP_RANGE_WIDE -> -7.0
            else -> -5.0 // NORMAL
        }
    }

    fun resetToDefaults() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "app_settings"
        private const val KEY_TEMP_RANGE = "temperature_range"
        private const val KEY_CONSTITUTION = "constitution_level"
        private const val KEY_SENSITIVITY = "sensitivity_level"

        const val TEMP_RANGE_NARROW = "좁게"
        const val TEMP_RANGE_NORMAL = "보통"
        const val TEMP_RANGE_WIDE = "넓게"
    }
}