package com.yehyun.whatshouldiweartoday.data.database

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface StyleDao {

    @Transaction
    suspend fun insertStyleWithItems(style: SavedStyle, items: List<ClothingItem>) {
        val styleId = insertStyle(style)
        items.forEach { item ->
            insertCrossRef(StyleItemCrossRef(styleId, item.id))
        }
    }

    @Insert
    suspend fun insertStyle(style: SavedStyle): Long

    @Insert
    suspend fun insertCrossRef(crossRef: StyleItemCrossRef)

    @Transaction
    @Query("SELECT * FROM saved_styles WHERE styleName LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun getStylesOrderByRecent(query: String): LiveData<List<StyleWithItems>>

    @Transaction
    @Query("SELECT * FROM saved_styles WHERE styleName LIKE '%' || :query || '%' ORDER BY timestamp ASC")
    fun getStylesOrderByOldest(query: String): LiveData<List<StyleWithItems>>

    @Transaction
    @Query("SELECT * FROM saved_styles WHERE styleName LIKE '%' || :query || '%' ORDER BY styleName ASC")
    fun getStylesOrderByNameAsc(query: String): LiveData<List<StyleWithItems>>

    @Transaction
    @Query("SELECT * FROM saved_styles WHERE styleName LIKE '%' || :query || '%' ORDER BY styleName DESC")
    fun getStylesOrderByNameDesc(query: String): LiveData<List<StyleWithItems>>

    // ▼▼▼▼▼ 핵심 수정: ViewModel에서 사용할 함수 추가 ▼▼▼▼▼
    @Transaction
    @Query("SELECT * FROM saved_styles")
    fun getAllStylesWithItems(): LiveData<List<StyleWithItems>>
    // ▲▲▲▲▲ 핵심 수정 ▲▲▲▲▲

    @Update
    suspend fun updateStyle(style: SavedStyle)

    @Query("DELETE FROM style_item_cross_ref WHERE styleId = :styleId")
    suspend fun deleteCrossRefsForStyle(styleId: Long)

    @Transaction
    suspend fun updateStyleWithItems(style: SavedStyle, items: List<ClothingItem>) {
        updateStyle(style)
        deleteCrossRefsForStyle(style.styleId)
        items.forEach { item ->
            insertCrossRef(StyleItemCrossRef(style.styleId, item.id))
        }
    }

    @Delete
    suspend fun deleteStyle(style: SavedStyle)

    @Query("DELETE FROM style_item_cross_ref WHERE clothingId = :clothingId")
    suspend fun deleteCrossRefsByClothingId(clothingId: Int)

    @Query("DELETE FROM saved_styles WHERE styleId NOT IN (SELECT DISTINCT styleId FROM style_item_cross_ref)")
    suspend fun deleteOrphanedStyles()

    @Transaction
    suspend fun deleteStyleAndRefs(style: SavedStyle) {
        deleteCrossRefsForStyle(style.styleId)
        deleteStyle(style)
    }
    @Transaction
    @Query("SELECT * FROM saved_styles WHERE styleId = :styleId")
    fun getStyleById(styleId: Long): LiveData<StyleWithItems?>

    @Transaction
    @Query("SELECT * FROM saved_styles WHERE styleId = :styleId")
    suspend fun getStyleByIdSuspend(styleId: Long): StyleWithItems?

    @Query("DELETE FROM saved_styles")
    suspend fun clearAll()

    @Query("DELETE FROM style_item_cross_ref")
    suspend fun clearAllCrossRefs()

    @Transaction
    suspend fun clearAllStylesAndRefs() {
        clearAll()
        clearAllCrossRefs()
    }
}