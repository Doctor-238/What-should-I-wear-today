package com.yehyun.whatshouldiweartoday.data.repository

import androidx.lifecycle.LiveData
import com.yehyun.whatshouldiweartoday.data.database.ClothingDao
import com.yehyun.whatshouldiweartoday.data.database.ClothingItem

class ClothingRepository(private val clothingDao: ClothingDao) {

    fun getItems(category: String, query: String): LiveData<List<ClothingItem>> {
        return clothingDao.searchItems(query, category)
    }

    // [추가] ID로 옷 정보 가져오기
    fun getItemById(id: Int): LiveData<ClothingItem> {
        return clothingDao.getItemById(id)
    }

    // [추가] 옷 정보 업데이트하기
    suspend fun update(item: ClothingItem) {
        clothingDao.update(item)
    }
}
