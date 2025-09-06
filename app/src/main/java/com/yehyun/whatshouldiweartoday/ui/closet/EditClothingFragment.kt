package com.yehyun.whatshouldiweartoday.ui.closet

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
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
import com.yehyun.whatshouldiweartoday.ui.home.HomeViewModel
import java.io.File

class EditClothingFragment : Fragment(R.layout.fragment_edit_clothing), OnTabReselectedListener {

    private val viewModel: EditClothingViewModel by viewModels()
    private val homeViewModel: HomeViewModel by activityViewModels()
    private val args: EditClothingFragmentArgs by navArgs()

    private lateinit var onBackPressedCallback: OnBackPressedCallback
    private lateinit var imageViewPreview: ImageView
    private lateinit var switchRemoveBackground: SwitchMaterial
    private lateinit var textViewTemperature: TextView
    private lateinit var editTextName: TextInputEditText
    private lateinit var chipGroupCategory: ChipGroup
    private lateinit var buttonDelete: Button
    private lateinit var toolbar: MaterialToolbar
    private lateinit var viewColorSwatch: View
    private lateinit var layoutBackgroundRemoval: RelativeLayout
    private lateinit var settingsManager: SettingsManager
    private lateinit var buttonTempIncrease: ImageButton
    private lateinit var buttonTempDecrease: ImageButton
    private lateinit var iconSpecialEdit: ImageView

    override fun onResume() {
        super.onResume()
        viewModel.currentClothingItem.value?.let { updateTemperatureDisplay(it) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settingsManager = SettingsManager(requireContext())
        setupViews(view)
        viewModel.loadClothingItem(args.clothingItemId)
        setupBackButtonHandler()
        setupListeners()
        observeViewModel()
    }

    private fun setupViews(view: View) {
        imageViewPreview = view.findViewById(R.id.imageView_edit_preview)
        switchRemoveBackground = view.findViewById(R.id.switch_edit_remove_background)
        textViewTemperature = view.findViewById(R.id.textView_edit_temperature)
        editTextName = view.findViewById(R.id.editText_edit_name)
        chipGroupCategory = view.findViewById(R.id.chipGroup_edit_category)
        buttonDelete = view.findViewById(R.id.button_delete)
        toolbar = view.findViewById(R.id.toolbar_edit)
        viewColorSwatch = view.findViewById(R.id.view_color_swatch_edit)
        layoutBackgroundRemoval = view.findViewById(R.id.layout_background_removal)
        buttonTempIncrease = view.findViewById(R.id.button_edit_temp_increase)
        buttonTempDecrease = view.findViewById(R.id.button_edit_temp_decrease)
        iconSpecialEdit = view.findViewById(R.id.icon_special_edit)
        toolbar.inflateMenu(R.menu.edit_clothing_menu)
    }

    private fun observeViewModel() {
        viewModel.clothingItemFromDb.observe(viewLifecycleOwner) { dbItem ->
            dbItem?.let {
                viewModel.setInitialState(it)
            }
        }

        viewModel.currentClothingItem.observe(viewLifecycleOwner) { editingItem ->
            editingItem?.let { bindDataToViews(it) }
        }

        viewModel.canBeSaved.observe(viewLifecycleOwner) { canBeSaved ->
            val saveMenuItem = toolbar.menu.findItem(R.id.menu_save)
            saveMenuItem?.isEnabled = canBeSaved

            val title = saveMenuItem.title.toString()
            val spannable = SpannableString(title)
            val color = if (canBeSaved) Color.parseColor("#88bfec") else Color.GRAY
            spannable.setSpan(ForegroundColorSpan(color), 0, spannable.length, 0)
            spannable.setSpan(StyleSpan(Typeface.BOLD), 0, spannable.length, 0)
            saveMenuItem.title = spannable
        }

        viewModel.isChanged.observe(viewLifecycleOwner) { hasChanges ->
            onBackPressedCallback.isEnabled = hasChanges
        }

        viewModel.isProcessing.observe(viewLifecycleOwner) { isProcessing ->
            val isSavable = !isProcessing && (viewModel.canBeSaved.value == true)
            toolbar.menu.findItem(R.id.menu_save)?.isEnabled = isSavable
            buttonDelete.isEnabled = !isProcessing

            if (isProcessing) {
                toolbar.navigationIcon = null
            } else {
                toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
            }
        }

        viewModel.isSaveComplete.observe(viewLifecycleOwner) { isComplete ->
            if (isComplete) {
                findNavController().popBackStack()
            }
        }

        viewModel.isDeleteComplete.observe(viewLifecycleOwner) { isComplete ->
            if (isComplete) {
                Toast.makeText(context, "삭제되었습니다.", Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
            }
        }

        homeViewModel.todayRecommendedClothingIds.observe(viewLifecycleOwner) {
            viewModel.currentClothingItem.value?.let { bindDataToViews(it) }
        }

        homeViewModel.todayRecommendation.observe(viewLifecycleOwner) {
            viewModel.currentClothingItem.value?.let { bindDataToViews(it) }
        }
    }

    private fun bindDataToViews(item: ClothingItem) {
        if (editTextName.text.toString() != item.name) {
            editTextName.setText(item.name)
        }
        updateTemperatureDisplay(item)

        try {
            val colorDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.parseColor(item.colorHex))
                setStroke(3, Color.BLACK)
            }
            viewColorSwatch.background = colorDrawable
            viewColorSwatch.visibility = View.VISIBLE
        } catch (e: Exception) {
            viewColorSwatch.visibility = View.GONE
        }

        for (i in 0 until chipGroupCategory.childCount) {
            val chip = chipGroupCategory.getChildAt(i) as Chip
            if (chip.text == item.category) {
                chip.isChecked = true
                break
            }
        }

        layoutBackgroundRemoval.isVisible = item.processedImageUri != null
        switchRemoveBackground.isChecked = item.useProcessedImage

        updateImagePreview(item)

        val isRecommended = homeViewModel.todayRecommendedClothingIds.value?.contains(item.id) ?: false
        val isPackable = homeViewModel.todayRecommendation.value?.packableOuters?.any { it.id == item.id } ?: false

        if (settingsManager.showRecommendationIcon) {
            if (isPackable) {
                iconSpecialEdit.setImageResource(R.drawable.ic_packable_bag)
                iconSpecialEdit.isVisible = true
            } else if (isRecommended) {
                iconSpecialEdit.setImageResource(R.drawable.sun)
                iconSpecialEdit.isVisible = true
            } else {
                iconSpecialEdit.isVisible = false
            }
        } else {
            iconSpecialEdit.isVisible = false
        }
    }

    private fun updateTemperatureDisplay(item: ClothingItem) {
        val isTempCategory = item.category in listOf("상의", "하의", "아우터")

        buttonTempIncrease.isVisible = isTempCategory
        buttonTempDecrease.isVisible = isTempCategory

        if (isTempCategory) {
            val tolerance = settingsManager.getTemperatureTolerance()
            val constitutionAdjustment = settingsManager.getConstitutionAdjustment()
            val adjustedTemp = item.suitableTemperature + constitutionAdjustment

            val minTemp = adjustedTemp - tolerance
            val maxTemp = adjustedTemp + tolerance
            textViewTemperature.text = "%.1f°C ~ %.1f°C".format(minTemp, maxTemp)
            textViewTemperature.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
        } else {
            textViewTemperature.text = "상의, 하의, 아우터에만 표시됩니다."
            textViewTemperature.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
        }
    }

    private fun updateImagePreview(item: ClothingItem) {
        val imageUriString = if (item.useProcessedImage && item.processedImageUri != null) {
            item.processedImageUri
        } else {
            item.imageUri
        }
        Glide.with(this)
            .load(Uri.fromFile(File(imageUriString)))
            .into(imageViewPreview)
    }

    private fun setupListeners() {
        toolbar.setNavigationOnClickListener { handleBackButton() }

        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_save -> {
                    trySaveChanges()
                    true
                }
                else -> false
            }
        }

        buttonDelete.setOnClickListener { showDeleteConfirmDialog() }

        editTextName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.updateName(s.toString())
            }
        })

        chipGroupCategory.setOnCheckedChangeListener { _, checkedId ->
            view?.findViewById<Chip>(checkedId)?.let {
                viewModel.updateCategory(it.text.toString())
            }
        }

        switchRemoveBackground.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateUseProcessedImage(isChecked)
        }

        buttonTempIncrease.setOnClickListener { viewModel.increaseTemp() }
        buttonTempDecrease.setOnClickListener { viewModel.decreaseTemp() }
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

        if (viewModel.isChanged.value == true) {
            showSaveChangesDialog()
        } else {
            findNavController().popBackStack()
        }
    }

    private fun showSaveChangesDialog() {
        AlertDialog.Builder(requireContext())
            .setMessage("변경사항을 저장하시겠습니까?")
            .setPositiveButton("예") { _, _ -> trySaveChanges() }
            .setNegativeButton("아니오") { _, _ ->
                findNavController().popBackStack()
            }
            .setCancelable(true)
            .show()
    }

    private fun trySaveChanges() {
        if (viewModel.currentClothingItem.value?.name.isNullOrBlank()) {
            Toast.makeText(context, "옷 이름을 입력해주세요.", Toast.LENGTH_SHORT).show()
        } else {
            viewModel.saveChanges()
        }
    }

    private fun showDeleteConfirmDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("삭제 확인")
            .setMessage("'${viewModel.currentClothingItem.value?.name}' 을(를) 정말 삭제하시겠습니까?")
            .setPositiveButton("예") { _, _ -> viewModel.deleteClothingItem() }
            .setNegativeButton("아니오", null)
            .show()
    }

    override fun onTabReselected() {
        handleBackButton()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        onBackPressedCallback.remove()
    }
}