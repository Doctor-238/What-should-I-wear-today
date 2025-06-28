package com.yehyun.whatshouldiweartoday.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clothing_items")
data class ClothingItem(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val imageUri: String,
    val processedImageUri: String?,
    var useProcessedImage: Boolean,
    val category: String,
    val suitableTemperature: Int,
    val timestamp: Long = System.currentTimeMillis(),

    // [핵심 추가] 온도 재계산을 위해 AI 분석 점수를 DB에 저장합니다.
    val lengthScore: Int,
    val thicknessScore: Int
)