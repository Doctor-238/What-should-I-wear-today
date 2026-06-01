package com.yehyun.whatshouldiweartoday.data.repository

import androidx.lifecycle.LiveData
import com.yehyun.whatshouldiweartoday.data.database.mall.MallDao
import com.yehyun.whatshouldiweartoday.data.database.mall.MallItem

class MallRepository(private val mallDao: MallDao) {
    fun getAllItems(): LiveData<List<MallItem>> = mallDao.getAllItems()
    suspend fun getAllItemsList(): List<MallItem> = mallDao.getAllItemsList()
    fun getItemsByCategory(category: String): LiveData<List<MallItem>> = mallDao.getItemsByCategory(category)
    suspend fun getItemsByCategoryList(category: String): List<MallItem> = mallDao.getItemsByCategoryList(category)
    suspend fun getItemById(id: Int): MallItem? = mallDao.getItemById(id)
    suspend fun insert(item: MallItem): Long = mallDao.insert(item)
    suspend fun update(item: MallItem) = mallDao.update(item)
    suspend fun deleteById(id: Int) = mallDao.deleteById(id)
}
