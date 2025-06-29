package com.yehyun.whatshouldiweartoday.ui.style

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
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
import com.yehyun.whatshouldiweartoday.data.database.SavedStyle
import com.yehyun.whatshouldiweartoday.ui.OnTabReselectedListener
import com.yehyun.whatshouldiweartoday.ui.home.RecommendationAdapter

class EditStyleFragment : Fragment(R.layout.fragment_edit_style), OnTabReselectedListener {

    private val viewModel: EditStyleViewModel by viewModels()
    private val args: EditStyleFragmentArgs by navArgs()
    private lateinit var tabLayout: TabLayout
    private var originalStyle: SavedStyle? = null
    private var initialItemIds: Set<Int>? = null
    private val currentSelectedItems = mutableListOf<ClothingItem>()
    private lateinit var onBackPressedCallback: OnBackPressedCallback
    private lateinit var adapterForAll: SaveStyleAdapter
    private lateinit var adapterForSelected: RecommendationAdapter
    private lateinit var buttonSave: Button
    private lateinit var tvSelectedItemLabel: TextView
    private lateinit var editTextName: TextInputEditText
    private lateinit var toolbar: MaterialToolbar
    private lateinit var chipGroupSeason: ChipGroup

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbar = view.findViewById(R.id.toolbar_edit_style)
        chipGroupSeason = view.findViewById(R.id.chip_group_season_edit)
        setupViews(view)
        setupAdapters(view)
        observeViewModel()
        setupListeners(view)
        setupBackButtonHandler()
        setupTabs(view)
    }

    private fun setupViews(view: View) {
        buttonSave = view.findViewById(R.id.button_save_style_edit)
        tvSelectedItemLabel = view.findViewById(R.id.tv_selected_items_label)
        editTextName = view.findViewById(R.id.editText_edit_style_name)
        buttonSave.isEnabled = false
        tabLayout = view.findViewById(R.id.tab_layout_edit_style_category)
    }

    private fun observeViewModel() {
        viewModel.getStyleWithItems(args.styleId).observe(viewLifecycleOwner) { styleWithItems ->
            if (styleWithItems != null && initialItemIds == null) {
                originalStyle = styleWithItems.style
                initialItemIds = styleWithItems.items.map { it.id }.toSet()
                currentSelectedItems.clear()
                currentSelectedItems.addAll(styleWithItems.items)
                toolbar.title = "'${originalStyle!!.styleName}' 수정"
                editTextName.setText(originalStyle!!.styleName)
                for (i in 0 until chipGroupSeason.childCount) {
                    val chip = chipGroupSeason.getChildAt(i) as Chip
                    if (chip.text == styleWithItems.style.season) {
                        chip.isChecked = true
                        break
                    }
                }
                updateAdaptersAndCheckChanges()
            }
        }
        viewModel.filteredClothes.observe(viewLifecycleOwner) { filteredClothes ->
            adapterForAll.submitList(filteredClothes)
            adapterForAll.setSelectedItems(currentSelectedItems.map { it.id }.toSet())
        }
    }

    private fun setupAdapters(view: View) {
        adapterForSelected = RecommendationAdapter { item ->
            currentSelectedItems.remove(item)
            updateAdaptersAndCheckChanges()
        }
        view.findViewById<RecyclerView>(R.id.rv_selected_items).adapter = adapterForSelected
        adapterForAll = SaveStyleAdapter(
            onItemClicked = { item, isSelected ->
                if (isSelected) {
                    currentSelectedItems.remove(item)
                } else {
                    if (currentSelectedItems.size < 10) {
                        currentSelectedItems.add(item)
                    } else {
                        Toast.makeText(context, "최대 10개까지 선택할 수 있습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
                updateAdaptersAndCheckChanges()
            },
            onItemLongClicked = { item ->
                val action = EditStyleFragmentDirections.actionEditStyleFragmentToEditClothingFragment(item.id)
                findNavController().navigate(action)
            }
        )
        view.findViewById<RecyclerView>(R.id.rv_all_items_for_edit).adapter = adapterForAll
    }

    private fun setupListeners(view: View) {
        val deleteButton = view.findViewById<Button>(R.id.button_delete_style)
        toolbar.setNavigationOnClickListener { handleBackButton() }
        buttonSave.setOnClickListener { saveChangesAndExit() }
        deleteButton.setOnClickListener { showDeleteConfirmDialog() }
        editTextName.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(s: Editable?) { checkForChanges() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        chipGroupSeason.setOnCheckedChangeListener { _, _ -> checkForChanges() }
    }

    private fun updateAdaptersAndCheckChanges() {
        val categoryOrder = mapOf(
            "상의" to 1, "하의" to 2, "아우터" to 3, "신발" to 4,
            "가방" to 5, "모자" to 6, "기타" to 7
        )
        val sortedItems = currentSelectedItems.sortedWith(
            compareBy<ClothingItem> { categoryOrder[it.category] ?: 8 }
                .thenBy { it.suitableTemperature }
        )
        adapterForSelected.submitList(sortedItems)
        adapterForAll.setSelectedItems(currentSelectedItems.map { it.id }.toSet())
        tvSelectedItemLabel.text = "현재 스타일 (${currentSelectedItems.size}/10)"
        checkForChanges()
    }

    private fun checkForChanges() {
        if (originalStyle == null || initialItemIds == null) return
        val initialName = originalStyle!!.styleName
        val currentName = editTextName.text.toString().trim()
        val currentIds = currentSelectedItems.map { it.id }.toSet()
        val initialSeason = originalStyle!!.season
        val selectedSeasonId = chipGroupSeason.checkedChipId
        val currentSeason = if (selectedSeasonId != View.NO_ID) {
            chipGroupSeason.findViewById<Chip>(selectedSeasonId).text.toString()
        } else { "" }
        val hasChanges = initialName != currentName || initialItemIds != currentIds || initialSeason != currentSeason
        buttonSave.isEnabled = hasChanges && currentName.isNotEmpty() && selectedSeasonId != View.NO_ID
        onBackPressedCallback.isEnabled = hasChanges
    }

    private fun setupBackButtonHandler() {
        onBackPressedCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                showSaveChangesDialog()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, onBackPressedCallback)
    }

    private fun handleBackButton() {
        if (buttonSave.isEnabled) {
            showSaveChangesDialog()
        } else {
            findNavController().popBackStack()
        }
    }

    private fun showSaveChangesDialog() {
        AlertDialog.Builder(requireContext())
            .setMessage("변경사항을 저장하시겠습니까?")
            .setPositiveButton("예") { _, _ -> saveChangesAndExit() }
            .setNegativeButton("아니오") { _, _ -> findNavController().popBackStack() }
            .setCancelable(false)
            .show()
    }

    private fun showDeleteConfirmDialog() {
        originalStyle?.let { styleToDelete ->
            AlertDialog.Builder(requireContext())
                .setTitle("삭제 확인")
                .setMessage("'${styleToDelete.styleName}' 스타일을 정말 삭제하시겠습니까?")
                .setPositiveButton("예") { _, _ ->
                    viewModel.deleteStyle(styleToDelete)
                    Toast.makeText(context, "'${styleToDelete.styleName}' 스타일이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack()
                }
                .setNegativeButton("아니오", null)
                .show()
        }
    }

    private fun saveChangesAndExit() {
        val styleName = editTextName.text.toString().trim()
        if (styleName.isEmpty()) {
            Toast.makeText(context, "스타일 이름을 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        if (currentSelectedItems.isEmpty()) {
            Toast.makeText(context, "스타일에 추가된 옷이 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        val selectedSeasonId = chipGroupSeason.checkedChipId
        if (selectedSeasonId == View.NO_ID) {
            Toast.makeText(context, "계절을 선택해주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        val selectedSeason = chipGroupSeason.findViewById<Chip>(selectedSeasonId).text.toString()
        originalStyle?.let { styleToUpdate ->
            val updatedStyle = styleToUpdate.copy(styleName = styleName, season = selectedSeason)
            viewModel.updateStyle(updatedStyle, currentSelectedItems.toList())
            Toast.makeText(context, "저장되었습니다.", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
        }
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

    override fun onTabReselected() {
        handleBackButton()
    }
}