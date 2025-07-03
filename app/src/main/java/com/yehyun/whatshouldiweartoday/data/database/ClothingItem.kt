package com.yehyun.whatshouldiweartoday.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clothing_items")
data class ClothingItem(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    var name: String,
    val imageUri: String,
    val processedImageUri: String?,
    var useProcessedImage: Boolean,
    var category: String,
    // ▼▼▼▼▼ 핵심 수정 부분 ▼▼▼▼▼
    // suitableTemperature: 사용자의 체질, 설정 등이 반영된 최종 추천용 온도
    // baseTemperature: AI가 처음 분석한 순수 적정 온도 (가중치 없음)
    var suitableTemperature: Double,
    val baseTemperature: Double, // 새로 추가된 필드
    // ▲▲▲▲▲ 핵심 수정 부분 ▲▲▲▲▲
    val timestamp: Long = System.currentTimeMillis(),
    val colorHex: String
)