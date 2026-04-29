package com.yehyun.whatshouldiweartoday.ui.mall

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.yehyun.whatshouldiweartoday.data.database.mall.MallItem

data class CartEntry(val item: MallItem, val quantity: Int = 1)

class CartViewModel : ViewModel() {
    private val _cartItems = MutableLiveData<List<CartEntry>>(emptyList())
    val cartItems: LiveData<List<CartEntry>> = _cartItems

    val totalCount: Int get() = _cartItems.value?.sumOf { it.quantity } ?: 0
    val totalPrice: Int get() = _cartItems.value?.sumOf { it.item.price * it.quantity } ?: 0

    fun addItem(item: MallItem) {
        val current = _cartItems.value?.toMutableList() ?: mutableListOf()
        val idx = current.indexOfFirst { it.item.id == item.id }
        if (idx >= 0) {
            current[idx] = current[idx].copy(quantity = current[idx].quantity + 1)
        } else {
            current.add(CartEntry(item))
        }
        _cartItems.value = current
    }

    fun addItems(items: List<MallItem>) {
        items.forEach { addItem(it) }
    }

    fun removeItem(itemId: Int) {
        _cartItems.value = _cartItems.value?.filter { it.item.id != itemId }
    }

    fun increaseQuantity(itemId: Int) {
        val current = _cartItems.value?.toMutableList() ?: return
        val idx = current.indexOfFirst { it.item.id == itemId }
        if (idx >= 0) current[idx] = current[idx].copy(quantity = current[idx].quantity + 1)
        _cartItems.value = current
    }

    fun decreaseQuantity(itemId: Int) {
        val current = _cartItems.value?.toMutableList() ?: return
        val idx = current.indexOfFirst { it.item.id == itemId }
        if (idx >= 0) {
            if (current[idx].quantity <= 1) current.removeAt(idx)
            else current[idx] = current[idx].copy(quantity = current[idx].quantity - 1)
        }
        _cartItems.value = current
    }

    fun clearCart() {
        _cartItems.value = emptyList()
    }
}
