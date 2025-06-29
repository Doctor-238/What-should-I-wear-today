package com.yehyun.whatshouldiweartoday.ui.closet

import android.graphics.Bitmap
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.GenerationConfig
import com.google.ai.client.generativeai.type.content
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import com.yehyun.whatshouldiweartoday.R
import com.yehyun.whatshouldiweartoday.data.database.AppDatabase
import com.yehyun.whatshouldiweartoday.data.database.ClothingItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 필드 이름은 suitable_temperature를 그대로 사용합니다.
@Serializable
data class ClothingAnalysis(
    val is_wearable: Boolean,
    val category: String,
    val suitable_temperature: Double,
    val color_hex: String
)

class AddClothingFragment : Fragment(R.layout.fragment_add_clothing) {

    private enum class UiState { IDLE, ANALYZING, ANALYZED }

    private lateinit var imageViewPreview: ImageView
    private lateinit var editTextName: TextInputEditText
    private lateinit var buttonSave: Button
    private lateinit var toolbar: MaterialToolbar
    private lateinit var textViewAiResult: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var switchRemoveBackground: SwitchMaterial
    private lateinit var viewColorSwatch: View
    private lateinit var textColorLabel: TextView

    private var originalBitmap: Bitmap? = null
    private var processedBitmap: Bitmap? = null
    private var clothingAnalysisResult: ClothingAnalysis? = null

    private lateinit var generativeModel: GenerativeModel

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val bitmap = MediaStore.Images.Media.getBitmap(requireActivity().contentResolver, it)
            originalBitmap = bitmap
            updateUiState(UiState.ANALYZING)
            imageViewPreview.setImageBitmap(bitmap)
            startAiAnalysis(bitmap)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeGenerativeModel()
        setupViews(view)
        setupListeners()
    }

    private fun setupViews(view: View) {
        imageViewPreview = view.findViewById(R.id.imageView_clothing_preview)
        editTextName = view.findViewById(R.id.editText_clothing_name)
        buttonSave = view.findViewById(R.id.button_save)
        toolbar = view.findViewById(R.id.toolbar)
        textViewAiResult = view.findViewById(R.id.textView_ai_result)
        progressBar = view.findViewById(R.id.progressBar)
        switchRemoveBackground = view.findViewById(R.id.switch_remove_background)
        viewColorSwatch = view.findViewById(R.id.view_color_swatch)
        textColorLabel = view.findViewById(R.id.textView_color_label_add)
    }

    private fun setupListeners() {
        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        imageViewPreview.setOnClickListener { openGallery() }
        buttonSave.setOnClickListener { saveClothingItem() }

        switchRemoveBackground.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                imageViewPreview.setImageBitmap(processedBitmap)
            } else {
                imageViewPreview.setImageBitmap(originalBitmap)
            }
        }
    }

    private fun initializeGenerativeModel() {
        val apiKey = getString(R.string.gemini_api_key)
        val config = GenerationConfig.Builder().apply {
            responseMimeType = "application/json"
        }.build()
        generativeModel = GenerativeModel(
            modelName = "gemini-2.5-flash",
            apiKey = apiKey,
            generationConfig = config
        )
    }

    private fun updateUiState(state: UiState, didSegmentationSucceed: Boolean = false) {
        progressBar.visibility = if (state == UiState.ANALYZING) View.VISIBLE else View.GONE
        val isAnalyzed = state == UiState.ANALYZED
        textViewAiResult.visibility = if (isAnalyzed) View.VISIBLE else View.GONE
        buttonSave.isEnabled = isAnalyzed
        imageViewPreview.isClickable = state == UiState.IDLE

        val colorVisible = isAnalyzed && clothingAnalysisResult != null
        viewColorSwatch.visibility = if(colorVisible) View.VISIBLE else View.GONE
        textColorLabel.visibility = if(colorVisible) View.VISIBLE else View.GONE

        if (isAnalyzed) {
            if (didSegmentationSucceed) {
                switchRemoveBackground.visibility = View.VISIBLE
                switchRemoveBackground.isChecked = false
            } else {
                switchRemoveBackground.visibility = View.GONE
                if (originalBitmap != null) {
                    Toast.makeText(requireContext(), "배경 제거 실패. 원본 이미지를 사용합니다.", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            switchRemoveBackground.visibility = View.GONE
        }
    }

    private fun openGallery() {
        pickImageLauncher.launch("image/*")
    }

    private fun startAiAnalysis(bitmap: Bitmap) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val resizedBitmap = resizeBitmap(bitmap)
                val inputContent = content {
                    image(resizedBitmap)
                    // [핵심 수정] "최대 권장 온도"를 "추위 덜 타는 사람 기준"으로 높게 설정해달라고 최종 요청
                    text("""
                        You are a Precise Climate & Fashion Analyst for Korean weather.
                        Your task is to analyze the clothing item in the image and provide a detailed analysis in a strict JSON format, without any additional text or explanations.

                        Your JSON response MUST contain ONLY the following keys: "is_wearable", "category", "suitable_temperature", and "color_hex".

                        - "is_wearable": (boolean) True if the item is wearable clothing.
                        - "category": (string) One of '상의', '하의', '아우터', '신발', '가방', '모자', '기타'.
                        - "color_hex": (string) The dominant color of the item as a hex string.
                        - "suitable_temperature": (double) This is the most important. Estimate the MAXIMUM comfortable temperature for this item, calibrated for a person who is LESS SENSITIVE to cold. The final value should be slightly higher than the average standard. You MUST provide a specific, non-round number with one decimal place (e.g., 23.5, 8.0, -2.5). A generic integer like 15.0 is a bad response. Base your judgment on the visual evidence of material, thickness, and design.
                    """.trimIndent())
                }

                val response = generativeModel.generateContent(inputContent)

                Log.d("AI_RESPONSE", "Raw JSON from AI: ${response.text}")

                val analysisResult = Json { ignoreUnknownKeys = true }.decodeFromString<ClothingAnalysis>(response.text!!)

                withContext(Dispatchers.Main) {
                    if (!analysisResult.is_wearable) {
                        handleAiFailure("올바른 사진을 입력해주세요")
                    } else {
                        clothingAnalysisResult = analysisResult
                        segmentWithMask(bitmap)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("AI_ERROR", "Error during AI analysis", e)
                    handleAiFailure("분석 실패: ${e.message}")
                }
            }
        }
    }

    private fun segmentWithMask(originalBitmap: Bitmap) {
        val segmenter = Segmentation.getClient(SelfieSegmenterOptions.Builder().setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE).build())
        segmenter.process(InputImage.fromBitmap(originalBitmap, 0))
            .addOnSuccessListener { mask ->
                processedBitmap = createBitmapWithMask(originalBitmap, mask)
                val succeed = !originalBitmap.sameAs(processedBitmap)
                handleAiSuccess(didSegmentationSucceed = succeed)
            }
            .addOnFailureListener {
                processedBitmap = originalBitmap
                handleAiSuccess(didSegmentationSucceed = false)
            }
    }

    private fun resizeBitmap(bitmap: Bitmap, maxDimension: Int = 512): Bitmap {
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height
        var resizedWidth = originalWidth
        var resizedHeight = originalHeight

        if (originalHeight > maxDimension || originalWidth > maxDimension) {
            if (originalWidth > originalHeight) {
                resizedWidth = maxDimension
                resizedHeight = (resizedWidth * originalHeight) / originalWidth
            } else {
                resizedHeight = maxDimension
                resizedWidth = (resizedHeight * originalWidth) / originalHeight
            }
        }
        return Bitmap.createScaledBitmap(bitmap, resizedWidth, resizedHeight, false)
    }

    private fun handleAiSuccess(didSegmentationSucceed: Boolean) {
        imageViewPreview.setImageBitmap(originalBitmap)
        clothingAnalysisResult?.let {
            // [핵심 수정] UI에 표시할 때도 +4.0을 더해서 보여줍니다.
            val finalTemperature = it.suitable_temperature + 3.0
            textViewAiResult.text = "분류:${it.category}, 적정 온도:${String.format("%.1f", finalTemperature)}°C"
            try {
                viewColorSwatch.setBackgroundColor(Color.parseColor(it.color_hex))
                viewColorSwatch.visibility = View.VISIBLE
                textColorLabel.visibility = View.VISIBLE
            } catch (e: IllegalArgumentException) {
                viewColorSwatch.visibility = View.GONE
                textColorLabel.visibility = View.GONE
            }
        }
        updateUiState(UiState.ANALYZED, didSegmentationSucceed)
    }

    private fun handleAiFailure(message: String) {
        updateUiState(UiState.IDLE)
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    private fun saveClothingItem() {
        val bitmapToSave = originalBitmap
        val name = editTextName.text.toString().trim()
        val analysis = clothingAnalysisResult

        if (name.isEmpty() || bitmapToSave == null || analysis == null) {
            Toast.makeText(requireContext(), "모든 정보를 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        val finalTemperature = analysis.suitable_temperature + 3.0

        lifecycleScope.launch(Dispatchers.IO) {
            val originalImagePath = saveBitmapToInternalStorage(bitmapToSave, "original_")
            val processedImagePath = processedBitmap?.let { saveBitmapToInternalStorage(it, "processed_") }

            if (originalImagePath != null) {
                val newClothingItem = ClothingItem(
                    name = name,
                    imageUri = originalImagePath,
                    processedImageUri = processedImagePath,
                    useProcessedImage = switchRemoveBackground.isChecked,
                    category = analysis.category,
                    suitableTemperature = finalTemperature,
                    colorHex = analysis.color_hex
                )
                AppDatabase.getDatabase(requireContext()).clothingDao().insert(newClothingItem)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "'$name'(${String.format("%.1f", finalTemperature)}°C)이(가) 옷장에 추가!", Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack()
                }
            }
        }
    }

    private fun createBitmapWithMask(original: Bitmap, mask: SegmentationMask): Bitmap {
        val maskedBitmap = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
        val maskBuffer = mask.buffer
        val maskWidth = mask.width
        val maskHeight = mask.height
        for (y in 0 until maskHeight) {
            for (x in 0 until maskWidth) {
                if (maskBuffer.float > 0.001f) {
                    maskedBitmap.setPixel(x, y, original.getPixel(x, y))
                }
            }
        }
        maskBuffer.rewind()
        return maskedBitmap
    }

    private fun saveBitmapToInternalStorage(bitmap: Bitmap, prefix: String): String? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "$prefix$timeStamp.png"
        val directory = requireContext().filesDir
        try {
            val file = File(directory, fileName)
            val stream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.flush()
            stream.close()
            return file.absolutePath
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        }
    }
}