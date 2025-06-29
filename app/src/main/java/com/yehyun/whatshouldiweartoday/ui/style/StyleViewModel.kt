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
    fun setSortType(sortType: String) {
        if (_sortType.value != sortType) _sortType.value = sortType
    }
}