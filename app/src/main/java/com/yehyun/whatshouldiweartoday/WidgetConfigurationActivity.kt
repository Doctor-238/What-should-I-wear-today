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
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

class WidgetConfigurationActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var isPermissionRequestInProgress = false
    private var dialog: AlertDialog? = null

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            configureWidget()
        } else {
            if (!shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                showGoToSettingsDialog()
            } else {
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
        if (!isPermissionRequestInProgress) {
            checkPermissions()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        dialog?.dismiss()
        isPermissionRequestInProgress = false
    }

    private fun checkPermissions() {
        if (isPermissionRequestInProgress || dialog?.isShowing == true) return

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            configureWidget()
        } else {
            isPermissionRequestInProgress = true
            locationPermissionRequest.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

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
        val workRequest = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
            .setInputData(workDataOf(
                AppWidgetManager.EXTRA_APPWIDGET_ID to appWidgetId,
                "IS_TODAY" to true
            ))
            .build()
        WorkManager.getInstance(this).enqueue(workRequest)
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