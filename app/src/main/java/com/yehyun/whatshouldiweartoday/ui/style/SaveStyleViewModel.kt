// app/src/main/java/com/yehyun/whatshouldiweartoday/ui/style/SaveStyleViewModel.kt

package com.yehyun.whatshouldiweartoday.ui.style

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.yehyun.whatshouldiweartoday.data.database.AppDatabase
import com.yehyun.whatshouldiweartoday.data.database.ClothingItem
import com.yehyun.whatshouldiweartoday.data.database.SavedStyle
import com.yehyun.whatshouldiweartoday.data.repository.ClothingRepository
import com.yehyun.whatshouldiweartoday.data.repository.StyleRepository
import kotlinx.coroutines.launch

class SaveStyleViewModel(application: Application) : AndroidViewModel(application) {
    private val clothingRepository: ClothingRepository
    private val styleRepository: StyleRepository

    private val _clothingCategory = MutableLiveData("전체")
    val filteredClothes = MediatorLiveData<List<ClothingItem>>()
    private val allClothes: LiveData<List<ClothingItem>>

    val selectedItems = MutableLiveData<MutableSet<ClothingItem>>(mutableSetOf())
    val styleName = MutableLiveData<String>()
    val selectedSeason = MutableLiveData<String?>()

    private val _isSaveComplete = MutableLiveData(false)
    val isSaveComplete: LiveData<Boolean> = _isSaveComplete

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    val hasChanges = MutableLiveData(false)
    private var initialItemIds: Set<Int>? = null
    private var initialName: String? = null
    private var initialSeason: String? = null


    init {
        val db = AppDatabase.getDatabase(application)
        clothingRepository = ClothingRepository(db.clothingDao())
        styleRepository = StyleRepository(db.styleDao())
        allClothes = clothingRepository.getItems("전체", "", "최신순")

        filteredClothes.addSource(allClothes) { filter() }
        filteredClothes.addSource(_clothingCategory) { filter() }
    }

    private fun filter() {
        val category = _clothingCategory.value ?: "전체"
        val clothes = allClothes.value ?: emptyList()

        filteredClothes.value = if (category == "전체") {
            clothes
        } else {
            clothes.filter { it.category == category }
        }
    }

    fun setClothingFilter(category: String) {
        if (_clothingCategory.value != category) {
            _clothingCategory.value = category
        }
    }

    fun toggleItemSelected(item: ClothingItem) {
        val currentSet = selectedItems.value ?: mutableSetOf()
        if (currentSet.any { it.id == item.id }) {
            currentSet.removeAll { it.id == item.id }
        } else {
            if (currentSet.size < 10) {
                currentSet.add(item)
            }
        }
        selectedItems.value = currentSet
        checkForChanges()
    }

    fun preselectItems(ids: IntArray) {
        if (initialItemIds != null) return

        initialItemIds = ids.toSet()
        allClothes.observeForever { all ->
            val preselected = all.filter { it.id in ids }.toMutableSet()
            selectedItems.postValue(preselected)
            hasChanges.postValue(false)
        }
    }

    fun setStyleName(name: String) {
        if(initialName == null) initialName = styleName.value
        styleName.value = name
        checkForChanges()
    }

    fun setSeason(season: String?) {
        if(initialSeason == null) initialSeason = selectedSeason.value
        selectedSeason.value = season
        checkForChanges()
    }

    private fun checkForChanges() {
        if(initialItemIds == null && styleName.value.isNullOrEmpty() && selectedSeason.value.isNullOrEmpty()) {
            hasChanges.value = false
            return
        }

        val itemsChanged = initialItemIds != selectedItems.value?.map { it.id }?.toSet()
        val nameChanged = initialName != styleName.value
        val seasonChanged = initialSeason != selectedSeason.value

        hasChanges.value = itemsChanged || nameChanged || seasonChanged
    }


    fun saveStyle() {
        if (_isLoading.value == true) return

        val name = styleName.value
        val season = selectedSeason.value
        val items = selectedItems.value

        if (name.isNullOrEmpty() || season.isNullOrEmpty() || items.isNullOrEmpty()) {
            return
        }

        _isLoading.value = true
        viewModelScope.launch {
            try {
                val newStyle = SavedStyle(styleName = name, season = season)
                styleRepository.insertStyleWithItems(newStyle, items.toList())
                _isSaveComplete.postValue(true)
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    fun resetAllState() {
        selectedItems.value = mutableSetOf()
        styleName.value = ""
        selectedSeason.value = null
        _isSaveComplete.value = false
        hasChanges.value = false
        initialItemIds = null
        initialName = null
        initialSeason = null
        _isLoading.value = false
    }
}