package com.tangjin.personalizehyper.theme

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.io.StringWriter

/**
 * 模块日志界面。
 *
 * 界面完全用代码构建（不依赖 layout），这样即使资源被 shrink 也不会崩。
 * 日志来源见 [LogReader]。
 */
class LogActivity : Activity(), View.OnClickListener, Runnable {

    /** 供 [LogReader] 回传的待追加内容 */
    var pending: String? = null

    private var logView: TextView? = null
    private var reader: LogReader? = null

    private var idStart = 0
    private var idStop = 0
    private var idClear = 0
    private var idRefresh = 0
    private var idRestartTheme = 0
    private var idRestartHome = 0
    private var idTglV = 0
    private var idTglD = 0
    private var idTglI = 0
    private var idTglW = 0
    private var idTglE = 0

    private var showV = false
    private var showD = true
    private var showI = true
    private var showW = true
    private var showE = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_PersonalizeHyperTheme)  // 应用SnowUI主题
        try {
            buildUi()
        } catch (t: Throwable) {
            report(t)
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { 
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF8FAFC.toInt())  // 浅灰色背景
        }
        val bar = LinearLayout(this).apply { 
            orientation = LinearLayout.HORIZONTAL
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
        }

        idStart = View.generateViewId()
        bar.addView(mkBtn("开始", idStart).apply { 
            setBackgroundColor(0xFF2563EB.toInt())  // 蓝色按钮
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
        })
        idStop = View.generateViewId()
        bar.addView(mkBtn("停止", idStop).apply { 
            setBackgroundColor(0xFF3B82F6.toInt())  // 浅蓝色按钮
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
        })
        idClear = View.generateViewId()
        bar.addView(mkBtn("清空", idClear).apply { 
            setBackgroundColor(0xFFEF4444.toInt())  // 红色按钮
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
        })
        
        bar.addView(Space(this).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(8), LinearLayout.LayoutParams.WRAP_CONTENT)
        })

        idRefresh = View.generateViewId()
        bar.addView(mkBtn("刷新日志", idRefresh).apply { 
            setBackgroundColor(0xFF10B981.toInt())  // 绿色按钮
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
        })
        
        bar.addView(Space(this).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(8), LinearLayout.LayoutParams.WRAP_CONTENT)
        })

        bar.addView(TextView(this).apply { 
            text = "等级" 
            setTextColor(0xFF666666.toInt())  // 深灰色文本
            setPadding(dpToPx(4), dpToPx(8), 0, dpToPx(8))
        })

        idTglV = View.generateViewId()
        bar.addView(mkToggle("V", idTglV).apply { 
            isChecked = showV
            setBackgroundColor(0xFF999999.toInt())  // 灰色
            setTextColor(0xFFFFFFFF.toInt())
        })
        idTglD = View.generateViewId()
        bar.addView(mkToggle("D", idTglD).apply { 
            isChecked = showD
            setBackgroundColor(0xFF3B82F6.toInt())  // 蓝色
            setTextColor(0xFFFFFFFF.toInt())
        })
        idTglI = View.generateViewId()
        bar.addView(mkToggle("I", idTglI).apply { 
            isChecked = showI
            setBackgroundColor(0xFF10B981.toInt())  // 绿色
            setTextColor(0xFFFFFFFF.toInt())
        })
        idTglW = View.generateViewId()
        bar.addView(mkToggle("W", idTglW).apply { 
            isChecked = showW
            setBackgroundColor(0xFFF59E0B.toInt())  // 黄色
            setTextColor(0xFFFFFFFF.toInt())
        })
        idTglE = View.generateViewId()
        bar.addView(mkToggle("E", idTglE).apply { 
            isChecked = showE
            setBackgroundColor(0xFFEF4444.toInt())  // 红色
            setTextColor(0xFFFFFFFF.toInt())
        })

        root.addView(bar)

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setBackgroundColor(0xFFFFFFFF.toInt())  // 白色背景
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
        }
        val tv = TextView(this).apply {
            id = View.generateViewId()
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(0xFF333333.toInt())  // 深灰色文本
            setBackgroundColor(0xFFFFFFFF.toInt())  // 白色背景
            setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
        }
        logView = tv
        scroll.addView(tv, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ))
        root.addView(scroll)

        setContentView(root)
    }
    
    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun mkBtn(text: String, id: Int): Button = Button(this).apply {
        setText(text)
        this.id = id
        setOnClickListener(this@LogActivity)
    }

    private fun mkToggle(text: String, id: Int): ToggleButton = ToggleButton(this).apply {
        this.text = text
        textOn = text
        textOff = text
        this.id = id
        setOnClickListener(this@LogActivity)
    }

    private fun updateLevels() {
        showV = (findViewById(idTglV) as ToggleButton).isChecked
        showD = (findViewById(idTglD) as ToggleButton).isChecked
        showI = (findViewById(idTglI) as ToggleButton).isChecked
        showW = (findViewById(idTglW) as ToggleButton).isChecked
        showE = (findViewById(idTglE) as ToggleButton).isChecked
    }

    fun isLevelEnabled(level: Char): Boolean = when (level) {
        'V' -> showV
        'D' -> showD
        'I' -> showI
        'W' -> showW
        'E' -> showE
        else -> true
    }

    override fun onClick(v: View) {
        try {
            when (v.id) {
                idStart -> onStartClick()
                idStop -> onStopClick()
                idClear -> onClearClick()
                idRefresh -> onRefreshLog()
                idRestartTheme -> onRestartTheme()
                idRestartHome -> onRestartHome()
                else -> updateLevels()
            }
        } catch (t: Throwable) {
            report(t)
        }
    }

    private fun onStartClick() {
        val r = LogReader(this)
        reader = r
        Thread(r).start()
        Toast.makeText(this, "开始跟随日志", Toast.LENGTH_SHORT).show()
    }

    private fun onStopClick() {
        reader?.let {
            it.stop = true
            it.proc?.destroy()
        }
        reader = null
    }

    private fun onClearClick() {
        logView?.text = ""
    }

    /** 一次性拉取最近 300 行本模块日志 */
    private fun onRefreshLog() {
        try {
            val cmd = arrayOf(
                "su",
                "-c",
                "grep -F '[com.tangjin.personalizehyper.theme,XposedBridge' ${LogReader.LSPD_LOG} | tail -n 300"
            )
            val reader = BufferedReader(
                InputStreamReader(Runtime.getRuntime().exec(cmd).inputStream)
            )
            val sb = StringBuilder()
            reader.use {
                while (true) {
                    val line = it.readLine() ?: break
                    sb.append(LogReader.trimLine(line)).append('\n')
                }
            }

            val text = sb.toString()
            logView?.text = if (text.isEmpty()) {
                "暂无模块 hook 日志。\n请确认 LSPosed 中本模块已启用并勾选作用域（主题管理器/桌面/系统框架），然后重启手机。"
            } else {
                text
            }
        } catch (t: Throwable) {
            LogHelper.ex(t)
            logView?.text = "读取日志失败：${t.message}"
        }
    }

    private fun onRestartTheme() {
        Runtime.getRuntime().exec(
            arrayOf(
                "su",
                "-c",
                "am force-stop com.android.thememanager >/dev/null 2>&1; " +
                    "am start -n com.android.thememanager/com.android.thememanager.ThemeResourceProxyTabActivity >/dev/null 2>&1"
            )
        ).waitFor()
        Toast.makeText(this, "已发送重启主题管理器", Toast.LENGTH_SHORT).show()
    }

    private fun onRestartHome() {
        Runtime.getRuntime().exec(
            arrayOf(
                "su",
                "-c",
                "am force-stop com.miui.home >/dev/null 2>&1; " +
                    "am start -c android.intent.category.HOME -a android.intent.action.MAIN >/dev/null 2>&1"
            )
        ).waitFor()
        Toast.makeText(this, "已发送重启桌面", Toast.LENGTH_SHORT).show()
    }

    /** 崩溃兜底：把堆栈直接铺到界面上，避免直接闪退无从排查 */
    private fun report(t: Throwable) {
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw).also { it.flush() })
        val msg = sw.toString()

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val tv = TextView(this).apply {
            text = msg
            textSize = 10f
        }
        root.addView(ScrollView(this).apply { addView(tv) })
        setContentView(root)
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    /** [LogReader] 通过 runOnUiThread 回调到这里，避免子线程直接操作 View */
    override fun run() {
        val text = pending ?: return
        logView?.append("$text\n")
    }
}
