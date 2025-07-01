// app/src/main/java/com/yehyun/whatshouldiweartoday/ui/style/SaveStyleFragment.kt

package com.yehyun.whatshouldiweartoday.ui.style

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
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

    private val viewModel: SaveStyleViewModel by activityViewModels()
    private val args: SaveStyleFragmentArgs by navArgs()
    private lateinit var adapter: SaveStyleAdapter
    private lateinit var tabLayout: TabLayout
    private lateinit var tvSelectionGuide: TextView
    private lateinit var chipGroupSeason: ChipGroup
    private lateinit var editTextName: TextInputEditText
    private lateinit var buttonSave: Button
    private lateinit var onBackPressedCallback: OnBackPressedCallback
    private lateinit var loadingOverlay: FrameLayout // 로딩 오버레이 추가

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tabLayout = view.findViewById(R.id.tab_layout_save_style_category)
        tvSelectionGuide = view.findViewById(R.id.tv_selection_guide)
        chipGroupSeason = view.findViewById(R.id.chip_group_season_save)
        editTextName = view.findViewById(R.id.editText_style_name)
        buttonSave = view.findViewById(R.id.button_save_style_final)
        loadingOverlay = view.findViewById(R.id.loading_overlay) // 로딩 오버레이 초기화

        setupRecyclerView(view)
        setupListeners(view)
        setupTabs(view)
        setupBackButtonHandler()
        observeViewModel()

        args.preselectedIds?.let {
            viewModel.preselectItems(it)
        }
    }

    private fun setupTabs(view: View) {
        val categories = listOf("전체", "상의", "하의", "아우터", "신발", "가방", "모자", "기타")
        if (tabLayout.tabCount == 0) {
            categories.forEach { tabLayout.addTab(tabLayout.newTab().setText(it)) }
        }
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
            onItemClicked = { item, _ ->
                viewModel.toggleItemSelected(item)
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
        }

        viewModel.selectedItems.observe(viewLifecycleOwner) { items ->
            adapter.setSelectedItems(items.map { it.id }.toSet())
            tvSelectionGuide.text = "스타일에 포함할 옷을 선택하세요 (${items.size}/10)"
        }

        viewModel.styleName.observe(viewLifecycleOwner) { name ->
            if (editTextName.text.toString() != name) {
                editTextName.setText(name)
            }
        }

        viewModel.selectedSeason.observe(viewLifecycleOwner) { season ->
            for (i in 0 until chipGroupSeason.childCount) {
                val chip = chipGroupSeason.getChildAt(i) as Chip
                chip.isChecked = (chip.text == season)
            }
        }

        viewModel.isSaveComplete.observe(viewLifecycleOwner) { isComplete ->
            if (isComplete) {
                Toast.makeText(requireContext(), "'${viewModel.styleName.value}' 스타일이 저장되었습니다.", Toast.LENGTH_SHORT).show()
                viewModel.resetAllState()
                findNavController().popBackStack()
            }
        }

        viewModel.hasChanges.observe(viewLifecycleOwner) {
            onBackPressedCallback.isEnabled = it
        }

        // 로딩 상태 관찰
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            loadingOverlay.isVisible = isLoading
            buttonSave.isEnabled = !isLoading // 로딩 중에는 버튼 비활성화
        }
    }

    private fun setupListeners(view: View) {
        buttonSave.setOnClickListener {
            if (viewModel.styleName.value.isNullOrEmpty()){
                Toast.makeText(requireContext(), "스타일 이름을 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (viewModel.selectedSeason.value.isNullOrEmpty()){
                Toast.makeText(requireContext(), "계절을 선택해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (viewModel.selectedItems.value.isNullOrEmpty()){
                Toast.makeText(requireContext(), "하나 이상의 옷을 선택해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.saveStyle()
        }

        view.findViewById<MaterialToolbar>(R.id.toolbar_save_style).setNavigationOnClickListener {
            handleBackButton()
        }

        editTextName.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.setStyleName(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        chipGroupSeason.setOnCheckedChangeListener { group, checkedId ->
            val chip = group.findViewById<Chip>(checkedId)
            viewModel.setSeason(chip?.text.toString())
        }
    }

    private fun setupBackButtonHandler() {
        onBackPressedCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                showCancelDialog()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, onBackPressedCallback)
    }

    private fun handleBackButton() {
        if (onBackPressedCallback.isEnabled) {
            showCancelDialog()
        } else {
            findNavController().popBackStack()
        }
    }

    private fun showCancelDialog() {
        AlertDialog.Builder(requireContext())
            .setMessage("작업을 취소하시겠습니까? 변경사항이 저장되지 않습니다.")
            .setPositiveButton("예") { _, _ ->
                viewModel.resetAllState()
                findNavController().popBackStack()
            }
            .setNegativeButton("아니오", null)
            .show()
    }

    override fun onTabReselected() {
        handleBackButton()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        onBackPressedCallback.remove()
        editTextName.removeTextChangedListener(null)
    }
}