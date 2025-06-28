package com.yehyun.whatshouldiweartoday.ui.style

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.yehyun.whatshouldiweartoday.data.database.AppDatabase
import com.yehyun.whatshouldiweartoday.data.database.ClothingItem
import com.yehyun.whatshouldiweartoday.data.repository.ClothingRepository
import com.yehyun.whatshouldiweartoday.data.repository.StyleRepository
import kotlinx.coroutines.launch

class SaveStyleViewModel(application: Application) : AndroidViewModel(application) {
    private val clothingRepository: ClothingRepository
    private val styleRepository: StyleRepository
    val allClothes: LiveData<List<ClothingItem>>

    init {
        val db = AppDatabase.getDatabase(application)
        clothingRepository = ClothingRepository(db.clothingDao())
        styleRepository = StyleRepository(db.styleDao())
        allClothes = clothingRepository.getItems("전체", "", "최신순")
    }

    fun saveStyle(styleName: String, selectedItems: List<ClothingItem>) {
        viewModelScope.launch {
            styleRepository.insertStyleWithItems(styleName, selectedItems)
        }
    }
}