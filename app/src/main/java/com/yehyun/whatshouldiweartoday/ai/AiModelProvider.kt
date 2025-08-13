package com.yehyun.whatshouldiweartoday.ai

import android.content.Context
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.GenerationConfig
import com.yehyun.whatshouldiweartoday.data.preference.SettingsManager


object AiModelProvider {

    @Volatile
    private var instance: GenerativeModel? = null
    private var currentModelName: String? = null

    @Synchronized
    fun getModel(context: Context, apiKey: String): GenerativeModel {
        val settingsManager = SettingsManager(context)
        val requestedModelName = settingsManager.getAiModelName()

        if (instance == null || requestedModelName != currentModelName) {
            currentModelName = requestedModelName
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
}