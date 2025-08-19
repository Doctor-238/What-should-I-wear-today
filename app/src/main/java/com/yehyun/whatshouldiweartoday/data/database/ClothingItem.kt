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
    var suitableTemperature: Double,
    val baseTemperature: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val colorHex: String
)