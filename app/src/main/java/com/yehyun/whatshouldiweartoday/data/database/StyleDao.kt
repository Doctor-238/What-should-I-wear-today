package com.yehyun.whatshouldiweartoday.data.database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface StyleDao {

    // 스타일과 그에 속한 옷들의 관계를 한 번에 저장
    @Transaction
    suspend fun insertStyleWithItems(style: SavedStyle, items: List<ClothingItem>) {
        val styleId = insertStyle(style)
        items.forEach { item ->
            insertCrossRef(StyleItemCrossRef(styleId, item.id))
        }
    }

    @Insert
    suspend fun insertStyle(style: SavedStyle): Long // 저장된 스타일의 ID를 반환

    @Insert
    suspend fun insertCrossRef(crossRef: StyleItemCrossRef)

    // 이름으로 스타일을 검색
    @Transaction
    @Query("SELECT * FROM saved_styles WHERE styleName LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun getStyles(query: String): LiveData<List<StyleWithItems>>
}
