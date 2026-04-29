package com.yehyun.whatshouldiweartoday.ui.mall

import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.yehyun.whatshouldiweartoday.R
import com.yehyun.whatshouldiweartoday.databinding.FragmentMallMainBinding
import com.yehyun.whatshouldiweartoday.ui.OnTabReselectedListener

class MallMainFragment : Fragment(), OnTabReselectedListener {

    private var _binding: FragmentMallMainBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MallMainViewModel by viewModels()
    private val cartViewModel: CartViewModel by activityViewModels()

    private lateinit var adapter: MallProductAdapter
    private val tabs get() = listOf(
        binding.tabAll, binding.tabTop, binding.tabBottom,
        binding.tabOuter, binding.tabShoes, binding.tabAcc
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMallMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupTabs()
        setupSearch()
        setupClickListeners()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        adapter = MallProductAdapter { item ->
            val bundle = Bundle().apply { putInt("mall_item_id", item.id) }
            findNavController().navigate(R.id.action_mallMainFragment_to_mallItemDetailFragment, bundle)
        }
        binding.rvProducts.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvProducts.adapter = adapter
    }

    private fun setupTabs() {
        val categories = listOf(null, "상의", "하의", "아우터", "신발", "기타")
        tabs.forEachIndexed { index, tab ->
            tab.setOnClickListener { selectTab(index, categories[index]) }
        }
    }

    private fun selectTab(selectedIndex: Int, category: String?) {
        tabs.forEachIndexed { index, tab ->
            if (index == selectedIndex) {
                tab.setBackgroundResource(R.drawable.bg_mall_tab_selected)
                tab.setTextColor(android.graphics.Color.WHITE)
                tab.textSize = 13f
                tab.setTypeface(null, android.graphics.Typeface.BOLD)
            } else {
                tab.background = null
                tab.setTextColor(android.graphics.Color.parseColor("#FFFFFFCC"))
                tab.setTypeface(null, android.graphics.Typeface.NORMAL)
            }
        }
        viewModel.selectCategory(category)
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString() ?: ""
                viewModel.setSearchQuery(query)
                binding.ivSearchClear.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                binding.etSearch.clearFocus()
                true
            } else false
        }

        binding.ivSearchClear.setOnClickListener {
            binding.etSearch.text?.clear()
            binding.etSearch.clearFocus()
        }
    }

    private fun setupClickListeners() {
        binding.ivBack.setOnClickListener { findNavController().popBackStack() }

        binding.btnCart.setOnClickListener {
            findNavController().navigate(R.id.action_mallMainFragment_to_cartFragment)
        }

        binding.btnAdmin.setOnClickListener {
            if (MallAdminManager.isLoggedIn) {
                findNavController().navigate(R.id.action_mallMainFragment_to_mallAdminManageFragment)
            } else {
                showAdminLoginDialog()
            }
        }

        binding.btnGotoAdmin.setOnClickListener {
            findNavController().navigate(R.id.action_mallMainFragment_to_mallAdminManageFragment)
        }

        binding.btnAdminLogout.setOnClickListener {
            MallAdminManager.logout()
            binding.layoutAdminBanner.visibility = View.GONE
        }

        binding.fabRecommend.setOnClickListener {
            findNavController().navigate(R.id.action_mallMainFragment_to_mallRecommendationFragment)
        }
    }

    private fun observeViewModel() {
        viewModel.items.observe(viewLifecycleOwner) { items ->
            adapter.submitList(items)
            val isEmpty = items.isEmpty()
            binding.layoutEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
            binding.rvProducts.visibility = if (isEmpty) View.GONE else View.VISIBLE
            binding.tvItemCount.text = "상품 ${items.size}개"

            val query = binding.etSearch.text?.toString() ?: ""
            binding.tvEmptySub.text = if (query.isNotBlank()) {
                "'$query' 검색 결과가 없어요"
            } else {
                "다른 카테고리를 확인해보세요"
            }
        }

        cartViewModel.cartItems.observe(viewLifecycleOwner) { items ->
            val count = items.sumOf { it.quantity }
            if (count > 0) {
                binding.tvCartBadge.visibility = View.VISIBLE
                binding.tvCartBadge.text = if (count > 9) "9+" else count.toString()
            } else {
                binding.tvCartBadge.visibility = View.GONE
            }
        }
    }

    private fun showAdminLoginDialog() {
        val ctx = requireContext()
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }
        val etId = EditText(ctx).apply { hint = "아이디" }
        val etPw = EditText(ctx).apply {
            hint = "비밀번호"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        layout.addView(etId)
        layout.addView(etPw)

        AlertDialog.Builder(ctx)
            .setTitle("관리자 로그인")
            .setView(layout)
            .setPositiveButton("로그인") { _, _ ->
                if (MallAdminManager.login(etId.text.toString(), etPw.text.toString())) {
                    binding.layoutAdminBanner.visibility = View.VISIBLE
                    Toast.makeText(ctx, "관리자 모드 활성화", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(ctx, "아이디 또는 비밀번호가 올바르지 않습니다", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        binding.layoutAdminBanner.visibility = if (MallAdminManager.isLoggedIn) View.VISIBLE else View.GONE
    }

    override fun onTabReselected() {
        findNavController().popBackStack(R.id.navigation_closet, false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
