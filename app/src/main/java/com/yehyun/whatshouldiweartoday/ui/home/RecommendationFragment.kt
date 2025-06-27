package com.yehyun.whatshouldiweartoday.ui.home

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.yehyun.whatshouldiweartoday.R
import java.util.Locale

class RecommendationFragment : Fragment(R.layout.fragment_recommendation) {

    private val homeViewModel: HomeViewModel by activityViewModels()
    private val isToday: Boolean by lazy { arguments?.getBoolean(ARG_IS_TODAY, true) ?: true }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // [수정] 모든 UI 요소들을 찾습니다.
        val tvNoRecommendation = view.findViewById<TextView>(R.id.tv_no_recommendation)
        val weatherSummaryTextView = view.findViewById<TextView>(R.id.tv_weather_summary)

        val tvBestComboTitle = view.findViewById<TextView>(R.id.tv_best_combo_title)
        val rvBestCombo = view.findViewById<RecyclerView>(R.id.rv_best_combo)
        val saveStyleButton = view.findViewById<Button>(R.id.button_save_style)

        val tvTopsTitle = view.findViewById<TextView>(R.id.tv_tops_title)
        val rvTops = view.findViewById<RecyclerView>(R.id.rv_tops)

        val tvBottomsTitle = view.findViewById<TextView>(R.id.tv_bottoms_title)
        val rvBottoms = view.findViewById<RecyclerView>(R.id.rv_bottoms)

        val tvPackableOuterTitle = view.findViewById<TextView>(R.id.tv_packable_outer_title)
        val rvPackableOuter = view.findViewById<RecyclerView>(R.id.rv_packable_outer)

        val tvUmbrella = view.findViewById<TextView>(R.id.tv_umbrella_recommendation)

        // 각 추천 목록을 위한 어댑터 생성
        val bestComboAdapter = RecommendationAdapter()
        val topsAdapter = RecommendationAdapter()
        val bottomsAdapter = RecommendationAdapter()
        val packableOuterAdapter = RecommendationAdapter()

        rvBestCombo.adapter = bestComboAdapter
        rvTops.adapter = topsAdapter
        rvBottoms.adapter = bottomsAdapter
        rvPackableOuter.adapter = packableOuterAdapter

        val weatherSummaryLiveData = if (isToday) homeViewModel.todayWeatherSummary else homeViewModel.tomorrowWeatherSummary
        val recommendationLiveData = if (isToday) homeViewModel.todayRecommendation else homeViewModel.tomorrowRecommendation

        weatherSummaryLiveData.observe(viewLifecycleOwner) { summary ->
            val weatherText = String.format(Locale.KOREAN,
                "최고: %.1f°C (체감 %.1f°C)\n최저: %.1f°C (체감 %.1f°C)\n날씨: %s, 강수확률: %d%%",
                summary.maxTemp, summary.maxFeelsLike, summary.minTemp, summary.minFeelsLike, summary.weatherCondition, summary.precipitationProbability
            )
            weatherSummaryTextView.text = weatherText
        }

        recommendationLiveData.observe(viewLifecycleOwner) { result ->
            val hasRecommendations = result.bestCombination.isNotEmpty()
            tvNoRecommendation.isVisible = !hasRecommendations

            tvBestComboTitle.isVisible = hasRecommendations
            rvBestCombo.isVisible = hasRecommendations
            saveStyleButton.isVisible = hasRecommendations

            tvTopsTitle.isVisible = result.recommendedTops.isNotEmpty()
            rvTops.isVisible = result.recommendedTops.isNotEmpty()

            tvBottomsTitle.isVisible = result.recommendedBottoms.isNotEmpty()
            rvBottoms.isVisible = result.recommendedBottoms.isNotEmpty()

            tvPackableOuterTitle.isVisible = result.packableOuter != null
            rvPackableOuter.isVisible = result.packableOuter != null

            tvUmbrella.isVisible = result.umbrellaRecommendation.isNotBlank()

            bestComboAdapter.submitList(result.bestCombination)
            topsAdapter.submitList(result.recommendedTops)
            bottomsAdapter.submitList(result.recommendedBottoms)
            result.packableOuter?.let { packableOuterAdapter.submitList(listOf(it)) }
            tvUmbrella.text = result.umbrellaRecommendation

//            saveStyleButton.setOnClickListener {
//                val preselectedIds = result.bestCombination.map { it.id }.toIntArray()
//                val action = HomeFragmentDirections.actionNavigationHomeToSaveStyleFragment(preselectedIds)
//                parentFragment?.findNavController()?.navigate(action)
//            }
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