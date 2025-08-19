package com.yehyun.whatshouldiweartoday.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "style_item_cross_ref",
    primaryKeys = ["styleId", "clothingId"],
    indices = [Index(value = ["clothingId"])],
    foreignKeys = [
        ForeignKey(
            entity = ClothingItem::class,
            parentColumns = ["id"],
            childColumns = ["clothingId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class StyleItemCrossRef(
    val styleId: Long,
    val clothingId: Int
)
