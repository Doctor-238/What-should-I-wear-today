package com.yehyun.whatshouldiweartoday.util

import android.content.Context
import android.os.Process
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

class CrashLogger(private val context: Context) : Thread.UncaughtExceptionHandler {

    private val defaultHandler: Thread.UncaughtExceptionHandler? = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        // 로그 파일에 크래시 정보를 기록합니다.
        logCrashToFile(throwable)

        // 기본 핸들러를 호출하여 시스템이 크래시를 처리하도록 합니다 (예: '앱이 중지되었습니다' 대화상자).
        defaultHandler?.uncaughtException(thread, throwable)

        // 앱을 완전히 종료하여 무한 루프를 방지합니다.
        Process.killProcess(Process.myPid())
        exitProcess(10)
    }

    private fun logCrashToFile(throwable: Throwable) {
        try {
            // 로그를 저장할 디렉토리 경로를 설정합니다. (내장메모리/Android/data/앱패키지/files/logs)
            val logDir = File(context.getExternalFilesDir(null), "logs")
            if (!logDir.exists()) {
                logDir.mkdirs()
            }

            // 현재 시간을 기반으로 로그 파일 이름을 생성합니다.
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.KOREA).format(Date())
            val logFile = File(logDir, "crash_log_$timestamp.txt")

            // 파일에 크래시 정보를 기록합니다.
            val writer = FileWriter(logFile, true)
            writer.append("Crash Time: $timestamp\n")
            writer.append("---------------------------------\n\n")

            val stringWriter = StringWriter()
            val printWriter = PrintWriter(stringWriter)
            throwable.printStackTrace(printWriter)
            writer.append(stringWriter.toString())

            writer.append("\n---------------------------------\n\n")
            writer.flush()
            writer.close()

        } catch (e: Exception) {

            // 로그 기록 자체에서 오류가 발생하면 막을 방법이 없으므로 콘솔에만 출력합니다. e.printStackTrace()
        }
    }
}