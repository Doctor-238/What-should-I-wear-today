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
import com.yehyun.whatshouldiweartoday.data.database.AppDatabase
import com.yehyun.whatshouldiweartoday.data.preference.SettingsManager
import com.yehyun.whatshouldiweartoday.databinding.FragmentSettingsBinding
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

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
        setupListeners()
    }

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

        // 현재 설정 값으로 스피너 초기 선택 설정
        val currentRange = settingsManager.temperatureRange
        binding.spinnerTempRange.setSelection(rangeOptions.indexOf(currentRange))

        binding.spinnerTempRange.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                settingsManager.temperatureRange = rangeOptions[position]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupListeners() {
        // 깃허브 링크 클릭
        binding.tvGithubLink.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(binding.tvGithubLink.text.toString()))
            startActivity(intent)
        }

        // 전체 초기화 버튼 클릭
        binding.buttonResetAll.setOnClickListener {
            showResetConfirmDialog()
        }
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
            // DB 데이터 삭제
            AppDatabase.getDatabase(requireContext()).clearAllData()
            // 설정 값 초기화
            settingsManager.resetToDefaults()

            Toast.makeText(requireContext(), "모든 데이터가 초기화되었습니다.", Toast.LENGTH_SHORT).show()
            // 스피너도 기본값으로 리셋
            setupSpinner()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}