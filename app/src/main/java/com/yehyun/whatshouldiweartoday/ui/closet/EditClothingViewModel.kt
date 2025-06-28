package com.yehyun.whatshouldiweartoday.ui.closet

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.yehyun.whatshouldiweartoday.data.database.AppDatabase
import com.yehyun.whatshouldiweartoday.data.database.ClothingItem
import com.yehyun.whatshouldiweartoday.data.database.StyleDao
import com.yehyun.whatshouldiweartoday.data.repository.ClothingRepository
import kotlinx.coroutines.launch

class EditClothingViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ClothingRepository
    // [수정] 스타일 정리를 위해 StyleDao를 직접 사용
    private val styleDao: StyleDao

    init {
        val db = AppDatabase.getDatabase(application)
        repository = ClothingRepository(db.clothingDao())
        styleDao = db.styleDao()
    }

    fun getClothingItem(id: Int): LiveData<ClothingItem> = repository.getItemById(id)
    fun updateClothingItem(item: ClothingItem) = viewModelScope.launch { repository.update(item) }

    // [수정] 옷 삭제 시, 스타일까지 함께 정리하는 새로운 로직
    fun deleteClothingItem(item: ClothingItem) {
        viewModelScope.launch {
            // 1. 이 옷을 포함하는 모든 스타일의 '연결고리'를 먼저 삭제 (DB에서 자동으로 처리됨)
            // 2. 옷 자체를 삭제합니다.
            repository.delete(item)
            // 3. 이제 아무 옷도 포함하지 않게 된 '빈 스타일'이 있다면, 그 스타일을 삭제합니다.
            styleDao.deleteOrphanedStyles()
        }
    }
}
