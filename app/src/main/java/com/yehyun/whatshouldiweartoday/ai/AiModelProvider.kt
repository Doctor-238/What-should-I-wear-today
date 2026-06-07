package com.yehyun.whatshouldiweartoday.ai

import android.content.Context
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.GenerationConfig
import com.yehyun.whatshouldiweartoday.data.preference.SettingsManager


object AiModelProvider {

    @Volatile
    private var instance: GenerativeModel? = null
    private var currentModelName: String? = null
    private var currentApiKey: String? = null

    @Volatile
    private var bodyAnalysisInstance: GenerativeModel? = null
    private var bodyAnalysisApiKey: String? = null
    private var bodyAnalysisModelName: String? = null

    @Synchronized
    fun getModel(context: Context, apiKey: String): GenerativeModel {
        val settingsManager = SettingsManager(context)
        val requestedModelName = settingsManager.getAiModelName()

        if (instance == null || requestedModelName != currentModelName || apiKey != currentApiKey) {
            currentModelName = requestedModelName
            currentApiKey = apiKey
            val config = GenerationConfig.Builder().apply {
                responseMimeType = "application/json"
            }.build()
            instance = GenerativeModel(
                modelName = requestedModelName,
                apiKey = apiKey,
                generationConfig = config
            )
        }
        return instance!!
    }

    @Synchronized
    fun getBodyAnalysisModel(apiKey: String): GenerativeModel {
        if (bodyAnalysisInstance == null || bodyAnalysisApiKey != apiKey) {
            bodyAnalysisApiKey = apiKey
            val config = GenerationConfig.Builder().apply {
                responseMimeType = "application/json"
            }.build()
            bodyAnalysisInstance = GenerativeModel(
                modelName = "gemini-3.1-flash-lite",
                apiKey = apiKey,
                generationConfig = config
            )
        }
        return bodyAnalysisInstance!!
    }
}