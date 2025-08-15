package com.yehyun.whatshouldiweartoday.ui.style

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.yehyun.whatshouldiweartoday.R
import com.yehyun.whatshouldiweartoday.ui.OnTabReselectedListener
import kotlin.math.abs

class EditStyleFragment : Fragment(R.layout.fragment_edit_style), OnTabReselectedListener {

    private val viewModel: EditStyleViewModel by viewModels()
    private val args: EditStyleFragmentArgs by navArgs()
    private lateinit var tabLayout: TabLayout

    private lateinit var onBackPressedCallback: OnBackPressedCallback
    private lateinit var adapterForAll: SaveStyleAdapter
    private lateinit var adapterForSelected: SaveStyleAdapter
    private lateinit var buttonDelete: TextView
    private lateinit var tvSelectedItemLabel: TextView
    private lateinit var editTextName: TextInputEditText
    private lateinit var toolbar: MaterialToolbar
    private lateinit var chipGroupSeason: ChipGroup
    private var nameTextWatcher: TextWatcher? = null
    private val defaultItemAnimator = DefaultItemAnimator()


    override fun onResume() {
        super.onResume()
        viewModel.refreshCurrentStyle()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbar = view.findViewById(R.id.toolbar_edit_style)
        chipGroupSeason = view.findViewById(R.id.chip_group_season_edit)
        setupViews(view)
        setupAdapters(view)
        observeViewModel()
        setupListeners()
        setupBackButtonHandler()
        setupTabs(view)

        viewModel.loadStyleIfNeeded(args.styleId)
    }

    private fun setupViews(view: View) {
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
            adapterForSelected.submitList(items.toMutableList())
            adapterForAll.notifyDataSetChanged()
            tvSelectedItemLabel.text = "현재 스타일 (${items.size}/9)"
        }

        viewModel.filteredClothes.observe(viewLifecycleOwner) { filteredClothes ->
            adapterForAll.submitList(filteredClothes)
        }

        viewModel.saveButtonEnabled.observe(viewLifecycleOwner) { isEnabled ->
            val saveMenuItem = toolbar.menu.findItem(R.id.menu_save_style)
            saveMenuItem?.isEnabled = isEnabled

            val title = saveMenuItem?.title.toString()
            val spannable = SpannableString(title)
            val color = if (isEnabled) Color.parseColor("#0EB4FC") else Color.GRAY
            spannable.setSpan(ForegroundColorSpan(color), 0, spannable.length, 0)
            spannable.setSpan(StyleSpan(Typeface.BOLD), 0, spannable.length, 0)
            saveMenuItem?.title = spannable
        }

        viewModel.backPressedCallbackEnabled.observe(viewLifecycleOwner) { isEnabled ->
            onBackPressedCallback.isEnabled = isEnabled
        }

        viewModel.isProcessing.observe(viewLifecycleOwner) { isProcessing ->
            val isSavable = !isProcessing && (viewModel.saveButtonEnabled.value == true)
            toolbar.menu.findItem(R.id.menu_save_style)?.isEnabled = isSavable
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

    // ▼▼▼▼▼ 핵심 수정: 스크롤과 클릭 이벤트를 분리하는 TouchListener를 설정하는 함수 ▼▼▼▼▼
    private fun addScrollTouchListener(recyclerView: RecyclerView) {
        val touchSlop = ViewConfiguration.get(requireContext()).scaledTouchSlop
        var startX = 0f
        var startY = 0f

        val touchListener = object : RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = e.x
                        startY = e.y
                        // 부모 뷰(ScrollView)가 터치 이벤트를 가로채지 못하도록 설정
                        rv.parent.requestDisallowInterceptTouchEvent(true)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = abs(e.x - startX)
                        val dy = abs(e.y - startY)
                        // 수직 스크롤이 확실할 경우, 부모 뷰가 이벤트를 가져가도록 허용
                        if (dy > touchSlop && dy > dx) {
                            rv.parent.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                }
                // 이 리스너는 이벤트를 가로채지만 소비하지는 않으므로 항상 false 반환
                return false
            }
            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}
            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
        }
        recyclerView.addOnItemTouchListener(touchListener)
    }
    // ▲▲▲▲▲ 핵심 수정 ▲▲▲▲▲

    private fun setupAdapters(view: View) {
        val rvSelectedItems = view.findViewById<RecyclerView>(R.id.rv_selected_items)
        rvSelectedItems.layoutManager = GridLayoutManager(context, 3)
        rvSelectedItems.itemAnimator = DefaultItemAnimator()
        adapterForSelected = SaveStyleAdapter { false }
        rvSelectedItems.adapter = adapterForSelected

        rvSelectedItems.addOnItemTouchListener(RecyclerItemClickListener(
            context = requireContext(),
            recyclerView = rvSelectedItems,
            onItemClick = { _, position ->
                adapterForSelected.currentList.getOrNull(position)?.let {
                    viewModel.toggleItemSelection(it)
                }
            },
            onItemLongClick = { _, position ->
                adapterForSelected.currentList.getOrNull(position)?.let {
                    val action = EditStyleFragmentDirections.actionEditStyleFragmentToEditClothingFragment(it.id)
                    findNavController().navigate(action)
                }
            }
        ))

        val rvAllItems = view.findViewById<RecyclerView>(R.id.rv_all_items_for_edit)
        rvAllItems.itemAnimator = DefaultItemAnimator()
        adapterForAll = SaveStyleAdapter { false }
        rvAllItems.adapter = adapterForAll

        rvAllItems.addOnItemTouchListener(RecyclerItemClickListener(
            context = requireContext(),
            recyclerView = rvAllItems,
            onItemClick = { _, position ->
                adapterForAll.currentList.getOrNull(position)?.let {
                    rvAllItems.itemAnimator = defaultItemAnimator
                    viewModel.toggleItemSelection(it)
                }
            },
            onItemLongClick = { _, position ->
                adapterForAll.currentList.getOrNull(position)?.let { item ->
                    val action = EditStyleFragmentDirections.actionEditStyleFragmentToEditClothingFragment(item.id)
                    findNavController().navigate(action)
                }
            }

        ))



        // ▼▼▼▼▼ 핵심 수정: 두 RecyclerView에 터치 리스너를 추가합니다. ▼▼▼▼▼
        addScrollTouchListener(rvSelectedItems)
        addScrollTouchListener(rvAllItems)
        // ▲▲▲▲▲ 핵심 수정 ▲▲▲▲▲
    }

    private fun setupListeners() {
        toolbar.setNavigationOnClickListener { handleBackButton() }
        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_save_style -> {
                    saveChangesAndExit()
                    true
                }
                else -> false
            }
        }

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
                if (viewModel.saveButtonEnabled.value == true) {
                    showSaveChangesDialog()
                } else {
                    findNavController().popBackStack()
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, onBackPressedCallback)
    }

    private fun handleBackButton() {
        if(viewModel.isProcessing.value == true) return

        if (viewModel.saveButtonEnabled.value == true) {
            showSaveChangesDialog()
        } else {
            findNavController().popBackStack()
        }
    }

    private fun showSaveChangesDialog() {
        AlertDialog.Builder(requireContext())
            .setMessage("변경사항을 저장하시겠습니까?")
            .setPositiveButton("예") { _, _ ->
                saveChangesAndExit()
            }
            .setNegativeButton("아니오") { _, _ ->
                if (viewModel.selectedItems.value.isNullOrEmpty()) {
                    viewModel.deleteStyle()}
                findNavController().popBackStack()
            }
            .setCancelable(true)
            .show()
    }

    private fun showDeleteConfirmDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("삭제 확인")
            .setMessage("'${viewModel.getOriginalStyleName()}' 스타일을 정말 삭제하시겠습니까?")
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
            Toast.makeText(context, "스타일에 추가된 옷이 없습니다. 스타일을 삭제하시겠습니까?", Toast.LENGTH_LONG).show()
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
        val rvAllItems = view.findViewById<RecyclerView>(R.id.rv_all_items_for_edit)
        if (tabLayout.tabCount == 0) {
            categories.forEach { tabLayout.addTab(tabLayout.newTab().setText(it)) }
        }
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                rvAllItems.itemAnimator = null
                viewModel.setClothingFilter(tab?.text.toString())
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {
                rvAllItems.smoothScrollToPosition(0)
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        nameTextWatcher?.let { editTextName.removeTextChangedListener(it) }
        onBackPressedCallback.remove()
    }

    override fun onTabReselected() {
        handleBackButton()
    }
}