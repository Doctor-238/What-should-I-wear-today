package com.yehyun.whatshouldiweartoday.ui.home

import android.os.Bundle
import android.view.View
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
        // ▼▼▼▼▼ 핵심 수정: 스크롤 리스너 복원 ▼▼▼▼▼
        // ScrollView의 스크롤 상태를 감지하여 ViewModel에 전달하는 리스너입니다.
        scrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            homeViewModel.setScrollState(scrollY == 0)
        }
        // ▲▲▲▲▲ 핵심 수정 ▲▲▲▲▲

        view.findViewById<RecyclerView>(R.id.rv_tops).adapter = RecommendationAdapter(onClothingItemClicked)
        view.findViewById<RecyclerView>(R.id.rv_bottoms).adapter = RecommendationAdapter(onClothingItemClicked)
        view.findViewById<RecyclerView>(R.id.rv_outers).adapter = RecommendationAdapter(onClothingItemClicked)
        view.findViewById<RecyclerView>(R.id.rv_best_combination).adapter = RecommendationAdapter(onClothingItemClicked)
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
            // 추천 아이템이 하나라도 있는지 확인
            val hasRecommendations = result != null && (result.recommendedTops.isNotEmpty() || result.recommendedBottoms.isNotEmpty() || result.recommendedOuters.isNotEmpty() || result.bestCombination.isNotEmpty())

            if (hasRecommendations) {
                // 추천 아이템이 있으면: 관련 UI를 보여주고 데이터를 바인딩
                setRecommendationVisibility(view, true)
                bindRecommendationData(view, result!!)
            } else {
                // 추천 아이템이 없으면: 관련 UI를 숨기고, "옷 부족" 메시지를 표시
                setRecommendationVisibility(view, false)
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
        // 전체 추천 영역과 "옷 부족" 메시지의 보이기/숨기기 상태를 전환
        view.findViewById<TextView>(R.id.tv_no_recommendation).isVisible = !show
        view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.card_best_combination).isVisible = show
        view.findViewById<TextView>(R.id.tv_tops_title).isVisible = show
        view.findViewById<RecyclerView>(R.id.rv_tops).isVisible = show
        view.findViewById<TextView>(R.id.tv_bottoms_title).isVisible = show
        view.findViewById<RecyclerView>(R.id.rv_bottoms).isVisible = show
        view.findViewById<TextView>(R.id.tv_outers_title).isVisible = show
        view.findViewById<RecyclerView>(R.id.rv_outers).isVisible = show
        view.findViewById<TextView>(R.id.tv_temp_difference_notice).isVisible = false // 일단 숨김 처리

        // 추천 영역이 숨겨질 때, 각 카테고리별 "옷 없음" 메시지도 확실히 숨김
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

        (rvTops.adapter as RecommendationAdapter).submitList(result.recommendedTops, result.packableOuter?.id)
        (rvBottoms.adapter as RecommendationAdapter).submitList(result.recommendedBottoms)
        (rvOuters.adapter as RecommendationAdapter).submitList(result.recommendedOuters, result.packableOuter?.id)

        val cardBestCombination = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.card_best_combination)
        if (result.bestCombination.isNotEmpty()) {
            cardBestCombination.isVisible = true
            val rvBestCombination = view.findViewById<RecyclerView>(R.id.rv_best_combination)
            (rvBestCombination.adapter as RecommendationAdapter).submitList(result.bestCombination)

            view.findViewById<android.widget.Button>(R.id.btn_save_combination).setOnClickListener {
                val ids = result.bestCombination.map { it.id }.toIntArray()
                val action = HomeFragmentDirections.actionNavigationHomeToSaveStyleFragment(ids)
                parentFragment?.findNavController()?.navigate(action)
            }
        } else {
            cardBestCombination.isVisible = false
        }


        // 각 카테고리별로 옷이 없으면 "옷 없음" 메시지를 표시하고, 있으면 숨김
        view.findViewById<TextView>(R.id.tv_no_tops).isVisible = result.recommendedTops.isEmpty()
        view.findViewById<TextView>(R.id.tv_no_bottoms).isVisible = result.recommendedBottoms.isEmpty()
        view.findViewById<TextView>(R.id.tv_no_outers).isVisible = result.recommendedOuters.isEmpty()

        // 일교차/우산 관련 메시지 표시 로직
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