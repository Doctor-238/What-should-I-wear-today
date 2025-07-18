// app/src/main/java/com/yehyun/whatshouldiweartoday/data/repository/StyleRepository.kt

package com.yehyun.whatshouldiweartoday.data.repository

import androidx.lifecycle.LiveData
import com.yehyun.whatshouldiweartoday.data.database.ClothingItem
import com.yehyun.whatshouldiweartoday.data.database.SavedStyle
import com.yehyun.whatshouldiweartoday.data.database.StyleDao
import com.yehyun.whatshouldiweartoday.data.database.StyleWithItems

class StyleRepository(private val styleDao: StyleDao) {

    suspend fun insertStyleWithItems(style: SavedStyle, items: List<ClothingItem>) {
        styleDao.insertStyleWithItems(style, items)
    }

    fun getStyles(query: String, sortType: String): LiveData<List<StyleWithItems>> {
        return when (sortType) {
            "오래된 순" -> styleDao.getStylesOrderByOldest(query)
            "이름 오름차순" -> styleDao.getStylesOrderByNameAsc(query)
            "이름 내림차순" -> styleDao.getStylesOrderByNameDesc(query)
            else -> styleDao.getStylesOrderByRecent(query)
        }
    }

    fun getStyleById(styleId: Long): LiveData<StyleWithItems?> {
        return styleDao.getStyleById(styleId)
    }

    // [추가] suspend 함수 버전
    suspend fun getStyleByIdSuspend(styleId: Long): StyleWithItems? {
        return styleDao.getStyleByIdSuspend(styleId)
    }

    suspend fun updateStyleWithItems(style: SavedStyle, items: List<ClothingItem>) {
        styleDao.updateStyleWithItems(style, items)
    }

    suspend fun deleteStyleAndRefs(style: SavedStyle) {
        styleDao.deleteStyleAndRefs(style)
    }

}