// 파일 경로: app/src/main/java/com/yehyun/whatshouldiweartoday/ui/closet/EditClothingViewModel.kt
package com.yehyun.whatshouldiweartoday.ui.closet

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
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

    // '저장 가능' 상태를 관리하는 LiveData
    val canBeSaved = MediatorLiveData<Boolean>().apply {
        // isChanged나 currentClothingItem이 변경될 때마다 저장 가능 여부를 다시 계산
        addSource(_isChanged) { value = checkCanBeSaved() }
        addSource(_currentClothingItem) { value = checkCanBeSaved() }
    }

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

    // 저장 가능 조건을 확인하는 함수
    private fun checkCanBeSaved(): Boolean {
        val hasChanges = _isChanged.value ?: false
        val isNameValid = !_currentClothingItem.value?.name.isNullOrBlank()
        return hasChanges && isNameValid
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
                _currentClothingItem.postValue(it) // LiveData를 업데이트하여 옵저버가 알 수 있도록 함
                checkForChanges()
            }
        }
    }

    fun updateCategory(category: String) {
        _currentClothingItem.value?.let {
            if (it.category != category) {
                it.category = category
                val baseTemp = it.baseTemperature
                it.suitableTemperature = when (category) {
                    "아우터" -> baseTemp - 3.0
                    "상의", "하의" -> baseTemp + 2.0
                    else -> baseTemp
                }
                _currentClothingItem.postValue(it)
                checkForChanges()
            }
        }
    }

    fun increaseTemp() {
        _currentClothingItem.value?.let {
            it.suitableTemperature += 0.5
            _currentClothingItem.postValue(it)
            checkForChanges()
        }
    }

    fun decreaseTemp() {
        _currentClothingItem.value?.let {
            it.suitableTemperature -= 0.5
            _currentClothingItem.postValue(it)
            checkForChanges()
        }
    }

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
        // 저장하기 전, ViewModel에서도 한 번 더 저장 가능 여부 확인
        if (_isProcessing.value == true || canBeSaved.value != true) return

        _currentClothingItem.value?.let {
            _isProcessing.value = true
            viewModelScope.launch {
                try {
                    repository.update(it)
                    _isSaveComplete.postValue(true)
                } finally {
                    _isProcessing.postValue(false)
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
                    _isDeleteComplete.postValue(true)
                } finally {
                    _isProcessing.postValue(false)
                }
            }
        }
    }
}