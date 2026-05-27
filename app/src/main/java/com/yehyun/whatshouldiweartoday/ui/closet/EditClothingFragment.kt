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
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.ScrollView
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
    private lateinit var toolbar: MaterialToolbar
    private lateinit var viewColorSwatch: View
    private lateinit var layoutBackgroundRemoval: RelativeLayout
    private lateinit var settingsManager: SettingsManager
    private lateinit var buttonTempIncrease: ImageButton
    private lateinit var buttonTempDecrease: ImageButton
    private lateinit var iconSpecialEdit: ImageView
    private lateinit var tvEditFitLevel: TextView
    private lateinit var tvEditSizeLabel: TextView
    private lateinit var dividerEditSizeFit: View
    private lateinit var layoutEditFitLevel: View
    private lateinit var dividerEditFitLevel: View
    private lateinit var tvEditTempHint: TextView
    private lateinit var tvEditPurpose: TextView
    private lateinit var layoutEditPurpose: View
    private lateinit var dividerEditPurpose: View

    private var toast: Toast? = null

    override fun onResume() {
        super.onResume()
        viewModel.currentClothingItem.value?.let { updateTemperatureDisplay(it) }
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
        toolbar = view.findViewById(R.id.toolbar_edit)
        viewColorSwatch = view.findViewById(R.id.view_color_swatch_edit)
        layoutBackgroundRemoval = view.findViewById(R.id.layout_background_removal)
        buttonTempIncrease = view.findViewById(R.id.button_edit_temp_increase)
        buttonTempDecrease = view.findViewById(R.id.button_edit_temp_decrease)
        iconSpecialEdit = view.findViewById(R.id.icon_special_edit)
        tvEditFitLevel = view.findViewById(R.id.tv_edit_fit_level)
        tvEditSizeLabel = view.findViewById(R.id.tv_edit_size_label)
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
            if (chip.text == item.category) {
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
        // 상의/하의/아우터 외 카테고리는 사이즈 행 자체를 숨긴다
        if (item.category !in AddClothingViewModel.SIZE_CATEGORIES) {
            layoutEditFitLevel.isVisible = false
            dividerEditFitLevel.isVisible = false
            return
        }
        if (!settingsManager.bodyFitEnabled) {
            layoutEditFitLevel.isVisible = false
            dividerEditFitLevel.isVisible = false
            return
        }
        layoutEditFitLevel.isVisible = true
        dividerEditFitLevel.isVisible = true
        if (settingsManager.isBodyRegistered) {
            val level = AddClothingViewModel.calculateFitLevel(
                settingsManager.estimatedHeight, settingsManager.estimatedWeight, settingsManager.estimatedWaist,
                item.fitMinHeight, item.fitMaxHeight,
                item.fitMinWeight, item.fitMaxWeight,
                item.fitMinWaist, item.fitMaxWaist
            )
            val sizeLabel = AddClothingViewModel.calculateSizeLabel(
                item.category,
                settingsManager.estimatedHeight, settingsManager.estimatedWeight, settingsManager.estimatedWaist,
                settingsManager.sizeNotationType
            )
            // 적합여부(왼쪽, 컬러) │ 사이즈(오른쪽, 기본색)
            val levelColorRes = if (level == AddClothingViewModel.FIT_NO_INFO)
                R.color.text_tertiary else AddClothingFragment.fitLevelColorRes(level)
            tvEditFitLevel.text = level
            tvEditFitLevel.setTextColor(ContextCompat.getColor(requireContext(), levelColorRes))
            if (sizeLabel != null) {
                dividerEditSizeFit.isVisible = true
                tvEditSizeLabel.isVisible = true
                tvEditSizeLabel.text = sizeLabel
            } else {
                dividerEditSizeFit.isVisible = false
                tvEditSizeLabel.isVisible = false
            }
        } else {
            tvEditFitLevel.text = "설정에서 사이즈를 등록해주세요"
            tvEditFitLevel.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_tertiary))
            dividerEditSizeFit.isVisible = false
            tvEditSizeLabel.isVisible = false
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

    override fun onTabReselected() {
        handleBackButton()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        onBackPressedCallback.remove()
        toast?.cancel()
    }
}