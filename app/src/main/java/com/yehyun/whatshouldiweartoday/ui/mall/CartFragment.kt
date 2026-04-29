package com.yehyun.whatshouldiweartoday.ui.mall

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.yehyun.whatshouldiweartoday.R
import com.yehyun.whatshouldiweartoday.data.database.AppDatabase
import com.yehyun.whatshouldiweartoday.databinding.FragmentCartBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

class CartFragment : Fragment() {

    private var _binding: FragmentCartBinding? = null
    private val binding get() = _binding!!
    private val cartViewModel: CartViewModel by activityViewModels()
    private lateinit var adapter: CartAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        adapter = CartAdapter(
            onIncrease = { cartViewModel.increaseQuantity(it) },
            onDecrease = { cartViewModel.decreaseQuantity(it) },
            onRemove = { cartViewModel.removeItem(it) }
        )
        binding.rvCart.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCart.adapter = adapter

        cartViewModel.cartItems.observe(viewLifecycleOwner) { items ->
            adapter.submitList(items)
            binding.layoutEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            binding.rvCart.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
            binding.layoutCheckout.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
            val total = items.sumOf { it.item.price * it.quantity }
            binding.tvTotalPrice.text = NumberFormat.getNumberInstance(Locale.KOREA).format(total) + "원"
        }

        binding.btnCheckout.setOnClickListener {
            val items = cartViewModel.cartItems.value ?: return@setOnClickListener
            if (items.isEmpty()) return@setOnClickListener
            val total = items.sumOf { it.item.price * it.quantity }
            AlertDialog.Builder(requireContext())
                .setTitle("결제 확인")
                .setMessage("총 ${NumberFormat.getNumberInstance(Locale.KOREA).format(total)}원을 결제하시겠습니까?\n구매 완료 시 내 옷 목록에 추가됩니다. (가상 결제)")
                .setPositiveButton("결제하기") { _, _ -> checkout(items) }
                .setNegativeButton("취소", null)
                .show()
        }
    }

    private fun checkout(entries: List<CartEntry>) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val db = AppDatabase.getDatabase(requireContext())
                entries.forEach { entry ->
                    val clothingItem = mallItemToClothingItem(entry.item)
                    db.clothingDao().insert(clothingItem)
                }
            }
            cartViewModel.clearCart()
            Toast.makeText(requireContext(), "결제 완료! 내 옷 목록에 추가되었습니다.", Toast.LENGTH_LONG).show()
            findNavController().popBackStack(R.id.mallMainFragment, false)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
