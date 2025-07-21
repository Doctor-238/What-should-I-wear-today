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
import kotlinx.coroutines.launch

class StyleViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: StyleRepository
    private val settingsManager = SettingsManager(application)

    private val _searchQuery = MutableLiveData("")
    private val _sortType = MutableLiveData(settingsManager.styleSortType)

    private val allStyles: LiveData<List<StyleWithItems>>

    private val _categorizedStyles = mutableMapOf<String, MutableLiveData<List<StyleWithItems>>>()
    private val seasons = listOf("전체", "봄", "여름", "가을", "겨울")

    init {
        val styleDao = AppDatabase.getDatabase(application).styleDao()
        repository = StyleRepository(styleDao)
        // ▼▼▼▼▼ 핵심 수정: 올바른 함수 호출로 변경 ▼▼▼▼▼
        allStyles = repository.getAllStylesWithItems()
        // ▲▲▲▲▲ 핵심 수정 ▲▲▲▲▲

        seasons.forEach { season ->
            _categorizedStyles[season] = MutableLiveData()
        }

        val filterTrigger = MediatorLiveData<Unit>()
        val triggerObserver = Observer<Any> { filterAndSortStyles() }

        filterTrigger.addSource(allStyles, triggerObserver)
        filterTrigger.addSource(_searchQuery, triggerObserver)
        filterTrigger.addSource(_sortType, triggerObserver)
        filterTrigger.observeForever {}
    }

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
            settingsManager.styleSortType = sortType
        }
    }

    fun getCurrentSortType(): String = _sortType.value ?: settingsManager.styleSortType
}