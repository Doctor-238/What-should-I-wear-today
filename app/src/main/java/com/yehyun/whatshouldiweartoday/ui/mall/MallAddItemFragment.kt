package com.yehyun.whatshouldiweartoday.ui.mall

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.yehyun.whatshouldiweartoday.databinding.FragmentMallAddItemBinding
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class MallAddItemFragment : Fragment() {

    private var _binding: FragmentMallAddItemBinding? = null
    private val binding get() = _binding!!

    private val shownCompletedWorkIds = mutableSetOf<UUID>()

    private val pickImages = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isEmpty()) return@registerForActivityResult
        startBatchWork(uris)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMallAddItemBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        binding.cardPickImages.setOnClickListener { pickImages.launch("image/*") }
        observeWork()
    }

    private fun observeWork() {
        WorkManager.getInstance(requireContext())
            .getWorkInfosByTagLiveData(MallBatchAddWorker.TAG)
            .observe(viewLifecycleOwner) { workInfos ->
                val active = workInfos.filter {
                    it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
                }

                if (active.isNotEmpty()) {
                    binding.layoutProgress.visibility = View.VISIBLE
                    binding.cardPickImages.isEnabled = false
                    val running = active.firstOrNull { it.state == WorkInfo.State.RUNNING }
                    val progress = running?.progress
                    val current = progress?.getInt(MallBatchAddWorker.PROGRESS_CURRENT, 0) ?: 0
                    val total = progress?.getInt(MallBatchAddWorker.PROGRESS_TOTAL, 0) ?: 0
                    binding.tvProgress.text = if (total > 0) "AI 분석 중... ($current/$total)" else "AI 분석 중..."
                    if (total > 0) binding.progressBar.progress = current * 100 / total
                } else {
                    binding.layoutProgress.visibility = View.GONE
                    binding.cardPickImages.isEnabled = true
                }

                workInfos.filter { it.state == WorkInfo.State.SUCCEEDED && it.id !in shownCompletedWorkIds }
                    .forEach { info ->
                        val count = info.outputData.getInt(MallBatchAddWorker.OUTPUT_SUCCESS_COUNT, 0)
                        if (count > 0) {
                            Toast.makeText(requireContext(), "${count}개 상품이 등록되었습니다", Toast.LENGTH_SHORT).show()
                        }
                        shownCompletedWorkIds.add(info.id)
                    }
            }
    }

    private fun startBatchWork(uris: List<Uri>) {
        val paths = uris.mapNotNull { saveUriToCache(it) }
        if (paths.isEmpty()) {
            Toast.makeText(requireContext(), "이미지를 불러올 수 없습니다", Toast.LENGTH_SHORT).show()
            return
        }

        val batchId = UUID.randomUUID().toString()
        val pathsFile = File(requireContext().filesDir, "mall_paths_$batchId.txt")
        pathsFile.writeText(paths.joinToString("\n"))

        val request = OneTimeWorkRequestBuilder<MallBatchAddWorker>()
            .setInputData(
                workDataOf(
                    MallBatchAddWorker.KEY_BATCH_ID to batchId,
                    MallBatchAddWorker.KEY_PATHS_FILE to pathsFile.absolutePath,
                    MallBatchAddWorker.KEY_API to com.yehyun.whatshouldiweartoday.data.preference.SettingsManager(requireContext()).getEffectiveGeminiApiKey()
                )
            )
            .addTag(MallBatchAddWorker.TAG)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(requireContext()).enqueue(request)
        Toast.makeText(requireContext(), "${paths.size}장 분석을 시작합니다", Toast.LENGTH_SHORT).show()
    }

    private fun saveUriToCache(uri: Uri): String? {
        return try {
            val fileName = "mall_input_${System.currentTimeMillis()}_${(Math.random() * 100000).toInt()}.jpg"
            val file = File(requireContext().cacheDir, fileName)
            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { input.copyTo(it) }
            }
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
