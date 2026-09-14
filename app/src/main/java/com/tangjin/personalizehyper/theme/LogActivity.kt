package com.tangjin.personalizehyper.theme

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.io.StringWriter

/**
 * 模块日志界面（Jetpack Compose 现代化版）。
 *
 * 仅改 UI 层，后端逻辑（日志读取、等级判定、重启命令）与原版完全一致。
 * 日志来源见 [LogReader]。
 */
class LogActivity : ComponentActivity(), Runnable {

    /** 供 [LogReader] 回传的待追加内容 */
    var pending: String? = null

    private var reader: LogReader? = null

    // Compose 状态
    private val logText = mutableStateOf("")
    private val showV = mutableStateOf(false)
    private val showD = mutableStateOf(true)
    private val showI = mutableStateOf(true)
    private val showW = mutableStateOf(true)
    private val showE = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 状态栏透明 + 深色图标，让内容延伸到状态栏后方、与本机融合
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        try {
            setContent {
                MaterialTheme(colorScheme = appColorScheme()) {
                    LogScreen()
                }
            }
        } catch (t: Throwable) {
            report(t)
        }
    }

    fun isLevelEnabled(level: Char): Boolean = when (level) {
        'V' -> showV.value
        'D' -> showD.value
        'I' -> showI.value
        'W' -> showW.value
        'E' -> showE.value
        else -> true
    }

    /** [LogReader] 通过 runOnUiThread 回调到这里，避免子线程直接操作 View */
    override fun run() {
        val text = pending ?: return
        logText.value += "$text\n"
    }

    // ============================================================ 后端逻辑（与原版一致）

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
        logText.value = ""
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
            logText.value = if (text.isEmpty()) {
                "暂无模块 hook 日志。\n请确认 LSPosed 中本模块已启用并勾选作用域（主题管理器/桌面/系统框架），然后重启手机。"
            } else {
                text
            }
        } catch (t: Throwable) {
            LogHelper.ex(t)
            logText.value = "读取日志失败：${t.message}"
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

    private fun onRestartAssistant() {
        Runtime.getRuntime().exec(
            arrayOf(
                "su",
                "-c",
                "am force-stop com.miui.personalassistant >/dev/null 2>&1; " +
                    "am start -n com.miui.personalassistant/.PersonalAssistantActivity >/dev/null 2>&1"
            )
        ).waitFor()
        Toast.makeText(this, "已发送重启智能助理", Toast.LENGTH_SHORT).show()
    }

    /** 崩溃兜底：把堆栈铺到日志区 + Toast */
    private fun report(t: Throwable) {
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw).also { it.flush() })
        val msg = sw.toString()
        try {
            logText.value += "\n$msg\n"
        } catch (_: Throwable) {
        }
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    // ============================================================ Compose UI

    private fun appColorScheme() = lightColorScheme(
        primary = Color(0xFF2563EB),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFDBEAFE),
        onPrimaryContainer = Color(0xFF1E3A8A),
        secondaryContainer = Color(0xFFEDE9FE),
        onSecondaryContainer = Color(0xFF4C1D95),
        background = Color(0xFFF5F7FA),
        onBackground = Color(0xFF1F2937),
        surface = Color.White,
        onSurface = Color(0xFF1F2937),
        surfaceVariant = Color(0xFFEEF2F7),
        onSurfaceVariant = Color(0xFF6B7280),
        error = Color(0xFFEF4444),
    )

    @Composable
    private fun LogScreen() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                // 顶部标题
                Text(
                    text = "HyperOS 主题破解",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "模块日志监控器",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))

                // 日志控制卡片
                ControlCard()
                Spacer(Modifier.height(12.dp))

                // 系统快捷操作卡片
                SystemActionCard()
                Spacer(Modifier.height(12.dp))

                // 日志显示卡片
                LogCard()
                Spacer(Modifier.height(16.dp))

                // 底部作者信息图标
                FooterIcons()
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    @Composable
    private fun FooterIcons() {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                FooterIcon("\uD83D\uDC19", "GitHub") {
                    openUrl("https://github.com/tangjin2580")
                }
                FooterIcon("\uD83D\uDC27", "QQ") {
                    openQQ()
                }
                FooterIcon("\uD83D\uDCAC", "酷安") {
                    openUrl("https://www.coolapk.com/u/1143984")
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "作者：Mr.li",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    @Composable
    private fun FooterIcon(emoji: String, label: String, onClick: () -> Unit) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable { onClick() }
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(emoji, fontSize = 22.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (t: Throwable) {
            Toast.makeText(this, "无法打开链接：$url", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openQQ() {
        val qq = "2324099478"
        try {
            // 打开 QQ 个人资料卡（主页）
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("mqqapi://card/show_pslcard?src_type=internal&version=1&uin=$qq")
                )
            )
        } catch (t: Throwable) {
            copyToClipboard(qq)
            Toast.makeText(this, "QQ 号已复制：$qq", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyToClipboard(text: String) {
        val cm = getSystemService(ClipboardManager::class.java)
        cm?.setPrimaryClip(ClipData.newPlainText("qq", text))
    }

    @Composable
    private fun ControlCard() {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = "日志控制",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(
                        onClick = { onStartClick() },
                        modifier = Modifier.weight(1f)
                    ) { Text("开始") }
                    FilledTonalButton(
                        onClick = { onStopClick() },
                        modifier = Modifier.weight(1f)
                    ) { Text("停止") }
                    FilledTonalButton(
                        onClick = { onClearClick() },
                        modifier = Modifier.weight(1f)
                    ) { Text("清空") }
                }

                Spacer(Modifier.height(10.dp))

                FilledTonalButton(
                    onClick = { onRefreshLog() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("刷新日志") }

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "日志等级过滤",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LevelChip('V', showV.value) { showV.value = !showV.value }
                    LevelChip('D', showD.value) { showD.value = !showD.value }
                    LevelChip('I', showI.value) { showI.value = !showI.value }
                    LevelChip('W', showW.value) { showW.value = !showW.value }
                    LevelChip('E', showE.value) { showE.value = !showE.value }
                }
            }
        }
    }

    @Composable
    private fun LevelChip(level: Char, selected: Boolean, onClick: () -> Unit) {
        val color = levelColor(level)
        val bg = if (selected) color else Color(0xFFE5E7EB)
        val fg = if (selected) Color.White else Color(0xFF6B7280)
        Box(
            modifier = Modifier
                .size(width = 48.dp, height = 36.dp)
                .background(bg, RoundedCornerShape(10.dp))
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = level.toString(),
                color = fg,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }

    @Composable
    private fun SystemActionCard() {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = "系统快捷操作",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                RestartItem(
                    emoji = "\uD83C\uDFE0", // 🏠
                    title = "重启系统桌面",
                    description = "重新加载桌面与 SystemUI",
                    onClick = { onRestartHome() }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                )
                RestartItem(
                    emoji = "\u2728", // ✨
                    title = "重启智能助理",
                    description = "刷新负一屏及快捷指令",
                    onClick = { onRestartAssistant() }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                )
                RestartItem(
                    emoji = "\uD83C\uDFA8", // 🎨
                    title = "重启主题",
                    description = "重新加载主题与壁纸",
                    onClick = { onRestartTheme() }
                )
            }
        }
    }

    @Composable
    private fun RestartItem(
        emoji: String,
        title: String,
        description: String,
        onClick: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(emoji, fontSize = 20.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalButton(onClick = onClick) {
                Text("重启", style = MaterialTheme.typography.labelMedium)
            }
        }
    }

    @Composable
    private fun LogCard() {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = "模块日志",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                val scrollState = rememberScrollState()
                LaunchedEffect(logText.value) {
                    if (logText.value.isNotEmpty()) {
                        scrollState.scrollTo(scrollState.maxValue)
                    }
                }
                Text(
                    text = if (logText.value.isEmpty()) "点击「开始」或「刷新日志」查看本模块 hook 日志" else logText.value,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .verticalScroll(scrollState)
                )
            }
        }
    }

    private fun levelColor(c: Char): Color = when (c) {
        'V' -> Color(0xFF9CA3AF)
        'D' -> Color(0xFF3B82F6)
        'I' -> Color(0xFF10B981)
        'W' -> Color(0xFFF59E0B)
        'E' -> Color(0xFFEF4444)
        else -> Color.Gray
    }
}
