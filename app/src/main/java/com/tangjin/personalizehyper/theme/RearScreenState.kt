package com.tangjin.personalizehyper.theme

/**
 * 背屏（后置屏）主题的应用窗口标记。
 *
 * 背景：背屏主题的 DRM 校验走的是另一条链路，强行返回 DRM_SUCCESS 反而会让
 * 背屏主题应用失败。这里用一个 60 秒的时间窗口标记「刚刚点过背屏主题」，
 * 窗口内 DrmResult 放行真实校验，窗口外才强制成功。
 */
object RearScreenState {

    /** 标记后多久内视为「背屏流程仍在进行」 */
    private const val WINDOW_MS = 60_000L

    private var rearActiveUntil = 0L

    fun isRearActive(): Boolean = System.currentTimeMillis() < rearActiveUntil

    fun markRear() {
        rearActiveUntil = System.currentTimeMillis() + WINDOW_MS
    }
}
