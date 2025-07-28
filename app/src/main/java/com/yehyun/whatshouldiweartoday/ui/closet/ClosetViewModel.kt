package com.yehyun.whatshouldiweartoday.ui.closet

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.yehyun.whatshouldiweartoday.data.database.AppDatabase
import com.yehyun.whatshouldiweartoday.data.database.ClothingItem
import com.yehyun.whatshouldiweartoday.data.preference.SettingsManager
import com.yehyun.whatshouldiweartoday.data.repository.ClothingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class ClosetViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ClothingRepository
    private val settingsManager = SettingsManager(application)
    val workManager: WorkManager = WorkManager.getInstance(application)
    val batchAddWorkInfo: LiveData<List<WorkInfo>> = workManager.getWorkInfosForUniqueWorkLiveData("batch_add")

    private val _searchQuery = MutableLiveData("")
    private val _sortType = MutableLiveData(settingsManager.closetSortType)

    private val allClothes: LiveData<List<ClothingItem>>

    private val _categorizedClothes = mutableMapOf<String, MutableLiveData<List<ClothingItem>>>()
    private val categories = listOf("전체", "상의", "하의", "아우터", "신발", "가방", "모자", "기타")
    private val _sortChangedEvent = MutableLiveData<Unit>()
    val sortChangedEvent: LiveData<Unit> = _sortChangedEvent

    init {
        val clothingDao = AppDatabase.getDatabase(application).clothingDao()
        repository = ClothingRepository(clothingDao)

        // DB에서 가져오는 모든 옷 데이터 (정렬/필터는 ViewModel에서 처리)
        allClothes = repository.getItems("전체", "", "최신순")

        categories.forEach { category ->
            _categorizedClothes[category] = MutableLiveData()
        }

        // 옷 목록, 검색어, 정렬 방식 중 하나라도 변경되면 필터링을 다시 수행
        val filterTrigger = MediatorLiveData<Unit>()
        val triggerObserver = Observer<Any> { filterAndSortClothes() }

        filterTrigger.addSource(allClothes, triggerObserver)
        filterTrigger.addSource(_searchQuery, triggerObserver)
        filterTrigger.addSource(_sortType, triggerObserver)

        // MediatorLiveData가 활성화되도록 더미 관찰자 추가
        filterTrigger.observeForever { }
    }

    // 각 Fragment가 호출할 함수
    fun getClothesForCategory(category: String): LiveData<List<ClothingItem>> {
        return _categorizedClothes[category] ?: MutableLiveData()
    }

    private fun filterAndSortClothes() {
        // CPU 사용량이 많은 필터링/정렬 작업을 백그라운드 스레드에서 수행
        viewModelScope.launch(Dispatchers.Default) {
            val clothesList = allClothes.value ?: return@launch
            val query = _searchQuery.value ?: ""
            val sort = _sortType.value ?: settingsManager.closetSortType

            val filtered = if (query.isEmpty()) {
                clothesList
            } else {
                clothesList.filter { it.name.contains(query, ignoreCase = true) }
            }

            val sorted = when (sort) {
                "오래된 순" -> filtered.sortedBy { it.timestamp }
                "이름 오름차순" -> filtered.sortedBy { it.name }
                "이름 내림차순" -> filtered.sortedByDescending { it.name }
                "온도 오름차순" -> filtered.sortedBy { it.suitableTemperature }
                "온도 내림차순" -> filtered.sortedByDescending { it.suitableTemperature }
                else -> filtered.sortedByDescending { it.timestamp } // "최신순"
            }

            // 각 카테고리별로 데이터를 분류하여 LiveData에 postValue
            val groupedByCategory = sorted.groupBy { it.category }

            categories.forEach { category ->
                val categoryList = if (category == "전체") {
                    sorted
                } else {
                    groupedByCategory[category] ?: emptyList()
                }
                // postValue는 결과를 메인 스레드로 안전하게 전달
                _categorizedClothes[category]?.postValue(categoryList)
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
            settingsManager.closetSortType = sortType

            // ▼▼▼ 정렬 변경 이벤트 발생 ▼▼▼
            _sortChangedEvent.value = Unit
            // ▲▲▲ 이벤트 발생 ▲▲▲
        }
    }

    fun getCurrentSortType(): String = _sortType.value ?: settingsManager.closetSortType

    fun refreshData() {
        filterAndSortClothes()
    }
}