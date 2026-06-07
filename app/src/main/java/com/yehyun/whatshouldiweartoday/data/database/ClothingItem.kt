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
    val colorHex: String,
    val fitMinHeight: Double? = null,
    val fitMaxHeight: Double? = null,
    val fitMinWeight: Double? = null,
    val fitMaxWeight: Double? = null,
    val fitMinWaist: Double? = null,
    val fitMaxWaist: Double? = null,
    val purpose: String = "",
    val purchaseSource: String? = null,
    var size: String? = null,
    val aiCategory: String? = null,
    val imageHash: String? = null
)