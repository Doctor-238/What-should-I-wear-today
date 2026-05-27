package com.yehyun.whatshouldiweartoday.ui.mall

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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
import com.yehyun.whatshouldiweartoday.data.preference.SettingsManager
import com.yehyun.whatshouldiweartoday.databinding.FragmentMallItemDetailBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.NumberFormat
import java.util.Locale
import java.util.Random
import kotlin.math.abs
import kotlin.math.roundToInt

class MallItemDetailFragment : Fragment() {

    private var _binding: FragmentMallItemDetailBinding? = null
    private val binding get() = _binding!!
    private val cartViewModel: CartViewModel by activityViewModels()
    private var mallItem: MallItem? = null
    private var selectedSize: String? = null

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
        val fmt = NumberFormat.getNumberInstance(Locale.KOREA)

        // Image
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

        // Badge (discount or NEW)
        val discount = MallProductAdapter.getDiscountRate(item.id)
        if (discount > 0) {
            binding.tvDetailBadge.text = "${discount}% SALE"
            binding.tvDetailBadge.setBackgroundResource(R.drawable.bg_discount_badge)
            binding.tvDetailBadge.visibility = View.VISIBLE
        } else if (MallProductAdapter.isNewItem(item.id)) {
            binding.tvDetailBadge.text = "NEW"
            binding.tvDetailBadge.setBackgroundResource(R.drawable.bg_new_badge)
            binding.tvDetailBadge.visibility = View.VISIBLE
        }

        // Brand
        binding.tvBrand.text = item.brand.ifBlank { "" }

        // Rating header
        val (rating, reviewCount) = MallProductAdapter.getRating(item.id)
        binding.tvRatingHeader.text = "%.1f".format(rating)
        binding.tvReviewCountHeader.text = "(${fmt.format(reviewCount)})"

        // Name
        binding.tvName.text = item.name

        // Price with discount
        if (discount > 0) {
            val originalPrice = (item.price * 100L / (100 - discount)).toInt()
            binding.tvDiscountRate.text = "${discount}%"
            binding.tvDiscountRate.visibility = View.VISIBLE
            binding.tvPrice.text = fmt.format(item.price) + "원"
            binding.tvOriginalPrice.text = fmt.format(originalPrice) + "원"
            binding.tvOriginalPrice.paintFlags = binding.tvOriginalPrice.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            binding.tvOriginalPrice.visibility = View.VISIBLE
        } else {
            binding.tvDiscountRate.visibility = View.GONE
            binding.tvPrice.text = fmt.format(item.price) + "원"
            binding.tvOriginalPrice.visibility = View.GONE
        }

        // Delivery info (deterministic based on item.id)
        val deliveryMessages = listOf(
            "오늘 주문시 내일 도착 예정",
            "오늘 주문시 모레 도착 예정",
            "2~3일 내 도착 예정",
            "오늘 주문시 내일 도착 예정"
        )
        binding.tvDeliveryInfo.text = deliveryMessages[abs(item.id) % deliveryMessages.size]

        // Tags
        binding.llTags.removeAllViews()
        item.tags.split(",").filter { it.isNotBlank() }.forEach { tag ->
            val tv = TextView(requireContext()).apply {
                text = "#$tag"
                textSize = 11f
                setTextColor(Color.parseColor("#FF4E6A"))
                setPadding(dp(10), dp(4), dp(10), dp(4))
                setBackgroundResource(R.drawable.bg_tag_chip)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.marginEnd = 6 }
            }
            binding.llTags.addView(tv)
        }

        // Size section
        setupSizeSection(item)

        // Product info
        binding.tvCategory.text = item.category
        binding.tvSeason.text = item.season.split(",").filter { it.isNotBlank() }.joinToString(" · ")
        binding.tvMaterial.text = item.material.ifBlank { "-" }
        binding.tvTempRange.text = "%.0f°C ~ %.0f°C".format(item.suitableMinTemp, item.suitableMaxTemp)

        // Color chip
        try {
            val color = Color.parseColor(item.colorHex)
            binding.viewColorChip.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                setStroke(2, Color.parseColor("#20000000"))
            }
        } catch (e: Exception) {
            binding.viewColorChip.setBackgroundColor(Color.LTGRAY)
        }

        binding.layoutFit.visibility = View.GONE

        // Purpose
        val purposes = item.purposes.split(",").filter { it.isNotBlank() }
        if (purposes.isNotEmpty()) {
            binding.layoutPurpose.visibility = View.VISIBLE
            binding.tvPurpose.text = purposes.joinToString(", ")
        } else {
            binding.layoutPurpose.visibility = View.GONE
        }

        // Description
        if (item.description.isNotBlank()) {
            binding.dividerPurpose.visibility = View.VISIBLE
            binding.cardDescription.visibility = View.VISIBLE
            binding.tvDescription.text = item.description
        } else {
            binding.dividerPurpose.visibility = View.GONE
            binding.cardDescription.visibility = View.GONE
        }

        // Reviews section
        setupReviewsSection(item, rating, reviewCount)

        // Admin
        if (MallAdminManager.isLoggedIn) {
            binding.btnAdminEdit.visibility = View.VISIBLE
            binding.btnAdminEdit.setOnClickListener {
                val bundle = Bundle().apply { putInt("mall_item_id", item.id) }
                findNavController().navigate(R.id.action_mallItemDetailFragment_to_mallAdminEditFragment, bundle)
            }
        }

        // Wishlist button
        val settings = SettingsManager(requireContext())
        binding.ivWishlistBtn.setImageResource(
            if (settings.isWishlisted(item.id)) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
        )
        binding.btnWishlist.setOnClickListener {
            val added = settings.toggleWishlist(item.id)
            binding.ivWishlistBtn.setImageResource(
                if (added) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
            )
            val msg = if (added) "위시리스트에 추가했습니다" else "위시리스트에서 제거했습니다"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }

        // Cart
        binding.btnAddCart.setOnClickListener {
            if (needsSizeAndNotSelected()) {
                highlightSizeRequired()
                return@setOnClickListener
            }
            cartViewModel.addItem(item)
            val sizeMsg = selectedSize?.let { " (${it})" } ?: ""
            Toast.makeText(requireContext(), "장바구니에 담았습니다$sizeMsg", Toast.LENGTH_SHORT).show()
        }

        // Buy now
        binding.btnBuyNow.setOnClickListener {
            if (needsSizeAndNotSelected()) {
                highlightSizeRequired()
                return@setOnClickListener
            }
            showBuyConfirmDialog(listOf(item))
        }
    }

    private fun needsSizeAndNotSelected(): Boolean {
        val cat = mallItem?.category ?: return false
        val hasSizes = cat in listOf("상의", "하의", "아우터", "신발")
        return hasSizes && selectedSize == null
    }

    private fun highlightSizeRequired() {
        binding.tvSizeRequired.setTextColor(Color.parseColor("#FF4E6A"))
        binding.tvSizeRequired.text = "사이즈를 선택해주세요 !"
        Toast.makeText(requireContext(), "사이즈를 먼저 선택해주세요", Toast.LENGTH_SHORT).show()
    }

    private fun setupSizeSection(item: MallItem) {
        val sizes = getSizesForItem(item)
        if (sizes.isEmpty()) return

        binding.layoutSizeSection.visibility = View.VISIBLE
        binding.sizeSectionDivider.visibility = View.VISIBLE
        binding.llSizes.removeAllViews()

        sizes.forEach { size ->
            val chip = TextView(requireContext()).apply {
                text = size
                textSize = 13f
                setTextColor(Color.parseColor("#1A1A2E"))
                gravity = android.view.Gravity.CENTER
                setPadding(dp(16), dp(10), dp(16), dp(10))
                minWidth = dp(52)
                setBackgroundResource(R.drawable.bg_size_chip_unselected)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.marginEnd = dp(8) }
            }
            chip.setOnClickListener {
                selectedSize = size
                binding.tvSizeRequired.setTextColor(Color.parseColor("#888888"))
                binding.tvSizeRequired.text = "사이즈를 선택해주세요"
                updateSizeChips(sizes, size)
            }
            binding.llSizes.addView(chip)
        }
    }

    private fun updateSizeChips(sizes: List<String>, selected: String) {
        for (i in 0 until binding.llSizes.childCount) {
            val chip = binding.llSizes.getChildAt(i) as? TextView ?: continue
            val isSelected = chip.text.toString() == selected
            chip.setBackgroundResource(
                if (isSelected) R.drawable.bg_size_chip_selected else R.drawable.bg_size_chip_unselected
            )
            chip.setTextColor(Color.parseColor(if (isSelected) "#FFFFFF" else "#1A1A2E"))
        }
    }

    private fun getSizesForItem(item: MallItem): List<String> {
        return when (item.category) {
            "상의" -> when (item.id % 4) {
                0 -> listOf("S", "M", "L", "XL")
                1 -> listOf("XS", "S", "M", "L", "XL", "XXL")
                2 -> listOf("S", "M", "L")
                else -> listOf("FREE", "S", "M", "L")
            }
            "하의" -> when (item.id % 3) {
                0 -> listOf("25\"", "26\"", "27\"", "28\"", "29\"", "30\"")
                1 -> listOf("26\"", "27\"", "28\"", "29\"", "30\"", "31\"", "32\"")
                else -> listOf("S", "M", "L", "XL")
            }
            "아우터" -> when (item.id % 3) {
                0 -> listOf("S", "M", "L", "XL", "XXL")
                1 -> listOf("XS", "S", "M", "L", "XL")
                else -> listOf("S", "M", "L")
            }
            "신발" -> listOf("230", "235", "240", "245", "250", "255", "260", "265", "270")
            else -> emptyList()
        }
    }

    private fun setupReviewsSection(item: MallItem, avgRating: Float, totalCount: Int) {
        binding.tvTotalReviewCount.text = "${NumberFormat.getNumberInstance(Locale.KOREA).format(totalCount)}개"
        binding.tvAvgScore.text = "%.1f".format(avgRating)

        // Avg stars (5 small stars)
        binding.llAvgStars.removeAllViews()
        val fullStars = avgRating.toInt()
        repeat(5) { i ->
            val star = TextView(requireContext()).apply {
                text = if (i < fullStars) "★" else "☆"
                textSize = 14f
                setTextColor(if (i < fullStars) Color.parseColor("#FFB800") else Color.parseColor("#DDDDDD"))
            }
            binding.llAvgStars.addView(star)
        }

        // Rating distribution (deterministic from item.id)
        val rng = Random(item.id.toLong() * 31L + 11L)
        val fiveStar = (totalCount * (0.55f + rng.nextFloat() * 0.25f)).roundToInt()
        val fourStar = (totalCount * (0.15f + rng.nextFloat() * 0.15f)).roundToInt()
        val threeStar = (totalCount * (0.03f + rng.nextFloat() * 0.07f)).roundToInt()
        val twoStar = (totalCount * rng.nextFloat() * 0.03f).roundToInt()
        val oneStar = maxOf(0, totalCount - fiveStar - fourStar - threeStar - twoStar)

        binding.llRatingBars.removeAllViews()
        val barData = listOf(5 to fiveStar, 4 to fourStar, 3 to threeStar, 2 to twoStar, 1 to oneStar)
        barData.forEach { (stars, count) ->
            addRatingBar(binding.llRatingBars, stars, count, totalCount)
        }

        // Review cards (4 reviews)
        binding.llReviews.removeAllViews()
        val reviews = generateFakeReviews(item, totalCount)
        reviews.forEach { review ->
            addReviewCard(binding.llReviews, review)
        }
    }

    private fun addRatingBar(container: LinearLayout, stars: Int, count: Int, total: Int) {
        val ctx = requireContext()
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(22)
            ).also { if (stars < 5) it.topMargin = dp(6) }
        }

        val tvLabel = TextView(ctx).apply {
            text = "${stars}★"
            textSize = 11f
            setTextColor(Color.parseColor("#888888"))
            minWidth = dp(28)
        }

        val fillWeight = if (total > 0) count.toFloat() / total else 0.01f
        val emptyWeight = 1f - fillWeight

        val barWrapper = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, dp(5), 1f).also {
                it.marginStart = dp(8); it.marginEnd = dp(8)
            }
        }
        val fillView = View(ctx).apply {
            setBackgroundResource(R.drawable.bg_rating_bar_fill)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, fillWeight)
        }
        val emptyView = View(ctx).apply {
            setBackgroundResource(R.drawable.bg_rating_bar_bg)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, emptyWeight)
        }
        barWrapper.addView(fillView)
        barWrapper.addView(emptyView)

        val tvCount = TextView(ctx).apply {
            text = count.toString()
            textSize = 10f
            setTextColor(Color.parseColor("#AAAAAA"))
            minWidth = dp(28)
            gravity = android.view.Gravity.END
        }

        row.addView(tvLabel)
        row.addView(barWrapper)
        row.addView(tvCount)
        container.addView(row)
    }

    data class ReviewData(
        val nickname: String,
        val rating: Int,
        val content: String,
        val sizePurchased: String,
        val date: String,
        val helpful: Int
    )

    private fun generateFakeReviews(item: MallItem, totalCount: Int): List<ReviewData> {
        val sizes = getSizesForItem(item)
        val nicknamePool = listOf("패션러**", "스타일**", "쇼핑중독**", "옷마니**", "트렌디**",
            "코디왕**", "데일리**", "힙스터**", "감성쟁***", "패션피플**")
        val reviews5 = listOf(
            "완전 만족이에요! 사진이랑 실물이 거의 똑같고 소재도 너무 좋아요. 배송도 빠르고 포장도 꼼꼼했어요 재구매 의사 있어요",
            "핏이 정말 예쁘게 나와요. 길에서 어디서 샀냐고 물어봤어요 ㅋㅋ 색상도 딱 원하던 색이었어요",
            "가격 대비 퀄리티가 정말 좋아요! 소재도 좋고 마감도 깔끔하고 너무 만족해요",
            "소재가 생각보다 훨씬 좋아요. 이 가격에 이 퀄리티면 진짜 대박이에요",
            "배송 너무 빠르고 상품도 완전 예뻐요! 지인들한테 추천해줬어요"
        )
        val reviews4 = listOf(
            "전반적으로 만족해요. 색상이 사진보다 조금 밝은 편인데 실물이 더 예쁜 것 같아요",
            "사이즈가 약간 크게 나와요. 한 사이즈 작게 사시는 게 맞을 것 같아요. 상품 자체는 좋아요",
            "소재는 정말 좋은데 구김이 조금 잘 생겨요. 다림질하면 괜찮아요",
            "배송이 조금 걸렸지만 상품은 정말 좋아요. 다음에도 살 것 같아요"
        )
        val reviews3 = listOf(
            "보통이에요. 가격 생각하면 그냥저냥한 것 같아요. 기대가 너무 컸나봐요",
            "사이즈 차트랑 실물이 조금 달라요. 꼭 사이즈 가이드 확인하고 구매하세요"
        )

        val fmt = NumberFormat.getNumberInstance(Locale.KOREA)
        return (0 until 4).map { i ->
            val r = Random(item.id.toLong() + i * 113L + 7L)
            val ratingVal = when {
                r.nextFloat() < 0.62f -> 5
                r.nextFloat() < 0.75f -> 4
                r.nextFloat() < 0.9f -> 4
                else -> 3
            }
            val content = when (ratingVal) {
                5 -> reviews5[abs(r.nextInt()) % reviews5.size]
                4 -> reviews4[abs(r.nextInt()) % reviews4.size]
                else -> reviews3[abs(r.nextInt()) % reviews3.size]
            }
            val sizeStr = if (sizes.isNotEmpty()) sizes[abs(r.nextInt()) % sizes.size] else "M"
            val daysAgo = 3 + abs(r.nextInt()) % 90
            val dateStr = getDateStr(daysAgo)
            ReviewData(
                nickname = nicknamePool[abs(r.nextInt()) % nicknamePool.size],
                rating = ratingVal,
                content = content,
                sizePurchased = sizeStr,
                date = dateStr,
                helpful = abs(r.nextInt()) % 48
            )
        }
    }

    private fun getDateStr(daysAgo: Int): String {
        return when {
            daysAgo <= 1 -> "오늘"
            daysAgo <= 7 -> "${daysAgo}일 전"
            daysAgo <= 30 -> "${daysAgo / 7}주 전"
            else -> "${daysAgo / 30}달 전"
        }
    }

    private fun addReviewCard(container: LinearLayout, review: ReviewData) {
        val ctx = requireContext()

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(16), 0, dp(16))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Top row: stars + date
        val topRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val tvStars = TextView(ctx).apply {
            text = "★".repeat(review.rating) + "☆".repeat(5 - review.rating)
            textSize = 13f
            setTextColor(Color.parseColor("#FFB800"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tvDate = TextView(ctx).apply {
            text = review.date
            textSize = 11f
            setTextColor(Color.parseColor("#AAAAAA"))
        }
        topRow.addView(tvStars)
        topRow.addView(tvDate)

        // Nickname
        val tvNick = TextView(ctx).apply {
            text = review.nickname
            textSize = 12f
            setTextColor(Color.parseColor("#888888"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(3) }
        }

        // Content
        val tvContent = TextView(ctx).apply {
            text = review.content
            textSize = 14f
            setTextColor(Color.parseColor("#1A1A1A"))
            setLineSpacing(dp(3).toFloat(), 1f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(8) }
        }

        // Bottom: size chip + helpful
        val bottomRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dp(10) }
        }
        val tvSize = TextView(ctx).apply {
            text = "구매 ${review.sizePurchased}"
            textSize = 11f
            setTextColor(Color.parseColor("#888888"))
            setPadding(dp(8), dp(3), dp(8), dp(3))
            setBackgroundResource(R.drawable.bg_size_chip_unselected)
        }
        val spacer = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }
        val tvHelpful = TextView(ctx).apply {
            text = "도움이 돼요  ${review.helpful}"
            textSize = 11f
            setTextColor(Color.parseColor("#AAAAAA"))
        }
        bottomRow.addView(tvSize)
        bottomRow.addView(spacer)
        bottomRow.addView(tvHelpful)

        card.addView(topRow)
        card.addView(tvNick)
        card.addView(tvContent)
        card.addView(bottomRow)

        // Divider
        val divider = View(ctx).apply {
            setBackgroundColor(Color.parseColor("#F2F2F2"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)
            )
        }

        container.addView(card)
        container.addView(divider)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun showBuyConfirmDialog(items: List<MallItem>) {
        val fmt = NumberFormat.getNumberInstance(Locale.KOREA)
        val sizeNote = selectedSize?.let { "\n선택 사이즈: $it" } ?: ""
        AlertDialog.Builder(requireContext())
            .setTitle("구매 확인")
            .setMessage("${fmt.format(items.sumOf { it.price })}원을 결제하시겠습니까?$sizeNote\n(가상 결제)")
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
