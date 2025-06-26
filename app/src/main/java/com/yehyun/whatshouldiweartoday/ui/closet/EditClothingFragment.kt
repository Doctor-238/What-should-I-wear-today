package com.yehyun.whatshouldiweartoday.ui.closet

import android.content.DialogInterface
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.yehyun.whatshouldiweartoday.R
import com.yehyun.whatshouldiweartoday.data.database.ClothingItem
import java.io.File

class EditClothingFragment : Fragment(R.layout.fragment_edit_clothing) {

    private val viewModel: EditClothingViewModel by viewModels()
    private val args: EditClothingFragmentArgs by navArgs()

    private var currentClothingItem: ClothingItem? = null
    private var isChanged = false

    private lateinit var imageViewPreview: ImageView
    private lateinit var switchRemoveBackground: SwitchMaterial
    private lateinit var textViewTemperature: TextView
    private lateinit var editTextName: TextInputEditText
    private lateinit var chipGroupCategory: ChipGroup
    private lateinit var buttonSave: Button
    private lateinit var toolbar: MaterialToolbar

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews(view)
        setupListeners()
        observeViewModel()
        setupBackButtonHandler()
    }

    private fun setupViews(view: View) {
        imageViewPreview = view.findViewById(R.id.imageView_edit_preview)
        switchRemoveBackground = view.findViewById(R.id.switch_edit_remove_background)
        textViewTemperature = view.findViewById(R.id.textView_edit_temperature)
        editTextName = view.findViewById(R.id.editText_edit_name)
        chipGroupCategory = view.findViewById(R.id.chipGroup_edit_category)
        buttonSave = view.findViewById(R.id.button_menu_save)
        toolbar = view.findViewById(R.id.toolbar_edit)
        buttonSave.isEnabled = false // 처음에는 저장 버튼 비활성화
    }

    private fun observeViewModel() {
        viewModel.getClothingItem(args.clothingItemId).observe(viewLifecycleOwner) { item ->
            if (item != null && currentClothingItem == null) { // 데이터가 처음 로드될 때만 화면을 채움
                currentClothingItem = item
                bindDataToViews(item)
            }
        }
    }

    // DB에서 가져온 데이터로 화면을 채우는 함수
    private fun bindDataToViews(item: ClothingItem) {
        editTextName.setText(item.name)
        textViewTemperature.text = "적정 온도: ${item.suitableTemperature}°C"

        for (i in 0 until chipGroupCategory.childCount) {
            val chip = chipGroupCategory.getChildAt(i) as Chip
            if (chip.text == item.category) {
                chip.isChecked = true
                break
            }
        }

        if (item.processedImageUri != null) {
            switchRemoveBackground.visibility = View.VISIBLE
            switchRemoveBackground.isChecked = true
            Glide.with(this).load(Uri.fromFile(File(item.processedImageUri))).into(imageViewPreview)
        } else {
            switchRemoveBackground.visibility = View.GONE
            Glide.with(this).load(Uri.fromFile(File(item.imageUri))).into(imageViewPreview)
        }
    }

    private fun setupListeners() {
        toolbar.setNavigationOnClickListener { handleBackButton() }
        buttonSave.setOnClickListener { saveChangesAndExit() }

        switchRemoveBackground.setOnCheckedChangeListener { _, isChecked ->
            onDataChanged()
            val imageUri = if (isChecked) currentClothingItem?.processedImageUri else currentClothingItem?.imageUri
            Glide.with(this).load(Uri.fromFile(File(imageUri!!))).into(imageViewPreview)
        }

        editTextName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                onDataChanged()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        chipGroupCategory.setOnCheckedStateChangeListener { group, checkedIds ->
            onDataChanged()
        }
    }

    private fun onDataChanged() {
        isChanged = true
        buttonSave.isEnabled = true
    }

    private fun setupBackButtonHandler() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            handleBackButton()
        }
    }

    private fun handleBackButton() {
        if (isChanged) {
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

    private fun saveChangesAndExit() {
        val updatedName = editTextName.text.toString()
        val selectedChipId = chipGroupCategory.checkedChipId
        // 선택된 칩이 없을 경우를 대비한 안전장치
        if (selectedChipId == View.NO_ID) {
            Toast.makeText(requireContext(), "카테고리를 선택해주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        val updatedCategory = view?.findViewById<Chip>(selectedChipId)?.text.toString()

        currentClothingItem?.let {
            val updatedItem = it.copy(
                name = updatedName,
                category = updatedCategory
            )
            viewModel.updateClothingItem(updatedItem)
            findNavController().popBackStack()
        }
    }
}
