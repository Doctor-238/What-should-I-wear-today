package com.yehyun.whatshouldiweartoday.data.repository

import androidx.lifecycle.LiveData
import com.yehyun.whatshouldiweartoday.data.database.ClothingItem
import com.yehyun.whatshouldiweartoday.data.database.SavedStyle
import com.yehyun.whatshouldiweartoday.data.database.StyleDao
import com.yehyun.whatshouldiweartoday.data.database.StyleWithItems

class StyleRepository(private val styleDao: StyleDao) {

    // 새로운 스타일과 그에 포함된 옷들을 한 번에 저장
    suspend fun insertStyleWithItems(styleName: String, items: List<ClothingItem>) {
        val newStyle = SavedStyle(styleName = styleName)
        styleDao.insertStyleWithItems(newStyle, items)
    }

    // 저장된 모든 스타일을 가져오기 (이름으로 검색 가능)
    fun getStyles(query: String): LiveData<List<StyleWithItems>> {
        return styleDao.getStyles(query)
    }
}
