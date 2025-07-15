package com.yehyun.whatshouldiweartoday.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
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
            .setPositiveButton("예") { _, _ ->
                viewModel.resetAllData()
            }
            .setNegativeButton("아니오", null)
            .show()
    }

    private fun setupTopBar() {
        binding.buttonClose.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun setupSpinners() {
        // ▼▼▼▼▼ 오류 수정 부분 ▼▼▼▼▼
        // 온도 범위 스피너 설정
        val rangeOptions = listOf(SettingsManager.TEMP_RANGE_NARROW, SettingsManager.TEMP_RANGE_NORMAL, SettingsManager.TEMP_RANGE_WIDE)
        // 안드로이드 기본 레이아웃을 사용하도록 수정
        val tempRangeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, rangeOptions)
        tempRangeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerTempRange.adapter = tempRangeAdapter
        val currentRange = settingsManager.temperatureRange
        binding.spinnerTempRange.setSelection(rangeOptions.indexOf(currentRange))

        // AI 모델 스피너 설정
        val aiModelOptions = listOf("빠름", "느림")
        // 안드로이드 기본 레이아웃을 사용하도록 수정
        val aiModelAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, aiModelOptions)
        aiModelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerAiModel.adapter = aiModelAdapter
        val currentAiModel = if (settingsManager.aiModel == SettingsManager.AI_MODEL_FAST) "빠름" else "느림"
        binding.spinnerAiModel.setSelection(aiModelOptions.indexOf(currentAiModel))
        // ▲▲▲▲▲ 오류 수정 부분 ▲▲▲▲▲
    }

    private fun setupSliders() {
        binding.sliderConstitution.value = settingsManager.constitutionLevel.toFloat()
        updateConstitutionLabel(settingsManager.constitutionLevel)

        binding.sliderSensitivity.value = settingsManager.sensitivityLevel.toFloat()
        updateSensitivityLabel(settingsManager.sensitivityLevel)
    }

    private fun setupListeners() {
        // 스피너 컨테이너 클릭 리스너
        binding.spinnerTempRangeContainer.setOnClickListener {
            binding.spinnerTempRange.performClick()
        }
        binding.spinnerAiModelContainer.setOnClickListener {
            binding.spinnerAiModel.performClick()
        }

        // 스피너 선택 리스너
        binding.spinnerTempRange.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val rangeOptions = listOf(SettingsManager.TEMP_RANGE_NARROW, SettingsManager.TEMP_RANGE_NORMAL, SettingsManager.TEMP_RANGE_WIDE)
                settingsManager.temperatureRange = rangeOptions[position]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.spinnerAiModel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val model = if (position == 0) SettingsManager.AI_MODEL_FAST else SettingsManager.AI_MODEL_ACCURATE
                settingsManager.aiModel = model
                updateAiModelLabel(model)
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        // 슬라이더 변경 리스너
        binding.sliderConstitution.addOnChangeListener { _, value, _ ->
            val level = value.toInt()
            settingsManager.constitutionLevel = level
            updateConstitutionLabel(level)
        }

        binding.sliderSensitivity.addOnChangeListener { _, value, _ ->
            val level = value.toInt()
            settingsManager.sensitivityLevel = level
            updateSensitivityLabel(level)
        }

        // 기타 클릭 리스너
        binding.tvGithubLink.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(binding.tvGithubLink.text.toString()))
            startActivity(intent)
        }

        binding.cardReset.setOnClickListener {
            showResetConfirmDialog()
        }
    }

    private fun updateConstitutionLabel(level: Int) {
        binding.tvConstitutionValue.text = when (level) {
            1 -> "더위 많이 탐"
            2 -> "더위 조금 탐"
            4 -> "추위 조금 탐"
            5 -> "추위 많이 탐"
            else -> "보통"
        }
    }

    private fun updateSensitivityLabel(level: Int) {
        binding.tvSensitivityValue.text = when (level) {
            1 -> "둔감"
            2 -> "조금 둔감"
            4 -> "조금 민감"
            5 -> "민감"
            else -> "보통"
        }
    }

    private fun updateAiModelLabel(model: String) {
        binding.tvAiModelValue.text = if (model == SettingsManager.AI_MODEL_FAST) "빠름, 정확성 감소" else "느림, 정확성 증가"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}