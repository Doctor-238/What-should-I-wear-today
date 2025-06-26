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
    val useProcessedImage: Boolean = true,
    val category: String,
    val suitableTemperature: Int
)