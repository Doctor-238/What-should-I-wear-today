package com.yehyun.whatshouldiweartoday

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MainViewModel : ViewModel() {

    // 전체 초기화 이벤트를 관찰하기 위한 LiveData
    private val _resetAllEvent = MutableLiveData<Boolean>()
    val resetAllEvent: LiveData<Boolean> = _resetAllEvent

    fun triggerResetAll() {
        _resetAllEvent.value = true
    }

    fun onResetAllEventHandled() {
        _resetAllEvent.value = false
    }
}