package com.yehyun.whatshouldiweartoday.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_styles")
data class SavedStyle(
    @PrimaryKey(autoGenerate = true)
    val styleId: Long = 0,
    val styleName: String,
    val season: String, // [추가] 스타일의 계절을 저장할 필드
    val timestamp: Long = System.currentTimeMillis()
)