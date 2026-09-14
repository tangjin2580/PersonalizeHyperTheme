package com.tangjin.personalizehyper.theme

import android.util.Log
import de.robv.android.xposed.XposedBridge

/**
 * 模块日志工具。
 *
 * 双写：既写 logcat（本进程可见，便于 adb 调试），也写 XposedBridge.log。
 * XposedBridge 的日志会被 LSPosed 落盘到 /data/adb/lspd/log/modules_*.log，
 * 这是 App 内「日志」界面真正的数据来源（Android 11+ 应用跨进程读不到 logcat）。
 *
 * 注意：Xposed API 是 compileOnly，模块 APK 的 dex 里并没有 XposedBridge 这个类。
 * 在**被 hook 的宿主进程**里它由 LSPosed 注入、可正常调用；
 * 但在**模块 App 自身的进程**里并不存在，直接调用会抛 NoClassDefFoundError。
 * 原版对此没有任何保护（App 自身进程一调用就崩），这里加上探测 + 兜底，
 * 使 LogHelper 在两种进程里都可用：宿主进程双写，自身进程只写 logcat。
 */
object LogHelper {

    private const val TAG = "FuckThemeManager"

    /** Xposed API 是否可用。只在首次调用时探测一次，结果缓存。 */
    private val xposedAvailable: Boolean by lazy {
        try {
            Class.forName("de.robv.android.xposed.XposedBridge")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Xposed API 不可用（模块自身进程），日志仅写 logcat")
            false
        }
    }

    private fun xposedLog(msg: String) {
        if (!xposedAvailable) return
        try {
            XposedBridge.log(msg)
        } catch (t: Throwable) {
            // 兜底：探测通过但实际调用仍失败时，不能让日志把业务流程带崩
        }
    }

    private fun xposedLog(t: Throwable) {
        if (!xposedAvailable) return
        try {
            XposedBridge.log(t)
        } catch (e: Throwable) {
            // 同上
        }
    }

    fun d(msg: String) {
        Log.d(TAG, msg)
        xposedLog(msg)
    }

    fun i(msg: String) {
        Log.i(TAG, msg)
        xposedLog(msg)
    }

    fun w(msg: String) {
        Log.w(TAG, msg)
        xposedLog(msg)
    }

    fun e(msg: String) {
        Log.e(TAG, msg)
        xposedLog(msg)
    }

    fun ex(t: Throwable) {
        Log.e(TAG, "exception", t)
        xposedLog(t)
    }
}
