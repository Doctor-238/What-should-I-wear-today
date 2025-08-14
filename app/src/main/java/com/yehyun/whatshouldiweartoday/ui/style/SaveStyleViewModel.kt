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

    // ▼▼▼▼▼ 핵심 수정: 정렬 방식에 따라 LiveData를 분리합니다. ▼▼▼▼▼
    private val allClothesLatestOrder: LiveData<List<ClothingItem>>
    private val allClothesTempOrder: LiveData<List<ClothingItem>>
    // ▲▲▲▲▲ 핵심 수정 ▲▲▲▲▲

    val selectedItems = MutableLiveData<MutableList<ClothingItem>>()
    val styleName = MutableLiveData<String>()
    val selectedSeason = MutableLiveData<String?>()

    private val _isSaveComplete = MutableLiveData(false)
    val isSaveComplete: LiveData<Boolean> = _isSaveComplete

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    val hasChanges = MutableLiveData(false)
    private var initialItemIds: Set<Int>? = null
    private var initialName: String? = ""
    private var initialSeason: String? = null


    init {
        val db = AppDatabase.getDatabase(application)
        clothingRepository = ClothingRepository(db.clothingDao())
        styleRepository = StyleRepository(db.styleDao())

        // ▼▼▼▼▼ 핵심 수정: 각 정렬 방식에 맞는 데이터를 가져오도록 초기화합니다. ▼▼▼▼▼
        allClothesLatestOrder = clothingRepository.getItems("전체", "", "최신순")
        allClothesTempOrder = clothingRepository.getItems("전체", "", "온도 내림차순")

        // MediatorLiveData가 두 LiveData를 모두 관찰하도록 설정합니다.
        filteredClothes.addSource(allClothesLatestOrder) { clothesList ->
            validateSelectedItems(clothesList)
            filter()
        }
        filteredClothes.addSource(allClothesTempOrder) { clothesList ->
            validateSelectedItems(clothesList)
            filter()
        }
        filteredClothes.addSource(_clothingCategory) { filter() }
        filteredClothes.addSource(selectedItems) { filter() }
        // ▲▲▲▲▲ 핵심 수정 ▲▲▲▲▲
    }

    private fun validateSelectedItems(currentClothes: List<ClothingItem>?) {
        val clothes = currentClothes ?: return
        selectedItems.value?.let { selected ->
            val existingIds = clothes.map { it.id }.toSet()
            val selectionChanged = selected.removeAll { it.id !in existingIds }
            if (selectionChanged) {
                selectedItems.postValue(selected)
            }
        }
    }

    private fun filter() {
        val category = _clothingCategory.value ?: "전체"
        // ▼▼▼▼▼ 핵심 수정: 카테고리에 따라 원본 데이터 소스를 선택합니다. ▼▼▼▼▼
        val clothes = if (category == "전체") {
            allClothesLatestOrder.value ?: emptyList()
        } else {
            allClothesTempOrder.value ?: emptyList()
        }
        // ▲▲▲▲▲ 핵심 수정 ▲▲▲▲▲

        val categoryFiltered = if (category == "전체") {
            clothes
        } else {
            clothes.filter { it.category == category }
        }

        val selectedIdsSet = selectedItems.value?.map { it.id }?.toSet() ?: emptySet()
        val selectionOrderMap = selectedItems.value?.mapIndexed { index, item -> item.id to index }?.toMap() ?: emptyMap()

        val sortedList = categoryFiltered.sortedWith(
            compareByDescending<ClothingItem> { it.id in selectedIdsSet }
                .thenBy { selectionOrderMap[it.id] }
        )

        filteredClothes.value = sortedList
    }

    fun setClothingFilter(category: String) {
        if (_clothingCategory.value != category) {
            _clothingCategory.value = category
        }
    }

    fun toggleItemSelected(item: ClothingItem) {
        val currentList = selectedItems.value ?: mutableListOf()
        if (currentList.any { it.id == item.id }) {
            currentList.removeAll { it.id == item.id }
        } else {
            if (currentList.size < 9) {
                currentList.add(item)
            }
        }
        selectedItems.value = currentList
        checkForChanges()
    }

    fun preselectItems(ids: IntArray) {
        if (initialItemIds != null) return

        initialItemIds = ids.toSet()
        // '전체' 탭의 정렬 기준인 최신순 데이터를 사용하여 아이템을 가져옵니다.
        val observer = object : androidx.lifecycle.Observer<List<ClothingItem>> {
            override fun onChanged(all: List<ClothingItem>) {
                val preselected = all.filter { it.id in ids }.toMutableList()
                selectedItems.postValue(preselected)
                checkForChanges()
                allClothesLatestOrder.removeObserver(this)
            }
        }
        allClothesLatestOrder.observeForever(observer)
    }


    fun setStyleName(name: String) {
        if (initialName == null) initialName = ""
        styleName.value = name
        checkForChanges()
    }

    fun setSeason(season: String?) {
        if (initialSeason == null) initialSeason = selectedSeason.value
        selectedSeason.value = season
        checkForChanges()
    }

    private fun checkForChanges() {
        if (initialItemIds == null && initialName == null && initialSeason == null) {
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