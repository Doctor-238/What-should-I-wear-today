package com.yehyun.whatshouldiweartoday.ui.mall

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.yehyun.whatshouldiweartoday.R
import com.yehyun.whatshouldiweartoday.data.database.mall.MallDatabase
import com.yehyun.whatshouldiweartoday.data.database.mall.MallItem
import com.yehyun.whatshouldiweartoday.databinding.FragmentMallAdminEditBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MallAdminEditFragment : Fragment() {

    private var _binding: FragmentMallAdminEditBinding? = null
    private val binding get() = _binding!!
    private var currentItem: MallItem? = null
    private val mallDao get() = MallDatabase.getDatabase(requireContext()).mallDao()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMallAdminEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        val itemId = arguments?.getInt("mall_item_id") ?: return
        lifecycleScope.launch {
            val item = withContext(Dispatchers.IO) { mallDao.getItemById(itemId) }
            item?.let {
                currentItem = it
                populateFields(it)
            }
        }

        binding.btnSave.setOnClickListener { saveItem() }
        binding.btnDelete.setOnClickListener { confirmDelete() }
    }

    private fun populateFields(item: MallItem) {
        loadImagePreview(item)

        binding.etName.setText(item.name)
        binding.etBrand.setText(item.brand)
        binding.etPrice.setText(item.price.toString())
        binding.etColorHex.setText(item.colorHex)
        binding.etMinTemp.setText(item.suitableMinTemp.toInt().toString())
        binding.etMaxTemp.setText(item.suitableMaxTemp.toInt().toString())
        binding.etMinHeight.setText(item.fitMinHeight?.toInt()?.toString() ?: "")
        binding.etMaxHeight.setText(item.fitMaxHeight?.toInt()?.toString() ?: "")
        binding.etMinWeight.setText(item.fitMinWeight?.toInt()?.toString() ?: "")
        binding.etMaxWeight.setText(item.fitMaxWeight?.toInt()?.toString() ?: "")
        binding.etMinWaist.setText(item.fitMinWaist?.toInt()?.toString() ?: "")
        binding.etMaxWaist.setText(item.fitMaxWaist?.toInt()?.toString() ?: "")
        binding.etPurposes.setText(item.purposes)
        binding.etSeason.setText(item.season)
        binding.etMaterial.setText(item.material)
        binding.etTags.setText(item.tags)
        binding.etDescription.setText(item.description)

        selectCategoryChip(item.category)
    }

    private fun loadImagePreview(item: MallItem) {
        val imageUri = if (item.useProcessedImage && !item.processedImageUri.isNullOrBlank())
            item.processedImageUri else item.imageUri

        if (!imageUri.isNullOrBlank() && File(imageUri).exists()) {
            binding.ivProductPreview.visibility = View.VISIBLE
            binding.viewColorPreview.visibility = View.GONE
            Glide.with(this).load(File(imageUri)).fitCenter().into(binding.ivProductPreview)
        } else {
            binding.ivProductPreview.visibility = View.GONE
            binding.viewColorPreview.visibility = View.VISIBLE
            try {
                val color = Color.parseColor(item.colorHex)
                val shape = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(color)
                    cornerRadius = 12f
                }
                binding.viewColorPreview.background = shape
            } catch (e: Exception) {
                binding.viewColorPreview.setBackgroundColor(Color.LTGRAY)
            }
        }
    }

    private fun selectCategoryChip(category: String) {
        val chipId = when (category) {
            "상의" -> R.id.chip_top
            "하의" -> R.id.chip_bottom
            "아우터" -> R.id.chip_outer
            "신발" -> R.id.chip_shoes
            else -> R.id.chip_other
        }
        binding.chipGroupCategory.check(chipId)
    }

    private fun getSelectedCategory(): String {
        return when (binding.chipGroupCategory.checkedChipId) {
            R.id.chip_top -> "상의"
            R.id.chip_bottom -> "하의"
            R.id.chip_outer -> "아우터"
            R.id.chip_shoes -> "신발"
            R.id.chip_other -> "기타"
            else -> currentItem?.category ?: "기타"
        }
    }

    private fun saveItem() {
        val item = currentItem ?: return
        val name = binding.etName.text?.toString()?.trim() ?: ""
        if (name.isBlank()) {
            Toast.makeText(requireContext(), "상품명을 입력하세요", Toast.LENGTH_SHORT).show()
            return
        }

        val brand = binding.etBrand.text?.toString()?.trim() ?: ""
        val category = getSelectedCategory()
        val price = binding.etPrice.text?.toString()?.toIntOrNull() ?: item.price
        val colorHex = binding.etColorHex.text?.toString()?.trim().takeIf { !it.isNullOrBlank() } ?: item.colorHex
        val minTemp = binding.etMinTemp.text?.toString()?.toDoubleOrNull() ?: item.suitableMinTemp
        val maxTemp = binding.etMaxTemp.text?.toString()?.toDoubleOrNull() ?: item.suitableMaxTemp
        val minHeight = binding.etMinHeight.text?.toString()?.toDoubleOrNull()
        val maxHeight = binding.etMaxHeight.text?.toString()?.toDoubleOrNull()
        val minWeight = binding.etMinWeight.text?.toString()?.toDoubleOrNull()
        val maxWeight = binding.etMaxWeight.text?.toString()?.toDoubleOrNull()
        val minWaist = binding.etMinWaist.text?.toString()?.toDoubleOrNull()
        val maxWaist = binding.etMaxWaist.text?.toString()?.toDoubleOrNull()
        val purposes = binding.etPurposes.text?.toString()?.trim() ?: ""
        val season = binding.etSeason.text?.toString()?.trim() ?: ""
        val material = binding.etMaterial.text?.toString()?.trim() ?: ""
        val tags = binding.etTags.text?.toString()?.trim() ?: ""
        val desc = binding.etDescription.text?.toString()?.trim() ?: ""

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                mallDao.update(item.copy(
                    name = name,
                    brand = brand,
                    category = category,
                    price = price,
                    colorHex = colorHex,
                    suitableMinTemp = minTemp,
                    suitableMaxTemp = maxTemp,
                    fitMinHeight = minHeight,
                    fitMaxHeight = maxHeight,
                    fitMinWeight = minWeight,
                    fitMaxWeight = maxWeight,
                    fitMinWaist = minWaist,
                    fitMaxWaist = maxWaist,
                    purposes = purposes,
                    season = season,
                    material = material,
                    tags = tags,
                    description = desc
                ))
            }
            Toast.makeText(requireContext(), "저장되었습니다", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
        }
    }

    private fun confirmDelete() {
        val item = currentItem ?: return
        AlertDialog.Builder(requireContext())
            .setTitle("상품 삭제")
            .setMessage("'${item.name}'을(를) 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { mallDao.deleteById(item.id) }
                    Toast.makeText(requireContext(), "삭제되었습니다", Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack(R.id.mallAdminManageFragment, false)
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
