package com.yehyun.whatshouldiweartoday.ai

import android.content.Context
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.GenerationConfig
import com.yehyun.whatshouldiweartoday.data.preference.SettingsManager

/**
 * 앱 전체에서 GenerativeModel 인스턴스를 하나만 생성하고 공유(싱글턴)하여
 * 동시 요청으로 인한 충돌을 방지합니다.
 */
object AiModelProvider {

    @Volatile
    private var instance: GenerativeModel? = null
    private var currentModelName: String? = null

    // 여러 스레드에서 동시에 접근해도 안전하도록 synchronized 사용
    @Synchronized
    fun getModel(context: Context, apiKey: String): GenerativeModel {
        val settingsManager = SettingsManager(context)
        val requestedModelName = settingsManager.getAiModelName()

        // 현재 인스턴스가 없거나, 설정에서 모델 이름이 변경된 경우에만 새로 생성
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