package com.yehyun.whatshouldiweartoday.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.yehyun.whatshouldiweartoday.data.database.AppDatabase
import com.yehyun.whatshouldiweartoday.data.preference.SettingsManager
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsManager = SettingsManager(application)
    private val db = AppDatabase.getDatabase(application)
    private val workManager = WorkManager.getInstance(application)

    private val _isProcessing = MutableLiveData(false)
    val isProcessing: LiveData<Boolean> = _isProcessing

    private val _resetComplete = MutableLiveData(false)
    val resetComplete: LiveData<Boolean> = _resetComplete

    fun resetAllData() {
        if (_isProcessing.value == true) return

        _isProcessing.value = true
        viewModelScope.launch {
            try {
                // 백그라운드 작업 취소
                workManager.cancelUniqueWork("batch_add")
                // 데이터베이스 클리어
                db.clearAllData(getApplication())
                // 설정값 초기화
                settingsManager.resetToDefaults()

                _resetComplete.postValue(true)
            } finally {
                _isProcessing.postValue(false)
            }
        }
    }
}