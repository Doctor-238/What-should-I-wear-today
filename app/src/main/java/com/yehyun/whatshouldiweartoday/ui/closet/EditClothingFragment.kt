package com.yehyun.whatshouldiweartoday.ui.closet

import android.content.res.Configuration
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
import android.util.TypedValue
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.view.children
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
    private lateinit var buttonReset: Button
    private lateinit var toolbar: MaterialToolbar
    private lateinit var viewColorSwatch: View
    private lateinit var layoutBackgroundRemoval: RelativeLayout
    private lateinit var settingsManager: SettingsManager
    private lateinit var buttonTempIncrease: ImageButton
    private lateinit var buttonTempDecrease: ImageButton
    private lateinit var iconSpecialEdit: ImageView
    private lateinit var tvEditFitLevel: TextView
    private lateinit var spinnerEditSize: Spinner
    private lateinit var dividerEditSizeFit: View
    private lateinit var layoutEditFitLevel: View
    private lateinit var dividerEditFitLevel: View
    private var isBindingSpinner = false
    private var spinnerBindingGeneration = 0
    private lateinit var tvEditTempHint: TextView
    private lateinit var tvEditPurpose: TextView
    private lateinit var layoutEditPurpose: View
    private lateinit var dividerEditPurpose: View

    private var toast: Toast? = null

    override fun onResume() {
        super.onResume()
        viewModel.currentClothingItem.value?.let {
            updateTemperatureDisplay(it)
            updateFitLevelDisplay(it)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settingsManager = SettingsManager(requireContext())
        setupViews(view)

        val isTablet = (resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE
        if (isTablet) {
            val container = view.findViewById<ScrollView>(R.id.scrollView).getChildAt(0) as ConstraintLayout
            val constraintSet = ConstraintSet()
            constraintSet.clone(container)
            constraintSet.constrainPercentWidth(R.id.frameLayout_edit_preview, 0.75f)
            constraintSet.applyTo(container)

            chipGroupCategory.children.forEach { chipView ->
                if (chipView is Chip) {
                    chipView.setTextSize(TypedValue.COMPLEX_UNIT_PX, chipView.textSize * 1.2f)
                }
            }
        }

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
        buttonReset = view.findViewById(R.id.button_reset)
        toolbar = view.findViewById(R.id.toolbar_edit)
        viewColorSwatch = view.findViewById(R.id.view_color_swatch_edit)
        layoutBackgroundRemoval = view.findViewById(R.id.layout_background_removal)
        buttonTempIncrease = view.findViewById(R.id.button_edit_temp_increase)
        buttonTempDecrease = view.findViewById(R.id.button_edit_temp_decrease)
        iconSpecialEdit = view.findViewById(R.id.icon_special_edit)
        tvEditFitLevel = view.findViewById(R.id.tv_edit_fit_level)
        spinnerEditSize = view.findViewById(R.id.spinner_edit_size)
        dividerEditSizeFit = view.findViewById(R.id.divider_edit_size_fit)
        layoutEditFitLevel = view.findViewById(R.id.layout_edit_fit_level)
        dividerEditFitLevel = view.findViewById(R.id.divider_edit_fit_level)
        tvEditTempHint = view.findViewById(R.id.tv_edit_temp_hint)
        tvEditPurpose = view.findViewById(R.id.tv_edit_purpose)
        layoutEditPurpose = view.findViewById(R.id.layout_edit_purpose)
        dividerEditPurpose = view.findViewById(R.id.divider_edit_purpose)
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
            val color = if (canBeSaved) Color.parseColor("#0EB4FC") else Color.GRAY
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
            viewModel.currentClothingItem.value?.let { updateResetButtonState(it) }
                ?: run { buttonReset.isEnabled = false }

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
                showToast("삭제되었습니다.")
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
            if (chip.text.toString() == item.category) {
                chip.isChecked = true
                break
            }
        }

        layoutBackgroundRemoval.isVisible = item.processedImageUri != null
        switchRemoveBackground.isChecked = item.useProcessedImage

        updateImagePreview(item)
        updateFitLevelDisplay(item)
        updatePurposeDisplay(item)
        updatePurchaseSourceDisplay(item)
        updateResetButtonState(item)

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
        textViewTemperature.isVisible = isTempCategory
        tvEditTempHint.isVisible = !isTempCategory

        if (isTempCategory) {
            val tolerance = settingsManager.getTemperatureTolerance()
            val constitutionAdjustment = settingsManager.getConstitutionAdjustment()
            val adjustedTemp = item.suitableTemperature + constitutionAdjustment

            val minTemp = adjustedTemp - tolerance
            val maxTemp = adjustedTemp + tolerance
            textViewTemperature.text = "%.1f°C ~ %.1f°C".format(minTemp, maxTemp)
            textViewTemperature.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
        }
    }

    private fun updateFitLevelDisplay(item: ClothingItem) {
        if (item.category !in AddClothingViewModel.SIZE_CATEGORIES) {
            layoutEditFitLevel.isVisible = false
            dividerEditFitLevel.isVisible = false
            return
        }
        layoutEditFitLevel.isVisible = true
        dividerEditFitLevel.isVisible = true

        // populate size spinner
        val notationType = settingsManager.sizeNotationType
        val sizeOptions = when {
            item.category in listOf("상의", "아우터") ->
                if (notationType == SettingsManager.SIZE_NOTATION_NUMERIC)
                    listOf("85", "90", "95", "100", "105", "110")
                else listOf("XS", "S", "M", "L", "XL", "XXL")
            item.category == "하의" ->
                if (notationType == SettingsManager.SIZE_NOTATION_NUMERIC)
                    listOf("26", "27", "28", "29", "30", "31", "32", "33", "34")
                else listOf("XS", "S", "M", "L", "XL", "XXL")
            else -> emptyList<String>()
        }
        val displayedSize = item.size
            ?: AddClothingViewModel.calculateItemSizeLabel(
                item.category,
                item.fitMinHeight, item.fitMaxHeight,
                item.fitMinWeight, item.fitMaxWeight,
                item.fitMinWaist, item.fitMaxWaist,
                notationType
            )
        spinnerBindingGeneration += 1
        val bindingGeneration = spinnerBindingGeneration
        isBindingSpinner = true
        val adapter = ArrayAdapter(requireContext(), R.layout.spinner_item_centered, sizeOptions)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        spinnerEditSize.adapter = adapter
        val idx = if (displayedSize != null) sizeOptions.indexOf(displayedSize) else -1
        spinnerEditSize.setSelection(if (idx >= 0) idx else 0, false)
        spinnerEditSize.post {
            if (spinnerBindingGeneration == bindingGeneration) {
                isBindingSpinner = false
            }
        }

        // fit level text (only when bodyFitEnabled)
        if (settingsManager.bodyFitEnabled && settingsManager.isBodyRegistered) {
            // 저장된 사이즈가 있으면 사이즈 비교 기반 적합도, 없으면 range 기반
            if (displayedSize != null) {
                syncFitLevelToSize(item.category, displayedSize)
            } else {
                val level = AddClothingViewModel.calculateFitLevel(
                    settingsManager.estimatedHeight, settingsManager.estimatedWeight, settingsManager.estimatedWaist,
                    item.fitMinHeight, item.fitMaxHeight,
                    item.fitMinWeight, item.fitMaxWeight,
                    item.fitMinWaist, item.fitMaxWaist
                )
                val levelColorRes = if (level == AddClothingViewModel.FIT_NO_INFO)
                    R.color.text_tertiary else AddClothingFragment.fitLevelColorRes(level)
                tvEditFitLevel.text = level
                tvEditFitLevel.setTextColor(ContextCompat.getColor(requireContext(), levelColorRes))
                tvEditFitLevel.isVisible = true
                dividerEditSizeFit.isVisible = true
            }
        } else if (settingsManager.bodyFitEnabled && !settingsManager.isBodyRegistered) {
            tvEditFitLevel.text = "설정에서 사이즈를 등록해주세요"
            tvEditFitLevel.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_tertiary))
            tvEditFitLevel.isVisible = true
            dividerEditSizeFit.isVisible = true
        } else {
            tvEditFitLevel.isVisible = false
            dividerEditSizeFit.isVisible = false
        }
    }

    private fun updatePurposeDisplay(item: ClothingItem) {
        val purposes = item.purpose.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (purposes.isEmpty()) {
            layoutEditPurpose.isVisible = false
            dividerEditPurpose.isVisible = false
        } else {
            layoutEditPurpose.isVisible = true
            dividerEditPurpose.isVisible = true
            tvEditPurpose.text = purposes.joinToString(", ")
        }
    }

    private fun updatePurchaseSourceDisplay(item: ClothingItem) {
        val source = item.purchaseSource
        val layoutPurchaseSource = view?.findViewById<View>(R.id.layout_purchase_source)
        val dividerPurchaseSource = view?.findViewById<View>(R.id.divider_purchase_source)
        val tvPurchaseSource = view?.findViewById<TextView>(R.id.tv_purchase_source)
        val ivPurchaseSourceIcon = view?.findViewById<ImageView>(R.id.iv_purchase_source_icon)

        if (source.isNullOrBlank()) {
            layoutPurchaseSource?.isVisible = false
            dividerPurchaseSource?.isVisible = false
            return
        }

        layoutPurchaseSource?.isVisible = true
        dividerPurchaseSource?.isVisible = true

        val (text, iconRes) = when (source) {
            "COUPANG" -> "쿠팡에서 구매" to R.drawable.shopping_coupang
            "MUSINSA" -> "무신사에서 구매" to R.drawable.shopping_musinsa
            "HIVER" -> "하이버에서 구매" to R.drawable.shopping_hiver
            "NAVER" -> "네이버스토어에서 구매" to R.drawable.shopping_naver
            "ABLY" -> "에이블리에서 구매" to R.drawable.shopping_ably
            "CM29" -> "29CM에서 구매" to R.drawable.shopping_29cm
            "GOOGLE" -> "구글에서 이미지 추가" to R.drawable.shopping_google
            "오늘 뭐 살래?" -> "오늘 뭐 살래?에서 구매" to R.drawable.ic_mall_logo
            else -> source to null
        }

        tvPurchaseSource?.text = text
        if (iconRes != null) {
            ivPurchaseSourceIcon?.setImageResource(iconRes)
            ivPurchaseSourceIcon?.isVisible = true
        } else {
            ivPurchaseSourceIcon?.isVisible = false
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
        buttonReset.setOnClickListener { showResetConfirmDialog() }

        editTextName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.updateName(s.toString())
            }
        })

        for (i in 0 until chipGroupCategory.childCount) {
            val chip = chipGroupCategory.getChildAt(i) as? Chip ?: continue
            chip.setOnClickListener {
                if (chip.isChecked) viewModel.updateCategory(chip.text.toString())
            }
        }

        switchRemoveBackground.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateUseProcessedImage(isChecked)
        }

        buttonTempIncrease.setOnClickListener { viewModel.increaseTemp() }
        buttonTempDecrease.setOnClickListener { viewModel.decreaseTemp() }

        spinnerEditSize.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (isBindingSpinner) return
                val selectedSize = parent.getItemAtPosition(position) as? String
                viewModel.updateSize(selectedSize)
                val category = viewModel.currentClothingItem.value?.category ?: return
                if (selectedSize != null && settingsManager.bodyFitEnabled && settingsManager.isBodyRegistered) {
                    syncFitLevelToSize(category, selectedSize)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
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
            showToast("옷 이름을 입력해주세요.")
        } else {
            viewModel.saveChanges()
        }
    }

    private fun showToast(message: String) {
        toast?.cancel()
        toast = Toast.makeText(context, message, Toast.LENGTH_SHORT)
        toast?.show()
    }

    private fun showDeleteConfirmDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("삭제 확인")
            .setMessage("'${viewModel.currentClothingItem.value?.name}' 을(를) 정말 삭제하시겠습니까?")
            .setPositiveButton("예") { _, _ -> viewModel.deleteClothingItem(args.fromStyleEdit) }
            .setNegativeButton("아니오", null)
            .show()
    }

    private fun updateResetButtonState(item: ClothingItem) {
        val resetNeeded = viewModel.isResetNeeded(item)
        if (resetNeeded) {
            buttonReset.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_button_reset)
            buttonReset.setTextColor(Color.WHITE)
            buttonReset.isEnabled = viewModel.isProcessing.value != true
        } else {
            buttonReset.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_button_reset_outline)
            buttonReset.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
            buttonReset.isEnabled = false
        }
    }

    private fun showResetConfirmDialog() {
        AlertDialog.Builder(requireContext())
            .setMessage("AI가 분석한 원래 정보로 초기화하시겠습니까?\n(이름·배경제거 제외)")
            .setPositiveButton("예") { _, _ -> viewModel.resetToAiDefaults() }
            .setNegativeButton("아니오", null)
            .show()
    }

    private fun syncFitLevelToSize(category: String, selectedSize: String) {
        val notationType = settingsManager.sizeNotationType
        val userSize = AddClothingViewModel.calculateSizeLabel(
            category,
            settingsManager.estimatedHeight, settingsManager.estimatedWeight, settingsManager.estimatedWaist,
            notationType
        ) ?: return
        val sizeList = AddClothingViewModel.getSizeList(category, notationType)
        val userIdx = sizeList.indexOf(userSize)
        val itemIdx = sizeList.indexOf(selectedSize)
        if (userIdx < 0 || itemIdx < 0) return
        val diff = kotlin.math.abs(userIdx - itemIdx)
        val level = when (diff) {
            0 -> AddClothingViewModel.FIT_VERY_GOOD
            1 -> AddClothingViewModel.FIT_GOOD
            2 -> AddClothingViewModel.FIT_NORMAL
            else -> AddClothingViewModel.FIT_BAD
        }
        tvEditFitLevel.text = level
        tvEditFitLevel.setTextColor(ContextCompat.getColor(requireContext(), AddClothingFragment.fitLevelColorRes(level)))
        tvEditFitLevel.isVisible = true
        dividerEditSizeFit.isVisible = true
    }

    override fun onTabReselected() {
        handleBackButton()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        onBackPressedCallback.remove()
        toast?.cancel()
    }
}
