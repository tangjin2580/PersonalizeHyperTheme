package com.tangjin.personalizehyper.theme

import android.util.Log
import de.robv.android.xposed.XposedBridge

/**
 * 模块日志工具。
 *
 * 双写：既写 logcat（本进程可见，便于 adb 调试），也写 XposedBridge.log。
 * XposedBridge 的日志会被 LSPosed 落盘到 /data/adb/lspd/log/modules_*.log，
 * 这是 App 内「日志」界面真正的数据来源（Android 11+ 应用跨进程读不到 logcat）。
 */
object LogHelper {

    private const val TAG = "FuckThemeManager"

    fun d(msg: String) {
        Log.d(TAG, msg)
        XposedBridge.log(msg)
    }

    fun i(msg: String) {
        Log.i(TAG, msg)
        XposedBridge.log(msg)
    }

    fun w(msg: String) {
        Log.w(TAG, msg)
        XposedBridge.log(msg)
    }

    fun e(msg: String) {
        Log.e(TAG, msg)
        XposedBridge.log(msg)
    }

    fun ex(t: Throwable) {
        Log.e(TAG, "exception", t)
        XposedBridge.log(t)
    }
}
