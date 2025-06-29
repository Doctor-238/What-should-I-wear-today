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
import com.yehyun.whatshouldiweartoday.data.preference.SettingsManager
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
    private lateinit var settingsManager: SettingsManager

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settingsManager = SettingsManager(requireContext())
        setupViews(view)
        observeViewModel()
        setupBackButtonHandler()
    }

    override fun onResume() {
        super.onResume()
        // 설정 화면에서 돌아왔을 때, 현재 옷 데이터가 있다면 최신 설정 값으로 UI를 새로고침합니다.
        currentClothingItem?.let {
            bindDataToViews(it)
        }
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
            if (item != null) {
                // 데이터가 처음 로드될 때만 리스너를 설정합니다.
                if (currentClothingItem == null) {
                    currentClothingItem = item
                    bindDataToViews(item)
                    setupListeners()
                } else {
                    currentClothingItem = item
                }
            }
        }
    }

    private fun bindDataToViews(item: ClothingItem) {
        toolbar.title = "'${item.name}' 수정"
        editTextName.setText(item.name)

        updateTemperatureDisplay(item)

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
        } else {
            switchRemoveBackground.visibility = View.GONE
        }
        updateImagePreview()
    }

    private fun updateTemperatureDisplay(item: ClothingItem) {
        if (item.category in listOf("상의", "하의", "아우터")) {
            val tolerance = settingsManager.getTemperatureTolerance()
            val minTemp = item.suitableTemperature - tolerance
            val maxTemp = item.suitableTemperature + tolerance
            textViewTemperature.text = "적정 온도: %.1f°C ~ %.1f°C".format(minTemp, maxTemp)
            textViewTemperature.visibility = View.VISIBLE
        } else {
            textViewTemperature.visibility = View.GONE
        }
    }

    private fun updateImagePreview() {
        currentClothingItem?.let { item ->
            val imageUriString = if (item.useProcessedImage && item.processedImageUri != null) {
                item.processedImageUri
            } else {
                item.imageUri
            }
            Glide.with(this).load(Uri.fromFile(File(imageUriString))).into(imageViewPreview)
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

        chipGroupCategory.setOnCheckedChangeListener { _, checkedId ->
            currentClothingItem?.let {
                val selectedChip = view?.findViewById<Chip>(checkedId)
                if (selectedChip != null) {
                    it.category = selectedChip.text.toString()
                }
                updateTemperatureDisplay(it)
            }
            checkForChanges()
        }

        switchRemoveBackground.setOnCheckedChangeListener { _, isChecked ->
            currentClothingItem?.useProcessedImage = isChecked
            updateImagePreview()
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
        if (updatedName.isEmpty()) {
            Toast.makeText(context, "옷 이름을 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedChipId == View.NO_ID) {
            Toast.makeText(context, "옷 종류를 선택해주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        val updatedCategory = view?.findViewById<Chip>(selectedChipId)?.text.toString()

        currentClothingItem?.let {
            val updatedItem = it.copy(
                name = updatedName,
                category = updatedCategory,
                useProcessedImage = switchRemoveBackground.isChecked
            )
            viewModel.updateClothingItem(updatedItem)
            findNavController().popBackStack()
        }
    }

    override fun onTabReselected() {
        handleBackButton()
    }
}