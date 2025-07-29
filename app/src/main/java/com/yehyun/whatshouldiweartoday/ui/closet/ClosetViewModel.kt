// 파일 경로: app/src/main/java/com/yehyun/whatshouldiweartoday/ui/closet/ClosetViewModel.kt
package com.yehyun.whatshouldiweartoday.ui.closet

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.yehyun.whatshouldiweartoday.data.database.AppDatabase
import com.yehyun.whatshouldiweartoday.data.database.ClothingItem
import com.yehyun.whatshouldiweartoday.data.preference.SettingsManager
import com.yehyun.whatshouldiweartoday.data.repository.ClothingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.File

// 현재 탭의 모든 상태를 담는 데이터 클래스
data class CurrentTabState(
    val items: List<ClothingItem> = emptyList(),
    val selectedItemIds: Set<Int> = emptySet(),
    val isDeleteMode: Boolean = false
)

class ClosetViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ClothingRepository
    private val settingsManager = SettingsManager(application)
    val workManager: WorkManager = WorkManager.getInstance(application)
    val batchAddWorkInfo: LiveData<List<WorkInfo>> = workManager.getWorkInfosForUniqueWorkLiveData("batch_add")

    private val _searchQuery = MutableLiveData("")
    private val _sortType = MutableLiveData(settingsManager.closetSortType)

    private val allClothes: LiveData<List<ClothingItem>>

    private val _categorizedClothes = mutableMapOf<String, MutableLiveData<List<ClothingItem>>>()
    private val categories = listOf("전체", "상의", "하의", "아우터", "신발", "가방", "모자", "기타")
    private val _sortChangedEvent = MutableLiveData<Unit>()
    val sortChangedEvent: LiveData<Unit> = _sortChangedEvent

    private val _isDeleteMode = MutableLiveData(false)
    val isDeleteMode: LiveData<Boolean> = _isDeleteMode

    private val _selectedItems = MutableLiveData<Set<Int>>(emptySet())
    val selectedItems: LiveData<Set<Int>> = _selectedItems
    private val _resetSearchEvent = MutableSharedFlow<Unit>()
    val resetSearchEvent = _resetSearchEvent.asSharedFlow()

    // 현재 탭 인덱스와 상태를 관리할 LiveData
    private val _currentTabIndex = MutableLiveData(0)
    val currentTabState = MediatorLiveData<CurrentTabState>()

    init {
        val clothingDao = AppDatabase.getDatabase(application).clothingDao()
        repository = ClothingRepository(clothingDao)

        allClothes = repository.getItems("전체", "", "최신순")

        categories.forEach { category ->
            _categorizedClothes[category] = MutableLiveData()
        }

        val filterTrigger = MediatorLiveData<Unit>()
        val triggerObserver = Observer<Any> { filterAndSortClothes() }

        filterTrigger.addSource(allClothes, triggerObserver)
        filterTrigger.addSource(_searchQuery, triggerObserver)
        filterTrigger.addSource(_sortType, triggerObserver)

        filterTrigger.observeForever { }

        // currentTabState 설정 로직
        val stateObserver = Observer<Any> {
            val tabIndex = _currentTabIndex.value ?: 0
            val category = categories.getOrNull(tabIndex) ?: "전체"
            val itemsForCategory = _categorizedClothes[category]?.value ?: emptyList()
            val selectedIds = _selectedItems.value ?: emptySet()
            val deleteMode = _isDeleteMode.value ?: false

            val newState = CurrentTabState(itemsForCategory, selectedIds, deleteMode)
            if (currentTabState.value != newState) {
                currentTabState.value = newState
            }
        }

        currentTabState.addSource(_currentTabIndex, stateObserver)
        currentTabState.addSource(_selectedItems, stateObserver)
        currentTabState.addSource(_isDeleteMode, stateObserver)
        _categorizedClothes.values.forEach {
            currentTabState.addSource(it, stateObserver)
        }
    }

    fun getClothesForCategory(category: String): LiveData<List<ClothingItem>> {
        return _categorizedClothes[category] ?: MutableLiveData()
    }

    private fun filterAndSortClothes() {
        viewModelScope.launch(Dispatchers.Default) {
            val clothesList = allClothes.value ?: return@launch
            val query = _searchQuery.value ?: ""
            val sort = _sortType.value ?: settingsManager.closetSortType

            val filtered = if (query.isEmpty()) {
                clothesList
            } else {
                clothesList.filter { it.name.contains(query, ignoreCase = true) }
            }

            val sorted = when (sort) {
                "오래된 순" -> filtered.sortedBy { it.timestamp }
                "이름 오름차순" -> filtered.sortedBy { it.name }
                "이름 내림차순" -> filtered.sortedByDescending { it.name }
                "온도 오름차순" -> filtered.sortedBy { it.suitableTemperature }
                "온도 내림차순" -> filtered.sortedByDescending { it.suitableTemperature }
                else -> filtered.sortedByDescending { it.timestamp } // "최신순"
            }

            val groupedByCategory = sorted.groupBy { it.category }

            categories.forEach { category ->
                val categoryList = if (category == "전체") {
                    sorted
                } else {
                    groupedByCategory[category] ?: emptyList()
                }
                _categorizedClothes[category]?.postValue(categoryList)
            }
        }
    }

    fun setSearchQuery(query: String) {
        if (_searchQuery.value != query) {
            _searchQuery.value = query
        }
    }

    fun setSortType(sortType: String) {
        if (_sortType.value != sortType) {
            _sortType.value = sortType
            settingsManager.closetSortType = sortType
            _sortChangedEvent.value = Unit
        }
    }

    fun getCurrentSortType(): String = _sortType.value ?: settingsManager.closetSortType

    fun refreshData() {
        filterAndSortClothes()
    }

    fun setCurrentTabIndex(index: Int) {
        if (_currentTabIndex.value != index) {
            _currentTabIndex.value = index
        }
    }

    fun enterDeleteMode(initialItemId: Int) {
        if (_isDeleteMode.value == false) {
            viewModelScope.launch { _resetSearchEvent.emit(Unit) }
            _isDeleteMode.value = true
            _selectedItems.value = setOf(initialItemId)
        }
    }

    fun exitDeleteMode() {
        if (_isDeleteMode.value == true) {
            viewModelScope.launch { _resetSearchEvent.emit(Unit) }
            _isDeleteMode.value = false
            _selectedItems.value = emptySet()
        }
    }

    fun toggleItemSelection(itemId: Int) {
        val currentSelected = _selectedItems.value ?: emptySet()
        _selectedItems.value = if (currentSelected.contains(itemId)) {
            currentSelected - itemId
        } else {
            currentSelected + itemId
        }
    }

    fun selectAll(itemsToSelect: List<ClothingItem>) {
        val currentSelected = _selectedItems.value ?: emptySet()
        _selectedItems.value = currentSelected + itemsToSelect.map { it.id }
    }

    fun deselectAll(itemsToDeselect: List<ClothingItem>) {
        val currentSelected = _selectedItems.value ?: emptySet()
        _selectedItems.value = currentSelected - itemsToDeselect.map { it.id }.toSet()
    }


    fun deleteSelectedItems() {
        viewModelScope.launch(Dispatchers.IO) {
            val itemsToDeleteIds = _selectedItems.value ?: return@launch
            if (itemsToDeleteIds.isEmpty()) return@launch

            val allItems = allClothes.value ?: return@launch
            val itemsToDelete = allItems.filter { it.id in itemsToDeleteIds }

            itemsToDelete.forEach { item ->
                try {
                    item.imageUri?.let { uri -> if(File(uri).exists()) File(uri).delete() }
                    item.processedImageUri?.let { uri -> if(File(uri).exists()) File(uri).delete() }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                repository.delete(item)
            }
        }
        exitDeleteMode()
    }
}