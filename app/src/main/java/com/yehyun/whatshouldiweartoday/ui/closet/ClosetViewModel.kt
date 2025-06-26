package com.yehyun.whatshouldiweartoday.ui.closet

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import com.yehyun.whatshouldiweartoday.data.database.AppDatabase
import com.yehyun.whatshouldiweartoday.data.database.ClothingItem
import com.yehyun.whatshouldiweartoday.data.repository.ClothingRepository

class ClosetViewModel(application: Application) : AndroidViewModel(application) {

    // [수정] Repository를 먼저 초기화합니다.
    private val repository: ClothingRepository = ClothingRepository(AppDatabase.getDatabase(application).clothingDao())

    // 현재 선택된 카테고리와 검색어를 저장하는 LiveData
    private val _filterData = MutableLiveData(Pair("전체", ""))

    // _filterData의 값이 바뀔 때마다, Repository에 새로운 데이터를 요청하여 clothes 리스트를 자동으로 업데이트
    val clothes: LiveData<List<ClothingItem>> = _filterData.switchMap { (category, query) ->
        repository.getItems(category, query)
    }

    // [수정] init 블록은 더 이상 필요 없으므로 삭제합니다.

    // 카테고리 필터를 변경하는 함수
    fun setCategory(category: String) {
        _filterData.value = Pair(category, _filterData.value?.second ?: "")
    }

    // 검색어를 변경하는 함수
    fun setSearchQuery(query: String) {
        _filterData.value = Pair(_filterData.value?.first ?: "전체", query)
    }
}
