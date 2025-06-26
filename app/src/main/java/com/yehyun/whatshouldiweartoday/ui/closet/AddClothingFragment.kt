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
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.GenerationConfig
import com.google.ai.client.generativeai.type.content
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputEditText
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

    // --- 상태 변수 ---
    private var originalBitmap: Bitmap? = null
    private var clothingAnalysisResult: ClothingAnalysis? = null

    // Gemini AI 모델을 나중에 초기화하도록 변경
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
        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        imageViewPreview.setOnClickListener { openGallery() }
        buttonSave.setOnClickListener { saveClothingItem() }
    }

    private fun initializeGenerativeModel() {
        val apiKey = getString(R.string.gemini_api_key)

        val config = GenerationConfig.Builder().apply {
            this.responseMimeType = "application/json"
        }.build()

        generativeModel = GenerativeModel(
            modelName = "gemini-1.5-flash-latest",
            apiKey = apiKey,
            generationConfig = config
        )
    }

    private fun updateUiState(state: UiState) {
        progressBar.visibility = if (state == UiState.ANALYZING) View.VISIBLE else View.GONE
        val isAnalyzed = state == UiState.ANALYZED
        textViewAiResult.visibility = if (isAnalyzed) View.VISIBLE else View.GONE
        buttonSave.isEnabled = isAnalyzed
        imageViewPreview.isClickable = state == UiState.IDLE
    }

    private fun setupViews(view: View) {
        imageViewPreview = view.findViewById(R.id.imageView_clothing_preview)
        editTextName = view.findViewById(R.id.editText_clothing_name)
        buttonSave = view.findViewById(R.id.button_save)
        toolbar = view.findViewById(R.id.toolbar)
        textViewAiResult = view.findViewById(R.id.textView_ai_result)
        progressBar = view.findViewById(R.id.progressBar)
    }

    private fun openGallery() {
        pickImageLauncher.launch("image/*")
    }

    private fun startAiAnalysis(bitmap: Bitmap) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (generativeModel.apiKey.isBlank() || generativeModel.apiKey == "ここにあなたのAPIキーを貼り付けてください") {
                    withContext(Dispatchers.Main) {
                        handleAiFailure("secrets.xml 파일에 Gemini API 키를 입력해주세요.")
                    }
                    return@launch
                }

                val resizedBitmap = resizeBitmap(bitmap)

                val inputContent = content {
                    image(resizedBitmap)
                    text("""
                        Please analyze the clothing item in this image and respond only in a valid JSON format.
                        The JSON object should contain these exact keys: "category", "length_score", and "thickness_score".
                        - "category" must be one of the following strings: '상의', '하의', '아우터', '신발', '가방', '모자', '기타'.
                        - "length_score" must be an integer between 0 (shortest) and 10 (longest).
                        - "thickness_score" must be an integer between 1 (thinnest) and 5 (thickest).
                        Do not include any other text or explanations.
                    """.trimIndent())
                }

                val response = generativeModel.generateContent(inputContent)

                clothingAnalysisResult = Json { ignoreUnknownKeys = true }.decodeFromString<ClothingAnalysis>(response.text!!)

                withContext(Dispatchers.Main) {
                    handleAiSuccess()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    handleAiFailure("분석 실패: ${e.message}")
                }
            }
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

    private fun handleAiSuccess() {
        clothingAnalysisResult?.let {
            textViewAiResult.text = "분류:${it.category}, 기장:${it.length_score}, 두께:${it.thickness_score}"
        }
        updateUiState(UiState.ANALYZED)
    }

    private fun handleAiFailure(message: String) {
        updateUiState(UiState.IDLE)
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    private fun saveClothingItem() {
        val name = editTextName.text.toString().trim()
        val bitmapToSave = originalBitmap
        val analysis = clothingAnalysisResult

        if (name.isEmpty() || bitmapToSave == null || analysis == null) {
            Toast.makeText(requireContext(), "모든 정보를 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        val finalTemperature = calculateSuitableTemperature(analysis.category, analysis.length_score, analysis.thickness_score)

        lifecycleScope.launch(Dispatchers.IO) {
            val imagePath = saveBitmapToInternalStorage(bitmapToSave)
            if (imagePath != null) {
                val newClothingItem = ClothingItem(
                    name = name,
                    imageUri = imagePath,
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
            "상의" -> 28
            "하의" -> 26
            "아우터" -> 20
            else -> 22
        }
        val lengthAdjustment = lengthScore * -1.5
        val thicknessAdjustment = (thickness - 1) * -4
        return (baseTemp + lengthAdjustment + thicknessAdjustment).toInt()
    }

    private fun saveBitmapToInternalStorage(bitmap: Bitmap): String? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "clothing_$timeStamp.png"
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
