package com.tangjin.personalizehyper.theme

import io.github.libxposed.api.XposedInterface

/**
 * 背屏（后置副屏）Maml 付费小组件的「跳过钩子」。
 *
 * ── 背景：为什么需要它 ──────────────────────────────────────────────
 * 在「智能助理」（负一屏 / 桌面小组件）里，付费 Maml 小组件有两个判断方法：
 *   - shouldCheckMamlBoughtState        （是否需要校验购买状态）
 *   - isTargetPositionMamlPayAndDownloading（该位置是否是付费且正在下载）
 * 这两个方法原本的语义是：
 *   - 返回 true  -> 走真实校验（没买就用不了）
 *   - 返回 false -> 跳过校验（直接可用）
 * 我们想让付费组件「免费可用」，所以直觉上应该让它们返回 false。
 *
 * 但是！背屏（手机背后的小屏幕）走的是同一条代码链路。如果在背屏流程里
 * 也强制返回 false，背屏小组件反而会出问题。因此需要「区分来源」：
 *   - 来自背屏  -> 放行真实逻辑（调用 chain.proceed()，不干预返回值）
 *   - 来自主屏  -> 强制返回 false（跳过原方法体，实现破解）
 *
 * ── 如何区分「是否来自背屏」─────────────────────────────────────────
 * 最稳妥的方式是看当前线程的调用栈（stackTrace）：
 * 背屏流程的方法名里会带有 "rearscreen"（大小写不敏感）。我们只要遍历
 * 整个调用栈，只要任意一帧的类名包含这个词，就判定为背屏流程。
 *
 * ── libxposed 102 改造 ──────────────────────────────────────────────
 * legacy 里它是一个 XC_MethodHook 子类，在 beforeHookedMethod 里 set result；
 * libxposed 102 改为提供一个 [XposedInterface.Hooker] 工厂 [hooker]：
 *   - 背屏：chain.proceed() 放行真实逻辑；
 *   - 主屏：直接 return false（不调 proceed，等价于「before 设 result 跳过原方法」）。
 */
object RearScreenMamlSkip {

    /**
     * 生成一个 libxposed Hooker，用于挂到需要区分主/背屏的判断方法上。
     */
    fun hooker(): XposedInterface.Hooker = XposedInterface.Hooker { chain ->
        val fromRearScreen = Thread.currentThread().stackTrace.any { element ->
            element.className?.contains("rearscreen", ignoreCase = true) == true
        }

        if (fromRearScreen) {
            // 来自背屏：不干预，让真实逻辑继续跑
            LogHelper.d("FTM: MamlRearScreen SKIP (rear) -> real flow")
            chain.proceed()
        } else {
            // 来自主屏：强制返回 false，跳过付费校验，使付费小组件免费可用
            LogHelper.d("FTM: MamlRearScreen AFTER (forced FALSE)")
            false
        }
    }
}
