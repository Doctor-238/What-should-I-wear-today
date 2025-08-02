// app/src/main/java/com/yehyun/whatshouldiweartoday/ui/closet/AddClothingFragment.kt
package com.yehyun.whatshouldiweartoday.ui.closet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.yehyun.whatshouldiweartoday.R
import com.yehyun.whatshouldiweartoday.ui.OnTabReselectedListener
import kotlinx.serialization.Serializable
import android.graphics.Matrix
import android.graphics.drawable.GradientDrawable
import android.widget.ImageButton
import androidx.exifinterface.media.ExifInterface
import java.io.InputStream


@Serializable
data class ClothingAnalysis(
    val is_wearable: Boolean,
    val category: String? = null,
    var suitable_temperature: Double? = null,
    val color_hex: String? = null
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


    private lateinit var onBackPressedCallback: OnBackPressedCallback

    private var lastClickTime = 0L

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val bitmap = getCorrectlyOrientedBitmap(it)
            if (bitmap != null) {
                viewModel.onImageSelected(bitmap, getString(R.string.gemini_api_key))
            } else {
                Toast.makeText(requireContext(), "이미지를 불러오는 데 실패했습니다.", Toast.LENGTH_SHORT).show()
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
        setupListeners()
        setupBackButtonHandler()
        observeViewModel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        onBackPressedCallback.remove()
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
            val textColorRes = if (isVisible) R.color.text_primary else R.color.text_secondary
            tvInfoTemperature.setTextColor(ContextCompat.getColor(requireContext(), textColorRes))
        }

        viewModel.temperatureText.observe(viewLifecycleOwner) { text ->
            tvInfoTemperature.text = text
        }

        viewModel.categoryText.observe(viewLifecycleOwner) { text ->
            tvInfoCategory.text = text
            cardAiInfo.isVisible = text.isNotEmpty()
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

        viewModel.isSaveCompleted.observe(viewLifecycleOwner) { isCompleted ->
            if (isCompleted) {
                findNavController().popBackStack()
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            if (!message.isNullOrEmpty()) {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
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
            Toast.makeText(requireContext(), "옷 이름을 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view?.windowToken, 0)
        viewModel.saveClothingItem(name)
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
}