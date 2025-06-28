package com.yehyun.whatshouldiweartoday.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_styles")
data class SavedStyle(
    @PrimaryKey(autoGenerate = true)
    val styleId: Long = 0,
    val styleName: String,
    val timestamp: Long = System.currentTimeMillis()
)