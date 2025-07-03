// 파일 경로: app/src/main/java/com/yehyun/whatshouldiweartoday/data/preference/SettingsManager.kt

package com.yehyun.whatshouldiweartoday.data.preference

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var temperatureRange: String
        get() = prefs.getString(KEY_TEMP_RANGE, TEMP_RANGE_NORMAL) ?: TEMP_RANGE_NORMAL
        set(value) { // [수정] set을 블록 형태로 변경
            prefs.edit().putString(KEY_TEMP_RANGE, value).commit()
        }

    var constitutionLevel: Int
        get() = prefs.getInt(KEY_CONSTITUTION, 3)
        set(value) { // [수정] set을 블록 형태로 변경
            prefs.edit().putInt(KEY_CONSTITUTION, value).commit()
        }

    var sensitivityLevel: Int
        get() = prefs.getInt(KEY_SENSITIVITY, 3)
        set(value) { // [수정] set을 블록 형태로 변경
            prefs.edit().putInt(KEY_SENSITIVITY, value).commit()
        }

    var aiModel: String
        get() = prefs.getString(KEY_AI_MODEL, AI_MODEL_ACCURATE) ?: AI_MODEL_ACCURATE
        set(value) { // [수정] set을 블록 형태로 변경
            prefs.edit().putString(KEY_AI_MODEL, value).commit()
        }

    var closetSortType: String
        get() = prefs.getString(KEY_CLOSET_SORT_TYPE, "최신순") ?: "최신순"
        set(value) { // [수정] set을 블록 형태로 변경
            prefs.edit().putString(KEY_CLOSET_SORT_TYPE, value).commit()
        }

    var styleSortType: String
        get() = prefs.getString(KEY_STYLE_SORT_TYPE, "최신순") ?: "최신순"
        set(value) { // [수정] set을 블록 형태로 변경
            prefs.edit().putString(KEY_STYLE_SORT_TYPE, value).commit()
        }

    fun getTemperatureTolerance(): Double {
        return when (temperatureRange) {
            TEMP_RANGE_NARROW -> 1.5
            TEMP_RANGE_WIDE -> 2.5
            else -> 2.0 // NORMAL
        }
    }

    fun getConstitutionAdjustment(): Double {
        return when (constitutionLevel) {
            1 -> -3.0  // 더위 많이 탐
            2 -> -1.5  // 더위 조금 탐
            4 -> 1.5   // 추위 조금 탐
            5 -> 3.0   // 추위 많이 탐
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
            TEMP_RANGE_NARROW -> -5.0
            TEMP_RANGE_WIDE -> -7.0
            else -> -6.0 // NORMAL
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
        private const val KEY_CLOSET_SORT_TYPE = "closet_sort_type"
        private const val KEY_STYLE_SORT_TYPE = "style_sort_type"

        const val TEMP_RANGE_NARROW = "좁게"
        const val TEMP_RANGE_NORMAL = "보통"
        const val TEMP_RANGE_WIDE = "넓게"
        const val AI_MODEL_FAST = "fast"
        const val AI_MODEL_ACCURATE = "accurate"
    }
}