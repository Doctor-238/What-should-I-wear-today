package com.yehyun.whatshouldiweartoday.ui.style

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.yehyun.whatshouldiweartoday.R
import com.yehyun.whatshouldiweartoday.data.database.ClothingItem
import com.yehyun.whatshouldiweartoday.ui.OnTabReselectedListener

class SaveStyleFragment : Fragment(R.layout.fragment_save_style), OnTabReselectedListener {

    private val viewModel: SaveStyleViewModel by viewModels()
    private val args: SaveStyleFragmentArgs by navArgs()
    private lateinit var adapter: SaveStyleAdapter
    private lateinit var tabLayout: TabLayout
    private lateinit var tvSelectionGuide: TextView
    private lateinit var chipGroupSeason: ChipGroup

    private val selectedItems = mutableSetOf<ClothingItem>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tabLayout = view.findViewById(R.id.tab_layout_save_style_category)
        tvSelectionGuide = view.findViewById(R.id.tv_selection_guide)
        chipGroupSeason = view.findViewById(R.id.chip_group_season_save)

        setupRecyclerView(view)
        observeViewModel()
        setupListeners(view)
        setupTabs(view)
        updateSelectionCount()
    }

    private fun setupTabs(view: View) {
        val categories = listOf("전체", "상의", "하의", "아우터", "신발", "가방", "모자", "기타")
        categories.forEach { tabLayout.addTab(tabLayout.newTab().setText(it)) }
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                viewModel.setClothingFilter(tab?.text.toString())
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupRecyclerView(view: View) {
        adapter = SaveStyleAdapter(
            onItemClicked = { item, isSelected ->
                if (isSelected) {
                    selectedItems.remove(item)
                } else {
                    if (selectedItems.size < 10) {
                        selectedItems.add(item)
                    } else {
                        Toast.makeText(requireContext(), "최대 10개까지 선택할 수 있습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
                adapter.setSelectedItems(selectedItems.map { it.id }.toSet())
                updateSelectionCount()
            },
            onItemLongClicked = { item ->
                val action = SaveStyleFragmentDirections.actionSaveStyleFragmentToEditClothingFragment(item.id)
                findNavController().navigate(action)
            }
        )
        val recyclerView = view.findViewById<RecyclerView>(R.id.rv_clothing_selection)
        recyclerView.adapter = adapter
    }

    private fun observeViewModel() {
        viewModel.filteredClothes.observe(viewLifecycleOwner) { clothes ->
            adapter.submitList(clothes)
            args.preselectedIds?.let { ids ->
                viewModel.filteredClothes.value?.let { allClothes ->
                    val preselected = allClothes.filter { it.id in ids }
                    selectedItems.addAll(preselected)
                    adapter.setSelectedItems(selectedItems.map { it.id }.toSet())
                    updateSelectionCount()
                }
            }
        }
    }

    private fun updateSelectionCount() {
        tvSelectionGuide.text = "스타일에 포함할 옷을 선택하세요 (${selectedItems.size}/10)"
    }

    private fun setupListeners(view: View) {
        view.findViewById<Button>(R.id.button_save_style_final).setOnClickListener {
            val styleName = view.findViewById<TextInputEditText>(R.id.editText_style_name).text.toString().trim()
            val selectedSeasonId = chipGroupSeason.checkedChipId
            if (selectedSeasonId == View.NO_ID) {
                Toast.makeText(requireContext(), "계절을 선택해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val selectedSeason = chipGroupSeason.findViewById<Chip>(selectedSeasonId).text.toString()

            if (styleName.isEmpty()) {
                Toast.makeText(requireContext(), "스타일 이름을 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (selectedItems.isEmpty()) {
                Toast.makeText(requireContext(), "하나 이상의 옷을 선택해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.saveStyle(styleName, selectedSeason, selectedItems.toList())
            Toast.makeText(requireContext(), "'$styleName' 스타일이 저장되었습니다.", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
        }
        view.findViewById<MaterialToolbar>(R.id.toolbar_save_style).setNavigationOnClickListener {
            findNavController().popBackStack()
        }
    }

    override fun onTabReselected() {
        findNavController().popBackStack()
    }
}