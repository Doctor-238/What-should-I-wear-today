package com.yehyun.whatshouldiweartoday.data.database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert

@Dao
interface ClothingDao {
    // 옷 아이템을 데이터베이스에 추가하는 함수
    @Insert
    suspend fun insert(item: ClothingItem)

    @androidx.room.Query("SELECT * FROM clothing_items ORDER BY id DESC")//모든 옷 아이템을 가져오는거
    fun getAllItems(): LiveData<List<ClothingItem>>// LiveData - 데이터가 변경되면 UI가 자동으로 업데이트 되도록 도와주는거
}