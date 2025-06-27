package com.yehyun.whatshouldiweartoday.ui.closet

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.yehyun.whatshouldiweartoday.data.database.AppDatabase
import com.yehyun.whatshouldiweartoday.data.database.ClothingItem
import com.yehyun.whatshouldiweartoday.data.repository.ClothingRepository
import kotlinx.coroutines.launch

class EditClothingViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ClothingRepository
    init {
        val clothingDao = AppDatabase.getDatabase(application).clothingDao()
        repository = ClothingRepository(clothingDao)
    }

    fun getClothingItem(id: Int): LiveData<ClothingItem> = repository.getItemById(id)
    fun updateClothingItem(item: ClothingItem) = viewModelScope.launch { repository.update(item) }
    // [추가] 삭제 기능
    fun deleteClothingItem(item: ClothingItem) = viewModelScope.launch { repository.delete(item) }
}