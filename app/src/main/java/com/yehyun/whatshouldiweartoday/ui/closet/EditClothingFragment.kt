package com.yehyun.whatshouldiweartoday.ui.closet

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

    private lateinit var imageViewPreview: ImageView
    private lateinit var switchRemoveBackground: SwitchMaterial
    private lateinit var textViewTemperature: TextView
    private lateinit var editTextName: TextInputEditText
    private lateinit var chipGroupCategory: ChipGroup
    private lateinit var buttonSave: Button
    private lateinit var buttonDelete: Button
    private lateinit var toolbar: MaterialToolbar

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews(view)
        observeViewModel()
    }

    private fun setupViews(view: View) {
        imageViewPreview = view.findViewById(R.id.imageView_edit_preview)
        switchRemoveBackground = view.findViewById(R.id.switch_edit_remove_background)
        textViewTemperature = view.findViewById(R.id.textView_edit_temperature)
        editTextName = view.findViewById(R.id.editText_edit_name)
        chipGroupCategory = view.findViewById(R.id.chipGroup_edit_category)
        buttonSave = view.findViewById(R.id.button_menu_save)
        buttonDelete = view.findViewById(R.id.button_delete)
        toolbar = view.findViewById(R.id.toolbar_edit)
        buttonSave.isEnabled = false
    }

    private fun observeViewModel() {
        viewModel.getClothingItem(args.clothingItemId).observe(viewLifecycleOwner) { item ->
            // observe는 화면 회전 등에서 여러 번 호출될 수 있으므로,
            // currentClothingItem이 아직 설정되지 않았을 때만 데이터를 바인딩합니다.
            if (item != null && currentClothingItem == null) {
                currentClothingItem = item
                bindDataToViews(item)
                // 데이터가 화면에 모두 그려진 후에 리스너를 설정해야 정확하게 작동합니다.
                setupListeners()
            }
        }
    }

    private fun bindDataToViews(item: ClothingItem) {
        toolbar.title = "'${item.name}' 수정"
        editTextName.setText(item.name)
        // [해결 1] 적정 온도 텍스트뷰가 확실히 표시되도록 수정
        textViewTemperature.text = "적정 온도: ${item.suitableTemperature}°C"

        for (i in 0 until chipGroupCategory.childCount) {
            val chip = chipGroupCategory.getChildAt(i) as Chip
            if (chip.text == item.category) {
                chip.isChecked = true
                break
            }
        }

        // [해결 2] DB에 저장된 useProcessedImage 값에 따라 스위치 초기 상태 설정
        if (item.processedImageUri != null) {
            switchRemoveBackground.visibility = View.VISIBLE
            switchRemoveBackground.isChecked = item.useProcessedImage
            val imageUri = if(item.useProcessedImage) item.processedImageUri else item.imageUri
            Glide.with(this).load(Uri.fromFile(File(imageUri))).into(imageViewPreview)
        } else {
            switchRemoveBackground.visibility = View.GONE
            Glide.with(this).load(Uri.fromFile(File(item.imageUri))).into(imageViewPreview)
        }
    }

    private fun setupListeners() {
        toolbar.setNavigationOnClickListener { handleBackButton() }
        buttonSave.setOnClickListener { saveChangesAndExit() }
        buttonDelete.setOnClickListener { showDeleteConfirmDialog() }

        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { checkForChanges() }
            override fun afterTextChanged(s: Editable?) {}
        }
        editTextName.addTextChangedListener(textWatcher)

        chipGroupCategory.setOnCheckedStateChangeListener { _, _ -> checkForChanges() }
        // [해결 2] 스위치 토글 시, 실시간으로 이미지뷰를 업데이트합니다.
        switchRemoveBackground.setOnCheckedChangeListener { _, isChecked ->
            val imageUri = if (isChecked) currentClothingItem?.processedImageUri else currentClothingItem?.imageUri
            imageUri?.let {
                Glide.with(this).load(Uri.fromFile(File(it))).into(imageViewPreview)
            }
            checkForChanges()
        }
    }

    // [해결 1] 데이터 변경 여부를 정확히 감지하는 로직
    private fun checkForChanges() {
        if (currentClothingItem == null) return

        val nameChanged = editTextName.text.toString() != currentClothingItem!!.name
        val selectedChip = view?.findViewById<Chip>(chipGroupCategory.checkedChipId)
        // 선택된 칩이 없을 경우를 대비하여, 원래 카테고리와 비교
        val categoryChanged = if (selectedChip != null) {
            selectedChip.text.toString() != currentClothingItem!!.category
        } else {
            true // 칩이 선택되지 않았다면 변경된 것으로 간주
        }
        val switchChanged = switchRemoveBackground.isChecked != currentClothingItem!!.useProcessedImage

        buttonSave.isEnabled = nameChanged || categoryChanged || switchChanged
    }

    private fun setupBackButtonHandler() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            handleBackButton()
        }
    }

    private fun handleBackButton() {
        // 버튼의 활성화 상태로 변경 여부를 판단
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
            .setMessage("'${currentClothingItem?.name}' 을(를) 정말 삭제하시겠습니까? 이 작업은 되돌릴 수 없습니다.")
            .setPositiveButton("예") { _, _ ->
                currentClothingItem?.let { viewModel.deleteClothingItem(it) }
                Toast.makeText(context, "삭제되었습니다.", Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
            }
            .setNegativeButton("아니오", null)
            .show()
    }

    private fun saveChangesAndExit() {
        val updatedName = editTextName.text.toString()
        val selectedChipId = chipGroupCategory.checkedChipId
        if (selectedChipId == View.NO_ID) { return }
        val updatedCategory = view?.findViewById<Chip>(selectedChipId)?.text.toString()
        val useProcessed = switchRemoveBackground.isChecked

        currentClothingItem?.let {
            val updatedItem = it.copy(
                name = updatedName,
                category = updatedCategory,
                useProcessedImage = useProcessed
            )
            viewModel.updateClothingItem(updatedItem)
            findNavController().popBackStack()
        }
    }
}
