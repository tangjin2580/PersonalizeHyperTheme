package com.tangjin.personalizehyper.theme

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.io.StringWriter

/**
 * 现代化日志界面，采用 SnowUI 设计系统风格。
 *
 * 界面重新设计：卡片式布局、现代按钮、更好的视觉层次。
 */
class LogActivity : Activity(), View.OnClickListener, Runnable {

    /** 供 [LogReader] 回传的待追加内容 */
    var pending: String? = null

    private var logView: TextView? = null
    private var reader: LogReader? = null

    // 按钮ID
    private var idStart = 0
    private var idStop = 0
    private var idClear = 0
    private var idRefresh = 0
    private var idRestartTheme = 0
    private var idRestartHome = 0
    
    // 日志等级开关
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
        // 主容器：垂直线性布局
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(this@LogActivity, R.color.background_light))
            setPadding(dpToPx(16), dpToPx(24), dpToPx(16), dpToPx(16))
        }

        // 标题区域
        val titleCard = createCard {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@LogActivity).apply {
                text = "HyperOS 主题破解"
                setTextAppearance(R.style.Text_H1)
                setPadding(0, 0, 0, dpToPx(4))
            })
            addView(TextView(this@LogActivity).apply {
                text = "模块日志监控器"
                setTextAppearance(R.style.Text_Caption)
                setTextColor(ContextCompat.getColor(this@LogActivity, R.color.text_tertiary))
            })
        }
        root.addView(titleCard)

        // 操作按钮卡片
        val actionsCard = createCard {
            orientation = LinearLayout.VERTICAL
            
            // 第一行按钮
            val row1 = LinearLayout(this@LogActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                weightSum =三条鲱鱼