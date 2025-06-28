package com.yehyun.whatshouldiweartoday.ui.style

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputEditText
import com.yehyun.whatshouldiweartoday.R
import com.yehyun.whatshouldiweartoday.data.database.ClothingItem
import com.yehyun.whatshouldiweartoday.data.database.SavedStyle
import com.yehyun.whatshouldiweartoday.ui.home.RecommendationAdapter

class EditStyleFragment : Fragment(R.layout.fragment_edit_style) {

    private val viewModel: EditStyleViewModel by viewModels()
    private val args: EditStyleFragmentArgs by navArgs()

    // [수정] 원본 데이터와 현재 선택된 아이템 목록을 명확히 구분
    private var initialStyleData: Pair<String, List<Int>>? = null
    private val currentSelectedItems = mutableListOf<ClothingItem>()

    private lateinit var adapterForAll: SaveStyleAdapter
    private lateinit var adapterForSelected: RecommendationAdapter

    private lateinit var buttonSave: Button
    private lateinit var tvSelectedItemLabel: TextView
    private lateinit var editTextName: TextInputEditText

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews(view)
        setupAdapters(view)
        observeViewModel()
        setupListeners(view)
        setupBackButtonHandler()
    }

    private fun setupViews(view: View) {
        buttonSave = view.findViewById(R.id.button_save_style_edit)
        tvSelectedItemLabel = view.findViewById(R.id.tv_selected_items_label)
        editTextName = view.findViewById(R.id.editText_edit_style_name)
        buttonSave.isEnabled = false
    }

    private fun setupAdapters(view: View) {
        // 선택된 아이템 목록을 보여주는 어댑터
        adapterForSelected = RecommendationAdapter { item ->
            currentSelectedItems.remove(item)
            updateAdaptersAndCheckChanges()
        }
        view.findViewById<RecyclerView>(R.id.rv_selected_items).adapter = adapterForSelected

        // 옷장 전체 목록을 보여주는 어댑터
        adapterForAll = SaveStyleAdapter { item, isSelected ->
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
        }
        view.findViewById<RecyclerView>(R.id.rv_all_items_for_edit).adapter = adapterForAll
    }

    private fun observeViewModel() {
        // 수정할 스타일 정보를 DB에서 가져옴
        viewModel.getStyleWithItems(args.styleId).observe(viewLifecycleOwner) { styleWithItems ->
            if (styleWithItems != null && initialStyleData == null) {
                val style = styleWithItems.style
                val items = styleWithItems.items

                // 원본 데이터를 저장 (변경 여부 확인용)
                initialStyleData = Pair(style.styleName, items.map { it.id })
                currentSelectedItems.addAll(items)

                editTextName.setText(style.styleName)
                updateAdaptersAndCheckChanges()
            }
        }

        // 옷장 전체 목록을 가져옴
        viewModel.allClothes.observe(viewLifecycleOwner) { allClothes ->
            adapterForAll.submitList(allClothes)
        }
    }

    private fun updateAdaptersAndCheckChanges() {
        // 선택된 아이템 목록 UI 업데이트
        adapterForSelected.submitList(currentSelectedItems.toList())
        // 옷장 목록의 선택 상태 UI 업데이트
        adapterForAll.setSelectedItems(currentSelectedItems.map { it.id }.toSet())
        tvSelectedItemLabel.text = "현재 스타일 (${currentSelectedItems.size}/10)"
        // 변경사항 확인
        checkForChanges()
    }

    private fun setupListeners(view: View) {
        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar_edit_style)
        val deleteButton = view.findViewById<Button>(R.id.button_delete_style)

        toolbar.setNavigationOnClickListener { handleBackButton() }
        buttonSave.setOnClickListener { saveChangesAndExit() }
        deleteButton.setOnClickListener { showDeleteConfirmDialog() }

        editTextName.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(s: Editable?) { checkForChanges() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun checkForChanges() {
        if (initialStyleData == null) return
        val initialName = initialStyleData!!.first
        val initialIds = initialStyleData!!.second.toSet()

        val currentName = editTextName.text.toString()
        val currentIds = currentSelectedItems.map { it.id }.toSet()

        val isChanged = initialName != currentName || initialIds != currentIds
        buttonSave.isEnabled = isChanged
    }

    private fun handleBackButton() {
        if (buttonSave.isEnabled) { showSaveChangesDialog() }
        else { findNavController().popBackStack() }
    }

    private fun showSaveChangesDialog() { /* ... */ }
    private fun showDeleteConfirmDialog() { /* ... */ }

    private fun saveChangesAndExit() {
        if (currentSelectedItems.isEmpty()) {
            Toast.makeText(context, "스타일에 추가된 옷이 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val updatedStyle = viewModel.getStyleWithItems(args.styleId).value?.style?.copy(
            styleName = editTextName.text.toString()
        ) ?: return

        viewModel.updateStyle(updatedStyle, currentSelectedItems.toList())
        findNavController().popBackStack()
    }

    private fun setupBackButtonHandler() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) { handleBackButton() }
    }
}
