package com.yehyun.whatshouldiweartoday.ui.style

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputEditText
import com.yehyun.whatshouldiweartoday.R

class SaveStyleFragment : Fragment(R.layout.fragment_save_style) {

    private val viewModel: SaveStyleViewModel by viewModels()
    private val args: SaveStyleFragmentArgs by navArgs()
    private lateinit var adapter: SaveStyleAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = SaveStyleAdapter()
        val recyclerView = view.findViewById<RecyclerView>(R.id.rv_clothing_selection)
        recyclerView.adapter = adapter

        viewModel.allClothes.observe(viewLifecycleOwner) { clothes ->
            adapter.submitList(clothes)

            // [수정] preselected_ids가 null이 아닐 경우에만 미리 선택하도록 변경
            args.preselectedIds?.let { ids ->
                if (ids.isNotEmpty()) {
                    adapter.setPreselectedItems(ids)
                }
            }
        }

        view.findViewById<Button>(R.id.button_save_style_final).setOnClickListener {
            val styleName = view.findViewById<TextInputEditText>(R.id.editText_style_name).text.toString().trim()
            val selectedItems = adapter.getSelectedItems()

            if (styleName.isEmpty()) {
                Toast.makeText(requireContext(), "스타일 이름을 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (selectedItems.isEmpty()) {
                Toast.makeText(requireContext(), "하나 이상의 옷을 선택해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.saveStyle(styleName, selectedItems)
            Toast.makeText(requireContext(), "'$styleName' 스타일이 저장되었습니다.", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
        }

        view.findViewById<MaterialToolbar>(R.id.toolbar_save_style).setNavigationOnClickListener {
            findNavController().popBackStack()
        }
    }
}
