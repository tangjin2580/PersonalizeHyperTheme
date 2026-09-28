package com.tangjin.personalizehyper.theme

import android.util.Log

/**
 * 模块日志工具。
 *
 * 双写：既写 logcat（本进程可见，便于 adb 调试），也写 libxposed 框架日志。
 * libxposed 102 下不再有 legacy 的 XposedBridge.log，框架日志统一由
 * [io.github.libxposed.api.XposedInterface.log] 提供。模块入口（Api102Entry）在
 * onModuleLoaded / onPackageReady / onSystemServerStarting 里通过
 * [initLogger] 把框架的 log 方法注入进来。
 *
 * ── 与 legacy 的差别 ────────────────────────────────────────────────
 * legacy 用 Class.forName 探测 XposedBridge 是否存在来兜底（模块自身进程里没有它）。
 * libxposed 下改为：注入的 logger 为 null 即表示「当前不是被 hook 的宿主进程」，
 * 此时只写 logcat。宿主进程里 logger 被注入，双写；调用若抛异常则静默吞掉，
 * 绝不因为日志把业务流程带崩。
 *
 * 注意：libxposed API 同样是 compileOnly，模块 APK 的 dex 里没有 XposedInterface 这个类，
 * 但在「被 hook 的宿主进程」里它由框架注入、可正常调用；模块自身进程里 logger 为 null，
 * 不会触发调用，因此两种进程都安全。
 */
object LogHelper {

    const val TAG = "FuckThemeManager"

    /** android.util.Log 的优先级常量（与 XposedInterface.log 的 priority 参数一致）。 */
    private const val VERBOSE = 2
    private const val DEBUG = 3
    private const val INFO = 4
    private const val WARN = 5
    private const val ERROR = 6

    /** 经 XposedModule 注入的日志回调；宿主进程里由框架提供，模块自身进程为 null。 */
    @Volatile
    private var xposedLog: ((Int, String) -> Unit)? = null

    /**
     * 注入框架日志回调。回调签名 (priority, message)，tag 固定为 [TAG]。
     * 在 Api102Entry 的生命周期回调里调用，确保拿到的是「当前已 attach 的框架实例」。
     */
    fun initLogger(logger: (Int, String) -> Unit) {
        xposedLog = logger
    }

    private fun xlog(priority: Int, msg: String) {
        val l = xposedLog
        if (l != null) {
            try {
                l(priority, msg)
            } catch (_: Throwable) {
                // 兜底：探测通过但实际调用仍失败时，不能让日志把业务流程带崩
            }
        }
    }

    fun d(msg: String) {
        Log.d(TAG, msg)
        xlog(DEBUG, msg)
    }

    fun i(msg: String) {
        Log.i(TAG, msg)
        xlog(INFO, msg)
    }

    fun w(msg: String) {
        Log.w(TAG, msg)
        xlog(WARN, msg)
    }

    fun e(msg: String) {
        Log.e(TAG, msg)
        xlog(ERROR, msg)
    }

    fun ex(t: Throwable) {
        Log.e(TAG, "exception", t)
        xlog(ERROR, "$t: ${t.message}")
    }
}
