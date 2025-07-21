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

class EditStyleViewModel(application: Application) : AndroidViewModel(application) {

    private val styleRepository: StyleRepository
    private val clothingRepository: ClothingRepository

    private val _clothingCategory = MutableLiveData("전체")
    val filteredClothes = MediatorLiveData<List<ClothingItem>>()
    private val allClothes: LiveData<List<ClothingItem>>

    private var originalStyle: SavedStyle? = null
    private var initialItemIds: Set<Int>? = null
    private var currentStyleId: Long? = null

    val currentStyleName = MutableLiveData<String>()
    val currentSeason = MutableLiveData<String>()

    // ▼▼▼▼▼ 핵심 수정 1: 선택 순서를 기억하기 위해 List로 변경 ▼▼▼▼▼
    private val _selectedItems = MutableLiveData<MutableList<ClothingItem>>(mutableListOf())
    val selectedItems: LiveData<MutableList<ClothingItem>> = _selectedItems
    // ▲▲▲▲▲ 핵심 수정 1 ▲▲▲▲▲

    val toolbarTitle = MutableLiveData<String>()
    val saveButtonEnabled = MutableLiveData(false)
    val backPressedCallbackEnabled = MutableLiveData(false)

    private val _isProcessing = MutableLiveData(false)
    val isProcessing: LiveData<Boolean> = _isProcessing

    private val _isUpdateComplete = MutableLiveData(false)
    val isUpdateComplete: LiveData<Boolean> = _isUpdateComplete

    private val _isDeleteComplete = MutableLiveData(false)
    val isDeleteComplete: LiveData<Boolean> = _isDeleteComplete

    init {
        val db = AppDatabase.getDatabase(application)
        styleRepository = StyleRepository(db.styleDao())
        clothingRepository = ClothingRepository(db.clothingDao())

        allClothes = clothingRepository.getItems("전체", "", "최신순")

        filteredClothes.addSource(allClothes) { filter() }
        filteredClothes.addSource(_clothingCategory) { filter() }
        // ▼▼▼▼▼ 핵심 수정 2: 선택된 아이템이 변경되어도 정렬 재실행 ▼▼▼▼▼
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

        val selectedIdsSet = selectedItems.value?.map { it.id }?.toSet() ?: emptySet()
        val selectionOrderMap = selectedItems.value?.mapIndexed { index, item -> item.id to index }?.toMap() ?: emptyMap()

        val sortedList = categoryFiltered.sortedWith(
            compareByDescending<ClothingItem> { it.id in selectedIdsSet }
                .thenBy { selectionOrderMap[it.id] }
        )

        filteredClothes.value = sortedList
    }
    // ▲▲▲▲▲ 핵심 수정 3 ▲▲▲▲▲

    fun loadStyleIfNeeded(styleId: Long) {
        if (originalStyle != null && currentStyleId == styleId) return
        this.currentStyleId = styleId
        refreshCurrentStyle()
    }

    fun refreshCurrentStyle() {
        val styleId = currentStyleId ?: return
        viewModelScope.launch {
            val styleWithItems = styleRepository.getStyleByIdSuspend(styleId)
            if (styleWithItems != null) {
                originalStyle = styleWithItems.style
                initialItemIds = styleWithItems.items.map { it.id }.toSet()

                _selectedItems.postValue(styleWithItems.items.toMutableList())
                currentStyleName.postValue(styleWithItems.style.styleName)
                currentSeason.postValue(styleWithItems.style.season)
                toolbarTitle.postValue("'${styleWithItems.style.styleName}' 수정")

                checkForChanges()
            } else {
                _isDeleteComplete.postValue(true)
            }
        }
    }

    fun setClothingFilter(category: String) {
        if (_clothingCategory.value != category) {
            _clothingCategory.value = category
        }
    }

    // ▼▼▼▼▼ 핵심 수정 4: List 자료구조에 맞게 로직 수정 ▼▼▼▼▼
    fun toggleItemSelection(item: ClothingItem) {
        val currentList = _selectedItems.value ?: mutableListOf()
        val isSelected = currentList.any { it.id == item.id }

        if (isSelected) {
            currentList.removeAll { it.id == item.id }
        } else {
            if (currentList.size < 10) {
                currentList.add(item)
            }
        }
        _selectedItems.value = currentList
        checkForChanges()
    }

    fun removeSelectedItem(item: ClothingItem) {
        val currentList = _selectedItems.value ?: mutableListOf()
        currentList.remove(item)
        _selectedItems.value = currentList
        checkForChanges()
    }
    // ▲▲▲▲▲ 핵심 수정 4 ▲▲▲▲▲

    private fun checkForChanges() {
        if (originalStyle == null || initialItemIds == null) return

        val nameChanged = originalStyle?.styleName != currentStyleName.value
        val seasonChanged = originalStyle?.season != currentSeason.value
        val itemsChanged = initialItemIds != _selectedItems.value?.map { it.id }?.toSet()

        val hasChanges = nameChanged || seasonChanged || itemsChanged
        val isSavable = hasChanges && !currentStyleName.value.isNullOrEmpty() && !currentSeason.value.isNullOrEmpty()

        saveButtonEnabled.value = isSavable
        backPressedCallbackEnabled.value = hasChanges
    }

    fun onNameChanged(newName: String) {
        if (currentStyleName.value != newName) {
            currentStyleName.value = newName
            checkForChanges()
        }
    }

    fun onSeasonChanged(newSeason: String) {
        if (currentSeason.value != newSeason) {
            currentSeason.value = newSeason
            checkForChanges()
        }
    }

    fun updateStyle() {
        if (_isProcessing.value == true) return

        val name = currentStyleName.value
        val season = currentSeason.value
        val items = _selectedItems.value
        val style = originalStyle

        if (name.isNullOrEmpty() || season.isNullOrEmpty() || items == null || style == null) {
            return
        }

        _isProcessing.value = true
        viewModelScope.launch {
            try {
                val updatedStyle = style.copy(styleName = name, season = season)
                styleRepository.updateStyleWithItems(updatedStyle, items)
                _isUpdateComplete.postValue(true)
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun deleteStyle() {
        if (_isProcessing.value == true) return
        _isProcessing.value = true
        viewModelScope.launch {
            try {
                originalStyle?.let {
                    styleRepository.deleteStyleAndRefs(it)
                    _isDeleteComplete.postValue(true)
                }
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun getOriginalStyleName(): String? {
        return originalStyle?.styleName
    }
}