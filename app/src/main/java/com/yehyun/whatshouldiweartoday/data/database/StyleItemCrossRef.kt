package com.yehyun.whatshouldiweartoday.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "style_item_cross_ref",
    primaryKeys = ["styleId", "clothingId"],
    indices = [Index(value = ["clothingId"])],
    foreignKeys = [
        // [추가] clothingId가 삭제되면, 이 연결고리도 함께 삭제됩니다.
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
