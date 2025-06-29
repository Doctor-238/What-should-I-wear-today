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
    private val _searchQuery = MutableLiveData("")
    private val _sortType = MutableLiveData("최신순")

    val clothes = MediatorLiveData<List<ClothingItem>>()
    private var currentSource: LiveData<List<ClothingItem>>? = null

    init {
        val clothingDao = AppDatabase.getDatabase(application).clothingDao()
        repository = ClothingRepository(clothingDao)
        clothes.addSource(_searchQuery) { updateClothesSource() }
        clothes.addSource(_sortType) { updateClothesSource() }
        updateClothesSource()
    }

    private fun updateClothesSource() {
        val query = _searchQuery.value ?: ""
        val sort = _sortType.value ?: "최신순"
        currentSource?.let { clothes.removeSource(it) }
        val newSource = repository.getItems("전체", query, sort)
        currentSource = newSource
        clothes.addSource(newSource) { result ->
            clothes.value = result
        }
    }

    // [추가] 설정 변경 후 UI를 강제로 새로고침하기 위한 함수
    fun refreshData() {
        updateClothesSource()
    }

    fun setSearchQuery(query: String) { if (_searchQuery.value != query) _searchQuery.value = query }
    fun setSortType(sortType: String) { if (_sortType.value != sortType) _sortType.value = sortType }
}