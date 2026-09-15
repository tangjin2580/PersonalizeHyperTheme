package com.tangjin.personalizehyper.theme

/**
 * 背屏（后置屏）主题的应用窗口标记。
 *
 * ── 这个单例解决什么问题 ────────────────────────────────────────────
 * 主题管理器（com.android.thememanager）里对「背屏主题」和「主屏主题」走的是
 * 同一套 DRM 校验链路。但我们只希望破解主屏主题，背屏主题必须走真实校验，
 * 否则强置成功反而会让背屏主题应用失败。
 *
 * 难点在于：破解逻辑分散在多处（见 XposedInit）：
 *   1. OnlineResourceDetail.toResource 里，根据 category 判断是否「rear」；
 *      一旦判断为背屏，就调用 [markRear] 打开一个时间窗口。
 *   2. DexKit 定位到的 DrmResult 方法里，每次 DRM 校验前都来问一句
 *      [isRearActive]：窗口内就放行真实校验，窗口外才强置 DRM_SUCCESS。
 *
 * ── 为什么用「时间窗口」而不是一个布尔开关 ──────────────────────────
 * 从用户点击背屏主题到真正触发 DRM 校验，中间隔了若干异步步骤，时间不固定。
 * 用一个「打开后 60 秒内都算背屏流程」的时间窗口，比瞬时布尔更稳：
 * 既能在校验点正确识别背屏，又不会让这个标记永久生效、误伤后续主屏主题。
 *
 * ── 实现方式 ───────────────────────────────────────────────────────
 * 用「到期时间戳」rearActiveUntil 表示窗口：
 *   - markRear()    把到期时间设为「现在 + 60 秒」
 *   - isRearActive() 只要当前时间还没过到期点，就返回 true
 * 这是一个典型且轻量的「时间窗口 / 滑动过期」实现，无需任何锁。
 */
object RearScreenState {

    /** 标记后多久内视为「背屏流程仍在进行」（60 秒，单位毫秒） */
    private const val WINDOW_MS = 60_000L

    /** 背屏窗口的到期时间戳（毫秒）。0 表示从未标记 / 已过期 */
    private var rearActiveUntil = 0L

    /**
     * 当前是否仍处于「背屏时间窗口」内。
     * 由 DrmResult 钩子在每次 DRM 校验前调用，决定放行还是强置成功。
     */
    fun isRearActive(): Boolean = System.currentTimeMillis() < rearActiveUntil

    /**
     * 打开背屏窗口：把到期时间推到「现在 + WINDOW_MS」。
     * 由 OnlineResourceDetail.toResource 判定为背屏主题时调用一次。
     */
    fun markRear() {
        rearActiveUntil = System.currentTimeMillis() + WINDOW_MS
    }
}
