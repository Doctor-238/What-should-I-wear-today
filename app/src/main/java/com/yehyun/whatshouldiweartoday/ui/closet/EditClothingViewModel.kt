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

    // 특정 ID의 옷 정보를 가져오는 함수
    fun getClothingItem(id: Int): LiveData<ClothingItem> {
        return repository.getItemById(id)
    }

    // 옷 정보를 업데이트하는 함수
    fun updateClothingItem(item: ClothingItem) {
        viewModelScope.launch {
            repository.update(item)
        }
    }
}
