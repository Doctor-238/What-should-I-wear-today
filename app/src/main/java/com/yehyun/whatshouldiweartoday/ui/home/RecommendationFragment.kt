package com.yehyun.whatshouldiweartoday.ui.home

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
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

        view.findViewById<RecyclerView>(R.id.rv_best_combo).adapter = RecommendationAdapter(onClothingItemClicked)
        view.findViewById<RecyclerView>(R.id.rv_tops).adapter = RecommendationAdapter(onClothingItemClicked)
        view.findViewById<RecyclerView>(R.id.rv_bottoms).adapter = RecommendationAdapter(onClothingItemClicked)
        view.findViewById<RecyclerView>(R.id.rv_outers).adapter = RecommendationAdapter(onClothingItemClicked)
    }

    private fun observeViewModel(view: View) {
        val weatherSummaryLiveData = if (isToday) homeViewModel.todayWeatherSummary else homeViewModel.tomorrowWeatherSummary
        val recommendationLiveData = if (isToday) homeViewModel.todayRecommendation else homeViewModel.tomorrowRecommendation

        weatherSummaryLiveData.observe(viewLifecycleOwner) { summary ->
            val weatherSummaryTextView = view.findViewById<TextView>(R.id.tv_weather_summary)
            if (summary != null) {
                weatherSummaryTextView.text = String.format(Locale.KOREAN,
                    "최고:%.1f°(체감%.1f°) | 최저:%.1f°(체감%.1f°) | %s | 강수:%d%%",
                    summary.maxTemp, summary.maxFeelsLike, summary.minTemp, summary.minFeelsLike, summary.weatherCondition, summary.precipitationProbability
                )
            } else {
                weatherSummaryTextView.text = "날씨 정보가 없습니다."
            }
        }

        recommendationLiveData.observe(viewLifecycleOwner) { result ->
            // ▼▼▼▼▼ 핵심 수정 부분 ▼▼▼▼▼
            // result가 null일 경우와 아닐 경우를 모두 처리합니다.
            if (result == null) {
                setRecommendationVisibility(view, false)
            } else {
                setRecommendationVisibility(view, true)
                bindRecommendationData(view, result)
            }
            // ▲▲▲▲▲ 핵심 수정 부분 ▲▲▲▲▲
        }
    }

    private fun setRecommendationVisibility(view: View, show: Boolean) {
        view.findViewById<TextView>(R.id.tv_no_recommendation).isVisible = !show
        view.findViewById<ConstraintLayout>(R.id.layout_best_combo).isVisible = show
        view.findViewById<TextView>(R.id.tv_tops_title).isVisible = show
        view.findViewById<RecyclerView>(R.id.rv_tops).isVisible = show
        view.findViewById<TextView>(R.id.tv_bottoms_title).isVisible = show
        view.findViewById<RecyclerView>(R.id.rv_bottoms).isVisible = show
        view.findViewById<TextView>(R.id.tv_outers_title).isVisible = show
        view.findViewById<RecyclerView>(R.id.rv_outers).isVisible = show
        view.findViewById<TextView>(R.id.tv_temp_difference_notice).isVisible = false
        view.findViewById<TextView>(R.id.tv_umbrella_recommendation).isVisible = false
    }

    private fun bindRecommendationData(view: View, result: RecommendationResult) {
        val hasAnyRecommendation = result.recommendedTops.isNotEmpty() || result.recommendedBottoms.isNotEmpty() || result.recommendedOuters.isNotEmpty()
        if (!hasAnyRecommendation) {
            setRecommendationVisibility(view, false)
            return
        }

        val rvBestCombo = view.findViewById<RecyclerView>(R.id.rv_best_combo)
        val rvTops = view.findViewById<RecyclerView>(R.id.rv_tops)
        val rvBottoms = view.findViewById<RecyclerView>(R.id.rv_bottoms)
        val rvOuters = view.findViewById<RecyclerView>(R.id.rv_outers)

        (rvBestCombo.adapter as RecommendationAdapter).submitList(result.bestCombination, result.packableOuter?.id)
        (rvTops.adapter as RecommendationAdapter).submitList(result.recommendedTops)
        (rvBottoms.adapter as RecommendationAdapter).submitList(result.recommendedBottoms)
        (rvOuters.adapter as RecommendationAdapter).submitList(result.recommendedOuters, result.packableOuter?.id)

        view.findViewById<TextView>(R.id.tv_no_best_combo).isVisible = result.bestCombination.isEmpty()
        view.findViewById<TextView>(R.id.tv_no_tops).isVisible = result.recommendedTops.isEmpty()
        view.findViewById<TextView>(R.id.tv_no_bottoms).isVisible = result.recommendedBottoms.isEmpty()
        view.findViewById<TextView>(R.id.tv_no_outers).isVisible = result.recommendedOuters.isEmpty()

        view.findViewById<TextView>(R.id.tv_temp_difference_notice).isVisible = result.isTempDifferenceSignificant
        view.findViewById<TextView>(R.id.tv_umbrella_recommendation).apply {
            isVisible = result.umbrellaRecommendation.isNotBlank()
            text = result.umbrellaRecommendation
        }

        view.findViewById<Button>(R.id.button_save_style).setOnClickListener {
            val preselectedIds = result.bestCombination.map { it.id }.toIntArray()
            parentFragment?.findNavController()?.navigate(
                HomeFragmentDirections.actionNavigationHomeToSaveStyleFragment(preselectedIds)
            )
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