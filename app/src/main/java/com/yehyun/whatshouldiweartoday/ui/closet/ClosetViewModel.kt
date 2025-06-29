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

    // [수정] 카테고리 필터를 제거하고, 검색과 정렬만 담당
    private val _searchQuery = MutableLiveData("")
    private val _sortType = MutableLiveData("최신순")

    val clothes = MediatorLiveData<List<ClothingItem>>()

    private var currentSource: LiveData<List<ClothingItem>>? = null

    init {
        val clothingDao = AppDatabase.getDatabase(application).clothingDao()
        repository = ClothingRepository(clothingDao)

        // [수정] 카테고리가 빠지고, 쿼리와 정렬만 관찰
        clothes.addSource(_searchQuery) { updateClothesSource() }
        clothes.addSource(_sortType) { updateClothesSource() }
        updateClothesSource() // 초기 데이터 로드
    }

    private fun updateClothesSource() {
        // [수정] 항상 "전체" 카테고리를 기준으로 데이터를 가져옴
        val query = _searchQuery.value ?: ""
        val sort = _sortType.value ?: "최신순"

        currentSource?.let { clothes.removeSource(it) }

        val newSource = repository.getItems("전체", query, sort)
        currentSource = newSource

        clothes.addSource(newSource) { result ->
            clothes.value = result
        }
    }

    // setCategory 함수는 이제 사용되지 않으므로 제거하거나 주석 처리할 수 있습니다.
    // fun setCategory(category: String) { if (_category.value != category) _category.value = category }
    fun setSearchQuery(query: String) { if (_searchQuery.value != query) _searchQuery.value = query }
    fun setSortType(sortType: String) { if (_sortType.value != sortType) _sortType.value = sortType }
}