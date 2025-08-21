package com.yehyun.whatshouldiweartoday.ui.home

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.yehyun.whatshouldiweartoday.R
import com.yehyun.whatshouldiweartoday.data.database.ClothingItem
import java.util.Locale

class RecommendationFragment : Fragment(R.layout.fragment_recommendation) {

    private val homeViewModel: HomeViewModel by activityViewModels()
    private val isToday: Boolean by lazy { arguments?.getBoolean(ARG_IS_TODAY, true) ?: true }
    private lateinit var scrollView: ScrollView

    fun canScrollUp(): Boolean {
        return if (::scrollView.isInitialized) scrollView.canScrollVertically(-1) else false
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        scrollView = view as ScrollView

        val onClothingItemClicked: (ClothingItem) -> Unit = { item ->
            parentFragment?.findNavController()?.navigate(
                HomeFragmentDirections.actionNavigationHomeToEditClothingFragment(item.id)
            )
        }

        setupViews(view, onClothingItemClicked)
        observeViewModel(view)
    }

    private fun setupViews(view: View, onClothingItemClicked: (ClothingItem) -> Unit) {
        view.findViewById<RecyclerView>(R.id.rv_best_combination).adapter = RecommendationAdapter(onClothingItemClicked)
        view.findViewById<RecyclerView>(R.id.rv_tops).adapter = RecommendationAdapter(onClothingItemClicked)
        view.findViewById<RecyclerView>(R.id.rv_bottoms).adapter = RecommendationAdapter(onClothingItemClicked)
        view.findViewById<RecyclerView>(R.id.rv_outers).adapter = RecommendationAdapter(onClothingItemClicked)

        if (!isToday) {
            view.findViewById<TextView>(R.id.tv_weather_title).text = "내일의 날씨"
            view.findViewById<TextView>(R.id.tv_best_combination_subtitle).text = "AI가 추천하는 내일의 조합"
        }
    }

    private fun observeViewModel(view: View) {
        val weatherSummaryLiveData = if (isToday) homeViewModel.todayWeatherSummary else homeViewModel.tomorrowWeatherSummary
        val recommendationLiveData = if (isToday) homeViewModel.todayRecommendation else homeViewModel.tomorrowRecommendation

        weatherSummaryLiveData.observe(viewLifecycleOwner) { summary ->
            val weatherCard = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.card_weather_summary)
            if (summary != null) {
                weatherCard.isVisible = true
                bindWeatherSummary(view, summary)
            } else {
                weatherCard.isVisible = false
            }
        }

        recommendationLiveData.observe(viewLifecycleOwner) { result ->
            val hasRecommendations = result != null && (result.recommendedTops.isNotEmpty() || result.recommendedBottoms.isNotEmpty() || result.recommendedOuters.isNotEmpty() || result.bestCombination.isNotEmpty())

            if (hasRecommendations) {
                setRecommendationVisibility(view, true)
                bindRecommendationData(view, result!!)
            } else {
                setRecommendationVisibility(view, false)
            }
        }
    }

    private fun bindWeatherSummary(view: View, summary: DailyWeatherSummary) {
        val maxTempTextView = view.findViewById<TextView>(R.id.tv_max_temp)
        maxTempTextView.text = String.format(Locale.KOREAN, "%.1f°", summary.maxTemp)
        maxTempTextView.setTextColor(ContextCompat.getColor(requireContext(), R.color.temp_high_red))

        val minTempTextView = view.findViewById<TextView>(R.id.tv_min_temp)
        minTempTextView.text = String.format(Locale.KOREAN, "%.1f°", summary.minTemp)
        minTempTextView.setTextColor(ContextCompat.getColor(requireContext(), R.color.temp_low_blue))

        view.findViewById<TextView>(R.id.tv_max_feels_like).text = String.format(Locale.KOREAN, "체감 %.1f°", summary.maxFeelsLike)
        view.findViewById<TextView>(R.id.tv_min_feels_like).text = String.format(Locale.KOREAN, "체감 %.1f°", summary.minFeelsLike)
    }

    private fun setRecommendationVisibility(view: View, show: Boolean) {
        view.findViewById<TextView>(R.id.tv_no_recommendation).isVisible = !show
        view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.card_best_combination).isVisible = show
        view.findViewById<TextView>(R.id.tv_tops_title).isVisible = show
        view.findViewById<RecyclerView>(R.id.rv_tops).isVisible = show
        view.findViewById<TextView>(R.id.tv_bottoms_title).isVisible = show
        view.findViewById<RecyclerView>(R.id.rv_bottoms).isVisible = show
        view.findViewById<TextView>(R.id.tv_outers_title).isVisible = show
        view.findViewById<RecyclerView>(R.id.rv_outers).isVisible = show
        view.findViewById<View>(R.id.ll_info_container).isVisible = false

        if (!show) {
            view.findViewById<TextView>(R.id.tv_no_tops).isVisible = false
            view.findViewById<TextView>(R.id.tv_no_bottoms).isVisible = false
            view.findViewById<TextView>(R.id.tv_no_outers).isVisible = false
        }
    }

    private fun bindRecommendationData(view: View, result: RecommendationResult) {
        val rvTops = view.findViewById<RecyclerView>(R.id.rv_tops)
        val rvBottoms = view.findViewById<RecyclerView>(R.id.rv_bottoms)
        val rvOuters = view.findViewById<RecyclerView>(R.id.rv_outers)

        (rvTops.adapter as RecommendationAdapter).submitList(result.recommendedTops, result.packableOuters)
        (rvBottoms.adapter as RecommendationAdapter).submitList(result.recommendedBottoms)
        (rvOuters.adapter as RecommendationAdapter).submitList(result.recommendedOuters, result.packableOuters)

        val cardBestCombination = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.card_best_combination)
        if (result.bestCombination.isNotEmpty()) {
            cardBestCombination.isVisible = true
            val rvBestCombination = view.findViewById<RecyclerView>(R.id.rv_best_combination)
            (rvBestCombination.adapter as RecommendationAdapter).submitList(result.bestCombination, result.packableOuters)

            view.findViewById<android.widget.Button>(R.id.btn_save_combination).setOnClickListener {
                val ids = result.bestCombination.map { it.id }.toIntArray()
                val action = HomeFragmentDirections.actionNavigationHomeToSaveStyleFragment(ids)
                parentFragment?.findNavController()?.navigate(action)
            }
        } else {
            cardBestCombination.isVisible = false
        }

        view.findViewById<TextView>(R.id.tv_no_tops).isVisible = result.recommendedTops.isEmpty()
        view.findViewById<TextView>(R.id.tv_no_bottoms).isVisible = result.recommendedBottoms.isEmpty()
        view.findViewById<TextView>(R.id.tv_no_outers).isVisible = result.recommendedOuters.isEmpty()

        val infoContainer = view.findViewById<LinearLayout>(R.id.ll_info_container)
        val tempDifferenceCard = view.findViewById<MaterialCardView>(R.id.card_temp_difference)
        val tempDifferenceText = view.findViewById<TextView>(R.id.tv_info_temp_difference)
        val rainWarningCard = view.findViewById<MaterialCardView>(R.id.card_rain_warning)
        val rainWarningText = view.findViewById<TextView>(R.id.tv_info_rain)

        val isTempDiffNoticeVisible = result.isTempDifferenceSignificant
        val isRainNoticeVisible = result.umbrellaRecommendation.isNotBlank()

        tempDifferenceCard.isVisible = isTempDiffNoticeVisible
        if (isTempDiffNoticeVisible) {
            tempDifferenceText.text = "오늘은 일교차가 큰 날이에요!\n아우터를 따로 챙겨가는 것을 추천드립니다."
        }

        rainWarningCard.isVisible = isRainNoticeVisible
        if (isRainNoticeVisible) {
            rainWarningText.text = result.umbrellaRecommendation
        }

        infoContainer.isVisible = isTempDiffNoticeVisible || isRainNoticeVisible
    }

    fun scrollToTop() {
        if (::scrollView.isInitialized) {
            scrollView.smoothScrollTo(0, 0)
            resetHorizontalScroll()
        }
    }

    private fun resetHorizontalScroll() {
        view?.findViewById<RecyclerView>(R.id.rv_best_combination)?.smoothScrollToPosition(0)
        view?.findViewById<RecyclerView>(R.id.rv_tops)?.smoothScrollToPosition(0)
        view?.findViewById<RecyclerView>(R.id.rv_bottoms)?.smoothScrollToPosition(0)
        view?.findViewById<RecyclerView>(R.id.rv_outers)?.smoothScrollToPosition(0)
    }

    override fun onResume() {
        super.onResume()
        resetHorizontalScroll()
    }

    companion object {
        private const val ARG_IS_TODAY = "is_today"
        fun newInstance(isToday: Boolean) = RecommendationFragment().apply {
            arguments = Bundle().apply {
                putBoolean(ARG_IS_TODAY, isToday)
            }
        }
    }
}