// 파일 경로: app/src/main/java/com/yehyun/whatshouldiweartoday/ui/style/StyleViewModel.kt

package com.yehyun.whatshouldiweartoday.ui.style

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import com.yehyun.whatshouldiweartoday.data.database.AppDatabase
import com.yehyun.whatshouldiweartoday.data.database.StyleWithItems
import com.yehyun.whatshouldiweartoday.data.preference.SettingsManager
import com.yehyun.whatshouldiweartoday.data.repository.StyleRepository

class StyleViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: StyleRepository
    // ▼▼▼▼▼ 핵심 수정 부분 ▼▼▼▼▼
    private val settingsManager = SettingsManager(application)
    private val _searchQuery = MutableLiveData("")
    // ViewModel이 처음 생성될 때 SettingsManager에서 저장된 정렬 값을 불러옵니다.
    private val _sortType = MutableLiveData(settingsManager.styleSortType)
    // ▲▲▲▲▲ 핵심 수정 부분 ▲▲▲▲▲

    val styles = MediatorLiveData<List<StyleWithItems>>()
    private var currentSource: LiveData<List<StyleWithItems>>? = null

    init {
        val styleDao = AppDatabase.getDatabase(application).styleDao()
        repository = StyleRepository(styleDao)

        styles.addSource(_searchQuery) { updateStylesSource() }
        styles.addSource(_sortType) { updateStylesSource() }
        updateStylesSource()
    }

    private fun updateStylesSource() {
        val query = _searchQuery.value ?: ""
        val sort = _sortType.value ?: "최신순"

        currentSource?.let { styles.removeSource(it) }

        val newSource = repository.getStyles(query, sort)
        currentSource = newSource

        styles.addSource(newSource) { result ->
            styles.value = result
        }
    }

    fun setSearchQuery(query: String) {
        if (_searchQuery.value != query) _searchQuery.value = query
    }

    // ▼▼▼▼▼ 핵심 수정 부분 ▼▼▼▼▼
    fun setSortType(sortType: String) {
        if (_sortType.value != sortType) {
            _sortType.value = sortType
            // 새로운 정렬 값이 선택되면 SettingsManager에 즉시 저장합니다.
            settingsManager.styleSortType = sortType
        }
    }

    // 현재 정렬 상태를 외부(Fragment)에 알려주기 위한 getter
    fun getCurrentSortType(): String? = _sortType.value
    // ▲▲▲▲▲ 핵심 수정 부분 ▲▲▲▲▲

    fun resetState() {
        _searchQuery.value = ""
        _sortType.value = "최신순"
    }
}