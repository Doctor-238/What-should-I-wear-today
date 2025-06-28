package com.yehyun.whatshouldiweartoday.data.database

import androidx.room.Entity

@Entity(tableName = "style_item_cross_ref", primaryKeys = ["styleId", "clothingId"])
data class StyleItemCrossRef(
    val styleId: Long,
    val clothingId: Int
)