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
import java.util.Calendar

data class MallRecommendationResult(
    val tops: List<MallItem>,
    val bottoms: List<MallItem>,
    val outers: List<MallItem>
)

private fun monthToSeason(month: Int): String = when (month) {
    3, 4, 5 -> "봄"
    6, 7, 8 -> "여름"
    9, 10, 11 -> "가을"
    else -> "겨울"
}

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

            val cal = Calendar.getInstance()
            val currentMonth = cal.get(Calendar.MONTH) + 1
            val nextMonth = if (currentMonth == 12) 1 else currentMonth + 1
            val currentSeason = monthToSeason(currentMonth)
            val nextSeason = monthToSeason(nextMonth)
            val hasUpcomingTransition = nextSeason != currentSeason

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

            fun seasonScore(item: MallItem): Int {
                val seasons = item.season.split(",").map { it.trim() }.filter { it.isNotBlank() }
                if (seasons.isEmpty()) return 0
                if (seasons.contains(currentSeason)) return 0
                if (hasUpcomingTransition && seasons.contains(nextSeason)) return 1
                return 2
            }

            val max = maxItemsPerCategory

            fun filterAndSort(category: String): List<MallItem> {
                val tempMatched = allItems.filter { it.category == category && matchTemp(it) }
                val sizeFiltered = tempMatched.filter { sizeFilter(it) }
                val base = if (sizeFiltered.isNotEmpty()) sizeFiltered else tempMatched
                return base.sortedWith(compareBy({ purposeScore(it) }, { seasonScore(it) })).take(max)
            }

            val tops = if (recommendTops) filterAndSort("상의") else emptyList()
            val bottoms = if (recommendBottoms) filterAndSort("하의") else emptyList()
            val outers = if (recommendOuters) filterAndSort("아우터") else emptyList()

            _result.value = MallRecommendationResult(tops, bottoms, outers)
            _isLoading.value = false
        }
    }
}
