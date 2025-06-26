package com.yehyun.whatshouldiweartoday.ui.closet

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
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

// AI 응답을 위한 데이터 클래스
@Serializable
data class ClothingAnalysis(
    val is_wearable: Boolean,
    val category: String,
    val length_score: Int,
    val thickness_score: Int
)

class AddClothingFragment : Fragment(R.layout.fragment_add_clothing) {

    private enum class UiState { IDLE, ANALYZING, ANALYZED }

    // --- UI 요소 ---
    private lateinit var imageViewPreview: ImageView
    private lateinit var editTextName: TextInputEditText
    private lateinit var buttonSave: Button
    private lateinit var toolbar: MaterialToolbar
    private lateinit var textViewAiResult: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var switchRemoveBackground: SwitchMaterial

    // --- 상태 변수 ---
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
            modelName = "gemini-1.5-flash-latest",
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

        if (isAnalyzed) {
            if (didSegmentationSucceed) {
                switchRemoveBackground.visibility = View.VISIBLE
                switchRemoveBackground.isChecked = false // 기본으로 OFF
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
                // [요청사항 반영] 불필요한 API Key 확인 코드 완전 삭제
                val resizedBitmap = resizeBitmap(bitmap)
                val inputContent = content {
                    image(resizedBitmap)
                    text("""
                        Please analyze the clothing item in this image and respond only in a valid JSON format.
                        The JSON object should contain these exact keys: "is_wearable", "category", "length_score", and "thickness_score".
                        - "is_wearable" must be a boolean (true if it's a wearable fashion item, false otherwise, for example an apple).
                        - "category" must be one of the following strings: '상의', '하의', '아우터', '신발', '가방', '모자', '기타'. '기타' is for wearable items that don't fit other categories like a scarf. If "is_wearable" is false, set category to "해당 없음".
                        - "length_score" must be an integer between 0 (shortest, end at shoulder) and 20 (longest, end at hand).
                        - "thickness_score" must be an integer between 1 (thinnest) and 10 (thickest).
                        Do not include any other text or explanations.
                    """.trimIndent())
                }

                val response = generativeModel.generateContent(inputContent)
                val analysisResult = Json { ignoreUnknownKeys = true }.decodeFromString<ClothingAnalysis>(response.text!!)

                withContext(Dispatchers.Main) {
                    if (!analysisResult.is_wearable) {
                        handleAiFailure("올바른 사진을 입력해주세요")
                    } else {
                        clothingAnalysisResult = analysisResult
                        // [요청사항 반영] 원래의 수치 기반 배경 제거 로직 호출
                        segmentWithMask(bitmap)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    handleAiFailure("분석 실패: ${e.message}")
                }
            }
        }
    }

    // 수치 기반 배경 제거 로직
    private fun segmentWithMask(originalBitmap: Bitmap) {
        val segmenter = Segmentation.getClient(SelfieSegmenterOptions.Builder().setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE).build())
        segmenter.process(InputImage.fromBitmap(originalBitmap, 0))
            .addOnSuccessListener { mask ->
                processedBitmap = createBitmapWithMask(originalBitmap, mask)
                // 성공 여부를 판단하기 위해, 원본과 결과물이 다른지 비교
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
        imageViewPreview.setImageBitmap(originalBitmap) // 항상 원본을 먼저 보여줌
        clothingAnalysisResult?.let {
            textViewAiResult.text = "분류:${it.category}, 기장:${it.length_score}, 두께:${it.thickness_score}"
        }
        updateUiState(UiState.ANALYZED, didSegmentationSucceed)
    }

    private fun handleAiFailure(message: String) {
        updateUiState(UiState.IDLE)
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    private fun saveClothingItem() {
        val bitmapToSave = if (switchRemoveBackground.isVisible && switchRemoveBackground.isChecked) {
            processedBitmap
        } else {
            originalBitmap
        }

        val name = editTextName.text.toString().trim()
        val analysis = clothingAnalysisResult

        if (name.isEmpty() || bitmapToSave == null || analysis == null) {
            Toast.makeText(requireContext(), "모든 정보를 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        val finalTemperature = calculateSuitableTemperature(analysis.category, analysis.length_score, analysis.thickness_score)

        lifecycleScope.launch(Dispatchers.IO) {
            val originalImagePath = saveBitmapToInternalStorage(originalBitmap!!, "original_")
            val processedImagePath = if (processedBitmap != null && !originalBitmap!!.sameAs(processedBitmap)) {
                saveBitmapToInternalStorage(processedBitmap!!, "processed_")
            } else {
                null
            }

            if (originalImagePath != null) {
                val newClothingItem = ClothingItem(
                    name = name,
                    imageUri = originalImagePath,
                    processedImageUri = processedImagePath,
                    category = analysis.category,
                    suitableTemperature = finalTemperature
                )
                AppDatabase.getDatabase(requireContext()).clothingDao().insert(newClothingItem)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "'$name'(${finalTemperature}°C)이(가) 옷장에 추가!", Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack()
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "이미지 파일 저장에 실패했습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun calculateSuitableTemperature(category: String, lengthScore: Int, thickness: Int): Int {
        val baseTemp = when (category) {
            "상의" -> 28; "하의" -> 26; "아우터" -> 20; else -> 22
        }
        val lengthAdjustment = lengthScore * -1.5
        val thicknessAdjustment = (thickness - 1) * -4
        return (baseTemp + lengthAdjustment + thicknessAdjustment).toInt()
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

    // [요청사항 반영] 0.000000000000000000001f 수치 기반 배경 제거 함수
    private fun createBitmapWithMask(original: Bitmap, mask: SegmentationMask): Bitmap {
        val maskedBitmap = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
        val maskBuffer = mask.buffer
        val maskWidth = mask.width
        val maskHeight = mask.height
        for (y in 0 until maskHeight) {
            for (x in 0 until maskWidth) {
                // 거의 0에 가까운 임계값, 아주 약간이라도 전경일 확률이 있으면 픽셀을 복사합니다.
                if (maskBuffer.float > 0.000000000000000000001f) {
                    maskedBitmap.setPixel(x, y, original.getPixel(x, y))
                }
            }
        }
        maskBuffer.rewind()
        return maskedBitmap
    }
}
