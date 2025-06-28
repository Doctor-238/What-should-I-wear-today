package com.yehyun.whatshouldiweartoday.ui.closet

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
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
import com.yehyun.whatshouldiweartoday.ui.OnTabReselectedListener
import java.io.File
import java.util.Locale

class EditClothingFragment : Fragment(R.layout.fragment_edit_clothing), OnTabReselectedListener {

    private val viewModel: EditClothingViewModel by viewModels()
    private val args: EditClothingFragmentArgs by navArgs()

    private var currentClothingItem: ClothingItem? = null
    private lateinit var onBackPressedCallback: OnBackPressedCallback

    private lateinit var imageViewPreview: ImageView
    private lateinit var switchRemoveBackground: SwitchMaterial
    private lateinit var textViewTemperature: TextView
    private lateinit var editTextName: TextInputEditText
    private lateinit var chipGroupCategory: ChipGroup
    private lateinit var buttonSave: Button
    private lateinit var buttonDelete: Button
    private lateinit var toolbar: MaterialToolbar
    private lateinit var viewColorSwatch: View
    private lateinit var textColorLabel: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews(view)
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
        buttonDelete = view.findViewById(R.id.button_delete)
        toolbar = view.findViewById(R.id.toolbar_edit)
        viewColorSwatch = view.findViewById(R.id.view_color_swatch_edit)
        textColorLabel = view.findViewById(R.id.textView_color_label_edit)
        buttonSave.isEnabled = false
    }

    private fun observeViewModel() {
        viewModel.getClothingItem(args.clothingItemId).observe(viewLifecycleOwner) { item ->
            if (item != null && currentClothingItem == null) {
                currentClothingItem = item
                bindDataToViews(item)
                setupListeners()
            }
        }
    }

    private fun bindDataToViews(item: ClothingItem) {
        toolbar.title = "'${item.name}' 수정"
        editTextName.setText(item.name)
        // [핵심 수정] 소수점 첫째 자리까지 온도를 표시
        textViewTemperature.text = "적정 온도: ${String.format(Locale.US, "%.1f", item.suitableTemperature)}°C"

        try {
            viewColorSwatch.setBackgroundColor(Color.parseColor(item.colorHex))
            viewColorSwatch.visibility = View.VISIBLE
            textColorLabel.visibility = View.VISIBLE
        } catch (e: Exception) {
            viewColorSwatch.visibility = View.GONE
            textColorLabel.visibility = View.GONE
        }

        for (i in 0 until chipGroupCategory.childCount) {
            val chip = chipGroupCategory.getChildAt(i) as Chip
            if (chip.text == item.category) {
                chip.isChecked = true
                break
            }
        }

        if (item.processedImageUri != null) {
            switchRemoveBackground.visibility = View.VISIBLE
            switchRemoveBackground.isChecked = item.useProcessedImage
            val imageUri = if(item.useProcessedImage) item.processedImageUri else item.imageUri
            imageUri?.let { Glide.with(this).load(Uri.fromFile(File(it))).into(imageViewPreview) }
        } else {
            switchRemoveBackground.visibility = View.GONE
            item.imageUri.let { Glide.with(this).load(Uri.fromFile(File(it))).into(imageViewPreview) }
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

        chipGroupCategory.setOnCheckedChangeListener { _, _ ->
            checkForChanges()
        }

        switchRemoveBackground.setOnCheckedChangeListener { _, _ ->
            checkForChanges()
        }
    }

    private fun checkForChanges() {
        if (currentClothingItem == null) return

        val nameChanged = editTextName.text.toString() != currentClothingItem!!.name
        val selectedChip = view?.findViewById<Chip>(chipGroupCategory.checkedChipId)
        val categoryChanged = if (selectedChip != null) {
            selectedChip.text.toString() != currentClothingItem!!.category
        } else {
            true
        }
        val switchChanged = switchRemoveBackground.isChecked != currentClothingItem!!.useProcessedImage

        val hasChanges = nameChanged || categoryChanged || switchChanged
        buttonSave.isEnabled = hasChanges
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
        val updatedName = editTextName.text.toString().trim()
        val selectedChipId = chipGroupCategory.checkedChipId
        if (selectedChipId == View.NO_ID) {
            Toast.makeText(context, "옷 종류를 선택해주세요.", Toast.LENGTH_SHORT).show()
            return
        }
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

    override fun onTabReselected() {
        handleBackButton()
    }
}