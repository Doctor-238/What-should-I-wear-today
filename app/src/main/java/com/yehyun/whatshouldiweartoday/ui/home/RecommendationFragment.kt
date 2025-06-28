package com.yehyun.whatshouldiweartoday.ui.home

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yehyun.whatshouldiweartoday.R
import java.util.Locale

class RecommendationFragment : Fragment(R.layout.fragment_recommendation) {

    private val homeViewModel: HomeViewModel by activityViewModels()
    private val isToday: Boolean by lazy { arguments?.getBoolean(ARG_IS_TODAY, true) ?: true }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 모든 UI 요소 연결
        val weatherSummaryTextView = view.findViewById<TextView>(R.id.tv_weather_summary)
        val rvTops = view.findViewById<RecyclerView>(R.id.rv_tops)
        val tvNoTops = view.findViewById<TextView>(R.id.tv_no_tops)
        val rvBottoms = view.findViewById<RecyclerView>(R.id.rv_bottoms)
        val tvNoBottoms = view.findViewById<TextView>(R.id.tv_no_bottoms)
        val rvOuters = view.findViewById<RecyclerView>(R.id.rv_outers)
        val tvNoOuters = view.findViewById<TextView>(R.id.tv_no_outers)
        val rvBestCombo = view.findViewById<RecyclerView>(R.id.rv_best_combo)
        val tvNoBestCombo = view.findViewById<TextView>(R.id.tv_no_best_combo)

        // 클릭 이벤트를 정의하는 람다
        val onClothingItemClicked: (com.yehyun.whatshouldiweartoday.data.database.ClothingItem) -> Unit = { item ->
            // HomeFragment를 통해 안전하게 화면 이동
            parentFragment?.parentFragment?.findNavController()?.navigate(
                HomeFragmentDirections.actionNavigationHomeToEditClothingFragment(item.id)
            )
        }

        // 각 목록에 맞는 어댑터 생성 및 연결
        val topsAdapter = RecommendationAdapter(onClothingItemClicked)
        val bottomsAdapter = RecommendationAdapter(onClothingItemClicked)
        val outersAdapter = RecommendationAdapter(onClothingItemClicked)
        val bestComboAdapter = RecommendationAdapter(onClothingItemClicked)

        rvTops.adapter = topsAdapter
        rvBottoms.adapter = bottomsAdapter
        rvOuters.adapter = outersAdapter
        rvBestCombo.adapter = bestComboAdapter

        // LiveData 관찰
        val weatherSummaryLiveData = if (isToday) homeViewModel.todayWeatherSummary else homeViewModel.tomorrowWeatherSummary
        val recommendationLiveData = if (isToday) homeViewModel.todayRecommendation else homeViewModel.tomorrowRecommendation

        weatherSummaryLiveData.observe(viewLifecycleOwner) { summary ->
            weatherSummaryTextView.text = String.format(Locale.KOREAN,
                "최고:%.1f°(체감%.1f°) | 최저:%.1f°(체감%.1f°) | %s | 강수:%d%%",
                summary.maxTemp, summary.maxFeelsLike, summary.minTemp, summary.minFeelsLike, summary.weatherCondition, summary.precipitationProbability
            )
        }

        recommendationLiveData.observe(viewLifecycleOwner) { result ->
            // 상의 추천
            tvNoTops.isVisible = result.recommendedTops.isEmpty()
            rvTops.isVisible = result.recommendedTops.isNotEmpty()
            topsAdapter.submitList(result.recommendedTops)

            // 하의 추천
            tvNoBottoms.isVisible = result.recommendedBottoms.isEmpty()
            rvBottoms.isVisible = result.recommendedBottoms.isNotEmpty()
            bottomsAdapter.submitList(result.recommendedBottoms)

            // 아우터 추천 (+챙겨갈 아우터 표시)
            tvNoOuters.isVisible = result.recommendedOuters.isEmpty()
            rvOuters.isVisible = result.recommendedOuters.isNotEmpty()
            outersAdapter.submitList(result.recommendedOuters, result.packableOuter?.id)

            // 최적의 코디 추천
            tvNoBestCombo.isVisible = result.bestCombination.isEmpty()
            rvBestCombo.isVisible = result.bestCombination.isNotEmpty()
            bestComboAdapter.submitList(result.bestCombination, result.packableOuter?.id)
        }
    }

    companion object {
        private const val ARG_IS_TODAY = "is_today"
        fun newInstance(isToday: Boolean) = RecommendationFragment().apply {
            arguments = Bundle().apply { putBoolean(ARG_IS_TODAY, isToday) }
        }
    }
}