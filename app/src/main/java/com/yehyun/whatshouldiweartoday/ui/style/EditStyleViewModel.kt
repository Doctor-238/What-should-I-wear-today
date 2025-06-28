package com.yehyun.whatshouldiweartoday.ui.style

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
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

    private val _clothingCategory = MutableLiveData("전체")
    val filteredClothes = MediatorLiveData<List<ClothingItem>>()
    private val allClothes: LiveData<List<ClothingItem>>

    init {
        val db = AppDatabase.getDatabase(application)
        styleRepository = StyleRepository(db.styleDao())
        clothingRepository = ClothingRepository(db.clothingDao())

        allClothes = clothingRepository.getItems("전체", "", "최신순")

        filteredClothes.addSource(allClothes) { filter() }
        filteredClothes.addSource(_clothingCategory) { filter() }
    }

    private fun filter() {
        val category = _clothingCategory.value ?: "전체"
        val clothes = allClothes.value ?: emptyList()

        filteredClothes.value = if (category == "전체") {
            clothes
        } else {
            clothes.filter { it.category == category }
        }
    }

    fun setClothingFilter(category: String) {
        if (_clothingCategory.value != category) {
            _clothingCategory.value = category
        }
    }

    fun getStyleWithItems(styleId: Long): LiveData<StyleWithItems> = styleRepository.getStyleById(styleId)

    // [수정] updateStyle 함수를 받아서 처리하도록 변경
    fun updateStyle(style: SavedStyle, items: List<ClothingItem>) {
        viewModelScope.launch {
            styleRepository.updateStyleWithItems(style, items)
        }
    }

    fun deleteStyle(style: SavedStyle) = viewModelScope.launch {
        styleRepository.deleteStyleAndRefs(style)
    }
}