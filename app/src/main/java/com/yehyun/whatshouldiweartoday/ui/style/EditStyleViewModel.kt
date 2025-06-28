package com.yehyun.whatshouldiweartoday.ui.style

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.yehyun.whatshouldiweartoday.data.database.AppDatabase
import com.yehyun.whatshouldiweartoday.data.database.ClothingItem
import com.yehyun.whatshouldiweartoday.data.database.SavedStyle
import com.yehyun.whatshouldiweartoday.data.database.StyleWithItems
import com.yehyun.whatshouldiweartoday.data.repository.ClothingRepository
import com.yehyun.whatshouldiweartoday.data.repository.StyleRepository
import kotlinx.coroutines.launch

class EditStyleViewModel(application: Application) : AndroidViewModel(application) {

    private val styleRepository: StyleRepository
    private val clothingRepository: ClothingRepository

    val allClothes: LiveData<List<ClothingItem>>

    init {
        val db = AppDatabase.getDatabase(application)
        styleRepository = StyleRepository(db.styleDao())
        clothingRepository = ClothingRepository(db.clothingDao())
        allClothes = clothingRepository.getItems("전체", "", "최신순")
    }

    fun getStyleWithItems(styleId: Long): LiveData<StyleWithItems> {
        return styleRepository.getStyleById(styleId)
    }

    fun updateStyle(style: SavedStyle, items: List<ClothingItem>) {
        viewModelScope.launch {
            styleRepository.updateStyleWithItems(style, items)
        }
    }

    // [수정] 이제 Repository에 있는 deleteStyleAndRefs 함수를 정상적으로 호출합니다.
    fun deleteStyle(style: SavedStyle) {
        viewModelScope.launch {
            styleRepository.deleteStyleAndRefs(style)
        }
    }
}
