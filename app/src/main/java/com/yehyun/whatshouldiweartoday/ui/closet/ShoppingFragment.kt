package com.yehyun.whatshouldiweartoday.ui.closet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.yehyun.whatshouldiweartoday.R
import com.yehyun.whatshouldiweartoday.databinding.FragmentShoppingBinding
import com.yehyun.whatshouldiweartoday.ui.OnTabReselectedListener

class ShoppingFragment : Fragment(), OnTabReselectedListener {

    private var _binding: FragmentShoppingBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentShoppingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.ivBack.setOnClickListener {
            findNavController().popBackStack()
        }

        setupPlatformButtons()
    }

    private fun setupPlatformButtons() {
        binding.btnCoupang.setOnClickListener { navigateToWebView(ShoppingPlatform.COUPANG) }
        binding.btnMusinsa.setOnClickListener { navigateToWebView(ShoppingPlatform.MUSINSA) }
        binding.btnHiver.setOnClickListener { navigateToWebView(ShoppingPlatform.HIVER) }
        binding.btnNaver.setOnClickListener { navigateToWebView(ShoppingPlatform.NAVER) }
        binding.btnAbly.setOnClickListener { navigateToWebView(ShoppingPlatform.ABLY) }
        binding.btn29cm.setOnClickListener { navigateToWebView(ShoppingPlatform.CM29) }
        binding.btnGoogle.setOnClickListener { navigateToWebView(ShoppingPlatform.GOOGLE) }
    }

    private fun navigateToWebView(platform: ShoppingPlatform) {
        val bundle = Bundle().apply {
            putString("platform_name", platform.name)
        }
        findNavController().navigate(R.id.action_shoppingFragment_to_shoppingWebViewFragment, bundle)
    }

    override fun onTabReselected() {
        findNavController().popBackStack(R.id.navigation_closet, false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
