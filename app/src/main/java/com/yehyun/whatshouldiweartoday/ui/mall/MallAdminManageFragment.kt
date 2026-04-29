package com.yehyun.whatshouldiweartoday.ui.mall

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.yehyun.whatshouldiweartoday.R
import com.yehyun.whatshouldiweartoday.data.database.mall.MallDatabase
import com.yehyun.whatshouldiweartoday.data.database.mall.MallItem
import com.yehyun.whatshouldiweartoday.databinding.FragmentMallAdminManageBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MallAdminManageFragment : Fragment() {

    private var _binding: FragmentMallAdminManageBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: MallAdminProductAdapter
    private val mallDao get() = MallDatabase.getDatabase(requireContext()).mallDao()
    private var currentCategory: String? = null
    private var currentItemsLiveData: LiveData<List<MallItem>>? = null
    private var currentItemsObserver: Observer<List<MallItem>>? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMallAdminManageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        setupAdapter()
        setupTabs()
        setupDeleteBar()
        observeItems()

        binding.fabAdd.setOnClickListener {
            findNavController().navigate(R.id.action_mallAdminManageFragment_to_mallAddItemFragment)
        }
    }

    private fun setupAdapter() {
        adapter = MallAdminProductAdapter(
            onItemClick = { item ->
                val bundle = Bundle().apply { putInt("mall_item_id", item.id) }
                findNavController().navigate(R.id.action_mallAdminManageFragment_to_mallAdminEditFragment, bundle)
            },
            onSelectionChanged = { count ->
                binding.tvDeleteCount.text = "${count}개 선택됨"
                binding.layoutDeleteBar.visibility = View.VISIBLE
            }
        )
        binding.rvProducts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvProducts.adapter = adapter
    }

    private fun setupTabs() {
        val tabs = listOf(binding.tabAll, binding.tabTop, binding.tabBottom, binding.tabOuter)
        val categories = listOf(null, "상의", "하의", "아우터")
        tabs.forEachIndexed { index, tab ->
            tab.setOnClickListener {
                selectTab(tabs, index)
                currentCategory = categories[index]
                observeItems()
            }
        }
    }

    private fun selectTab(tabs: List<TextView>, selectedIndex: Int) {
        tabs.forEachIndexed { index, tab ->
            if (index == selectedIndex) {
                tab.setBackgroundResource(R.drawable.bg_mall_tab_selected)
                tab.setTextColor(android.graphics.Color.WHITE)
            } else {
                tab.background = null
                tab.setTextColor(resources.getColor(R.color.text_secondary, null))
            }
        }
    }

    private fun setupDeleteBar() {
        binding.btnDeleteSelected.setOnClickListener {
            val ids = adapter.getSelectedIds()
            if (ids.isEmpty()) return@setOnClickListener
            AlertDialog.Builder(requireContext())
                .setTitle("상품 삭제")
                .setMessage("선택한 ${ids.size}개의 상품을 삭제하시겠습니까?")
                .setPositiveButton("삭제") { _, _ ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) { ids.forEach { mallDao.deleteById(it) } }
                        exitDeleteMode()
                        Toast.makeText(requireContext(), "${ids.size}개 삭제됨", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("취소", null)
                .show()
        }
        binding.btnCancelDelete.setOnClickListener { exitDeleteMode() }
    }

    private fun exitDeleteMode() {
        adapter.isDeleteMode = false
        adapter.clearSelection()
        binding.layoutDeleteBar.visibility = View.GONE
    }

    private fun observeItems() {
        currentItemsObserver?.let { currentItemsLiveData?.removeObserver(it) }
        val cat = currentCategory
        val liveData = if (cat == null) mallDao.getAllItems() else mallDao.getItemsByCategory(cat)
        val observer = Observer<List<MallItem>> { items -> adapter.submitList(items) }
        liveData.observe(viewLifecycleOwner, observer)
        currentItemsLiveData = liveData
        currentItemsObserver = observer
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
