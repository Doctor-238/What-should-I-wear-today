package com.yehyun.whatshouldiweartoday.ui.mall

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.yehyun.whatshouldiweartoday.R
import com.yehyun.whatshouldiweartoday.data.database.AppDatabase
import com.yehyun.whatshouldiweartoday.data.database.ClothingItem
import com.yehyun.whatshouldiweartoday.data.database.mall.MallDatabase
import com.yehyun.whatshouldiweartoday.data.database.mall.MallItem
import com.yehyun.whatshouldiweartoday.databinding.FragmentMallItemDetailBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.NumberFormat
import java.util.Locale

class MallItemDetailFragment : Fragment() {

    private var _binding: FragmentMallItemDetailBinding? = null
    private val binding get() = _binding!!
    private val cartViewModel: CartViewModel by activityViewModels()
    private var mallItem: MallItem? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMallItemDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        val itemId = arguments?.getInt("mall_item_id") ?: return
        lifecycleScope.launch {
            val item = withContext(Dispatchers.IO) {
                MallDatabase.getDatabase(requireContext()).mallDao().getItemById(itemId)
            }
            item?.let { bindItem(it) }
        }
    }

    private fun bindItem(item: MallItem) {
        mallItem = item

        val imageUri = if (item.useProcessedImage && !item.processedImageUri.isNullOrBlank()) item.processedImageUri else item.imageUri
        if (!imageUri.isNullOrBlank() && File(imageUri).exists()) {
            binding.ivProductImage.visibility = View.VISIBLE
            binding.viewColorBg.visibility = View.GONE
            Glide.with(this).load(File(imageUri)).fitCenter().into(binding.ivProductImage)
        } else {
            binding.ivProductImage.visibility = View.GONE
            binding.viewColorBg.visibility = View.VISIBLE
            val color = try { Color.parseColor(item.colorHex) } catch (e: Exception) { Color.parseColor("#FFCCCC") }
            binding.viewColorBg.setBackgroundColor(color)
        }

        binding.tvCategory.text = item.category
        binding.tvBrand.text = item.brand.ifBlank { "" }
        binding.tvName.text = item.name
        binding.tvPrice.text = NumberFormat.getNumberInstance(Locale.KOREA).format(item.price) + "원"
        binding.tvTempRange.text = "%.0f°C ~ %.0f°C".format(item.suitableMinTemp, item.suitableMaxTemp)
        binding.tvMaterial.text = item.material.ifBlank { "-" }
        binding.tvSeason.text = item.season.split(",").filter { it.isNotBlank() }.joinToString(" · ")

        // Color chip
        try {
            val color = Color.parseColor(item.colorHex)
            val shape = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                setStroke(2, Color.parseColor("#20000000"))
            }
            binding.viewColorChip.background = shape
        } catch (e: Exception) {
            binding.viewColorChip.setBackgroundColor(Color.LTGRAY)
        }
        binding.tvColorHex.text = item.colorHex

        // Body fit
        val fitParts = mutableListOf<String>()
        if (item.fitMinHeight != null && item.fitMaxHeight != null)
            fitParts.add("신장 %.0f~%.0fcm".format(item.fitMinHeight, item.fitMaxHeight))
        if (item.fitMinWeight != null && item.fitMaxWeight != null)
            fitParts.add("체중 %.0f~%.0fkg".format(item.fitMinWeight, item.fitMaxWeight))
        if (item.fitMinWaist != null && item.fitMaxWaist != null)
            fitParts.add("허리 %.0f~%.0f인치".format(item.fitMinWaist, item.fitMaxWaist))
        if (fitParts.isNotEmpty()) {
            binding.layoutFit.visibility = View.VISIBLE
            binding.tvFitInfo.text = fitParts.joinToString(" · ")
        } else {
            binding.layoutFit.visibility = View.GONE
        }

        // Purpose
        val purposes = item.purposes.split(",").filter { it.isNotBlank() }
        if (purposes.isNotEmpty()) {
            binding.dividerPurpose.visibility = View.VISIBLE
            binding.layoutPurpose.visibility = View.VISIBLE
            binding.tvPurpose.text = purposes.joinToString(", ")
        } else {
            binding.dividerPurpose.visibility = View.GONE
            binding.layoutPurpose.visibility = View.GONE
        }

        // Description card
        if (item.description.isNotBlank()) {
            binding.cardDescription.visibility = View.VISIBLE
            binding.tvDescription.text = item.description
        } else {
            binding.cardDescription.visibility = View.GONE
        }

        // Tags
        binding.llTags.removeAllViews()
        item.tags.split(",").filter { it.isNotBlank() }.forEach { tag ->
            val tv = TextView(requireContext()).apply {
                text = "#$tag"
                textSize = 11f
                setTextColor(Color.parseColor("#FF6B6B"))
                setPadding(12, 4, 12, 4)
                setBackgroundResource(R.drawable.bg_tag_chip)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.marginEnd = 6
                layoutParams = params
            }
            binding.llTags.addView(tv)
        }

        if (MallAdminManager.isLoggedIn) {
            binding.btnAdminEdit.visibility = View.VISIBLE
            binding.btnAdminEdit.setOnClickListener {
                val bundle = Bundle().apply { putInt("mall_item_id", item.id) }
                findNavController().navigate(R.id.action_mallItemDetailFragment_to_mallAdminEditFragment, bundle)
            }
        }

        binding.btnAddCart.setOnClickListener {
            cartViewModel.addItem(item)
            Toast.makeText(requireContext(), "장바구니에 담았습니다", Toast.LENGTH_SHORT).show()
        }

        binding.btnBuyNow.setOnClickListener {
            showBuyConfirmDialog(listOf(item))
        }
    }

    private fun showBuyConfirmDialog(items: List<MallItem>) {
        AlertDialog.Builder(requireContext())
            .setTitle("구매 확인")
            .setMessage("${NumberFormat.getNumberInstance(Locale.KOREA).format(items.sumOf { it.price })}원을 결제하시겠습니까?\n(가상 결제)")
            .setPositiveButton("결제하기") { _, _ -> purchase(items) }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun purchase(items: List<MallItem>) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val db = AppDatabase.getDatabase(requireContext())
                items.forEach { mallItem ->
                    val clothingItem = mallItemToClothingItem(mallItem)
                    db.clothingDao().insert(clothingItem)
                }
            }
            Toast.makeText(requireContext(), "구매 완료! 내 옷 목록에 추가되었습니다.", Toast.LENGTH_LONG).show()
            findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

internal fun mallItemToClothingItem(item: MallItem): ClothingItem {
    val avgTemp = (item.suitableMinTemp + item.suitableMaxTemp) / 2.0
    val baseTemp = avgTemp
    val suitableTemp = when (item.category) {
        "아우터" -> baseTemp - 3.0
        else -> baseTemp + 2.0
    }
    val purpose = item.purposes.split(",").take(2).filter { it.isNotBlank() }.joinToString(",")
    return ClothingItem(
        name = item.name,
        imageUri = item.imageUri,
        processedImageUri = item.processedImageUri,
        useProcessedImage = item.useProcessedImage,
        category = item.category,
        suitableTemperature = suitableTemp,
        baseTemperature = baseTemp,
        colorHex = item.colorHex,
        fitMinHeight = item.fitMinHeight,
        fitMaxHeight = item.fitMaxHeight,
        fitMinWeight = item.fitMinWeight,
        fitMaxWeight = item.fitMaxWeight,
        fitMinWaist = item.fitMinWaist,
        fitMaxWaist = item.fitMaxWaist,
        purpose = purpose,
        purchaseSource = "오늘 뭐 살래?"
    )
}
