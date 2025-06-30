// app/src/main/java/com/yehyun/whatshouldiweartoday/ui/closet/EditClothingViewModel.kt

package com.yehyun.whatshouldiweartoday.ui.closet

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.yehyun.whatshouldiweartoday.data.database.AppDatabase
import com.yehyun.whatshouldiweartoday.data.database.ClothingItem
import com.yehyun.whatshouldiweartoday.data.database.StyleDao
import com.yehyun.whatshouldiweartoday.data.repository.ClothingRepository
import kotlinx.coroutines.launch

class EditClothingViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ClothingRepository
    private val styleDao: StyleDao

    private val _originalClothingItem = MutableLiveData<ClothingItem?>()

    private val _currentClothingItem = MutableLiveData<ClothingItem?>()
    val currentClothingItem: LiveData<ClothingItem?> = _currentClothingItem

    private val _isChanged = MutableLiveData<Boolean>(false)
    val isChanged: LiveData<Boolean> = _isChanged

    private val _isSaveComplete = MutableLiveData<Boolean>(false)
    val isSaveComplete: LiveData<Boolean> = _isSaveComplete

    private val _isDeleteComplete = MutableLiveData<Boolean>(false)
    val isDeleteComplete: LiveData<Boolean> = _isDeleteComplete

    init {
        val db = AppDatabase.getDatabase(application)
        repository = ClothingRepository(db.clothingDao())
        styleDao = db.styleDao()
    }

    fun loadClothingItem(id: Int) {
        // 이미 로드된 아이템이 있고, ID가 같다면 다시 로드하지 않음
        if (_originalClothingItem.value?.id == id) {
            return
        }
        viewModelScope.launch {
            val item = repository.getItemById(id)
            item.observeForever { clothingItem ->
                if (_originalClothingItem.value == null) {
                    _originalClothingItem.value = clothingItem
                }
                _currentClothingItem.value = clothingItem
                checkForChanges()
            }
        }
    }

    fun updateName(name: String) {
        _currentClothingItem.value?.let {
            it.name = name
            checkForChanges()
        }
    }

    fun updateCategory(category: String) {
        _currentClothingItem.value?.let {
            it.category = category
            _currentClothingItem.postValue(it) // LiveData 갱신
            checkForChanges()
        }
    }

    fun updateUseProcessedImage(use: Boolean) {
        _currentClothingItem.value?.let {
            it.useProcessedImage = use
            _currentClothingItem.postValue(it) // LiveData 갱신
            checkForChanges()
        }
    }

    private fun checkForChanges() {
        _isChanged.value = _originalClothingItem.value != _currentClothingItem.value
    }

    fun saveChanges() {
        _currentClothingItem.value?.let {
            viewModelScope.launch {
                repository.update(it)
                _isSaveComplete.value = true
            }
        }
    }

    fun deleteClothingItem() {
        _currentClothingItem.value?.let { itemToDelete ->
            viewModelScope.launch {
                repository.delete(itemToDelete)
                styleDao.deleteOrphanedStyles()
                _isDeleteComplete.value = true
            }
        }
    }

    fun resetCompletionState() {
        _isSaveComplete.value = false
        _isDeleteComplete.value = false
    }

    fun resetAllState() {
        _originalClothingItem.value = null
        _currentClothingItem.value = null
        _isChanged.value = false
        resetCompletionState()
    }
}