// 파일 경로: app/src/main/java/com/yehyun/whatshouldiweartoday/ui/closet/ClosetViewModel.kt

package com.yehyun.whatshouldiweartoday.ui.closet

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.yehyun.whatshouldiweartoday.data.database.AppDatabase
import com.yehyun.whatshouldiweartoday.data.database.ClothingItem
import com.yehyun.whatshouldiweartoday.data.preference.SettingsManager
import com.yehyun.whatshouldiweartoday.data.repository.ClothingRepository
import java.util.UUID

class ClosetViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ClothingRepository
    private val settingsManager = SettingsManager(application)
    private val _searchQuery = MutableLiveData("")
    private val _sortType = MutableLiveData(settingsManager.closetSortType)

    val clothes = MediatorLiveData<List<ClothingItem>>()
    private var currentSource: LiveData<List<ClothingItem>>? = null

    val workManager = WorkManager.getInstance(application)
    val batchAddWorkInfo: LiveData<List<WorkInfo>> = workManager.getWorkInfosForUniqueWorkLiveData("batch_add")

    val processedWorkIds = mutableSetOf<UUID>()

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

    fun setSortType(sortType: String) {
        if (_sortType.value != sortType) {
            _sortType.value = sortType
            settingsManager.closetSortType = sortType
        }
    }

    fun getCurrentSortType(): String? = _sortType.value

    fun resetState() {
        _searchQuery.value = ""
        _sortType.value = "최신순"
    }
}