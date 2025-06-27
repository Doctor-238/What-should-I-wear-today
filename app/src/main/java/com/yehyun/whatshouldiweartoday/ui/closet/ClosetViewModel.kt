package com.yehyun.whatshouldiweartoday.ui.closet

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import com.yehyun.whatshouldiweartoday.data.database.AppDatabase
import com.yehyun.whatshouldiweartoday.data.database.ClothingItem
import com.yehyun.whatshouldiweartoday.data.repository.ClothingRepository

class ClosetViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ClothingRepository

    private val _category = MutableLiveData("전체")
    private val _searchQuery = MutableLiveData("")
    private val _sortType = MutableLiveData("최신순")

    val clothes = MediatorLiveData<List<ClothingItem>>()

    private var currentSource: LiveData<List<ClothingItem>>? = null

    init {
        val clothingDao = AppDatabase.getDatabase(application).clothingDao()
        repository = ClothingRepository(clothingDao)
        updateClothesSource() // 초기 데이터 로드

        clothes.addSource(_category) { updateClothesSource() }
        clothes.addSource(_searchQuery) { updateClothesSource() }
        clothes.addSource(_sortType) { updateClothesSource() }
    }

    private fun updateClothesSource() {
        val category = _category.value ?: "전체"
        val query = _searchQuery.value ?: ""
        val sort = _sortType.value ?: "최신순"

        currentSource?.let { clothes.removeSource(it) }

        val newSource = repository.getItems(category, query, sort)
        currentSource = newSource

        clothes.addSource(newSource) { result ->
            clothes.value = result
        }
    }

    fun setCategory(category: String) { if (_category.value != category) _category.value = category }
    fun setSearchQuery(query: String) { if (_searchQuery.value != query) _searchQuery.value = query }
    fun setSortType(sortType: String) { if (_sortType.value != sortType) _sortType.value = sortType }
}