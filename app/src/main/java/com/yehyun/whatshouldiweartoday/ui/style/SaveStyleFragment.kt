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
import com.yehyun.whatshouldiweartoday.data.database.ClothingItem

class SaveStyleFragment : Fragment(R.layout.fragment_save_style) {

    private val viewModel: SaveStyleViewModel by viewModels()
    private val args: SaveStyleFragmentArgs by navArgs()
    private lateinit var adapter: SaveStyleAdapter

    // [수정] Fragment가 직접 선택된 아이템 목록을 관리합니다.
    private val selectedItems = mutableSetOf<ClothingItem>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView(view)
        observeViewModel()
        setupListeners(view)
    }

    private fun setupRecyclerView(view: View) {
        // 어댑터를 생성할 때, 클릭 시 처리할 모든 로직을 람다로 전달합니다.
        adapter = SaveStyleAdapter { item, isSelected ->
            if (isSelected) {
                // 이미 선택된 아이템이라면, 목록에서 제거합니다.
                selectedItems.remove(item)
            } else {
                // 선택되지 않은 아이템이라면, 10개 제한을 확인하고 목록에 추가합니다.
                if (selectedItems.size < 10) {
                    selectedItems.add(item)
                } else {
                    Toast.makeText(requireContext(), "최대 10개까지 선택할 수 있습니다.", Toast.LENGTH_SHORT).show()
                }
            }
            // 어댑터에게 변경된 선택 상태를 알려주어, 화면의 체크 표시를 업데이트합니다.
            adapter.setSelectedItems(selectedItems.map { it.id }.toSet())
        }

        val recyclerView = view.findViewById<RecyclerView>(R.id.rv_clothing_selection)
        recyclerView.adapter = adapter
    }

    private fun observeViewModel() {
        viewModel.allClothes.observe(viewLifecycleOwner) { clothes ->
            adapter.submitList(clothes)
            // 홈 화면에서 추천 코디를 넘겨받았다면, 해당 아이템들을 미리 선택합니다.
            args.preselectedIds?.let { ids ->
                val preselected = clothes.filter { it.id in ids }
                selectedItems.clear()
                selectedItems.addAll(preselected)
                adapter.setSelectedItems(selectedItems.map { it.id }.toSet())
            }
        }
    }

    private fun setupListeners(view: View) {
        view.findViewById<Button>(R.id.button_save_style_final).setOnClickListener {
            val styleName = view.findViewById<TextInputEditText>(R.id.editText_style_name).text.toString().trim()

            if (styleName.isEmpty()) {
                Toast.makeText(requireContext(), "스타일 이름을 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (selectedItems.isEmpty()) {
                Toast.makeText(requireContext(), "하나 이상의 옷을 선택해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Fragment가 직접 관리하는 최종 선택 목록을 ViewModel에 전달하여 저장합니다.
            viewModel.saveStyle(styleName, selectedItems.toList())
            Toast.makeText(requireContext(), "'$styleName' 스타일이 저장되었습니다.", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
        }

        view.findViewById<MaterialToolbar>(R.id.toolbar_save_style).setNavigationOnClickListener {
            findNavController().popBackStack()
        }
    }
}
