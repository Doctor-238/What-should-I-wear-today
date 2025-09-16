package com.yehyun.whatshouldiweartoday

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.yehyun.whatshouldiweartoday.util.Event

class MainViewModel : ViewModel() {

    // 전체 초기화 이벤트를 관찰하기 위한 LiveData
    private val _resetAllEvent = MutableLiveData<Boolean>()
    val resetAllEvent: LiveData<Boolean> = _resetAllEvent

    private val _settingsChangedEvent = MutableLiveData<Event<Unit>>()
    val settingsChangedEvent: LiveData<Event<Unit>> = _settingsChangedEvent

    fun triggerResetAll() {
        _resetAllEvent.value = true
    }

    fun onResetAllEventHandled() {
        _resetAllEvent.value = false
    }

    fun notifySettingsChanged() {
        _settingsChangedEvent.value = Event(Unit)
    }
}