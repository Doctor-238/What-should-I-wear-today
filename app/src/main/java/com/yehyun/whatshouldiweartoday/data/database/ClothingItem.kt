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
    // [핵심 수정] 온도를 소수점까지 저장하기 위해 Double 타입으로 변경
    val suitableTemperature: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val colorHex: String
)