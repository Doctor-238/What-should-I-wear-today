package com.yehyun.whatshouldiweartoday.ui.closet

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.yehyun.whatshouldiweartoday.R
import com.yehyun.whatshouldiweartoday.data.preference.SettingsManager
import com.yehyun.whatshouldiweartoday.ui.OnTabReselectedListener
import kotlinx.serialization.Serializable
import java.io.InputStream


@Serializable
data class ClothingAnalysis(
    val is_wearable: Boolean,
    val category: String? = null,
    var suitable_temperature: Double? = null,
    val color_hex: String? = null,
    val fit_min_height: Double? = null,
    val fit_max_height: Double? = null,
    val fit_min_weight: Double? = null,
    val fit_max_weight: Double? = null,
    val fit_min_waist: Double? = null,
    val fit_max_waist: Double? = null,
    val purposes: List<String>? = null,
    val clothing_area_ratio: Double? = null,
    val clothing_completeness_ratio: Double? = null,
    val rejection_reason: String? = null
)

class AddClothingFragment : Fragment(R.layout.fragment_add_clothing), OnTabReselectedListener {

    private val viewModel: AddClothingViewModel by viewModels()

    private lateinit var imageViewPreview: ImageView
    private lateinit var textViewPlaceholder: TextView
    private lateinit var editTextName: TextInputEditText
    private lateinit var buttonSave: Button
    private lateinit var toolbar: MaterialToolbar
    private lateinit var progressBar: ProgressBar
    private lateinit var savingOverlay: FrameLayout
    private lateinit var switchRemoveBackground: SwitchMaterial
    private lateinit var frameLayoutPreview: FrameLayout

    private lateinit var cardAiInfo: CardView
    private lateinit var tvInfoCategory: TextView
    private lateinit var tvInfoTemperature: TextView
    private lateinit var viewInfoColorSwatch: View

    private lateinit var buttonTempIncrease: ImageButton
    private lateinit var buttonTempDecrease: ImageButton
    private lateinit var tvInfoFitLevel: TextView
    private lateinit var layoutFitLevel: View
    private lateinit var dividerFitLevel: View
    private lateinit var tvTempOnlyLabel: TextView
    private lateinit var rvPurposeChips: RecyclerView
    private lateinit var layoutPurpose: View
    private lateinit var dividerPurpose: View
    private lateinit var purposeAdapter: PurposeChipAdapter
    private lateinit var spinnerAddSize: Spinner
    private lateinit var dividerAddSizeFit: View

    private lateinit var onBackPressedCallback: OnBackPressedCallback

    private var lastClickTime = 0L
    private var toast: Toast? = null

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val bitmap = getCorrectlyOrientedBitmap(it)
            if (bitmap != null) {
                viewModel.onImageSelected(bitmap, SettingsManager(requireContext()).getEffectiveGeminiApiKey())
            } else {
                showToast("이미지를 불러오는 데 실패했습니다.")
            }
        }
    }

    private fun getCorrectlyOrientedBitmap(uri: Uri): Bitmap? {
        var inputStream: InputStream? = null
        return try {
            inputStream = requireContext().contentResolver.openInputStream(uri)
            if (inputStream == null) return null
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            inputStream = requireContext().contentResolver.openInputStream(uri)
            if (inputStream == null) return originalBitmap
            val exifInterface = ExifInterface(inputStream)
            val orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            }
            Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            inputStream?.close()
        }
    }
    override fun onResume() {
        super.onResume()
        viewModel.refreshDisplayWithNewSettings()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews(view)

        val isTablet = (resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE
        if (isTablet) {
            val container = view.findViewById<ScrollView>(R.id.scrollView).getChildAt(0) as ConstraintLayout
            val constraintSet = ConstraintSet()
            constraintSet.clone(container)
            constraintSet.constrainPercentWidth(R.id.frameLayout_preview, 0.75f)
            constraintSet.applyTo(container)
        }

        setupPurposeChips()
        setupListeners()
        setupBackButtonHandler()
        observeViewModel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        onBackPressedCallback.remove()
        toast?.cancel()
    }

    private fun setupViews(view: View) {
        imageViewPreview = view.findViewById(R.id.imageView_clothing_preview)
        textViewPlaceholder = view.findViewById(R.id.textView_placeholder)
        editTextName = view.findViewById(R.id.editText_clothing_name)
        buttonSave = view.findViewById(R.id.button_save)
        toolbar = view.findViewById(R.id.toolbar)
        progressBar = view.findViewById(R.id.progressBar)
        savingOverlay = view.findViewById(R.id.saving_overlay)
        switchRemoveBackground = view.findViewById(R.id.switch_remove_background)
        frameLayoutPreview = view.findViewById(R.id.frameLayout_preview)

        cardAiInfo = view.findViewById(R.id.card_ai_info)
        tvInfoCategory = view.findViewById(R.id.tv_info_category)
        tvInfoTemperature = view.findViewById(R.id.tv_info_temperature)
        viewInfoColorSwatch = view.findViewById(R.id.view_info_color_swatch)

        buttonTempIncrease = view.findViewById(R.id.button_temp_increase)
        buttonTempDecrease = view.findViewById(R.id.button_temp_decrease)
        tvInfoFitLevel = view.findViewById(R.id.tv_info_fit_level)
        layoutFitLevel = view.findViewById(R.id.layout_fit_level)
        dividerFitLevel = view.findViewById(R.id.divider_fit_level)
        tvTempOnlyLabel = view.findViewById(R.id.tv_temp_only_label)
        rvPurposeChips = view.findViewById(R.id.rv_purpose_chips)
        layoutPurpose = view.findViewById(R.id.layout_purpose)
        dividerPurpose = view.findViewById(R.id.divider_purpose)
        spinnerAddSize = view.findViewById(R.id.spinner_add_size)
        dividerAddSizeFit = view.findViewById(R.id.divider_add_size_fit)
    }

    private fun observeViewModel() {
        viewModel.isAiAnalyzing.observe(viewLifecycleOwner) { isAnalyzing ->
            progressBar.isVisible = isAnalyzing
            buttonSave.isEnabled = !isAnalyzing && viewModel.originalBitmap.value != null && !editTextName.text.isNullOrBlank()

            if (isAnalyzing) {
                cardAiInfo.isVisible = false
            }
        }

        viewModel.isSaving.observe(viewLifecycleOwner) { isSaving ->
            savingOverlay.isVisible = isSaving
        }

        viewModel.originalBitmap.observe(viewLifecycleOwner) { bitmap ->
            if (bitmap != null) {
                textViewPlaceholder.isVisible = false
                imageViewPreview.isVisible = true
                imageViewPreview.setImageBitmap(bitmap)
            } else {
                textViewPlaceholder.isVisible = true
                imageViewPreview.isVisible = false
                imageViewPreview.setImageDrawable(null)
                cardAiInfo.isVisible = false
            }
        }

        viewModel.useProcessedImage.observe(viewLifecycleOwner) { useProcessed ->
            val bitmapToShow = if (useProcessed) viewModel.processedBitmap.value else viewModel.originalBitmap.value
            imageViewPreview.setImageBitmap(bitmapToShow)
        }

        viewModel.segmentationSucceeded.observe(viewLifecycleOwner) { succeeded ->
            if (viewModel.isAiAnalyzing.value == false) {
                switchRemoveBackground.isEnabled = succeeded
            }
        }

        viewModel.isTemperatureVisible.observe(viewLifecycleOwner) { isVisible ->
            buttonTempIncrease.isVisible = isVisible
            buttonTempDecrease.isVisible = isVisible
            tvInfoTemperature.isVisible = isVisible
            tvTempOnlyLabel.isVisible = !isVisible
            val textColorRes = if (isVisible) R.color.text_primary else R.color.text_secondary
            tvInfoTemperature.setTextColor(ContextCompat.getColor(requireContext(), textColorRes))
        }

        viewModel.temperatureText.observe(viewLifecycleOwner) { text ->
            tvInfoTemperature.text = text
        }

        viewModel.categoryText.observe(viewLifecycleOwner) { text ->
            tvInfoCategory.text = text
            val cardVisible = text.isNotEmpty()
            cardAiInfo.isVisible = cardVisible
            layoutPurpose.isVisible = cardVisible
            dividerPurpose.isVisible = cardVisible
            val isSizeCategory = text in AddClothingViewModel.SIZE_CATEGORIES
            if (!isSizeCategory) {
                layoutFitLevel.isVisible = false
                dividerFitLevel.isVisible = false
            } else if (cardVisible) {
                layoutFitLevel.isVisible = true
                dividerFitLevel.isVisible = true
                updateSizeSpinnerOptions(text)
            }
        }

        viewModel.sizeLabelText.observe(viewLifecycleOwner) { sizeLabel ->
            if (!sizeLabel.isNullOrEmpty()) {
                val category = viewModel.categoryText.value ?: return@observe
                val options = getSizeOptions(category)
                val idx = options.indexOf(sizeLabel)
                if (idx >= 0) spinnerAddSize.setSelection(idx)
            }
        }

        viewModel.viewColor.observe(viewLifecycleOwner) { color ->
            if (color != null) {
                val colorDrawable = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(color)
                    setStroke(3, Color.BLACK)
                }
                viewInfoColorSwatch.background = colorDrawable
                viewInfoColorSwatch.isVisible = true
            } else {
                viewInfoColorSwatch.isVisible = false
            }
        }

        viewModel.fitLevelText.observe(viewLifecycleOwner) { text ->
            if (text.isNullOrEmpty()) {
                tvInfoFitLevel.isVisible = false
                dividerAddSizeFit.isVisible = false
            } else {
                tvInfoFitLevel.isVisible = true
                dividerAddSizeFit.isVisible = true
                tvInfoFitLevel.text = text
                tvInfoFitLevel.setTextColor(ContextCompat.getColor(requireContext(), fitLevelColorRes(text)))
            }
        }

        viewModel.purposeList.observe(viewLifecycleOwner) { list ->
            purposeAdapter.submitList(list)
        }

        viewModel.isSaveCompleted.observe(viewLifecycleOwner) { isCompleted ->
            if (isCompleted) {
                findNavController().popBackStack()
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            if (!message.isNullOrEmpty()) {
                showToast(message, Toast.LENGTH_LONG)
                viewModel.clearErrorMessage()
            }
        }

        viewModel.clothingName.observe(viewLifecycleOwner) { name ->
            if (editTextName.text.toString() != name) {
                editTextName.setText(name)
            }
        }

        viewModel.hasChanges.observe(viewLifecycleOwner) { hasChanges ->
            onBackPressedCallback.isEnabled = hasChanges
        }
    }

    private fun setupListeners() {
        toolbar.setNavigationOnClickListener { handleBackButton() }
        frameLayoutPreview.setOnClickListener { if (viewModel.isAiAnalyzing.value == false) openGallery() }
        buttonSave.setOnClickListener { saveClothingItem() }

        switchRemoveBackground.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onUseProcessedImageToggled(isChecked)
        }

        editTextName.addTextChangedListener { editable ->
            viewModel.setClothingName(editable.toString())
            buttonSave.isEnabled = (viewModel.isAiAnalyzing.value == false) &&
                    (viewModel.originalBitmap.value != null) &&
                    !editable.isNullOrBlank()
        }

        buttonTempIncrease.setOnClickListener { viewModel.increaseTemp() }
        buttonTempDecrease.setOnClickListener { viewModel.decreaseTemp() }
    }

    private fun openGallery() {
        if (System.currentTimeMillis() - lastClickTime < 700) {
            return
        }
        lastClickTime = System.currentTimeMillis()
        pickImageLauncher.launch("image/*")
    }
    private fun saveClothingItem() {
        val name = editTextName.text.toString().trim()
        if (name.isEmpty()) {
            showToast("옷 이름을 입력해주세요.")
            return
        }
        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view?.windowToken, 0)
        val selectedSize = if (layoutFitLevel.isVisible) spinnerAddSize.selectedItem as? String else null
        viewModel.saveClothingItem(name, selectedSize)
    }

    private fun setupBackButtonHandler() {
        onBackPressedCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                showCancelDialog()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, onBackPressedCallback)
    }

    private fun handleBackButton() {
        if (viewModel.isSaving.value == true) return
        if (onBackPressedCallback.isEnabled) {
            showCancelDialog()
        } else {
            findNavController().popBackStack()
        }
    }

    private fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        toast?.cancel()
        toast = Toast.makeText(requireContext(), message, duration)
        toast?.show()
    }

    private fun showCancelDialog() {
        AlertDialog.Builder(requireContext())
            .setMessage("작업을 취소하시겠습니까? 변경사항이 저장되지 않습니다.")
            .setPositiveButton("예") { _, _ ->
                viewModel.resetAllState()
                findNavController().popBackStack()
            }
            .setNegativeButton("아니오", null)
            .show()
    }

    override fun onTabReselected() { handleBackButton() }

    private fun getSizeOptions(category: String): List<String> {
        return AddClothingViewModel.getSizeList(category, SettingsManager(requireContext()).sizeNotationType)
    }

    private fun updateSizeSpinnerOptions(category: String) {
        val options = getSizeOptions(category)
        if (options.isEmpty()) return
        val adapter = ArrayAdapter(requireContext(), R.layout.spinner_item_centered, options)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        spinnerAddSize.adapter = adapter
        val calculatedSize = viewModel.sizeLabelText.value
        if (!calculatedSize.isNullOrEmpty()) {
            val idx = options.indexOf(calculatedSize)
            if (idx >= 0) spinnerAddSize.setSelection(idx)
        }
    }

    private fun setupPurposeChips() {
        purposeAdapter = PurposeChipAdapter(
            onDeleteRequest = { pos, purpose -> confirmDeletePurpose(pos, purpose) },
            onAddRequest = { showAddPurposeDialog() }
        )
        rvPurposeChips.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        rvPurposeChips.adapter = purposeAdapter

        val callback = object : ItemTouchHelper.Callback() {
            override fun getMovementFlags(rv: RecyclerView, vh: RecyclerView.ViewHolder): Int {
                if (vh.itemViewType != PurposeChipAdapter.TYPE_CHIP) return makeMovementFlags(0, 0)
                return makeMovementFlags(ItemTouchHelper.START or ItemTouchHelper.END, 0)
            }
            override fun onMove(rv: RecyclerView, from: RecyclerView.ViewHolder, to: RecyclerView.ViewHolder): Boolean {
                if (to.itemViewType != PurposeChipAdapter.TYPE_CHIP) return false
                purposeAdapter.moveItem(from.adapterPosition, to.adapterPosition)
                return true
            }
            override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {}
            override fun isLongPressDragEnabled() = true
            override fun onSelectedChanged(vh: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(vh, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    vh?.itemView?.animate()?.scaleX(1.12f)?.scaleY(1.12f)?.setDuration(120)?.start()
                    vh?.itemView?.elevation = 10f
                }
            }
            override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
                super.clearView(rv, vh)
                vh.itemView.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(120)?.start()
                vh.itemView.elevation = 0f
                viewModel.updatePurposes(purposeAdapter.getPurposes())
                purposeAdapter.refreshColors()
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(rvPurposeChips)
    }

    private fun confirmDeletePurpose(pos: Int, purpose: String) {
        AlertDialog.Builder(requireContext())
            .setMessage("'$purpose' 용도를 삭제하시겠습니까?")
            .setPositiveButton("예") { _, _ ->
                val updated = purposeAdapter.getPurposes().toMutableList().also { it.removeAt(pos) }
                viewModel.updatePurposes(updated)
            }
            .setNegativeButton("아니오", null)
            .show()
    }

    private fun showAddPurposeDialog() {
        val current = purposeAdapter.getPurposes()
        val available = viewModel.getAvailablePurposes().filter { it !in current }
        if (available.isEmpty()) {
            showToast("추가 가능한 용도가 없습니다.")
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle("용도 추가")
            .setItems(available.toTypedArray()) { _, which ->
                val updated = current.toMutableList().also { it.add(available[which]) }
                viewModel.updatePurposes(updated)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    companion object {
        fun fitLevelColorRes(level: String): Int {
            return when (level) {
                AddClothingViewModel.FIT_VERY_GOOD, AddClothingViewModel.FIT_GOOD -> R.color.fit_green
                AddClothingViewModel.FIT_NORMAL -> R.color.text_secondary
                AddClothingViewModel.FIT_BAD, AddClothingViewModel.FIT_VERY_BAD -> R.color.fit_red
                else -> R.color.text_tertiary
            }
        }
    }
}