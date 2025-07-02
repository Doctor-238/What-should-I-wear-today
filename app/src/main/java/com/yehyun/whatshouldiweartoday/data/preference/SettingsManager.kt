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

    var aiModel: String
        get() = prefs.getString(KEY_AI_MODEL, AI_MODEL_ACCURATE) ?: AI_MODEL_ACCURATE
        set(value) = prefs.edit().putString(KEY_AI_MODEL, value).apply()

    fun getTemperatureTolerance(): Double {
        return when (temperatureRange) {
            TEMP_RANGE_NARROW -> 1.5
            TEMP_RANGE_WIDE -> 3.5
            else -> 2.5 // NORMAL
        }
    }

    fun getConstitutionAdjustment(): Double {
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

    fun getPackableOuterTolerance(): Double {
        return when (temperatureRange) {
            TEMP_RANGE_NARROW -> -3.0
            TEMP_RANGE_WIDE -> -7.0
            else -> -5.0 // NORMAL
        }
    }

    fun getAiModelName(): String {
        return if (aiModel == AI_MODEL_FAST) "gemini-2.5-flash-lite-preview-06-17" else "gemini-2.5-flash"
    }

    fun resetToDefaults() {
        prefs.edit().clear().commit()
    }

    companion object {
        private const val PREFS_NAME = "app_settings"
        private const val KEY_TEMP_RANGE = "temperature_range"
        private const val KEY_CONSTITUTION = "constitution_level"
        private const val KEY_SENSITIVITY = "sensitivity_level"
        private const val KEY_AI_MODEL = "ai_model"

        const val TEMP_RANGE_NARROW = "좁게"
        const val TEMP_RANGE_NORMAL = "보통"
        const val TEMP_RANGE_WIDE = "넓게"
        const val AI_MODEL_FAST = "fast"
        const val AI_MODEL_ACCURATE = "accurate"
    }
}