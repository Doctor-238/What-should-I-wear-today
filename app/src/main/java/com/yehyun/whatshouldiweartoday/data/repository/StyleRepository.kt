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

    // ▼▼▼▼▼ 핵심 수정: ViewModel에서 사용할 함수 추가 ▼▼▼▼▼
    fun getAllStylesWithItems(): LiveData<List<StyleWithItems>> {
        return styleDao.getAllStylesWithItems()
    }
    // ▲▲▲▲▲ 핵심 수정 ▲▲▲▲▲

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