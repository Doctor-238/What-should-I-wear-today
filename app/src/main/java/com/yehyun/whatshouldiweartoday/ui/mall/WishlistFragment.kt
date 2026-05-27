package com.yehyun.whatshouldiweartoday.ui.mall

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.yehyun.whatshouldiweartoday.R
import com.yehyun.whatshouldiweartoday.data.database.mall.MallDatabase
import com.yehyun.whatshouldiweartoday.data.preference.SettingsManager
import com.yehyun.whatshouldiweartoday.databinding.FragmentWishlistBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WishlistFragment : Fragment() {

    private var _binding: FragmentWishlistBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWishlistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        loadWishlist()
    }

    private fun loadWishlist() {
        val settings = SettingsManager(requireContext())
        val ids = settings.wishlistedItemIds.mapNotNull { it.toIntOrNull() }

        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                val dao = MallDatabase.getDatabase(requireContext()).mallDao()
                ids.mapNotNull { dao.getItemById(it) }.toMutableList()
            }

            if (items.isEmpty()) {
                binding.tvEmpty.isVisible = true
                binding.rvWishlist.isVisible = false
            } else {
                binding.tvEmpty.isVisible = false
                binding.rvWishlist.isVisible = true
                lateinit var adapter: MallProductAdapter
                adapter = MallProductAdapter(
                    settingsManager = settings,
                    onItemClick = { item ->
                        val bundle = Bundle().apply { putInt("mall_item_id", item.id) }
                        findNavController().navigate(R.id.action_wishlistFragment_to_mallItemDetailFragment, bundle)
                    },
                    onWishlistToggled = { item, isWished ->
                        if (!isWished) {
                            items.remove(item)
                            adapter.submitList(items.toList())
                            if (items.isEmpty()) {
                                binding.tvEmpty.isVisible = true
                                binding.rvWishlist.isVisible = false
                            }
                        }
                    }
                )
                binding.rvWishlist.layoutManager = GridLayoutManager(requireContext(), 2)
                binding.rvWishlist.adapter = adapter
                adapter.submitList(items.toList())
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
