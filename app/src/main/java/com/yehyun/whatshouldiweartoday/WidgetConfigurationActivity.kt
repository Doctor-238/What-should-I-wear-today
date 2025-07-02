package com.yehyun.whatshouldiweartoday

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class WidgetConfigurationActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    // [추가] 중복 요청 및 다이얼로그 중복 표시를 막기 위한 플래그
    private var isPermissionRequestInProgress = false
    private var dialog: AlertDialog? = null

    // [핵심] 홈 화면과 완전히 동일한 권한 요청 런처
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        isPermissionRequestInProgress = false // 요청 완료
        if (isGranted) {
            // 권한이 허용되면 위젯 설정을 완료합니다.
            configureWidget()
        } else {
            // 권한이 거부된 경우
            if (!shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                // '다시 묻지 않음'을 선택하며 영구 거부했다면 설정 안내창을 띄웁니다.
                showGoToSettingsDialog()
            } else {
                // 한 번만 거부했다면, 위젯 추가를 취소합니다.
                finishWidgetSetup(isSuccess = false)
            }
        }
    }

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
    }

    override fun onResume() {
        super.onResume()
        // 화면이 다시 나타날 때마다 권한 상태를 확인합니다.
        checkPermissions()
    }

    private fun checkPermissions() {
        // 중복 요청 및 다이얼로그 중복 표시 방지
        if (isPermissionRequestInProgress || dialog?.isShowing == true) return

        // '앱 사용 중' 권한이 있는지 확인합니다.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // 권한이 있다면 위젯 설정을 완료합니다.
            configureWidget()
        } else {
            // 권한이 없다면, 시스템에 권한을 요청합니다.
            isPermissionRequestInProgress = true
            locationPermissionRequest.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    // [핵심] 홈 화면과 완전히 동일한 '예/아니오' 안내창
    private fun showGoToSettingsDialog() {
        if (isFinishing || isDestroyed || dialog?.isShowing == true) return

        val builder = AlertDialog.Builder(this)
            .setTitle("위치 권한이 필요합니다")
            .setMessage("날씨 정보 조회를 위해 위치 권한이 반드시 필요합니다. '예'를 눌러 설정 화면으로 이동한 후, '권한' 메뉴에서 '위치' 권한을 허용해주세요.")
            .setPositiveButton("예") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            }
            .setNegativeButton("아니오") { _, _ ->
                finishWidgetSetup(isSuccess = false)
            }
            .setOnCancelListener {
                finishWidgetSetup(isSuccess = false)
            }
            .setOnDismissListener {
                dialog = null
            }

        dialog = builder.create()
        dialog?.show()
    }

    private fun configureWidget() {
        // [수정] 위젯 업데이트를 브로드캐스트로 직접 트리거
        val workerIntent = Intent(this, TodayRecoWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
        }
        sendBroadcast(workerIntent)

        // 설정 완료를 시스템에 알림
        finishWidgetSetup(isSuccess = true)
    }

    private fun finishWidgetSetup(isSuccess: Boolean) {
        val resultValue = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        setResult(if (isSuccess) Activity.RESULT_OK else Activity.RESULT_CANCELED, resultValue)
        finish()
    }
}