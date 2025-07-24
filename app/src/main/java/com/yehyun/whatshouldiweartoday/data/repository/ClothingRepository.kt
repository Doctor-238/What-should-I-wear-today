package com.yehyun.whatshouldiweartoday.data.repository

import androidx.lifecycle.LiveData
import com.yehyun.whatshouldiweartoday.data.database.ClothingDao
import com.yehyun.whatshouldiweartoday.data.database.ClothingItem
import java.io.File

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
    suspend fun delete(item: ClothingItem) {
        try {
            val itemuri = File(item.imageUri)
            val itemprocessedImageUri = File(item.processedImageUri)
            if (itemuri.exists()) {
                itemuri.delete()
            }
            if (itemprocessedImageUri.exists()) {
                itemprocessedImageUri.delete()
            }
        } catch (e: Exception) {
            // 파일 삭제 중 오류가 발생할 경우를 대비한 예외 처리입니다.
            e.printStackTrace()
        }
        clothingDao.delete(item)
    }
    suspend fun getAllItemsList(): List<ClothingItem> = clothingDao.getAllItemsLists()
}
