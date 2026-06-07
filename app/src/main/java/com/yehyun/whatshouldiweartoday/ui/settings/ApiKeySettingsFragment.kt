package com.yehyun.whatshouldiweartoday.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.yehyun.whatshouldiweartoday.R
import com.yehyun.whatshouldiweartoday.MainViewModel
import com.yehyun.whatshouldiweartoday.data.preference.SettingsManager
import com.yehyun.whatshouldiweartoday.databinding.FragmentApiKeySettingsBinding

class ApiKeySettingsFragment : Fragment() {

    private var _binding: FragmentApiKeySettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var settingsManager: SettingsManager
    private val viewModel: SettingsViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
    private var toast: Toast? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentApiKeySettingsBinding.inflate(inflater, container, false)
        settingsManager = SettingsManager(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonBack.setOnClickListener {
            findNavController().popBackStack()
        }

        setupApiKeySection()
        observeViewModel()
    }

    private fun setupApiKeySection() {
        updateApiKeyStatusView()
        updateOpenWeatherApiKeyStatusView()

        binding.buttonApiKeyConfirm.setOnClickListener {
            val key = binding.etApiKey.text?.toString() ?: ""
            viewModel.validateAndSaveApiKey(key)
        }

        binding.buttonApiKeyReset.setOnClickListener {
            viewModel.resetApiKeyToDefault()
            binding.etApiKey.setText("")
        }

        binding.buttonOpenweatherApiKeyConfirm.setOnClickListener {
            val key = binding.etOpenweatherApiKey.text?.toString() ?: ""
            viewModel.validateAndSaveOpenWeatherApiKey(key)
        }

        binding.buttonOpenweatherApiKeyReset.setOnClickListener {
            viewModel.resetOpenWeatherApiKeyToDefault()
            binding.etOpenweatherApiKey.setText("")
        }
    }

    private fun updateApiKeyStatusView() {
        if (settingsManager.isUsingCustomGeminiApiKey) {
            binding.tvApiKeyStatus.text = "사용자 키 사용 중"
            binding.tvApiKeyStatus.setTextColor(resources.getColor(R.color.fit_green, null))
        } else {
            binding.tvApiKeyStatus.text = "기본 키 사용 중"
            binding.tvApiKeyStatus.setTextColor(resources.getColor(R.color.text_tertiary, null))
        }
    }

    private fun updateOpenWeatherApiKeyStatusView() {
        if (settingsManager.isUsingCustomOpenWeatherApiKey) {
            binding.tvOpenweatherApiKeyStatus.text = "사용자 키 사용 중"
            binding.tvOpenweatherApiKeyStatus.setTextColor(resources.getColor(R.color.fit_green, null))
        } else {
            binding.tvOpenweatherApiKeyStatus.text = "기본 키 사용 중"
            binding.tvOpenweatherApiKeyStatus.setTextColor(resources.getColor(R.color.text_tertiary, null))
        }
    }

    private fun observeViewModel() {
        viewModel.isApiKeyValidating.observe(viewLifecycleOwner) { isValidating ->
            binding.progressBarApiKey.isVisible = isValidating
            binding.buttonApiKeyConfirm.text = if (isValidating) "" else "확인"
            binding.buttonApiKeyConfirm.isEnabled = !isValidating
            binding.buttonApiKeyReset.isEnabled = !isValidating
        }

        viewModel.isOpenWeatherApiKeyValidating.observe(viewLifecycleOwner) { isValidating ->
            binding.progressBarOpenweatherApiKey.isVisible = isValidating
            binding.buttonOpenweatherApiKeyConfirm.text = if (isValidating) "" else "확인"
            binding.buttonOpenweatherApiKeyConfirm.isEnabled = !isValidating
            binding.buttonOpenweatherApiKeyReset.isEnabled = !isValidating
        }

        viewModel.apiKeyResult.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { result ->
                when (result) {
                    is SettingsViewModel.ApiKeyResult.Success -> {
                        if (result.isCustomKey) {
                            showToast("Gemini API 키가 등록되었습니다.")
                            binding.etApiKey.setText("")
                        } else {
                            showToast("Gemini 기본 키로 초기화되었습니다.")
                        }
                        updateApiKeyStatusView()
                        mainViewModel.notifySettingsChanged()
                    }
                    is SettingsViewModel.ApiKeyResult.Error -> {
                        showToast(result.message, Toast.LENGTH_LONG)
                    }
                }
            }
        }

        viewModel.openWeatherApiKeyResult.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { result ->
                when (result) {
                    is SettingsViewModel.ApiKeyResult.Success -> {
                        if (result.isCustomKey) {
                            showToast("OpenWeather API 키가 등록되었습니다.")
                            binding.etOpenweatherApiKey.setText("")
                        } else {
                            showToast("OpenWeather 기본 키로 초기화되었습니다.")
                        }
                        updateOpenWeatherApiKeyStatusView()
                        mainViewModel.notifySettingsChanged()
                    }
                    is SettingsViewModel.ApiKeyResult.Error -> {
                        showToast(result.message, Toast.LENGTH_LONG)
                    }
                }
            }
        }
    }

    private fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        toast?.cancel()
        toast = Toast.makeText(requireContext(), message, duration)
        toast?.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        toast?.cancel()
        _binding = null
    }
}