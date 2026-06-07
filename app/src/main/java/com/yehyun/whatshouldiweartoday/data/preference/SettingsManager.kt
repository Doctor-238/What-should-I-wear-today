package com.yehyun.whatshouldiweartoday.data.preference

import android.content.Context
import android.content.SharedPreferences
import com.yehyun.whatshouldiweartoday.R

class SettingsManager(context: Context) {

    private val appContext = context.applicationContext
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

    // Optional — 0f means "not provided". Waist is used only when > 0.
    var estimatedWaist: Float
        get() = prefs.getFloat(KEY_ESTIMATED_WAIST, 0f)
        set(value) {
            prefs.edit().putFloat(KEY_ESTIMATED_WAIST, value).commit()
        }

    val isBodyRegistered: Boolean
        get() = estimatedHeight > 0f && estimatedWeight > 0f

    val isWaistRegistered: Boolean
        get() = estimatedWaist > 0f

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

    var sizeNotationType: String
        get() = prefs.getString(KEY_SIZE_NOTATION, SIZE_NOTATION_LETTER) ?: SIZE_NOTATION_LETTER
        set(value) {
            prefs.edit().putString(KEY_SIZE_NOTATION, value).commit()
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

    var customGeminiApiKey: String
        get() = prefs.getString(KEY_CUSTOM_GEMINI_API_KEY, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_CUSTOM_GEMINI_API_KEY, value).commit()
        }

    val isUsingCustomGeminiApiKey: Boolean
        get() = customGeminiApiKey.isNotEmpty()

    fun getEffectiveGeminiApiKey(): String {
        val customKey = customGeminiApiKey
        return if (customKey.isNotEmpty()) customKey
        else appContext.getString(R.string.gemini_api_key)
    }

    var customOpenWeatherApiKey: String
        get() = prefs.getString(KEY_CUSTOM_OPENWEATHER_API_KEY, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_CUSTOM_OPENWEATHER_API_KEY, value).commit()
        }

    val isUsingCustomOpenWeatherApiKey: Boolean
        get() = customOpenWeatherApiKey.isNotEmpty()

    fun getEffectiveOpenWeatherApiKey(): String {
        val customKey = customOpenWeatherApiKey
        return if (customKey.isNotEmpty()) customKey
        else appContext.getString(R.string.openweathermap_api_key)
    }

    var wishlistedItemIds: Set<String>
        get() = prefs.getStringSet(KEY_WISHLISTED_ITEMS, emptySet()) ?: emptySet()
        set(value) { prefs.edit().putStringSet(KEY_WISHLISTED_ITEMS, value).commit() }

    fun isWishlisted(itemId: Int) = wishlistedItemIds.contains(itemId.toString())

    /** 찜 토글. 추가됐으면 true, 제거됐으면 false 반환 */
    fun toggleWishlist(itemId: Int): Boolean {
        val ids = wishlistedItemIds.toMutableSet()
        return if (ids.contains(itemId.toString())) {
            ids.remove(itemId.toString()); wishlistedItemIds = ids; false
        } else {
            ids.add(itemId.toString()); wishlistedItemIds = ids; true
        }
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
        private const val KEY_ESTIMATED_WAIST = "estimated_waist"
        private const val KEY_BODY_FIT_ENABLED = "body_fit_enabled"
        private const val KEY_BODY_FIT_BORDER_ENABLED = "body_fit_border_enabled"
        private const val KEY_SIZE_NOTATION = "size_notation_type"
        private const val KEY_EXTENDED_FORECAST_ENABLED = "extended_forecast_enabled"
        private const val KEY_CLOTHING_PURPOSE_ENABLED = "clothing_purpose_enabled"
        private const val KEY_SELECTED_PURPOSE = "selected_purpose"
        private const val KEY_CUSTOM_PURPOSES = "custom_purposes"
        private const val KEY_WISHLISTED_ITEMS = "wishlisted_item_ids"
        private const val KEY_CUSTOM_GEMINI_API_KEY = "custom_gemini_api_key"
        private const val KEY_CUSTOM_OPENWEATHER_API_KEY = "custom_openweather_api_key"

        val DEFAULT_PURPOSES = listOf("격식있는 자리용", "일상용", "활동용", "데이트용", "집앞용")

        const val TEMP_RANGE_NARROW = "좁게"
        const val TEMP_RANGE_NORMAL = "보통"
        const val TEMP_RANGE_WIDE = "넓게"
        const val AI_MODEL_FAST = "fast"
        const val AI_MODEL_ACCURATE = "accurate"
        const val SHOPPING_DETECTION_SENSITIVE = "sensitive"
        const val SHOPPING_DETECTION_NORMAL = "normal"
        const val SIZE_NOTATION_LETTER = "letter"   // XS / S / M / L / XL / XXL
        const val SIZE_NOTATION_NUMERIC = "numeric" // 상의:85~110, 하의:26~34
    }
}