package com.yehyun.whatshouldiweartoday.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clothing_items")
data class ClothingItem(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val imageUri: String, // 원본 이미지 경로
    val processedImageUri: String?, // 배경 제거 이미지 경로
    var useProcessedImage: Boolean, // [수정] 대표 이미지 선택 여부
    val category: String,
    val suitableTemperature: Int,
    val timestamp: Long = System.currentTimeMillis() // 등록 시간
)
