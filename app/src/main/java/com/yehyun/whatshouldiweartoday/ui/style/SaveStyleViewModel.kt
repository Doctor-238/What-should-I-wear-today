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
import com.yehyun.whatshouldiweartoday.data.repository.ClothingRepository
import com.yehyun.whatshouldiweartoday.data.repository.StyleRepository
import kotlinx.coroutines.launch

class SaveStyleViewModel(application: Application) : AndroidViewModel(application) {
    private val clothingRepository: ClothingRepository
    private val styleRepository: StyleRepository

    private val _clothingCategory = MutableLiveData("전체")
    val filteredClothes = MediatorLiveData<List<ClothingItem>>()
    private val allClothes: LiveData<List<ClothingItem>>

    // ▼▼▼▼▼ 핵심 수정 1: 선택 순서를 기억하기 위해 Set -> List로 변경 ▼▼▼▼▼
    val selectedItems = MutableLiveData<MutableList<ClothingItem>>(mutableListOf())
    // ▲▲▲▲▲ 핵심 수정 1 ▲▲▲▲▲
    val styleName = MutableLiveData<String>()
    val selectedSeason = MutableLiveData<String?>()

    private val _isSaveComplete = MutableLiveData(false)
    val isSaveComplete: LiveData<Boolean> = _isSaveComplete

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    val hasChanges = MutableLiveData(false)
    private var initialItemIds: Set<Int>? = null
    private var initialName: String? = null
    private var initialSeason: String? = null


    init {
        val db = AppDatabase.getDatabase(application)
        clothingRepository = ClothingRepository(db.clothingDao())
        styleRepository = StyleRepository(db.styleDao())
        allClothes = clothingRepository.getItems("전체", "", "최신순")

        filteredClothes.addSource(allClothes) { filter() }
        filteredClothes.addSource(_clothingCategory) { filter() }
        // ▼▼▼▼▼ 핵심 수정 2: 선택된 아이템 목록이 바뀔 때도 정렬이 다시 실행되도록 추가 ▼▼▼▼▼
        filteredClothes.addSource(selectedItems) { filter() }
        // ▲▲▲▲▲ 핵심 수정 2 ▲▲▲▲▲
    }

    // ▼▼▼▼▼ 핵심 수정 3: 요청하신 정렬 로직 적용 ▼▼▼▼▼
    private fun filter() {
        val category = _clothingCategory.value ?: "전체"
        val clothes = allClothes.value ?: emptyList()

        val categoryFiltered = if (category == "전체") {
            clothes
        } else {
            clothes.filter { it.category == category }
        }

        // 선택된 아이템을 앞으로, 그 외에는 최신순으로 정렬
        val selectedIdsSet = selectedItems.value?.map { it.id }?.toSet() ?: emptySet()
        val selectionOrderMap = selectedItems.value?.mapIndexed { index, item -> item.id to index }?.toMap() ?: emptyMap()

        val sortedList = categoryFiltered.sortedWith(
            compareByDescending<ClothingItem> { it.id in selectedIdsSet } // 1. 선택된 아이템을 맨 위로
                .thenBy { selectionOrderMap[it.id] } // 2. 선택된 아이템은 선택된 순서대로
        ) // 3. 선택되지 않은 아이템은 allClothes의 기본 정렬(최신순)을 따름

        filteredClothes.value = sortedList
    }
    // ▲▲▲▲▲ 핵심 수정 3 ▲▲▲▲▲

    fun setClothingFilter(category: String) {
        if (_clothingCategory.value != category) {
            _clothingCategory.value = category
        }
    }

    // ▼▼▼▼▼ 핵심 수정 4: List 자료구조에 맞게 로직 수정 ▼▼▼▼▼
    fun toggleItemSelected(item: ClothingItem) {
        val currentList = selectedItems.value ?: mutableListOf()
        if (currentList.any { it.id == item.id }) {
            currentList.removeAll { it.id == item.id }
        } else {
            if (currentList.size < 10) {
                currentList.add(item)
            }
        }
        selectedItems.value = currentList // LiveData에 변경사항 알림
        checkForChanges()
    }

    fun preselectItems(ids: IntArray) {
        if (initialItemIds != null) return

        initialItemIds = ids.toSet()
        allClothes.observeForever { all ->
            // preselect 시에는 순서가 중요하지 않으므로 Set으로 처리 후 List로 변환
            val preselected = all.filter { it.id in ids }.toMutableList()
            selectedItems.postValue(preselected)
            hasChanges.postValue(false)
        }
    }
    // ▲▲▲▲▲ 핵심 수정 4 ▲▲▲▲▲


    fun setStyleName(name: String) {
        if(initialName == null) initialName = styleName.value ?: ""
        styleName.value = name
        checkForChanges()
    }

    fun setSeason(season: String?) {
        if(initialSeason == null) initialSeason = selectedSeason.value
        selectedSeason.value = season
        checkForChanges()
    }

    private fun checkForChanges() {
        if(initialItemIds == null && styleName.value.isNullOrEmpty() && selectedSeason.value.isNullOrEmpty()) {
            hasChanges.value = false
            return
        }

        val itemsChanged = initialItemIds != selectedItems.value?.map { it.id }?.toSet()
        val nameChanged = initialName != styleName.value
        val seasonChanged = initialSeason != selectedSeason.value

        hasChanges.value = itemsChanged || nameChanged || seasonChanged
    }


    fun saveStyle() {
        if (_isLoading.value == true) return

        val name = styleName.value
        val season = selectedSeason.value
        val items = selectedItems.value

        if (name.isNullOrEmpty() || season.isNullOrEmpty() || items.isNullOrEmpty()) {
            return
        }

        _isLoading.value = true
        viewModelScope.launch {
            try {
                val newStyle = SavedStyle(styleName = name, season = season)
                styleRepository.insertStyleWithItems(newStyle, items.toList())
                _isSaveComplete.postValue(true)
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    fun resetAllState() {
        selectedItems.value = mutableListOf()
        styleName.value = ""
        selectedSeason.value = null
        _isSaveComplete.value = false
        hasChanges.value = false
        initialItemIds = null
        initialName = null
        initialSeason = null
        _isLoading.value = false
    }
}