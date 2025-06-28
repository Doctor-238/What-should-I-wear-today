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
    val clothingRepository: ClothingRepository

    // [추가] 현재 선택된 카테고리를 저장할 LiveData
    private val _clothingCategory = MutableLiveData("전체")

    // [수정] 필터링된 옷 목록을 담을 LiveData
    val filteredClothes = MediatorLiveData<List<ClothingItem>>()
    val allClothes: LiveData<List<ClothingItem>>

    init {
        val db = AppDatabase.getDatabase(application)
        styleRepository = StyleRepository(db.styleDao())
        clothingRepository = ClothingRepository(db.clothingDao())

        // 원본 전체 옷 목록은 한 번만 가져옵니다.
        allClothes = clothingRepository.getItems("전체", "", "최신순")

        // 카테고리 필터가 변경되거나, 원본 옷 목록이 변경될 때마다, filteredClothes를 업데이트합니다.
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

    // [추가] 이제 이 함수가 정상적으로 존재하여 오류가 발생하지 않습니다.
    fun setClothingFilter(category: String) {
        if (_clothingCategory.value != category) {
            _clothingCategory.value = category
        }
    }

    fun getStyleWithItems(styleId: Long): LiveData<StyleWithItems> = styleRepository.getStyleById(styleId)
    fun updateStyle(style: SavedStyle, items: List<ClothingItem>) = viewModelScope.launch { styleRepository.updateStyleWithItems(style, items) }
    fun deleteStyle(style: SavedStyle) = viewModelScope.launch { styleRepository.deleteStyleAndRefs(style) }
}
