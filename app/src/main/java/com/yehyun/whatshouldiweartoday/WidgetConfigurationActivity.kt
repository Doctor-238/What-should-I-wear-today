package com.yehyun.whatshouldiweartoday

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class WidgetConfigurationActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    // [삭제] 권한 요청 런처는 더 이상 필요 없으므로 삭제합니다.

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        // [중요] onCreate에서 바로 확인하지 않고, onResume에서 확인하도록 변경
        // 이렇게 하면 사용자가 설정 앱에 갔다가 돌아왔을 때, 변경된 권한을 다시 확인할 수 있습니다.
    }

    override fun onResume() {
        super.onResume()
        // 화면이 다시 보일 때마다 권한 상태를 확인합니다.
        checkBackgroundLocationPermission()
    }

    private fun checkBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                // 권한이 이미 있거나, 사용자가 설정에서 허용하고 돌아온 경우
                configureWidget()
            } else {
                // 권한이 없는 경우, 설정으로 이동하라는 안내창 표시
                showGoToSettingsDialog()
            }
        } else {
            // Android 10 미만에서는 권한이 필요 없음
            configureWidget()
        }
    }

    // [수정] 함수 이름을 바꾸고, '확인' 버튼의 동작을 설정 화면으로 이동하도록 변경
    private fun showGoToSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("백그라운드 위치 권한 필요")
            .setMessage("날씨 정보를 자동으로 업데이트하여 위젯에 표시하려면 위치 정보에 '항상 허용'으로 접근해야 합니다. '확인'을 누르면 권한 설정 화면으로 이동합니다.")
            .setPositiveButton("확인") { _, _ ->
                // '확인'을 누르면 앱의 상세 설정 화면으로 이동
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            }
            .setNegativeButton("취소") { _, _ ->
                finishWidgetSetup(isSuccess = false)
            }
            .setOnCancelListener {
                finishWidgetSetup(isSuccess = false)
            }
            .show()
    }

    private fun configureWidget() {
        // 위젯의 첫 업데이트를 수동으로 트리거
        val workerIntent = Intent(this, TodayRecoWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
        }
        sendBroadcast(workerIntent)

        finishWidgetSetup(isSuccess = true)
    }

    private fun finishWidgetSetup(isSuccess: Boolean) {
        val resultValue = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        val resultCode = if (isSuccess) Activity.RESULT_OK else Activity.RESULT_CANCELED
        setResult(resultCode, resultValue)
        finish()
    }
}