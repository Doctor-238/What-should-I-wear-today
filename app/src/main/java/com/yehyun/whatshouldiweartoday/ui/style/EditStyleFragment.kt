package com.yehyun.whatshouldiweartoday.ui.style

import android.os.Bundle
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.addTextChangedListener
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
import com.yehyun.whatshouldiweartoday.ui.home.RecommendationAdapter

class EditStyleFragment : Fragment(R.layout.fragment_edit_style), OnTabReselectedListener {

    private val viewModel: EditStyleViewModel by viewModels()
    private val args: EditStyleFragmentArgs by navArgs()
    private lateinit var tabLayout: TabLayout

    private lateinit var onBackPressedCallback: OnBackPressedCallback
    private lateinit var adapterForAll: SaveStyleAdapter
    private lateinit var adapterForSelected: RecommendationAdapter
    private lateinit var buttonSave: Button
    private lateinit var buttonDelete: Button
    private lateinit var tvSelectedItemLabel: TextView
    private lateinit var editTextName: TextInputEditText
    private lateinit var toolbar: MaterialToolbar
    private lateinit var chipGroupSeason: ChipGroup
    private var nameTextWatcher: TextWatcher? = null

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

        viewModel.loadStyleIfNeeded(args.styleId)
    }

    private fun setupViews(view: View) {
        buttonSave = view.findViewById(R.id.button_save_style_edit)
        buttonDelete = view.findViewById(R.id.button_delete_style)
        tvSelectedItemLabel = view.findViewById(R.id.tv_selected_items_label)
        editTextName = view.findViewById(R.id.editText_edit_style_name)
        tabLayout = view.findViewById(R.id.tab_layout_edit_style_category)
    }

    private fun observeViewModel() {
        viewModel.toolbarTitle.observe(viewLifecycleOwner) { title ->
            toolbar.title = title
        }

        viewModel.currentStyleName.observe(viewLifecycleOwner) { name ->
            if (editTextName.text.toString() != name) {
                editTextName.setText(name)
            }
        }

        viewModel.currentSeason.observe(viewLifecycleOwner) { season ->
            for (i in 0 until chipGroupSeason.childCount) {
                val chip = chipGroupSeason.getChildAt(i) as Chip
                if (chip.text == season) {
                    chip.isChecked = true
                    break
                }
            }
        }

        viewModel.selectedItems.observe(viewLifecycleOwner) { items ->
            val categoryOrder = mapOf(
                "상의" to 1, "하의" to 2, "아우터" to 3, "신발" to 4,
                "가방" to 5, "모자" to 6, "기타" to 7
            )
            val sortedItems = items.sortedWith(
                compareBy<ClothingItem> { categoryOrder[it.category] ?: 8 }
                    .thenBy { it.suitableTemperature }
            )
            adapterForSelected.submitList(sortedItems)
            adapterForAll.setSelectedItems(items.map { it.id }.toSet())
            tvSelectedItemLabel.text = "현재 스타일 (${items.size}/10)"
        }

        viewModel.filteredClothes.observe(viewLifecycleOwner) { filteredClothes ->
            adapterForAll.submitList(filteredClothes)
            viewModel.selectedItems.value?.let {
                adapterForAll.setSelectedItems(it.map { item -> item.id }.toSet())
            }
        }

        viewModel.saveButtonEnabled.observe(viewLifecycleOwner) { isEnabled ->
            buttonSave.isEnabled = isEnabled
        }

        viewModel.backPressedCallbackEnabled.observe(viewLifecycleOwner) { isEnabled ->
            onBackPressedCallback.isEnabled = isEnabled
        }

        viewModel.isProcessing.observe(viewLifecycleOwner) { isProcessing ->
            buttonSave.isEnabled = !isProcessing && viewModel.saveButtonEnabled.value == true
            buttonDelete.isEnabled = !isProcessing
            if (isProcessing) {
                toolbar.navigationIcon = null
            } else {
                toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
            }
        }

        viewModel.isUpdateComplete.observe(viewLifecycleOwner) { isComplete ->
            if (isComplete) {
                Toast.makeText(context, "저장되었습니다.", Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
            }
        }

        viewModel.isDeleteComplete.observe(viewLifecycleOwner) { isComplete ->
            if (isComplete) {
                Toast.makeText(context, "'${viewModel.getOriginalStyleName()}' 스타일이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
            }
        }
    }

    private fun setupAdapters(view: View) {
        adapterForSelected = RecommendationAdapter(
            onItemClicked = { item ->
                viewModel.removeSelectedItem(item)
            },
            onItemLongClicked = { item ->
                val action = EditStyleFragmentDirections.actionEditStyleFragmentToEditClothingFragment(item.id)
                findNavController().navigate(action)
            }
        )
        view.findViewById<RecyclerView>(R.id.rv_selected_items).adapter = adapterForSelected

        // ▼▼▼▼▼ 핵심 수정: 생성자에서 클릭 리스너를 제거합니다. ▼▼▼▼▼
        adapterForAll = SaveStyleAdapter()
        val recyclerViewAll = view.findViewById<RecyclerView>(R.id.rv_all_items_for_edit)
        recyclerViewAll.adapter = adapterForAll

        // ▼▼▼▼▼ 핵심 수정: RecyclerItemClickListener를 추가하여 클릭 이벤트를 처리합니다. ▼▼▼▼▼
        recyclerViewAll.addOnItemTouchListener(RecyclerItemClickListener(
            context = requireContext(),
            recyclerView = recyclerViewAll,
            onItemClick = { _, position ->
                adapterForAll.getItem(position)?.let { item ->
                    viewModel.toggleItemSelection(item)
                }
            },
            onItemLongClick = { _, position ->
                adapterForAll.getItem(position)?.let { item ->
                    val action = EditStyleFragmentDirections.actionEditStyleFragmentToEditClothingFragment(item.id)
                    findNavController().navigate(action)
                }
            }
        ))
    }

    private fun setupListeners(view: View) {
        toolbar.setNavigationOnClickListener { handleBackButton() }
        buttonSave.setOnClickListener { saveChangesAndExit() }
        buttonDelete.setOnClickListener { showDeleteConfirmDialog() }

        nameTextWatcher = editTextName.addTextChangedListener { editable ->
            viewModel.onNameChanged(editable.toString())
        }

        chipGroupSeason.setOnCheckedChangeListener { group, checkedId ->
            val chip = group.findViewById<Chip>(checkedId)
            if (chip != null && chip.isChecked) {
                viewModel.onSeasonChanged(chip.text.toString())
            }
        }
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
        if(viewModel.isProcessing.value == true) return

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
        AlertDialog.Builder(requireContext())
            .setTitle("삭제 확인")
            .setMessage("'${viewModel.currentStyleName.value}' 스타일을 정말 삭제하시겠습니까?")
            .setPositiveButton("예") { _, _ ->
                viewModel.deleteStyle()
            }
            .setNegativeButton("아니오", null)
            .show()
    }

    private fun saveChangesAndExit() {
        val styleName = editTextName.text.toString().trim()
        if (styleName.isEmpty()) {
            Toast.makeText(context, "스타일 이름을 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        if (viewModel.selectedItems.value.isNullOrEmpty()) {
            Toast.makeText(context, "스타일에 추가된 옷이 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        val selectedSeasonId = chipGroupSeason.checkedChipId
        if (selectedSeasonId == View.NO_ID) {
            Toast.makeText(context, "계절을 선택해주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.updateStyle()
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

    override fun onDestroyView() {
        super.onDestroyView()
        editTextName.removeTextChangedListener(nameTextWatcher)
    }

    override fun onTabReselected() {
        handleBackButton()
    }
}