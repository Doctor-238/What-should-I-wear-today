package com.yehyun.whatshouldiweartoday.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clothing_items")
data class ClothingItem(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    // [오류 해결] val을 var로 변경하여 값을 수정할 수 있도록 허용
    var name: String,
    val imageUri: String,
    val processedImageUri: String?,
    // [오류 해결] val을 var로 변경
    var useProcessedImage: Boolean,
    // [오류 해결] val을 var로 변경
    var category: String,
    val suitableTemperature: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val colorHex: String
)