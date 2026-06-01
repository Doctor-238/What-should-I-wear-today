package com.yehyun.whatshouldiweartoday.ui.mall

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.yehyun.whatshouldiweartoday.R
import com.yehyun.whatshouldiweartoday.data.database.mall.MallItem
import com.yehyun.whatshouldiweartoday.databinding.FragmentMallRecommendationBinding
import com.yehyun.whatshouldiweartoday.ui.home.HomeViewModel

class MallRecommendationFragment : Fragment() {

    private var _binding: FragmentMallRecommendationBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MallRecommendationViewModel by viewModels()
    private val cartViewModel: CartViewModel by activityViewModels()
    private val homeViewModel: HomeViewModel by activityViewModels()

    private var topsAdapter: MallRecommendationResultAdapter? = null
    private var bottomsAdapter: MallRecommendationResultAdapter? = null
    private var outersAdapter: MallRecommendationResultAdapter? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMallRecommendationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        setupCountChips()
        setupCategoryChips()
        setupRecyclerViews()
        setupButtons()
        observeViewModel()
    }

    private fun setupCountChips() {
        val chips = listOf(binding.chipCount1, binding.chipCount3, binding.chipCount5)
        val counts = listOf(1, 3, 5)
        chips.forEachIndexed { i, chip ->
            chip.setOnClickListener {
                viewModel.maxItemsPerCategory = counts[i]
                chips.forEach { it.setUnselected() }
                chip.setSelected()
            }
        }
        binding.chipCount1.setSelected()
        viewModel.maxItemsPerCategory = 1
    }

    private fun setupCategoryChips() {
        var tops = true; var bottoms = true; var outers = true
        binding.chipCatTop.setOnClickListener {
            tops = !tops; viewModel.recommendTops = tops
            if (tops) binding.chipCatTop.setSelected() else binding.chipCatTop.setUnselected()
        }
        binding.chipCatBottom.setOnClickListener {
            bottoms = !bottoms; viewModel.recommendBottoms = bottoms
            if (bottoms) binding.chipCatBottom.setSelected() else binding.chipCatBottom.setUnselected()
        }
        binding.chipCatOuter.setOnClickListener {
            outers = !outers; viewModel.recommendOuters = outers
            if (outers) binding.chipCatOuter.setSelected() else binding.chipCatOuter.setUnselected()
        }
        binding.chipCatTop.setSelected(); binding.chipCatBottom.setSelected(); binding.chipCatOuter.setSelected()
    }

    private fun TextView.setSelected() {
        setBackgroundResource(R.drawable.bg_mall_tab_selected)
        setTextColor(Color.WHITE)
        setTypeface(null, android.graphics.Typeface.BOLD)
    }

    private fun TextView.setUnselected() {
        setBackgroundResource(R.drawable.bg_tab_pill_unselected)
        setTextColor(resources.getColor(R.color.text_secondary, null))
        setTypeface(null, android.graphics.Typeface.NORMAL)
    }

    private fun setupRecyclerViews() {
        binding.rvTops.layoutManager = LinearLayoutManager(requireContext())
        binding.rvBottoms.layoutManager = LinearLayoutManager(requireContext())
        binding.rvOuters.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun setupButtons() {
        binding.btnRecommend.setOnClickListener {
            val summaries = homeViewModel.allDailySummaries.value
            if (summaries.isNullOrEmpty()) {
                showSettingsDialog("날씨 정보가 없습니다.", "홈 화면에서 날씨를 먼저 불러오세요.")
                return@setOnClickListener
            }
            val settings = com.yehyun.whatshouldiweartoday.data.preference.SettingsManager(requireContext())
            if (!settings.isBodyRegistered) {
                showSettingsDialog("체형 정보가 없습니다.", "설정에서 키와 몸무게를 입력해주세요.")
                return@setOnClickListener
            }
            if (!settings.clothingPurposeEnabled) {
                showSettingsDialog("용도 설정이 비활성화되어 있습니다.", "설정에서 용도 설정을 켜주세요.")
                return@setOnClickListener
            }
            viewModel.recommend(summaries)
        }

        binding.btnAddSelectedToCart.setOnClickListener {
            val selected = mutableListOf<MallItem>()
            topsAdapter?.getSelectedItems()?.let { selected.addAll(it) }
            bottomsAdapter?.getSelectedItems()?.let { selected.addAll(it) }
            outersAdapter?.getSelectedItems()?.let { selected.addAll(it) }
            if (selected.isEmpty()) { Toast.makeText(requireContext(), "선택된 상품이 없습니다", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            cartViewModel.addItems(selected)
            Toast.makeText(requireContext(), "${selected.size}개를 장바구니에 담았습니다", Toast.LENGTH_SHORT).show()
        }

        binding.btnAddAllToCart.setOnClickListener {
            topsAdapter?.selectAll(); bottomsAdapter?.selectAll(); outersAdapter?.selectAll()
            val all = mutableListOf<MallItem>()
            topsAdapter?.getSelectedItems()?.let { all.addAll(it) }
            bottomsAdapter?.getSelectedItems()?.let { all.addAll(it) }
            outersAdapter?.getSelectedItems()?.let { all.addAll(it) }
            cartViewModel.addItems(all)
            Toast.makeText(requireContext(), "전체 ${all.size}개를 장바구니에 담았습니다", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSettingsDialog(title: String, message: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("설정으로 이동") { _, _ ->
                findNavController().navigate(R.id.action_global_settingsFragment)
            }
            .setNegativeButton("닫기", null)
            .show()
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
            binding.btnRecommend.isEnabled = !loading
        }

        viewModel.result.observe(viewLifecycleOwner) { result ->
            if (result == null) return@observe
            binding.layoutResults.visibility = View.VISIBLE

            val onItemClick: (MallItem) -> Unit = { item ->
                val bundle = android.os.Bundle().apply { putInt("mall_item_id", item.id) }
                findNavController().navigate(R.id.action_mallRecommendationFragment_to_mallItemDetailFragment, bundle)
            }

            if (result.tops.isNotEmpty()) {
                binding.sectionTops.visibility = View.VISIBLE
                topsAdapter = MallRecommendationResultAdapter(result.tops, onItemClick) {}
                binding.rvTops.adapter = topsAdapter
            } else binding.sectionTops.visibility = View.GONE

            if (result.bottoms.isNotEmpty()) {
                binding.sectionBottoms.visibility = View.VISIBLE
                bottomsAdapter = MallRecommendationResultAdapter(result.bottoms, onItemClick) {}
                binding.rvBottoms.adapter = bottomsAdapter
            } else binding.sectionBottoms.visibility = View.GONE

            if (result.outers.isNotEmpty()) {
                binding.sectionOuters.visibility = View.VISIBLE
                outersAdapter = MallRecommendationResultAdapter(result.outers, onItemClick) {}
                binding.rvOuters.adapter = outersAdapter
            } else binding.sectionOuters.visibility = View.GONE

            if (result.tops.isEmpty() && result.bottoms.isEmpty() && result.outers.isEmpty()) {
                Toast.makeText(requireContext(), "현재 조건(온도·체형·용도)에 맞는 상품이 없습니다", Toast.LENGTH_LONG).show()
                binding.layoutResults.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
