// app/src/main/java/com/yehyun/whatshouldiweartoday/ui/closet/AddClothingFragment.kt

package com.yehyun.whatshouldiweartoday.ui.closet

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.yehyun.whatshouldiweartoday.R
import com.yehyun.whatshouldiweartoday.data.preference.SettingsManager
import com.yehyun.whatshouldiweartoday.ui.OnTabReselectedListener
import kotlinx.serialization.Serializable
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.InputStream


@Serializable
data class ClothingAnalysis(
    val is_wearable: Boolean,
    val category: String? = null,
    val suitable_temperature: Double? = null,
    val color_hex: String? = null
)

class AddClothingFragment : Fragment(R.layout.fragment_add_clothing), OnTabReselectedListener {

    private val viewModel: AddClothingViewModel by activityViewModels()

    private lateinit var imageViewPreview: ImageView
    private lateinit var imageViewPlaceholder: ImageView
    private lateinit var editTextName: TextInputEditText
    private lateinit var buttonSave: Button
    private lateinit var toolbar: MaterialToolbar
    private lateinit var textViewAiResult: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var switchRemoveBackground: SwitchMaterial
    private lateinit var viewColorSwatch: View
    private lateinit var textColorLabel: TextView
    private lateinit var settingsManager: SettingsManager

    private lateinit var onBackPressedCallback: OnBackPressedCallback

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // [수정] 이미지를 불러올 때 회전 정보를 포함하여 올바른 방향으로 가져오도록 수정
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
            inputStream.close() // 첫 번째 스트림 닫기

            // 회전 정보를 얻기 위해 스트림을 다시 열어야 함
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settingsManager = SettingsManager(requireContext())
        setupViews(view)
        setupListeners()
        setupBackButtonHandler()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        viewModel.clothingAnalysisResult.value?.let { result ->
            viewModel.processAnalysisResult(result)
        }
    }

    private fun setupViews(view: View) {
        imageViewPreview = view.findViewById(R.id.imageView_clothing_preview)
        imageViewPlaceholder = view.findViewById(R.id.imageView_placeholder)
        editTextName = view.findViewById(R.id.editText_clothing_name)
        buttonSave = view.findViewById(R.id.button_save)
        toolbar = view.findViewById(R.id.toolbar)
        textViewAiResult = view.findViewById(R.id.textView_ai_result)
        progressBar = view.findViewById(R.id.progressBar)
        switchRemoveBackground = view.findViewById(R.id.switch_remove_background)
        viewColorSwatch = view.findViewById(R.id.view_color_swatch)
        textColorLabel = view.findViewById(R.id.textView_color_label_add)
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            updateUiForState(state)
        }

        viewModel.originalBitmap.observe(viewLifecycleOwner) { bitmap ->
            if (bitmap != null) {
                updateImagePreview()
            } else {
                imageViewPreview.setImageDrawable(null)
                imageViewPlaceholder.visibility = View.VISIBLE
            }
        }

        viewModel.useProcessedImage.observe(viewLifecycleOwner) {
            updateImagePreview()
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            if(!message.isNullOrEmpty()){
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                viewModel.clearErrorMessage()
            }
        }

        viewModel.analysisResultText.observe(viewLifecycleOwner) { text ->
            textViewAiResult.text = text
        }

        viewModel.viewColor.observe(viewLifecycleOwner) { color ->
            if(color != null) {
                viewColorSwatch.setBackgroundColor(color)
            }
        }

        viewModel.isSaveCompleted.observe(viewLifecycleOwner) { isCompleted ->
            if (isCompleted) {
                Toast.makeText(requireContext(), "'${viewModel.clothingName.value}'이(가) 옷장에 추가!", Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
                viewModel.resetSaveState()
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

    private fun updateUiForState(state: AddClothingViewModel.UiState) {
        val isAnalyzing = state == AddClothingViewModel.UiState.ANALYZING
        val isAnalyzed = state == AddClothingViewModel.UiState.ANALYZED

        progressBar.visibility = if (isAnalyzing) View.VISIBLE else View.GONE
        imageViewPlaceholder.visibility = if (viewModel.originalBitmap.value == null) View.VISIBLE else View.GONE
        buttonSave.isEnabled = isAnalyzed
        imageViewPreview.isClickable = !isAnalyzing

        textViewAiResult.visibility = if (isAnalyzed) View.VISIBLE else View.GONE
        viewColorSwatch.visibility = if (isAnalyzed && viewModel.viewColor.value != null) View.VISIBLE else View.GONE
        textColorLabel.visibility = if (isAnalyzed && viewModel.viewColor.value != null) View.VISIBLE else View.GONE

        if (isAnalyzed) {
            val segmentationSuccess = viewModel.segmentationSucceeded.value ?: false
            switchRemoveBackground.visibility = if (segmentationSuccess) View.VISIBLE else View.GONE
        } else {
            switchRemoveBackground.visibility = View.GONE
        }
    }

    private fun setupListeners() {
        toolbar.setNavigationOnClickListener { handleBackButton() }
        imageViewPreview.setOnClickListener { if(!progressBar.isShown) openGallery() }
        imageViewPlaceholder.setOnClickListener { if(!progressBar.isShown) openGallery() }
        buttonSave.setOnClickListener { saveClothingItem() }

        switchRemoveBackground.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setUseProcessedImage(isChecked)
        }

        editTextName.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                viewModel.setClothingName(s.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }
    private fun updateImagePreview() {
        val useProcessed = viewModel.useProcessedImage.value ?: false
        val bitmapToShow = if (useProcessed) viewModel.processedBitmap.value else viewModel.originalBitmap.value
        imageViewPreview.setImageBitmap(bitmapToShow)
    }

    private fun openGallery() {
        pickImageLauncher.launch("image/*")
    }

    private fun saveClothingItem() {
        val name = editTextName.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(requireContext(), "옷 이름을 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.saveClothingItem(requireContext().filesDir, name)
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


    override fun onTabReselected() {
        handleBackButton()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        onBackPressedCallback.remove()
        editTextName.removeTextChangedListener(null)
    }
}