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

    val canBeSaved = MediatorLiveData<Boolean>().apply {
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
        _currentClothingItem.value?.let { current ->
            if (current.name != name) {
                _currentClothingItem.value = current.copy(name = name)
                checkForChanges()
            }
        }
    }

    fun updateCategory(category: String) {
        _currentClothingItem.value?.let { current ->
            if (current.category != category) {
                _currentClothingItem.value = current.copy(
                    category = category,
                    suitableTemperature = defaultTemperatureForCategory(category, current.baseTemperature)
                )
                checkForChanges()
            }
        }
    }

    fun increaseTemp() {
        _currentClothingItem.value?.let { current ->
            _currentClothingItem.value = current.copy(
                suitableTemperature = current.suitableTemperature + 0.5
            )
            checkForChanges()
        }
    }

    fun decreaseTemp() {
        _currentClothingItem.value?.let { current ->
            _currentClothingItem.value = current.copy(
                suitableTemperature = current.suitableTemperature - 0.5
            )
            checkForChanges()
        }
    }

    fun resetToAiDefaults() {
        _currentClothingItem.value?.let { current ->
            val resetCategory = aiDefaultCategory(current)
            _currentClothingItem.value = current.copy(
                category = resetCategory,
                suitableTemperature = defaultTemperatureForCategory(resetCategory, current.baseTemperature),
                size = null,
                purpose = originalClothingItem?.purpose ?: current.purpose
            )
            checkForChanges()
        }
    }

    fun updatePurposes(list: List<String>) {
        _currentClothingItem.value?.let { current ->
            val purposeStr = list.joinToString(",")
            if (current.purpose != purposeStr) {
                _currentClothingItem.value = current.copy(purpose = purposeStr)
                checkForChanges()
            }
        }
    }

    fun updateSize(size: String?) {
        _currentClothingItem.value?.let { current ->
            if (current.size != size) {
                _currentClothingItem.value = current.copy(size = size)
                checkForChanges()
            }
        }
    }

    fun updateUseProcessedImage(use: Boolean) {
        _currentClothingItem.value?.let { current ->
            if (current.useProcessedImage != use) {
                _currentClothingItem.value = current.copy(useProcessedImage = use)
                checkForChanges()
            }
        }
    }

    fun isResetNeeded(item: ClothingItem): Boolean {
        val resetCategory = aiDefaultCategory(item)
        val resetTemp = defaultTemperatureForCategory(resetCategory, item.baseTemperature)
        val categoryChanged = item.category != resetCategory
        val tempChanged = kotlin.math.abs(item.suitableTemperature - resetTemp) > 0.01
        val sizeChanged = item.size != null
        val purposeChanged = item.purpose != (originalClothingItem?.purpose ?: item.purpose)
        return categoryChanged || tempChanged || sizeChanged || purposeChanged
    }

    private fun aiDefaultCategory(item: ClothingItem): String {
        return item.aiCategory
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: originalClothingItem?.category
            ?: item.category
    }

    private fun defaultTemperatureForCategory(category: String, baseTemperature: Double): Double {
        return when (category) {
            "아우터" -> baseTemperature - 3.0
            "상의", "하의" -> baseTemperature + 2.0
            else -> baseTemperature
        }
    }

    private fun checkForChanges() {
        _isChanged.value = originalClothingItem != _currentClothingItem.value
    }

    fun saveChanges() {
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

    fun deleteClothingItem(skipOrphanCleanup: Boolean = false) {
        if (_isProcessing.value == true) return
        _currentClothingItem.value?.let { itemToDelete ->
            _isProcessing.value = true
            viewModelScope.launch {
                try {
                    repository.delete(itemToDelete)
                    if (!skipOrphanCleanup) {
                        styleDao.deleteOrphanedStyles()
                    }
                    _isDeleteComplete.postValue(true)
                } finally {
                    _isProcessing.postValue(false)
                }
            }
        }
    }
}
