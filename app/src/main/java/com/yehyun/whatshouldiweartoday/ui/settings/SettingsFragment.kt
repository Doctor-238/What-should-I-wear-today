// app/src/main/java/com/yehyun/whatshouldiweartoday/ui/settings/SettingsFragment.kt

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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.yehyun.whatshouldiweartoday.MainActivity
import com.yehyun.whatshouldiweartoday.data.database.AppDatabase
import com.yehyun.whatshouldiweartoday.data.preference.SettingsManager
import com.yehyun.whatshouldiweartoday.databinding.FragmentSettingsBinding
import com.yehyun.whatshouldiweartoday.ui.OnTabReselectedListener
import kotlinx.coroutines.launch

class SettingsFragment : Fragment(), OnTabReselectedListener {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var settingsManager: SettingsManager

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
        setupToolbar()
        setupSpinner()
        setupSliders()
        setupListeners()
    }

    override fun onTabReselected() {
        findNavController().popBackStack()
    }

    private fun showResetConfirmDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("전체 초기화")
            .setMessage("저장된 모든 옷, 스타일, 설정이 삭제됩니다. 정말 진행하시겠습니까?")
            .setPositiveButton("예") { _, _ ->
                resetAllData()
            }
            .setNegativeButton("아니오", null)
            .show()
    }

    private fun resetAllData() {
        lifecycleScope.launch {
            // 1. 디스크에 저장된 데이터 (DB, 설정값)를 모두 삭제합니다.
            AppDatabase.getDatabase(requireContext()).clearAllData()
            settingsManager.resetToDefaults()

            Toast.makeText(requireContext(), "모든 데이터가 초기화되었습니다. 앱을 다시 시작합니다.", Toast.LENGTH_LONG).show()

            // 2. 앱을 재시작하는 Intent를 만듭니다.
            val packageManager = requireContext().packageManager
            val intent = packageManager.getLaunchIntentForPackage(requireContext().packageName)
            val componentName = intent!!.component
            val mainIntent = Intent.makeRestartActivityTask(componentName)

            // 3. 앱을 재시작합니다.
            requireContext().startActivity(mainIntent)
            Runtime.getRuntime().exit(0)
        }
    }

    // ... 나머지 코드는 동일 ...
    private fun setupToolbar() {
        binding.toolbarSettings.setNavigationOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun setupSpinner() {
        val rangeOptions = listOf(SettingsManager.TEMP_RANGE_NARROW, SettingsManager.TEMP_RANGE_NORMAL, SettingsManager.TEMP_RANGE_WIDE)
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, rangeOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerTempRange.adapter = adapter

        val currentRange = settingsManager.temperatureRange
        binding.spinnerTempRange.setSelection(rangeOptions.indexOf(currentRange))
    }

    private fun setupSliders() {
        binding.sliderConstitution.value = settingsManager.constitutionLevel.toFloat()
        updateConstitutionLabel(settingsManager.constitutionLevel)

        binding.sliderSensitivity.value = settingsManager.sensitivityLevel.toFloat()
        updateSensitivityLabel(settingsManager.sensitivityLevel)
    }

    private fun setupListeners() {
        binding.spinnerTempRange.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val rangeOptions = listOf(SettingsManager.TEMP_RANGE_NARROW, SettingsManager.TEMP_RANGE_NORMAL, SettingsManager.TEMP_RANGE_WIDE)
                settingsManager.temperatureRange = rangeOptions[position]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

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

        binding.tvGithubLink.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(binding.tvGithubLink.text.toString()))
            startActivity(intent)
        }

        binding.buttonResetAll.setOnClickListener {
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}