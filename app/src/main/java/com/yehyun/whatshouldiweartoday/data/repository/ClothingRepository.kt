package com.yehyun.whatshouldiweartoday.data.repository

import androidx.lifecycle.LiveData
import com.yehyun.whatshouldiweartoday.data.database.ClothingDao
import com.yehyun.whatshouldiweartoday.data.database.ClothingItem

class ClothingRepository(private val clothingDao: ClothingDao) {

    fun getItems(category: String, query: String, sortType: String): LiveData<List<ClothingItem>> {
        return when (sortType) {
            "이름 오름차순" -> clothingDao.getItemsOrderByNameAsc(query, category)
            "이름 내림차순" -> clothingDao.getItemsOrderByNameDesc(query, category)
            "오래된 순" -> clothingDao.getItemsOrderByOldest(query, category)
            "온도 오름차순" -> clothingDao.getItemsOrderByTempAsc(query, category)
            "온도 내림차순" -> clothingDao.getItemsOrderByTempDesc(query, category)
            else -> clothingDao.getItemsOrderByRecent(query, category)
        }
    }

    fun getItemById(id: Int): LiveData<ClothingItem> = clothingDao.getItemById(id)
    suspend fun update(item: ClothingItem) = clothingDao.update(item)
    suspend fun delete(item: ClothingItem) = clothingDao.delete(item)
    suspend fun getAllItemsList(): List<ClothingItem> = clothingDao.getAllItemsList()
}
