package com.yehyun.whatshouldiweartoday.ui.style

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import com.yehyun.whatshouldiweartoday.data.database.AppDatabase
import com.yehyun.whatshouldiweartoday.data.database.StyleWithItems
import com.yehyun.whatshouldiweartoday.data.repository.StyleRepository

class StyleViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: StyleRepository
    private val _searchQuery = MutableLiveData("")
    val styles: LiveData<List<StyleWithItems>>

    init {
        val styleDao = AppDatabase.getDatabase(application).styleDao()
        repository = StyleRepository(styleDao)
        styles = _searchQuery.switchMap { query ->
            repository.getStyles(query)
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }
}