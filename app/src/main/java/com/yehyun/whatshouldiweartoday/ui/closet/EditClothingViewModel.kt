package com.yehyun.whatshouldiweartoday.ui.closet

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.yehyun.whatshouldiweartoday.data.database.AppDatabase
import com.yehyun.whatshouldiweartoday.data.database.ClothingItem
import com.yehyun.whatshouldiweartoday.data.database.StyleDao
import com.yehyun.whatshouldiweartoday.data.repository.ClothingRepository
import kotlinx.coroutines.launch

class EditClothingViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ClothingRepository
    private val styleDao: StyleDao

    private val _itemId = MutableLiveData<Int>()
    val clothingItemFromDb: LiveData<ClothingItem>

    private var originalClothingItem: ClothingItem? = null
    private val _currentClothingItem = MutableLiveData<ClothingItem?>()
    val currentClothingItem: LiveData<ClothingItem?> = _currentClothingItem

    private val _isChanged = MutableLiveData<Boolean>(false)
    val isChanged: LiveData<Boolean> = _isChanged

    private val _isProcessing = MutableLiveData(false)
    val isProcessing: LiveData<Boolean> = _isProcessing

    private val _isSaveComplete = MutableLiveData<Boolean>(false)
    val isSaveComplete: LiveData<Boolean> = _isSaveComplete

    private val _isDeleteComplete = MutableLiveData<Boolean>(false)
    val isDeleteComplete: LiveData<Boolean> = _isDeleteComplete

    init {
        val db = AppDatabase.getDatabase(application)
        repository = ClothingRepository(db.clothingDao())
        styleDao = db.styleDao()

        clothingItemFromDb = _itemId.switchMap { id ->
            repository.getItemById(id)
        }
    }

    fun loadClothingItem(id: Int) {
        if (_itemId.value == id) return
        _itemId.value = id
    }

    fun setInitialState(item: ClothingItem) {
        if (originalClothingItem == null || originalClothingItem?.id != item.id) {
            originalClothingItem = item.copy()
            _currentClothingItem.value = item.copy()
            _isChanged.value = false
        }
    }

    fun updateName(name: String) {
        _currentClothingItem.value?.let {
            if (it.name != name) {
                it.name = name
                checkForChanges()
            }
        }
    }

    // ▼▼▼▼▼ 핵심 수정 부분 ▼▼▼▼▼
    fun updateCategory(category: String) {
        _currentClothingItem.value?.let {
            if (it.category != category) {
                it.category = category

                // 카테고리 변경 시, baseTemperature를 기준으로 suitableTemperature를 다시 계산
                val baseTemp = it.baseTemperature
                it.suitableTemperature = when (category) {
                    "아우터" -> baseTemp - 3.0
                    "상의", "하의" -> baseTemp + 2.0
                    else -> baseTemp
                }

                _currentClothingItem.postValue(it) // LiveData 업데이트하여 UI에 즉시 반영
                checkForChanges()
            }
        }
    }
    // ▲▲▲▲▲ 핵심 수정 부분 ▲▲▲▲▲


    fun updateUseProcessedImage(use: Boolean) {
        _currentClothingItem.value?.let {
            if (it.useProcessedImage != use) {
                it.useProcessedImage = use
                _currentClothingItem.postValue(it)
                checkForChanges()
            }
        }
    }

    private fun checkForChanges() {
        _isChanged.value = originalClothingItem != _currentClothingItem.value
    }

    fun saveChanges() {
        if (_isProcessing.value == true) return
        _currentClothingItem.value?.let {
            _isProcessing.value = true
            viewModelScope.launch {
                try {
                    repository.update(it)
                    _isSaveComplete.value = true
                } finally {
                    _isProcessing.value = false
                }
            }
        }
    }

    fun deleteClothingItem() {
        if (_isProcessing.value == true) return
        _currentClothingItem.value?.let { itemToDelete ->
            _isProcessing.value = true
            viewModelScope.launch {
                try {
                    repository.delete(itemToDelete)
                    styleDao.deleteOrphanedStyles()
                    _isDeleteComplete.value = true
                } finally {
                    _isProcessing.value = false
                }
            }
        }
    }
}