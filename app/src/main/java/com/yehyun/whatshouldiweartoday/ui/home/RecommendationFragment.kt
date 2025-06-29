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
import java.util.Locale

class RecommendationFragment : Fragment(R.layout.fragment_recommendation) {

    private val homeViewModel: HomeViewModel by activityViewModels()
    private val isToday: Boolean by lazy { arguments?.getBoolean(ARG_IS_TODAY, true) ?: true }
    private lateinit var scrollView: ScrollView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        scrollView = view as ScrollView

        scrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            homeViewModel.setScrollState(scrollY == 0)
        }

        val tvNoRecommendation = view.findViewById<TextView>(R.id.tv_no_recommendation)
        val weatherSummaryTextView = view.findViewById<TextView>(R.id.tv_weather_summary)
        val layoutBestCombo = view.findViewById<ConstraintLayout>(R.id.layout_best_combo)
        val rvBestCombo = view.findViewById<RecyclerView>(R.id.rv_best_combo)
        val tvNoBestCombo = view.findViewById<TextView>(R.id.tv_no_best_combo)
        val saveStyleButton = view.findViewById<Button>(R.id.button_save_style)
        val tvTopsTitle = view.findViewById<TextView>(R.id.tv_tops_title)
        val rvTops = view.findViewById<RecyclerView>(R.id.rv_tops)
        val tvNoTops = view.findViewById<TextView>(R.id.tv_no_tops)
        val tvBottomsTitle = view.findViewById<TextView>(R.id.tv_bottoms_title)
        val rvBottoms = view.findViewById<RecyclerView>(R.id.rv_bottoms)
        val tvNoBottoms = view.findViewById<TextView>(R.id.tv_no_bottoms)
        val tvOutersTitle = view.findViewById<TextView>(R.id.tv_outers_title)
        val rvOuters = view.findViewById<RecyclerView>(R.id.rv_outers)
        val tvNoOuters = view.findViewById<TextView>(R.id.tv_no_outers)
        val tvTempDifferenceNotice = view.findViewById<TextView>(R.id.tv_temp_difference_notice)
        val tvUmbrella = view.findViewById<TextView>(R.id.tv_umbrella_recommendation)

        val onClothingItemClicked: (com.yehyun.whatshouldiweartoday.data.database.ClothingItem) -> Unit = { item ->
            parentFragment?.findNavController()?.navigate(
                HomeFragmentDirections.actionNavigationHomeToEditClothingFragment(item.id)
            )
        }

        val bestComboAdapter = RecommendationAdapter(onClothingItemClicked)
        val topsAdapter = RecommendationAdapter(onClothingItemClicked)
        val bottomsAdapter = RecommendationAdapter(onClothingItemClicked)
        val outersAdapter = RecommendationAdapter(onClothingItemClicked)

        rvBestCombo.adapter = bestComboAdapter
        rvTops.adapter = topsAdapter
        rvBottoms.adapter = bottomsAdapter
        rvOuters.adapter = outersAdapter

        val weatherSummaryLiveData = if (isToday) homeViewModel.todayWeatherSummary else homeViewModel.tomorrowWeatherSummary
        val recommendationLiveData = if (isToday) homeViewModel.todayRecommendation else homeViewModel.tomorrowRecommendation

        weatherSummaryLiveData.observe(viewLifecycleOwner) { summary ->
            weatherSummaryTextView.text = String.format(Locale.KOREAN,
                "최고:%.1f°(체감%.1f°) | 최저:%.1f°(체감%.1f°) | %s | 강수:%d%%",
                summary.maxTemp, summary.maxFeelsLike, summary.minTemp, summary.minFeelsLike, summary.weatherCondition, summary.precipitationProbability
            )
        }

        recommendationLiveData.observe(viewLifecycleOwner) { result ->
            val hasAnyRecommendation = result.recommendedTops.isNotEmpty() || result.recommendedBottoms.isNotEmpty() || result.recommendedOuters.isNotEmpty()
            tvNoRecommendation.isVisible = !hasAnyRecommendation
            tvTopsTitle.isVisible = hasAnyRecommendation
            tvBottomsTitle.isVisible = hasAnyRecommendation
            tvOutersTitle.isVisible = hasAnyRecommendation
            layoutBestCombo.isVisible = hasAnyRecommendation

            rvTops.isVisible = result.recommendedTops.isNotEmpty()
            tvNoTops.isVisible = result.recommendedTops.isEmpty()
            topsAdapter.submitList(result.recommendedTops)

            rvBottoms.isVisible = result.recommendedBottoms.isNotEmpty()
            tvNoBottoms.isVisible = result.recommendedBottoms.isEmpty()
            bottomsAdapter.submitList(result.recommendedBottoms)

            rvOuters.isVisible = result.recommendedOuters.isNotEmpty()
            tvNoOuters.isVisible = result.recommendedOuters.isEmpty()
            outersAdapter.submitList(result.recommendedOuters, result.packableOuter?.id)

            rvBestCombo.isVisible = result.bestCombination.isNotEmpty()
            tvNoBestCombo.isVisible = result.bestCombination.isEmpty()
            bestComboAdapter.submitList(result.bestCombination, result.packableOuter?.id)

            // [핵심 수정] 챙겨갈 아우터 존재 여부와 상관없이, 일교차가 크면 무조건 안내 문구가 보이도록 변경
            tvTempDifferenceNotice.isVisible = result.isTempDifferenceSignificant

            tvUmbrella.isVisible = result.umbrellaRecommendation.isNotBlank()
            tvUmbrella.text = result.umbrellaRecommendation

            saveStyleButton.setOnClickListener {
                val preselectedIds = result.bestCombination.map { it.id }.toIntArray()
                parentFragment?.findNavController()?.navigate(
                    HomeFragmentDirections.actionNavigationHomeToSaveStyleFragment(preselectedIds)
                )
            }
        }
    }

    fun scrollToTop() {
        scrollView.smoothScrollTo(0, 0)
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