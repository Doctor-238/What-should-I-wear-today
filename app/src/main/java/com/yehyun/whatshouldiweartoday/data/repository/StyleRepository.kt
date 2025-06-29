package com.yehyun.whatshouldiweartoday.data.repository

import androidx.lifecycle.LiveData
import com.yehyun.whatshouldiweartoday.data.database.ClothingItem
import com.yehyun.whatshouldiweartoday.data.database.SavedStyle
import com.yehyun.whatshouldiweartoday.data.database.StyleDao
import com.yehyun.whatshouldiweartoday.data.database.StyleWithItems

class StyleRepository(private val styleDao: StyleDao) {

    // 새로운 스타일과 그에 포함된 옷들을 한 번에 저장
    suspend fun insertStyleWithItems(style: SavedStyle, items: List<ClothingItem>) {
        styleDao.insertStyleWithItems(style, items)
    }

    // 저장된 모든 스타일을 가져오기 (이름으로 검색 가능)
    fun getStyles(query: String, sortType: String): LiveData<List<StyleWithItems>> {
        return when (sortType) {
            "오래된 순" -> styleDao.getStylesOrderByOldest(query)
            "이름 오름차순" -> styleDao.getStylesOrderByNameAsc(query)
            "이름 내림차순" -> styleDao.getStylesOrderByNameDesc(query)
            else -> styleDao.getStylesOrderByRecent(query) // 기본값: 최신순
        }
    }

    // [추가] ID로 스타일 정보 가져오기
    fun getStyleById(styleId: Long): LiveData<StyleWithItems> {
        return styleDao.getStyleById(styleId)
    }

    // [추가] 스타일 정보 업데이트하기
    suspend fun updateStyleWithItems(style: SavedStyle, items: List<ClothingItem>) {
        styleDao.updateStyleWithItems(style, items)
    }

    suspend fun deleteStyleAndRefs(style: SavedStyle) {
        styleDao.deleteStyleAndRefs(style)
    }

}
