package com.yehyun.whatshouldiweartoday.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clothing_items")
data class ClothingItem(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,  //옷들의 교유 id
    val name: String, //옷 이름
    val imageUri: String, //배경제거 이미지 저장경로
    val category: String,  //옷 카테고리
    val suitableTemperature: Int   //옷 적절 체감온도
)