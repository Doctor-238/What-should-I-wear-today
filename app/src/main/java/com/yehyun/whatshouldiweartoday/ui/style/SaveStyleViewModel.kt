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

    val selectedItems = MutableLiveData<MutableList<ClothingItem>>()
    val styleName = MutableLiveData<String>()
    val selectedSeason = MutableLiveData<String?>()

    private val _isSaveComplete = MutableLiveData(false)
    val isSaveComplete: LiveData<Boolean> = _isSaveComplete

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    val hasChanges = MutableLiveData(false)
    private var initialItemIds: Set<Int>? = null
    private var initialName: String? = "" // 초기값을 null이 아닌 빈 문자열로 설정
    private var initialSeason: String? = null


    init {
        val db = AppDatabase.getDatabase(application)
        clothingRepository = ClothingRepository(db.clothingDao())
        styleRepository = StyleRepository(db.styleDao())
        allClothes = clothingRepository.getItems("전체", "", "최신순")

        filteredClothes.addSource(allClothes) { clothesList ->
            validateSelectedItems(clothesList)
            filter()
        }
        filteredClothes.addSource(_clothingCategory) { filter() }
        filteredClothes.addSource(selectedItems) { filter() }
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
            if (currentList.size < 10) {
                currentList.add(item)
            }
        }
        selectedItems.value = currentList
        checkForChanges()
    }

    fun preselectItems(ids: IntArray) {
        if (initialItemIds != null) return

        initialItemIds = ids.toSet()
        val observer = object : androidx.lifecycle.Observer<List<ClothingItem>> {
            override fun onChanged(all: List<ClothingItem>) {
                val preselected = all.filter { it.id in ids }.toMutableList()
                selectedItems.postValue(preselected)
                // ▼▼▼▼▼ 핵심 수정: 초기 아이템 설정 후에도 변경사항을 확인합니다. ▼▼▼▼▼
                checkForChanges()
                // ▲▲▲▲▲ 핵심 수정 ▲▲▲▲▲
                allClothes.removeObserver(this)
            }
        }
        allClothes.observeForever(observer)
    }


    fun setStyleName(name: String) {
        if (initialName == null) initialName = "" // 최초 이름 설정 시 초기값 할당
        styleName.value = name
        checkForChanges()
    }

    fun setSeason(season: String?) {
        if (initialSeason == null) initialSeason = selectedSeason.value
        selectedSeason.value = season
        checkForChanges()
    }

    private fun checkForChanges() {
        // ▼▼▼▼▼ 핵심 수정: 초기값이 설정되지 않았으면 변경사항이 없는 것으로 간주합니다. ▼▼▼▼▼
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