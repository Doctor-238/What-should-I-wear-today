package com.yehyun.whatshouldiweartoday.ui.mall

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.yehyun.whatshouldiweartoday.data.database.mall.MallDatabase
import com.yehyun.whatshouldiweartoday.data.database.mall.MallItem
import com.yehyun.whatshouldiweartoday.data.preference.SettingsManager
import com.yehyun.whatshouldiweartoday.ui.closet.AddClothingViewModel
import com.yehyun.whatshouldiweartoday.ui.home.DailyWeatherSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MallRecommendationResult(
    val tops: List<MallItem>,
    val bottoms: List<MallItem>,
    val outers: List<MallItem>
)

class MallRecommendationViewModel(application: Application) : AndroidViewModel(application) {

    private val mallDao = MallDatabase.getDatabase(application).mallDao()
    private val settings = SettingsManager(application)

    private val _result = MutableLiveData<MallRecommendationResult?>()
    val result: LiveData<MallRecommendationResult?> = _result

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    var maxItemsPerCategory = 3
    var recommendTops = true
    var recommendBottoms = true
    var recommendOuters = true

    fun recommend(dailySummaries: Map<Int, DailyWeatherSummary>) {
        if (dailySummaries.isEmpty()) return
        viewModelScope.launch {
            _isLoading.value = true
            val avgTemp = dailySummaries.values.let { summaries ->
                val avgMax = summaries.map { it.maxTemp }.average()
                val avgMin = summaries.map { it.minTemp }.average()
                (avgMax + avgMin) / 2.0
            }

            val allItems = withContext(Dispatchers.IO) { mallDao.getAllItemsList() }

            val userHeight = settings.estimatedHeight.toDouble()
            val userWeight = settings.estimatedWeight.toDouble()
            val userWaist = settings.estimatedWaist.toDouble()
            val fitEnabled = settings.bodyFitEnabled && settings.isBodyRegistered
            val purposeEnabled = settings.clothingPurposeEnabled
            val selectedPurpose = settings.selectedPurpose

            fun matchTemp(item: MallItem) = avgTemp in item.suitableMinTemp..item.suitableMaxTemp

            fun sizeFilter(item: MallItem): Boolean {
                if (!fitEnabled) return true
                val level = AddClothingViewModel.calculateFitLevel(
                    userHeight.toFloat(), userWeight.toFloat(), userWaist.toFloat(),
                    item.fitMinHeight, item.fitMaxHeight,
                    item.fitMinWeight, item.fitMaxWeight,
                    item.fitMinWaist, item.fitMaxWaist
                )
                return level != AddClothingViewModel.FIT_BAD && level != AddClothingViewModel.FIT_VERY_BAD
            }

            fun purposeScore(item: MallItem): Int {
                if (!purposeEnabled) return 0
                val purposes = item.purposes.split(",").map { it.trim() }.filter { it.isNotBlank() }
                return if (purposes.contains(selectedPurpose)) 0 else 1
            }

            val max = maxItemsPerCategory

            fun filterAndSort(category: String): List<MallItem> {
                val tempMatched = allItems.filter { it.category == category && matchTemp(it) }
                val sizeFiltered = tempMatched.filter { sizeFilter(it) }
                val base = if (sizeFiltered.isNotEmpty()) sizeFiltered else tempMatched
                return base.sortedBy { purposeScore(it) }.take(max)
            }

            val tops = if (recommendTops) filterAndSort("상의") else emptyList()
            val bottoms = if (recommendBottoms) filterAndSort("하의") else emptyList()
            val outers = if (recommendOuters) filterAndSort("아우터") else emptyList()

            _result.value = MallRecommendationResult(tops, bottoms, outers)
            _isLoading.value = false
        }
    }
}
