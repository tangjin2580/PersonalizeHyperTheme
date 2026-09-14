package com.tangjin.personalizehyper.theme

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 日志读取器：跟随 LSPosed 落盘日志，把本模块的 XposedBridge 输出喂给界面。
 *
 * 与原版 APK 的差异只有一处——日志来源：
 *
 * 原版读 logcat（`logcat -v threadtime -s 'FuckThemeManager:*'`），
 * 但 Android 11 之后应用只能读自己的 logcat，主题管理器的日志根本读不到，
 * 且 `*` 不是合法优先级，命令恒返回空。因此改成用 root 直接读
 * LSPosed 落盘日志 `/data/adb/lspd/log/modules_*.log`
 * （XposedBridge.log 会被 LSPosed 统一写进这个文件）。
 *
 * 其余逻辑（等级判定、过滤、喂给界面的方式）与原版一致。
 */
class LogReader(private val act: LogActivity) : Runnable {

    /** 置 true 表示停止跟随 */
    @Volatile
    var stop = false

    /** 当前跟随的子进程，停止时需要销毁 */
    var proc: Process? = null
        private set

    /** 上一次成功解析出的等级；解析不出来时沿用，避免行与行之间等级跳变 */
    private var lastLevel: Char = 'I'

    override fun run() {
        try {
            val process = startLogcat()
            proc = process
            LogHelper.d("FTM_READER_START")

            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                // 原版 APK 这里的循环条件是 `while (stop)`，而 stop 初值为 false，
                // 导致「开始」后立刻退出、实时跟随完全不工作（R8 后的 smali 可确认）。
                // 保留修正：stop 为 false 时继续跟随。
                while (!stop) {
                    val line = reader.readLine() ?: break
                    Log.d(TAG_DIAG, line)

                    // 只保留本模块输出，剔掉系统/其它模块的噪音
                    if (!isModuleLine(line)) continue

                    val text = trimLine(line)
                    if (act.isLevelEnabled(levelOf(text))) {
                        act.pending = text
                        act.runOnUiThread(act)
                    }
                }
            }
        } catch (t: Throwable) {
            LogHelper.ex(t)
        }
    }

    /** 优先 root 直接跟随 LSPosed 日志；拿不到 root 时退回读本进程 logcat */
    private fun startLogcat(): Process = try {
        Log.d("FTM_SU_TRY", "attempt su logcat")
        Runtime.getRuntime()
            .exec(arrayOf("su", "-c", "tail -n 60 -f $LSPD_LOG"))
            .also { Log.d("FTM_SU_OK", "su logcat started") }
    } catch (t: Throwable) {
        LogHelper.ex(t)
        Log.d("FTM_SU_FAIL", "su denied, fallback plain logcat")
        Runtime.getRuntime().exec(arrayOf("logcat", "-v", "threadtime", "-s", "$TAG:*"))
    }

    /**
     * 判定日志等级。
     *
     * 先按原版的 logcat 规则取第 5 个字段（`-v threadtime` 下是优先级字母）；
     * LSPosed 落盘日志的格式不同（该位置是 `]`），解析不到时再按内容判断，
     * 保证「等级过滤」在新日志源下依然有效。
     */
    fun levelOf(line: String): Char {
        val parts = line.split("\\s+".toRegex())
        if (parts.size >= 5) {
            val token = parts[4]
            if (token.length == 1) {
                val c = token[0]
                if (c == 'V' || c == 'D' || c == 'I' || c == 'W' || c == 'E') {
                    lastLevel = c
                    return c
                }
            }
        }
        val c = if (line.contains("xception") || line.contains("rror")) 'E' else lastLevel
        lastLevel = c
        return c
    }

    companion object {

        private const val TAG = "FuckThemeManager"

        /** 原版用于打印原始行的诊断 tag */
        private const val TAG_DIAG = "FTM_DIAG"

        /** LSPosed 落盘日志路径（通配符，交给 shell 展开） */
        const val LSPD_LOG = "/data/adb/lspd/log/modules_*.log"

        /** LSPosed 写入的模块标识前缀，用于把系统/其它模块的日志全部过滤掉 */
        private const val MODULE_MARK = "[com.tangjin.personalizehyper.theme,XposedBridge"

        /** 只保留本模块的 XposedBridge 日志 */
        fun isModuleLine(line: String): Boolean = line.contains(MODULE_MARK)

        /**
         * 把 LSPosed 的长前缀裁掉，只留「时间 + 正文」：
         *
         * 输入：`[ 2026-09-14T04:36:13.441  10283:10283:10283 I/LSPosedFramework ] (宿主)[com.tangjin.personalizehyper.theme,XposedBridge...] FTM: xxx`
         * 输出：`2026-09-14T04:36:13.441 FTM: xxx`
         *
         * 解析不出前缀时原样返回，保证不丢日志。
         */
        fun trimLine(line: String): String {
            val mark = line.indexOf(MODULE_MARK)
            val body = if (mark >= 0) {
                val close = line.indexOf(']', mark)
                if (close >= 0) line.substring(close + 1) else line.substring(mark)
            } else {
                line
            }

            // 时间戳：行首 '[' 之后的第一个 token
            val stamp = if (line.startsWith("[")) {
                line.substring(1).trimStart().substringBefore(' ')
            } else {
                ""
            }

            val msg = body.trimStart()
            return if (stamp.isNotEmpty() && stamp != line) "$stamp $msg" else msg
        }
    }
}
