package com.yehyun.whatshouldiweartoday.data.preference

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var temperatureRange: String
        get() = prefs.getString(KEY_TEMP_RANGE, TEMP_RANGE_NORMAL) ?: TEMP_RANGE_NORMAL
        set(value) {
            prefs.edit().putString(KEY_TEMP_RANGE, value).commit()
        }

    var constitutionLevel: Int
        get() = prefs.getInt(KEY_CONSTITUTION, 3)
        set(value) {
            prefs.edit().putInt(KEY_CONSTITUTION, value).commit()
        }

    var sensitivityLevel: Int
        get() = prefs.getInt(KEY_SENSITIVITY, 3)
        set(value) {
            prefs.edit().putInt(KEY_SENSITIVITY, value).commit()
        }

    var aiModel: String
        get() = prefs.getString(KEY_AI_MODEL, AI_MODEL_ACCURATE) ?: AI_MODEL_ACCURATE
        set(value) {
            prefs.edit().putString(KEY_AI_MODEL, value).commit()
        }

    var shoppingDetectionSensitivity: String
        get() = prefs.getString(KEY_SHOPPING_DETECTION, SHOPPING_DETECTION_SENSITIVE) ?: SHOPPING_DETECTION_SENSITIVE
        set(value) {
            prefs.edit().putString(KEY_SHOPPING_DETECTION, value).commit()
        }

    var closetSortType: String
        get() = prefs.getString(KEY_CLOSET_SORT_TYPE, "최신순") ?: "최신순"
        set(value) {
            prefs.edit().putString(KEY_CLOSET_SORT_TYPE, value).commit()
        }

    var styleSortType: String
        get() = prefs.getString(KEY_STYLE_SORT_TYPE, "최신순") ?: "최신순"
        set(value) {
            prefs.edit().putString(KEY_STYLE_SORT_TYPE, value).commit()
        }

    var showRecommendationIcon: Boolean
        get() = prefs.getBoolean(KEY_SHOW_RECOMMENDATION_ICON, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SHOW_RECOMMENDATION_ICON, value).commit()
        }

    var bodyImageUri: String?
        get() = prefs.getString(KEY_BODY_IMAGE_URI, null)
        set(value) {
            prefs.edit().putString(KEY_BODY_IMAGE_URI, value).commit()
        }

    var estimatedHeight: Float
        get() = prefs.getFloat(KEY_ESTIMATED_HEIGHT, 0f)
        set(value) {
            prefs.edit().putFloat(KEY_ESTIMATED_HEIGHT, value).commit()
        }

    var estimatedWeight: Float
        get() = prefs.getFloat(KEY_ESTIMATED_WEIGHT, 0f)
        set(value) {
            prefs.edit().putFloat(KEY_ESTIMATED_WEIGHT, value).commit()
        }

    val isBodyRegistered: Boolean
        get() = estimatedHeight > 0f && estimatedWeight > 0f

    var bodyFitEnabled: Boolean
        get() = prefs.getBoolean(KEY_BODY_FIT_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_BODY_FIT_ENABLED, value).commit()
        }

    var bodyFitBorderEnabled: Boolean
        get() = prefs.getBoolean(KEY_BODY_FIT_BORDER_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_BODY_FIT_BORDER_ENABLED, value).commit()
        }

    var extendedForecastEnabled: Boolean
        get() = prefs.getBoolean(KEY_EXTENDED_FORECAST_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_EXTENDED_FORECAST_ENABLED, value).commit()
        }

    var clothingPurposeEnabled: Boolean
        get() = prefs.getBoolean(KEY_CLOTHING_PURPOSE_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_CLOTHING_PURPOSE_ENABLED, value).commit()
        }

    var selectedPurpose: String
        get() = prefs.getString(KEY_SELECTED_PURPOSE, DEFAULT_PURPOSES.first()) ?: DEFAULT_PURPOSES.first()
        set(value) {
            prefs.edit().putString(KEY_SELECTED_PURPOSE, value).commit()
        }

    var customPurposes: Set<String>
        get() = prefs.getStringSet(KEY_CUSTOM_PURPOSES, emptySet()) ?: emptySet()
        set(value) {
            prefs.edit().putStringSet(KEY_CUSTOM_PURPOSES, value).commit()
        }

    fun getAllPurposes(): List<String> {
        return DEFAULT_PURPOSES + customPurposes.sorted()
    }

    fun addCustomPurpose(purpose: String): Boolean {
        val allPurposes = getAllPurposes()
        if (allPurposes.any { it == purpose }) return false
        customPurposes = customPurposes + purpose
        return true
    }

    fun removeCustomPurpose(purpose: String) {
        customPurposes = customPurposes - purpose
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
            1 -> 3.0   // 추위 많이 탐
            2 -> 1.5   // 추위 조금 탐
            4 -> -1.5  // 더위 조금 탐
            5 -> -3.0  // 더위 많이 탐
            else -> 0.0 // 보통
        }
    }

    fun getBackgroundSensitivityValue(): Float {
        return when (sensitivityLevel) {
            5 -> 0.3f
            4 -> 0.01f
            2 -> 0.0000000001f
            1 -> 0.00000000000001f
            else -> 0.000001f // NORMAL
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
        return if (aiModel == AI_MODEL_FAST) "gemini-2.5-flash-lite" else "gemini-2.5-flash"
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
        private const val KEY_SHOPPING_DETECTION = "shopping_detection_sensitivity"
        private const val KEY_CLOSET_SORT_TYPE = "closet_sort_type"
        private const val KEY_STYLE_SORT_TYPE = "style_sort_type"
        private const val KEY_SHOW_RECOMMENDATION_ICON = "show_recommendation_icon"
        private const val KEY_BODY_IMAGE_URI = "body_image_uri"
        private const val KEY_ESTIMATED_HEIGHT = "estimated_height"
        private const val KEY_ESTIMATED_WEIGHT = "estimated_weight"
        private const val KEY_BODY_FIT_ENABLED = "body_fit_enabled"
        private const val KEY_BODY_FIT_BORDER_ENABLED = "body_fit_border_enabled"
        private const val KEY_EXTENDED_FORECAST_ENABLED = "extended_forecast_enabled"
        private const val KEY_CLOTHING_PURPOSE_ENABLED = "clothing_purpose_enabled"
        private const val KEY_SELECTED_PURPOSE = "selected_purpose"
        private const val KEY_CUSTOM_PURPOSES = "custom_purposes"

        val DEFAULT_PURPOSES = listOf("격식있는 자리용", "일상용", "활동용", "데이트용", "집앞용")

        const val TEMP_RANGE_NARROW = "좁게"
        const val TEMP_RANGE_NORMAL = "보통"
        const val TEMP_RANGE_WIDE = "넓게"
        const val AI_MODEL_FAST = "fast"
        const val AI_MODEL_ACCURATE = "accurate"
        const val SHOPPING_DETECTION_SENSITIVE = "sensitive"
        const val SHOPPING_DETECTION_NORMAL = "normal"
    }
}