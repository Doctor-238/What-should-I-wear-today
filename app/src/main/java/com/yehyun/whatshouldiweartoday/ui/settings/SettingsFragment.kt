package com.yehyun.whatshouldiweartoday.ui.settings

import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.slider.Slider
import com.yehyun.whatshouldiweartoday.BodyAnalysisState
import com.yehyun.whatshouldiweartoday.R
import com.yehyun.whatshouldiweartoday.MainViewModel
import com.yehyun.whatshouldiweartoday.data.preference.SettingsManager
import com.yehyun.whatshouldiweartoday.databinding.FragmentSettingsBinding
import com.yehyun.whatshouldiweartoday.ui.OnTabReselectedListener
import java.io.File
import java.io.InputStream

class SettingsFragment : Fragment(), OnTabReselectedListener {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var settingsManager: SettingsManager
    private val viewModel: SettingsViewModel by viewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
    private var toast: Toast? = null
    private var cameraPhotoUri: Uri? = null

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val bitmap = getCorrectlyOrientedBitmap(it)
            if (bitmap != null) {
                mainViewModel.analyzeBodyPhoto(bitmap, settingsManager.getEffectiveGeminiApiKey())
            } else {
                showToast("이미지를 불러오는 데 실패했습니다.")
            }
        }
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            cameraPhotoUri?.let { uri ->
                val bitmap = getCorrectlyOrientedBitmap(uri)
                if (bitmap != null) {
                    mainViewModel.analyzeBodyPhoto(bitmap, settingsManager.getEffectiveGeminiApiKey())
                } else {
                    showToast("이미지를 불러오는 데 실패했습니다.")
                }
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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        settingsManager = SettingsManager(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupTopBar()
        setupSpinners()
        setupSliders()
        setupPurposeSection()
                setupListeners()
        observeViewModel()
    }




    private fun observeViewModel() {
        viewModel.isProcessing.observe(viewLifecycleOwner) { isProcessing ->
            binding.cardReset.isEnabled = !isProcessing
            binding.buttonResetAll.text = if (isProcessing) "초기화 중..." else "전체 초기화하기"
        }

        viewModel.resetComplete.observe(viewLifecycleOwner) { isComplete ->
            if (isComplete) {
                showToast("모든 데이터가 초기화되었습니다. 앱을 다시 시작합니다.", Toast.LENGTH_LONG)
                val packageManager = requireContext().packageManager
                val intent = packageManager.getLaunchIntentForPackage(requireContext().packageName)
                val componentName = intent!!.component
                val mainIntent = Intent.makeRestartActivityTask(componentName)
                requireContext().startActivity(mainIntent)
                Runtime.getRuntime().exit(0)
            }
        }

        mainViewModel.isBodyAnalyzing.observe(viewLifecycleOwner) { isAnalyzing ->
            binding.progressBarBody.isVisible = isAnalyzing
            if (isAnalyzing) {
                binding.tvBodyStatus.isVisible = false
            } else {
                if (settingsManager.bodyFitEnabled) {
                    binding.tvBodyStatus.isVisible = true
                }
                updateBodyStatus()
            }
        }

        viewModel.isPurposeValidating.observe(viewLifecycleOwner) { isValidating ->
            binding.progressBarPurpose.isVisible = isValidating
            binding.buttonAddPurpose.isEnabled = !isValidating
        }

        viewModel.purposeValidationResult.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { result ->
                showToast(result.message)
                if (result.isValid) {
                    refreshCustomPurposeList()
                    setupPurposeSpinner()
                    mainViewModel.notifySettingsChanged()
                }
            }
        }

        mainViewModel.bodyAnalysisResult.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { result ->
                when (result) {
                    is BodyAnalysisState.Success -> {
                        updateBodyStatus()
                        showToast("사이즈가 등록되었습니다.")
                    }
                    is BodyAnalysisState.Error -> {
                        showToast(result.message, Toast.LENGTH_LONG)
                    }
                }
            }
        }
    }

    override fun onTabReselected() {
        findNavController().popBackStack()
    }

    private fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        toast?.cancel()
        toast = Toast.makeText(requireContext(), message, duration)
        toast?.show()
    }

    private fun showBodyPhotoSourceDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("사이즈 사진 선택")
            .setItems(arrayOf("카메라", "갤러리")) { _, which ->
                when (which) {
                    0 -> {
                        val photoFile = File(requireContext().cacheDir, "body_photo_${System.currentTimeMillis()}.jpg")
                        cameraPhotoUri = FileProvider.getUriForFile(
                            requireContext(),
                            "${requireContext().packageName}.fileprovider",
                            photoFile
                        )
                        takePictureLauncher.launch(cameraPhotoUri!!)
                    }
                    1 -> pickImageLauncher.launch("image/*")
                }
            }
            .show()
    }

    private fun showManualBodyInputDialog() {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 32, 64, 0)
        }
        val editHeight = EditText(requireContext()).apply {
            hint = "키 (cm)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            if (settingsManager.estimatedHeight > 0f) {
                setText("%.1f".format(settingsManager.estimatedHeight))
            }
        }
        val editWeight = EditText(requireContext()).apply {
            hint = "몸무게 (kg)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            if (settingsManager.estimatedWeight > 0f) {
                setText("%.1f".format(settingsManager.estimatedWeight))
            }
        }
        val editWaist = EditText(requireContext()).apply {
            hint = "허리둘레 (cm, 선택)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            if (settingsManager.estimatedWaist > 0f) {
                setText("%.1f".format(settingsManager.estimatedWaist))
            }
        }
        layout.addView(editHeight)
        layout.addView(editWeight)
        layout.addView(editWaist)

        AlertDialog.Builder(requireContext())
            .setTitle("사이즈 수동 입력")
            .setView(layout)
            .setPositiveButton("저장") { _, _ ->
                val height = editHeight.text.toString().toFloatOrNull()
                val weight = editWeight.text.toString().toFloatOrNull()
                val waistRaw = editWaist.text.toString().trim()
                val waist = waistRaw.toFloatOrNull()
                val waistValid = waistRaw.isEmpty() || (waist != null && waist > 0)
                if (height != null && weight != null && height > 0 && weight > 0 && waistValid) {
                    settingsManager.estimatedHeight = height
                    settingsManager.estimatedWeight = weight
                    settingsManager.estimatedWaist = if (waistRaw.isEmpty()) 0f else (waist ?: 0f)
                    updateBodyStatus()
                    mainViewModel.notifySettingsChanged()
                    showToast("사이즈가 등록되었습니다.")
                } else {
                    showToast("올바른 값을 입력해주세요.")
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showResetConfirmDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("전체 초기화")
            .setMessage("저장된 모든 옷, 스타일, 설정이 삭제됩니다. 정말 진행하시겠습니까?")
            .setPositiveButton("예") { _, _ -> viewModel.resetAllData() }
            .setNegativeButton("아니오", null)
            .show()
    }

    private fun setupTopBar() {
        binding.buttonClose.setOnClickListener { findNavController().popBackStack() }
    }

    private fun setupSpinners() {
        val rangeOptions = listOf(SettingsManager.TEMP_RANGE_NARROW, SettingsManager.TEMP_RANGE_NORMAL, SettingsManager.TEMP_RANGE_WIDE)
        val tempRangeAdapter = ArrayAdapter(requireContext(), R.layout.spinner_item_centered, rangeOptions).apply {
            setDropDownViewResource(R.layout.spinner_dropdown_item)
        }
        binding.spinnerTempRange.adapter = tempRangeAdapter
        binding.spinnerTempRange.setSelection(rangeOptions.indexOf(settingsManager.temperatureRange))

        val aiModelOptions = listOf("빠름", "느림")
        val aiModelAdapter = ArrayAdapter(requireContext(), R.layout.spinner_item_centered, aiModelOptions).apply {
            setDropDownViewResource(R.layout.spinner_dropdown_item)
        }
        binding.spinnerAiModel.adapter = aiModelAdapter
        binding.spinnerAiModel.setSelection(if (settingsManager.aiModel == SettingsManager.AI_MODEL_FAST) 0 else 1)

        val detectionOptions = listOf("민감", "보통")
        val detectionAdapter = ArrayAdapter(requireContext(), R.layout.spinner_item_centered, detectionOptions).apply {
            setDropDownViewResource(R.layout.spinner_dropdown_item)
        }
        binding.spinnerShoppingDetection.adapter = detectionAdapter
        binding.spinnerShoppingDetection.setSelection(
            if (settingsManager.shoppingDetectionSensitivity == SettingsManager.SHOPPING_DETECTION_SENSITIVE) 0 else 1
        )

        val sizeNotationOptions = listOf("글자 (XS ~ XXL)", "숫자 (85~110)")
        val sizeNotationAdapter = ArrayAdapter(requireContext(), R.layout.spinner_item_centered, sizeNotationOptions).apply {
            setDropDownViewResource(R.layout.spinner_dropdown_item)
        }
        binding.spinnerSizeNotation.adapter = sizeNotationAdapter
        binding.spinnerSizeNotation.setSelection(
            if (settingsManager.sizeNotationType == SettingsManager.SIZE_NOTATION_LETTER) 0 else 1
        )
    }

    private fun setupSliders() {
        binding.sliderConstitution.post {
            updateFakeThumbPosition(binding.sliderConstitution, binding.thumbConstitutionFake, false)
        }
        binding.sliderSensitivity.post {
            updateFakeThumbPosition(binding.sliderSensitivity, binding.thumbSensitivityFake, false)
        }

        binding.sliderConstitution.value = settingsManager.constitutionLevel.toFloat()
        updateConstitutionLabel(settingsManager.constitutionLevel)

        binding.sliderSensitivity.value = settingsManager.sensitivityLevel.toFloat()
        updateSensitivityLabel(settingsManager.sensitivityLevel)

        updateShoppingDetectionLabel(settingsManager.shoppingDetectionSensitivity)

        binding.switchShowRecoIcon.isChecked = settingsManager.showRecommendationIcon

        binding.switchExtendedForecast.isChecked = settingsManager.extendedForecastEnabled

        binding.switchBodyFit.isChecked = settingsManager.bodyFitEnabled
        binding.switchBodyBorder.isChecked = settingsManager.bodyFitBorderEnabled

        updateBodyStatus()
        updateBodySectionVisibility()
    }

    private fun updateBodyStatus() {
        if (settingsManager.isBodyRegistered) {
            val base = "등록 완료 (%.1fcm / %.1fkg".format(
                settingsManager.estimatedHeight, settingsManager.estimatedWeight
            )
            val suffix = if (settingsManager.isWaistRegistered) {
                " / 허리 %.1fcm)".format(settingsManager.estimatedWaist)
            } else {
                ")"
            }
            binding.tvBodyStatus.text = base + suffix
            binding.buttonBodyRegister.text = "사진 재등록"
        } else {
            binding.tvBodyStatus.text = "미등록"
            binding.buttonBodyRegister.text = "사진 등록"
        }
        binding.buttonBodyManual.text = "수동 등록"
    }

    private fun updateBodySectionVisibility() {
        val enabled = settingsManager.bodyFitEnabled
        binding.dividerBody1.isVisible = enabled
        binding.tvBodyBorderLabel.isVisible = enabled
        binding.switchBodyBorder.isVisible = enabled
        binding.dividerBodyNotation.isVisible = enabled
        binding.tvSizeNotationLabel.isVisible = enabled
        binding.spinnerSizeNotationContainer.isVisible = enabled
        binding.dividerBody2.isVisible = enabled
        binding.tvBodyLabel.isVisible = enabled
        binding.tvBodyStatus.isVisible = enabled
        binding.buttonBodyRegister.isVisible = enabled
        binding.buttonBodyManual.isVisible = enabled
        binding.tvBodyNote.isVisible = enabled
    }

    private fun setupPurposeSection() {
        binding.switchPurpose.isChecked = settingsManager.clothingPurposeEnabled
        updatePurposeSectionVisibility()
        setupPurposeSpinner()
        refreshCustomPurposeList()

        binding.switchPurpose.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.clothingPurposeEnabled = isChecked
            updatePurposeSectionVisibility()
            mainViewModel.notifySettingsChanged()
        }

        binding.spinnerPurposeContainer.setOnClickListener { binding.spinnerPurpose.performClick() }

        binding.buttonAddPurpose.setOnClickListener { showAddPurposeDialog() }
    }

    private fun updatePurposeSectionVisibility() {
        val enabled = settingsManager.clothingPurposeEnabled
        binding.dividerPurpose1.isVisible = enabled
        binding.tvPurposeSelectLabel.isVisible = enabled
        binding.spinnerPurposeContainer.isVisible = enabled
        binding.dividerPurpose2.isVisible = enabled
        binding.tvPurposeCustomLabel.isVisible = enabled
        binding.buttonAddPurpose.isVisible = enabled
        binding.layoutCustomPurposes.isVisible = enabled
        binding.progressBarPurpose.isVisible = false
        binding.tvPurposeNote.isVisible = enabled
    }

    private fun setupPurposeSpinner() {
        val allPurposes = settingsManager.getAllPurposes()
        val purposeAdapter = ArrayAdapter(requireContext(), R.layout.spinner_item_centered, allPurposes).apply {
            setDropDownViewResource(R.layout.spinner_dropdown_item)
        }
        binding.spinnerPurpose.adapter = purposeAdapter
        val currentIndex = allPurposes.indexOf(settingsManager.selectedPurpose)
        if (currentIndex >= 0) {
            binding.spinnerPurpose.setSelection(currentIndex)
        }

        binding.spinnerPurpose.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                settingsManager.selectedPurpose = allPurposes[position]
                mainViewModel.notifySettingsChanged()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun refreshCustomPurposeList() {
        binding.layoutCustomPurposes.removeAllViews()
        val customPurposes = settingsManager.customPurposes.sorted()
        for (purpose in customPurposes) {
            val itemLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8, 0, 8)
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            val textView = android.widget.TextView(requireContext()).apply {
                text = purpose
                textSize = 14f
                setTextColor(resources.getColor(R.color.text_primary, null))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            itemLayout.addView(textView)

            val deleteButton = ImageView(requireContext()).apply {
                setImageResource(R.drawable.ic_close)
                layoutParams = LinearLayout.LayoutParams(64, 64)
                setPadding(16, 16, 16, 16)
                setOnClickListener {
                    AlertDialog.Builder(requireContext())
                        .setMessage("'$purpose' 용도를 삭제하시겠습니까?")
                        .setPositiveButton("예") { _, _ ->
                            settingsManager.removeCustomPurpose(purpose)
                            refreshCustomPurposeList()
                            setupPurposeSpinner()
                            mainViewModel.notifySettingsChanged()
                            showToast("'$purpose' 용도가 삭제되었습니다.")
                        }
                        .setNegativeButton("아니오", null)
                        .show()
                }
            }
            itemLayout.addView(deleteButton)
            binding.layoutCustomPurposes.addView(itemLayout)
        }
    }

    private fun showAddPurposeDialog() {
        val editText = EditText(requireContext()).apply {
            hint = "용도 입력 (예: 운동용, 출근용)"
            inputType = InputType.TYPE_CLASS_TEXT
            setPadding(64, 32, 64, 32)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("옷 용도 추가")
            .setView(editText)
            .setPositiveButton("추가") { _, _ ->
                val purpose = editText.text.toString().trim()
                if (purpose.isNotEmpty()) {
                    viewModel.validateAndAddPurpose(purpose, settingsManager.getEffectiveGeminiApiKey())
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun setupListeners() {
        binding.spinnerTempRangeContainer.setOnClickListener { binding.spinnerTempRange.performClick() }
        binding.spinnerAiModelContainer.setOnClickListener { binding.spinnerAiModel.performClick() }
        binding.spinnerShoppingDetectionContainer.setOnClickListener { binding.spinnerShoppingDetection.performClick() }

        binding.spinnerTempRange.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                settingsManager.temperatureRange = (parent?.getItemAtPosition(position) as? String) ?: SettingsManager.TEMP_RANGE_NORMAL
                mainViewModel.notifySettingsChanged()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.spinnerAiModel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                settingsManager.aiModel = if (position == 0) SettingsManager.AI_MODEL_FAST else SettingsManager.AI_MODEL_ACCURATE
                updateAiModelLabel(settingsManager.aiModel)
                mainViewModel.notifySettingsChanged()
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        binding.spinnerShoppingDetection.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                settingsManager.shoppingDetectionSensitivity = if (position == 0) SettingsManager.SHOPPING_DETECTION_SENSITIVE else SettingsManager.SHOPPING_DETECTION_NORMAL
                updateShoppingDetectionLabel(settingsManager.shoppingDetectionSensitivity)
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        binding.spinnerSizeNotationContainer.setOnClickListener { binding.spinnerSizeNotation.performClick() }
        binding.spinnerSizeNotation.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                settingsManager.sizeNotationType = if (position == 0) SettingsManager.SIZE_NOTATION_LETTER else SettingsManager.SIZE_NOTATION_NUMERIC
                mainViewModel.notifySettingsChanged()
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        binding.sliderConstitution.addOnChangeListener { slider, value, _ ->
            val level = value.toInt()
            settingsManager.constitutionLevel = level
            updateConstitutionLabel(level)
            updateFakeThumbPosition(slider, binding.thumbConstitutionFake)
            mainViewModel.notifySettingsChanged()
        }

        binding.sliderSensitivity.addOnChangeListener { slider, value, _ ->
            val level = value.toInt()
            settingsManager.sensitivityLevel = level
            updateSensitivityLabel(level)
            updateFakeThumbPosition(slider, binding.thumbSensitivityFake)
        }

        binding.tvGithubLink.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(binding.tvGithubLink.text.toString()))
            startActivity(intent)
        }

        binding.cardApiKeys.setOnClickListener {
            findNavController().navigate(R.id.action_settingsFragment_to_apiKeySettingsFragment)
        }
        binding.buttonApiKeySettings.setOnClickListener {
            findNavController().navigate(R.id.action_settingsFragment_to_apiKeySettingsFragment)
        }

        binding.cardReset.setOnClickListener { showResetConfirmDialog() }

        binding.buttonBodyRegister.setOnClickListener { showBodyPhotoSourceDialog() }

        binding.switchBodyFit.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.bodyFitEnabled = isChecked
            updateBodySectionVisibility()
            mainViewModel.notifySettingsChanged()
        }

        binding.switchBodyBorder.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.bodyFitBorderEnabled = isChecked
            mainViewModel.notifySettingsChanged()
        }

        binding.buttonBodyManual.setOnClickListener { showManualBodyInputDialog() }

        binding.switchShowRecoIcon.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.showRecommendationIcon = isChecked
            mainViewModel.notifySettingsChanged()
        }

        binding.switchExtendedForecast.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.extendedForecastEnabled = isChecked
        }
    }

    private fun updateFakeThumbPosition(slider: Slider, fakeThumb: ImageView, animate: Boolean = true) {
        val trackWidth = slider.trackWidth
        val percentage = (slider.value - slider.valueFrom) / (slider.valueTo - slider.valueFrom)

        val targetX = trackWidth * percentage

        if (animate) {
            val animator = ObjectAnimator.ofFloat(fakeThumb, "translationX", fakeThumb.translationX, targetX)
            animator.duration = 150
            animator.interpolator = DecelerateInterpolator()
            animator.start()
        } else {
            fakeThumb.translationX = targetX
        }
    }


    private fun updateConstitutionLabel(level: Int) {
        binding.tvConstitutionValue.text = when (level) {
            1 -> "추위 많이 탐"; 2 -> "추위 조금 탐"; 4 -> "더위 조금 탐"; 5 -> "더위 많이 탐"; else -> "보통"
        }
    }

    private fun updateSensitivityLabel(level: Int) {
        binding.tvSensitivityValue.text = when (level) {
            1 -> "둔감"; 2 -> "조금 둔감"; 4 -> "조금 민감"; 5 -> "민감"; else -> "보통"
        }
    }

    private fun updateAiModelLabel(model: String) {
        binding.tvAiModelValue.text = if (model == SettingsManager.AI_MODEL_FAST) "정확도 감소" else "정확도 증가"
    }

    private fun updateShoppingDetectionLabel(sensitivity: String) {
        binding.tvShoppingDetectionValue.text = if (sensitivity == SettingsManager.SHOPPING_DETECTION_SENSITIVE) "모든 옷 감지" else "옷이 메인인 상품만 감지"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        toast?.cancel()
        _binding = null
    }
}