package com.yehyun.whatshouldiweartoday.ui.style

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import com.yehyun.whatshouldiweartoday.data.database.AppDatabase
import com.yehyun.whatshouldiweartoday.data.database.StyleWithItems
import com.yehyun.whatshouldiweartoday.data.preference.SettingsManager
import com.yehyun.whatshouldiweartoday.data.repository.StyleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

data class StyleTabState(
    val items: List<StyleWithItems> = emptyList(),
    val selectedItemIds: Set<Long> = emptySet(),
    val isDeleteMode: Boolean = false
)

class StyleViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: StyleRepository
    private val settingsManager = SettingsManager(application)

    private val _searchQuery = MutableLiveData("")
    val searchQuery: LiveData<String> = _searchQuery
    private val _sortType = MutableLiveData(settingsManager.styleSortType)

    private val allStyles: LiveData<List<StyleWithItems>>

    private val _categorizedStyles = mutableMapOf<String, MutableLiveData<List<StyleWithItems>>>()
    private val seasons = listOf("전체", "봄", "여름", "가을", "겨울")
    private val _sortTypeChanged = MutableLiveData<Unit>()
    val sortTypeChanged: LiveData<Unit> get() = _sortTypeChanged

    private val _isDeleteMode = MutableLiveData(false)
    val isDeleteMode: LiveData<Boolean> = _isDeleteMode

    private val _resetSearchEvent = MutableSharedFlow<Unit>()
    val resetSearchEvent = _resetSearchEvent.asSharedFlow()

    private val _selectedItems = MutableLiveData<Set<Long>>(emptySet())
    val selectedItems: LiveData<Set<Long>> = _selectedItems

    private val _currentTabIndex = MutableLiveData(0)
    val currentTabState = MediatorLiveData<StyleTabState>()

    private val _isDragging = MutableLiveData(false)
    val isDragging: LiveData<Boolean> = _isDragging

    private val _showDragAlert = MutableLiveData(false)
    val showDragAlert: LiveData<Boolean> = _showDragAlert
    private var dragAlertJob: Job? = null

    init {
        val styleDao = AppDatabase.getDatabase(application).styleDao()
        repository = StyleRepository(styleDao)
        allStyles = repository.getAllStylesWithItems()

        seasons.forEach { season ->
            _categorizedStyles[season] = MutableLiveData()
        }

        val filterTrigger = MediatorLiveData<Unit>()
        val triggerObserver = Observer<Any> { filterAndSortStyles() }

        filterTrigger.addSource(allStyles, triggerObserver)
        filterTrigger.addSource(_searchQuery, triggerObserver)
        filterTrigger.addSource(_sortType, triggerObserver)
        filterTrigger.observeForever {}

        val stateObserver = Observer<Any> {
            val tabIndex = _currentTabIndex.value ?: 0
            val season = seasons.getOrNull(tabIndex) ?: "전체"
            val itemsForSeason = _categorizedStyles[season]?.value ?: emptyList()
            val selectedIds = _selectedItems.value ?: emptySet()
            val deleteMode = _isDeleteMode.value ?: false

            val newState = StyleTabState(itemsForSeason, selectedIds, deleteMode)
            if (currentTabState.value != newState) {
                currentTabState.value = newState
            }
        }

        currentTabState.addSource(_currentTabIndex, stateObserver)
        currentTabState.addSource(_selectedItems, stateObserver)
        currentTabState.addSource(_isDeleteMode, stateObserver)
        _categorizedStyles.values.forEach {
            currentTabState.addSource(it, stateObserver)
        }
    }

    // ▼▼▼ 드래그 상태를 리셋하는 함수 추가 ▼▼▼
    fun resetDraggingState() {
        dragAlertJob?.cancel()
        if (_isDragging.value == true) {
            _isDragging.value = false
        }
        if (_showDragAlert.value == true) {
            _showDragAlert.value = false
        }
    }
    // ▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲▲

    fun getStylesForSeason(season: String): LiveData<List<StyleWithItems>> {
        return _categorizedStyles[season] ?: MutableLiveData()
    }

    private fun filterAndSortStyles() {
        viewModelScope.launch(Dispatchers.Default) {
            val styleList = allStyles.value ?: return@launch
            val query = _searchQuery.value ?: ""
            val sort = _sortType.value ?: settingsManager.styleSortType

            val filtered = if (query.isEmpty()) {
                styleList
            } else {
                styleList.filter { it.style.styleName.contains(query, ignoreCase = true) }
            }

            val sorted = when (sort) {
                "오래된 순" -> filtered.sortedBy { it.style.styleId }
                "이름 오름차순" -> filtered.sortedBy { it.style.styleName }
                "이름 내림차순" -> filtered.sortedByDescending { it.style.styleName }
                else -> filtered.sortedByDescending { it.style.styleId } // "최신순"
            }

            val groupedBySeason = sorted.groupBy { it.style.season }

            seasons.forEach { season ->
                val seasonList = if (season == "전체") {
                    sorted
                } else {
                    groupedBySeason[season] ?: emptyList()
                }
                _categorizedStyles[season]?.postValue(seasonList)
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
            _sortTypeChanged.value = Unit
        }
    }

    fun setDragging(dragging: Boolean) {
        if (_isDragging.value == dragging) return

        _isDragging.value = dragging

        if (dragging) {
            dragAlertJob = viewModelScope.launch {
                delay(500L)
                if (_isDragging.value == true) {
                    _showDragAlert.postValue(true)
                }
            }
        } else {
            dragAlertJob?.cancel()
            _showDragAlert.value = false
        }
    }

    fun getCurrentSortType(): String = _sortType.value ?: settingsManager.styleSortType

    fun setCurrentTabIndex(index: Int) {
        if (_currentTabIndex.value != index) {
            _currentTabIndex.value = index
        }
    }

    fun enterDeleteMode(initialStyleId: Long) {
        if (_isDeleteMode.value == false) {
            viewModelScope.launch { _resetSearchEvent.emit(Unit) }
            _isDeleteMode.value = true
            _selectedItems.value = setOf(initialStyleId)
        }
    }

    fun exitDeleteMode() {
        if (_isDeleteMode.value == true) {
            viewModelScope.launch { _resetSearchEvent.emit(Unit) }
            _isDeleteMode.value = false
            _selectedItems.value = emptySet()
        }
    }

    fun toggleItemSelection(styleId: Long) {
        val currentSelected = _selectedItems.value ?: emptySet()
        _selectedItems.value = if (currentSelected.contains(styleId)) {
            currentSelected - styleId
        } else {
            currentSelected + styleId
        }
    }

    fun selectAll(itemsToSelect: List<StyleWithItems>) {
        val currentSelected = _selectedItems.value ?: emptySet()
        _selectedItems.value = currentSelected + itemsToSelect.map { it.style.styleId }
    }

    fun deselectAll(itemsToDeselect: List<StyleWithItems>) {
        val currentSelected = _selectedItems.value ?: emptySet()
        _selectedItems.value = currentSelected - itemsToDeselect.map { it.style.styleId }.toSet()
    }

    fun deleteSelectedItems() {
        viewModelScope.launch(Dispatchers.IO) {
            val itemsToDeleteIds = _selectedItems.value ?: return@launch
            if (itemsToDeleteIds.isEmpty()) return@launch

            val allItems = allStyles.value ?: return@launch
            val itemsToDelete = allItems.filter { it.style.styleId in itemsToDeleteIds }

            itemsToDelete.forEach {
                repository.deleteStyleAndRefs(it.style)
            }
        }
        exitDeleteMode()
    }
}