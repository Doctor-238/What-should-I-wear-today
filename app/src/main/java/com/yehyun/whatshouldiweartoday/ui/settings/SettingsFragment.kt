package com.yehyun.whatshouldiweartoday.ui.settings

import android.animation.ObjectAnimator
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.slider.Slider
import com.yehyun.whatshouldiweartoday.R
import com.yehyun.whatshouldiweartoday.data.preference.SettingsManager
import com.yehyun.whatshouldiweartoday.databinding.FragmentSettingsBinding
import com.yehyun.whatshouldiweartoday.ui.OnTabReselectedListener

class SettingsFragment : Fragment(), OnTabReselectedListener {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var settingsManager: SettingsManager
    private val viewModel: SettingsViewModel by viewModels()

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
                Toast.makeText(requireContext(), "모든 데이터가 초기화되었습니다. 앱을 다시 시작합니다.", Toast.LENGTH_LONG).show()
                val packageManager = requireContext().packageManager
                val intent = packageManager.getLaunchIntentForPackage(requireContext().packageName)
                val componentName = intent!!.component
                val mainIntent = Intent.makeRestartActivityTask(componentName)
                requireContext().startActivity(mainIntent)
                Runtime.getRuntime().exit(0)
            }
        }
    }

    override fun onTabReselected() {
        findNavController().popBackStack()
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
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerTempRange.adapter = tempRangeAdapter
        binding.spinnerTempRange.setSelection(rangeOptions.indexOf(settingsManager.temperatureRange))

        val aiModelOptions = listOf("빠름", "느림")
        val aiModelAdapter = ArrayAdapter(requireContext(), R.layout.spinner_item_centered, aiModelOptions).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerAiModel.adapter = aiModelAdapter
        binding.spinnerAiModel.setSelection(if (settingsManager.aiModel == SettingsManager.AI_MODEL_FAST) 0 else 1)
    }

    private fun setupSliders() {
        // 뷰가 완전히 그려진 후에 초기 위치를 설정
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
    }

    private fun setupListeners() {
        binding.spinnerTempRangeContainer.setOnClickListener { binding.spinnerTempRange.performClick() }
        binding.spinnerAiModelContainer.setOnClickListener { binding.spinnerAiModel.performClick() }

        binding.spinnerTempRange.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                settingsManager.temperatureRange = (parent?.getItemAtPosition(position) as? String) ?: SettingsManager.TEMP_RANGE_NORMAL
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.spinnerAiModel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                settingsManager.aiModel = if (position == 0) SettingsManager.AI_MODEL_FAST else SettingsManager.AI_MODEL_ACCURATE
                updateAiModelLabel(settingsManager.aiModel)
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        binding.sliderConstitution.addOnChangeListener { slider, value, _ ->
            val level = value.toInt()
            settingsManager.constitutionLevel = level
            updateConstitutionLabel(level)
            updateFakeThumbPosition(slider, binding.thumbConstitutionFake)
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

        binding.cardReset.setOnClickListener { showResetConfirmDialog() }
    }

    // ▼▼▼▼▼ 최종 수정의 핵심: translationX 사용 ▼▼▼▼▼
    private fun updateFakeThumbPosition(slider: Slider, fakeThumb: ImageView, animate: Boolean = true) {
        val trackWidth = slider.trackWidth
        val percentage = (slider.value - slider.valueFrom) / (slider.valueTo - slider.valueFrom)

        // 조절 바의 최종 X 위치 계산: (슬라이더 트랙 전체 너비 * 현재 값의 비율) - (조절 바 너비의 절반)
        val targetX = trackWidth * percentage

        if (animate) {
            // "translationX" 속성을 현재 위치에서 목표 위치까지 애니메이션
            val animator = ObjectAnimator.ofFloat(fakeThumb, "translationX", fakeThumb.translationX, targetX)
            animator.duration = 150
            animator.interpolator = DecelerateInterpolator()
            animator.start()
        } else {
            // 애니메이션 없이 즉시 위치 설정
            fakeThumb.translationX = targetX
        }
    }
    // ▲▲▲▲▲ 최종 수정의 핵심 ▲▲▲▲▲


    private fun updateConstitutionLabel(level: Int) {
        binding.tvConstitutionValue.text = when (level) {
            1 -> "더위 많이 탐"; 2 -> "더위 조금 탐"; 4 -> "추위 조금 탐"; 5 -> "추위 많이 탐"; else -> "보통"
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}