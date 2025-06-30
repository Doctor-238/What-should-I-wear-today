// app/src/main/java/com/yehyun/whatshouldiweartoday/MainViewModel.kt

package com.yehyun.whatshouldiweartoday

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

/**
 * 앱 전역의 이벤트를 관리하기 위한 ViewModel.
 * 이 ViewModel은 MainActivity의 생명주기를 따릅니다.
 */
class MainViewModel : ViewModel() {

    // 전체 초기화 이벤트를 관찰하기 위한 LiveData
    private val _resetAllEvent = MutableLiveData<Boolean>()
    val resetAllEvent: LiveData<Boolean> = _resetAllEvent

    /**
     * 전체 초기화가 필요할 때 이 함수를 호출합니다.
     */
    fun triggerResetAll() {
        _resetAllEvent.value = true
    }

    /**
     * 이벤트 처리가 완료된 후 LiveData를 초기 상태로 되돌립니다.
     */
    fun onResetAllEventHandled() {
        _resetAllEvent.value = false
    }
}