package com.yehyun.whatshouldiweartoday.ui.mall

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import com.yehyun.whatshouldiweartoday.data.database.mall.MallDatabase
import com.yehyun.whatshouldiweartoday.data.database.mall.MallItem
import com.yehyun.whatshouldiweartoday.data.repository.MallRepository

class MallMainViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = MallRepository(MallDatabase.getDatabase(application).mallDao())

    private val _selectedCategory = MutableLiveData<String?>(null)
    private val _searchQuery = MutableLiveData<String>("")

    val selectedCategory: LiveData<String?> = _selectedCategory

    private val _categoryItems: LiveData<List<MallItem>> = _selectedCategory.switchMap { cat ->
        if (cat == null) repo.getAllItems() else repo.getItemsByCategory(cat)
    }

    val items = MediatorLiveData<List<MallItem>>()

    init {
        items.addSource(_categoryItems) { applyFilter() }
        items.addSource(_searchQuery) { applyFilter() }
    }

    private fun applyFilter() {
        val all = _categoryItems.value ?: emptyList()
        val query = _searchQuery.value?.trim() ?: ""
        items.value = if (query.isBlank()) all else all.filter {
            it.name.contains(query, ignoreCase = true) ||
            it.brand.contains(query, ignoreCase = true) ||
            it.tags.contains(query, ignoreCase = true)
        }
    }

    fun selectCategory(category: String?) {
        _selectedCategory.value = category
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }
}
