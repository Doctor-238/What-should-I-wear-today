package com.yehyun.whatshouldiweartoday.data.database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface ClothingDao {

    @Insert
    suspend fun insert(item: ClothingItem)

    @Query("SELECT * FROM clothing_items WHERE (:category = '전체' OR category = :category) AND name LIKE '%' || :query || '%' ORDER BY id DESC")
    fun searchItems(query: String, category: String): LiveData<List<ClothingItem>>

    // [추가] 특정 ID의 옷 아이템 하나만 가져오는 함수
    @Query("SELECT * FROM clothing_items WHERE id = :id")
    fun getItemById(id: Int): LiveData<ClothingItem>

    // [추가] 옷 아이템 정보를 업데이트(수정)하는 함수
    @Update
    suspend fun update(item: ClothingItem)
}
