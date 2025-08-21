package com.yehyun.whatshouldiweartoday.data.database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface ClothingDao {
    @Insert suspend fun insert(item: ClothingItem)
    @Update suspend fun update(item: ClothingItem)
    @Delete suspend fun delete(item: ClothingItem)

    @Query("SELECT * FROM clothing_items WHERE id = :id")
    fun getItemById(id: Int): LiveData<ClothingItem>

    @Query("SELECT * FROM clothing_items WHERE (:category = '전체' OR category = :category) AND name LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun getItemsOrderByRecent(query: String, category: String): LiveData<List<ClothingItem>>

    @Query("SELECT * FROM clothing_items WHERE (:category = '전체' OR category = :category) AND name LIKE '%' || :query || '%' ORDER BY timestamp ASC")
    fun getItemsOrderByOldest(query: String, category: String): LiveData<List<ClothingItem>>

    @Query("SELECT * FROM clothing_items WHERE (:category = '전체' OR category = :category) AND name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun getItemsOrderByNameAsc(query: String, category: String): LiveData<List<ClothingItem>>

    @Query("SELECT * FROM clothing_items WHERE (:category = '전체' OR category = :category) AND name LIKE '%' || :query || '%' ORDER BY name DESC")
    fun getItemsOrderByNameDesc(query: String, category: String): LiveData<List<ClothingItem>>

    @Query("SELECT * FROM clothing_items WHERE (:category = '전체' OR category = :category) AND name LIKE '%' || :query || '%' ORDER BY suitableTemperature ASC")
    fun getItemsOrderByTempAsc(query: String, category: String): LiveData<List<ClothingItem>>

    @Query("SELECT * FROM clothing_items WHERE (:category = '전체' OR category = :category) AND name LIKE '%' || :query || '%' ORDER BY suitableTemperature DESC")
    fun getItemsOrderByTempDesc(query: String, category: String): LiveData<List<ClothingItem>>


    @Query("SELECT * FROM clothing_items")
    suspend fun getAllItemsLists(): List<ClothingItem>


    @Query("DELETE FROM clothing_items")
    suspend fun clearAll()
}