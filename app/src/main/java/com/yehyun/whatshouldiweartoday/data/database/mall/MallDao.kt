package com.yehyun.whatshouldiweartoday.data.database.mall

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface MallDao {
    @Query("SELECT * FROM mall_items ORDER BY timestamp DESC")
    fun getAllItems(): LiveData<List<MallItem>>

    @Query("SELECT * FROM mall_items ORDER BY timestamp DESC")
    suspend fun getAllItemsList(): List<MallItem>

    @Query("SELECT * FROM mall_items WHERE category = :category ORDER BY timestamp DESC")
    fun getItemsByCategory(category: String): LiveData<List<MallItem>>

    @Query("SELECT * FROM mall_items WHERE category = :category ORDER BY timestamp DESC")
    suspend fun getItemsByCategoryList(category: String): List<MallItem>

    @Query("SELECT * FROM mall_items WHERE id = :id")
    suspend fun getItemById(id: Int): MallItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: MallItem): Long

    @Update
    suspend fun update(item: MallItem)

    @Delete
    suspend fun delete(item: MallItem)

    @Query("DELETE FROM mall_items WHERE id = :id")
    suspend fun deleteById(id: Int)
}
