package com.yehyun.whatshouldiweartoday.ui.style

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import com.yehyun.whatshouldiweartoday.data.database.AppDatabase
import com.yehyun.whatshouldiweartoday.data.database.StyleWithItems
import com.yehyun.whatshouldiweartoday.data.repository.StyleRepository

class StyleViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: StyleRepository

    private val _searchQuery = MutableLiveData("")
    private val _sortType = MutableLiveData("최신순")

    val styles = MediatorLiveData<List<StyleWithItems>>()
    private var currentSource: LiveData<List<StyleWithItems>>? = null

    init {
        val styleDao = AppDatabase.getDatabase(application).styleDao()
        repository = StyleRepository(styleDao)

        // 필터나 정렬 기준이 바뀔 때마다 DB에 새로운 쿼리를 요청하도록 설정
        styles.addSource(_searchQuery) { updateStylesSource() }
        styles.addSource(_sortType) { updateStylesSource() }
        updateStylesSource() // 초기 데이터 로드
    }

    private fun updateStylesSource() {
        val query = _searchQuery.value ?: ""
        val sort = _sortType.value ?: "최신순"

        currentSource?.let { styles.removeSource(it) }

        // [수정 완료] 이제 Repository가 정렬 기능을 지원하므로, 오류 없이 작동합니다.
        val newSource = repository.getStyles(query, sort)
        currentSource = newSource

        styles.addSource(newSource) { result ->
            styles.value = result
        }
    }

    fun setSearchQuery(query: String) {
        if (_searchQuery.value != query) _searchQuery.value = query
    }
    fun setSortType(sortType: String) {
        if (_sortType.value != sortType) _sortType.value = sortType
    }
}
