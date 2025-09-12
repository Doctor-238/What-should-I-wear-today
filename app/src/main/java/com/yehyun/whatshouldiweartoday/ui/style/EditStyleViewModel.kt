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

    private val allClothesLatestOrder: LiveData<List<ClothingItem>>
    private val allClothesTempOrder: LiveData<List<ClothingItem>>

    private var originalStyle: SavedStyle? = null
    private var initialItemIds: Set<Int>? = null
    private var currentStyleId: Long? = null
    private var isInitialLoadComplete = false

    val currentStyleName = MutableLiveData<String>()
    val currentSeason = MutableLiveData<String>()

    private val _selectedItems = MutableLiveData<MutableList<ClothingItem>>(mutableListOf())
    val selectedItems: LiveData<MutableList<ClothingItem>> = _selectedItems

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

        allClothesLatestOrder = clothingRepository.getItems("전체", "", "최신순")
        allClothesTempOrder = clothingRepository.getItems("전체", "", "온도 내림차순")

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
    }

    private fun validateSelectedItems(currentClothes: List<ClothingItem>?) {
        val allClothesList = currentClothes ?: return
        _selectedItems.value?.let { selected ->
            val currentSelectedIds = selected.map { it.id }.toSet()
            val refreshedSelectedItems = allClothesList.filter { it.id in currentSelectedIds }.toMutableList()

            if (selected.toSet() != refreshedSelectedItems.toSet()) {
                setSortedSelectedItems(refreshedSelectedItems)
            }
        }
    }

    private fun filter() {
        val category = _clothingCategory.value ?: "전체"
        val clothes = if (category == "전체") {
            allClothesLatestOrder.value ?: emptyList()
        } else {
            allClothesTempOrder.value ?: emptyList()
        }

        val selectedIdsSet = selectedItems.value?.map { it.id }?.toSet() ?: emptySet()

        val categoryFiltered = if (category == "전체") {
            clothes
        } else {
            clothes.filter { it.category == category }
        }

        val finalFiltered = categoryFiltered.filter { it.id !in selectedIdsSet }

        filteredClothes.value = finalFiltered
    }

    fun loadStyleIfNeeded(styleId: Long) {
        if (isInitialLoadComplete && currentStyleId == styleId) return
        this.currentStyleId = styleId

        viewModelScope.launch {
            val styleWithItems = styleRepository.getStyleByIdSuspend(styleId)
            if (styleWithItems != null) {
                if (!isInitialLoadComplete) {
                    originalStyle = styleWithItems.style.copy()
                    initialItemIds = styleWithItems.items.map { it.id }.toSet()
                    isInitialLoadComplete = true
                }

                setSortedSelectedItems(styleWithItems.items.toMutableList())
                currentStyleName.value = styleWithItems.style.styleName
                currentSeason.value = styleWithItems.style.season
                toolbarTitle.value = "'${styleWithItems.style.styleName}' 수정"

                checkForChanges()
            } else {
                _isDeleteComplete.value = true
            }
        }
    }

    fun onFragmentResume() {
        if (!isInitialLoadComplete) return
        viewModelScope.launch {
            val allCurrentClothes = clothingRepository.getAllItemsList()
            _selectedItems.value?.let { currentSelectedItems ->
                val currentSelectedIds = currentSelectedItems.map { it.id }.toSet()
                val refreshedSelectedItems = allCurrentClothes
                    .filter { it.id in currentSelectedIds }
                    .toMutableList()

                if (currentSelectedItems.map { it.id }.toSet() != refreshedSelectedItems.map { it.id }.toSet()) {
                    setSortedSelectedItems(refreshedSelectedItems)
                }
            }
        }
    }

    private fun setSortedSelectedItems(items: MutableList<ClothingItem>) {
        val categoryOrder = mapOf(
            "상의" to 1, "하의" to 2, "아우터" to 3, "신발" to 4,
            "가방" to 5, "모자" to 6, "기타" to 7
        )
        val sortedList = items.sortedWith(
            compareBy { categoryOrder[it.category] ?: 8 }
        ).toMutableList()
        _selectedItems.value = sortedList
        checkForChanges()
    }


    fun setClothingFilter(category: String) {
        if (_clothingCategory.value != category) {
            _clothingCategory.value = category
        }
    }

    fun toggleItemSelection(item: ClothingItem) {
        val currentList = _selectedItems.value ?: mutableListOf()
        if (currentList.any { it.id == item.id }) {
            currentList.removeAll { it.id == item.id }
        } else {
            if (currentList.size < 9) {
                currentList.add(item)
            }
        }
        setSortedSelectedItems(currentList)
    }


    private fun checkForChanges() {
        if (originalStyle == null || initialItemIds == null) {
            return
        }

        val nameChanged = originalStyle?.styleName != currentStyleName.value
        val seasonChanged = originalStyle?.season != currentSeason.value
        val itemsChanged = initialItemIds != selectedItems.value?.map { it.id }?.toSet()

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

        if (name.isNullOrEmpty() || season.isNullOrEmpty() || style == null) {
            return
        }

        if (items.isNullOrEmpty()) {
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

    fun handleCancelAndDeleteIfOrphaned() {
        viewModelScope.launch {
            val initialIds = initialItemIds ?: return@launch
            if (initialIds.isEmpty()) return@launch

            val allCurrentClothes = clothingRepository.getAllItemsList()
            val allCurrentIds = allCurrentClothes.map { it.id }.toSet()

            val stillExist = initialIds.any { it in allCurrentIds }

            if (!stillExist) {
                originalStyle?.let { styleRepository.deleteStyleAndRefs(it) }
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