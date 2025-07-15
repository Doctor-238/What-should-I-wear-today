package com.yehyun.whatshouldiweartoday.ui.home

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.yehyun.whatshouldiweartoday.R
import com.yehyun.whatshouldiweartoday.data.database.ClothingItem
import java.util.Locale

class RecommendationFragment : Fragment(R.layout.fragment_recommendation) {

    private val homeViewModel: HomeViewModel by activityViewModels()
    private val isToday: Boolean by lazy { arguments?.getBoolean(ARG_IS_TODAY, true) ?: true }
    private lateinit var scrollView: ScrollView

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
        scrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            homeViewModel.setScrollState(scrollY == 0)
        }

        view.findViewById<RecyclerView>(R.id.rv_tops).adapter = RecommendationAdapter(onClothingItemClicked)
        view.findViewById<RecyclerView>(R.id.rv_bottoms).adapter = RecommendationAdapter(onClothingItemClicked)
        view.findViewById<RecyclerView>(R.id.rv_outers).adapter = RecommendationAdapter(onClothingItemClicked)
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
            if (result == null) {
                setRecommendationVisibility(view, false)
            } else {
                setRecommendationVisibility(view, true)
                bindRecommendationData(view, result)
            }
        }
    }

    private fun bindWeatherSummary(view: View, summary: DailyWeatherSummary) {
        view.findViewById<TextView>(R.id.tv_max_temp).text = String.format(Locale.KOREAN, "%.1f°", summary.maxTemp)
        view.findViewById<TextView>(R.id.tv_max_feels_like).text = String.format(Locale.KOREAN, "체감 %.1f°", summary.maxFeelsLike)
        view.findViewById<TextView>(R.id.tv_min_temp).text = String.format(Locale.KOREAN, "%.1f°", summary.minTemp)
        view.findViewById<TextView>(R.id.tv_min_feels_like).text = String.format(Locale.KOREAN, "체감 %.1f°", summary.minFeelsLike)

    }

    private fun setRecommendationVisibility(view: View, show: Boolean) {
        view.findViewById<TextView>(R.id.tv_no_recommendation).isVisible = !show
        view.findViewById<TextView>(R.id.tv_tops_title).isVisible = show
        view.findViewById<RecyclerView>(R.id.rv_tops).isVisible = show
        view.findViewById<TextView>(R.id.tv_bottoms_title).isVisible = show
        view.findViewById<RecyclerView>(R.id.rv_bottoms).isVisible = show
        view.findViewById<TextView>(R.id.tv_outers_title).isVisible = show
        view.findViewById<RecyclerView>(R.id.rv_outers).isVisible = show
        view.findViewById<TextView>(R.id.tv_temp_difference_notice).isVisible = false
    }

    private fun bindRecommendationData(view: View, result: RecommendationResult) {
        val hasAnyRecommendation = result.recommendedTops.isNotEmpty() || result.recommendedBottoms.isNotEmpty() || result.recommendedOuters.isNotEmpty()
        if (!hasAnyRecommendation) {
            setRecommendationVisibility(view, false)
            return
        }

        val rvTops = view.findViewById<RecyclerView>(R.id.rv_tops)
        val rvBottoms = view.findViewById<RecyclerView>(R.id.rv_bottoms)
        val rvOuters = view.findViewById<RecyclerView>(R.id.rv_outers)

        (rvTops.adapter as RecommendationAdapter).submitList(result.recommendedTops, result.packableOuter?.id)
        (rvBottoms.adapter as RecommendationAdapter).submitList(result.recommendedBottoms)
        (rvOuters.adapter as RecommendationAdapter).submitList(result.recommendedOuters, result.packableOuter?.id)

        view.findViewById<TextView>(R.id.tv_no_tops).isVisible = result.recommendedTops.isEmpty()
        view.findViewById<TextView>(R.id.tv_no_bottoms).isVisible = result.recommendedBottoms.isEmpty()
        view.findViewById<TextView>(R.id.tv_no_outers).isVisible = result.recommendedOuters.isEmpty()

        view.findViewById<TextView>(R.id.tv_temp_difference_notice).apply {
            isVisible = result.isTempDifferenceSignificant || result.umbrellaRecommendation.isNotBlank()
            text = when {
                result.isTempDifferenceSignificant && result.umbrellaRecommendation.isNotBlank() ->
                    "오늘은 일교차가 큰 날이에요! 아우터를 챙겨가세요.\n${result.umbrellaRecommendation}"
                result.isTempDifferenceSignificant ->
                    "오늘은 일교차가 큰 날이에요!\n아우터를 따로 챙겨가는 것을 추천드립니다"
                result.umbrellaRecommendation.isNotBlank() ->
                    result.umbrellaRecommendation
                else -> ""
            }
        }
    }

    fun scrollToTop() {
        if (::scrollView.isInitialized) {
            scrollView.smoothScrollTo(0, 0)
        }
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