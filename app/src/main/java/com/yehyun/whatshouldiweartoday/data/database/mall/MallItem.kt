package com.yehyun.whatshouldiweartoday.data.database.mall

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mall_items")
data class MallItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val brand: String,
    val price: Int,
    val category: String,
    val colorHex: String,
    val imageUri: String = "",
    val processedImageUri: String? = null,
    val useProcessedImage: Boolean = false,
    val suitableMinTemp: Double,
    val suitableMaxTemp: Double,
    val fitMinHeight: Double? = null,
    val fitMaxHeight: Double? = null,
    val fitMinWeight: Double? = null,
    val fitMaxWeight: Double? = null,
    val fitMinWaist: Double? = null,
    val fitMaxWaist: Double? = null,
    val purposes: String = "",
    val season: String = "",
    val material: String = "",
    val description: String = "",
    val tags: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
