// app/src/main/java/com/yehyun/whatshouldiweartoday/ui/closet/ClosetViewModel.kt

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

    fun refreshData() {
        updateClothesSource()
    }

    fun setSearchQuery(query: String) { if (_searchQuery.value != query) _searchQuery.value = query }
    fun setSortType(sortType: String) { if (_sortType.value != sortType) _sortType.value = sortType }

    /**
     * [추가] ViewModel의 상태(검색어, 정렬 순서)를 초기화하는 함수.
     * 이 함수가 호출되면 LiveData가 새로운 기본값으로 업데이트되고,
     * 이를 관찰하는 clothes LiveData도 자동으로 갱신됩니다.
     */
    fun resetState() {
        _searchQuery.value = ""
        _sortType.value = "최신순"
    }
}