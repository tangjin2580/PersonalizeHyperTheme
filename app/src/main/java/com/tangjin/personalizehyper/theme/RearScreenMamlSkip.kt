package com.tangjin.personalizehyper.theme

import de.robv.android.xposed.XC_MethodHook

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
 *   - 来自背屏  -> 放行真实逻辑（返回原值，不干预）
 *   - 来自主屏  -> 强制返回 false（跳过付费校验，实现破解）
 *
 * ── 如何区分「是否来自背屏」─────────────────────────────────────────
 * 最稳妥的方式是看当前线程的调用栈（stackTrace）：
 * 背屏流程的方法名里会带有 "rearscreen"（大小写不敏感）。我们只要遍历
 * 整个调用栈，只要任意一帧的类名包含这个词，就判定为背屏流程。
 *
 * ── 技术要点 ───────────────────────────────────────────────────────
 * - XC_MethodHook 是 Xposed 提供的「方法钩子」基类：
 *     · beforeHookedMethod：在目标方法执行【前】插入逻辑，可改入参 / 直接给结果
 *     · afterHookedMethod ：在目标方法执行【后】插入逻辑，可改返回值
 * - 这里只需「在方法体执行前给出结果」，所以用 beforeHookedMethod，
 *   并通过 `param.result = false` 直接短路方法，原方法体不会再执行。
 */
class RearScreenMamlSkip : XC_MethodHook() {

    /**
     * 目标方法被调用前触发。
     *
     * @param param 被 hook 方法的上下文：
     *   - param.thisObject：调用该方法的实例（这里打印它的类名，方便调试确认 hook 命中）
     *   - param.result   ：设置后即为方法的最终返回值，方法原本的逻辑被跳过
     */
    override fun beforeHookedMethod(param: MethodHookParam) {
        // 打印 this 对象类名，确认确实命中了我们想 hook 的类
        param.thisObject?.let {
            LogHelper.d("FTM: MamlRearScreen thisObject=${it.javaClass.name}")
        }
        // 通用调试日志：说明这个钩子的 before 被触发了
        LogHelper.d("FTM: MamlRearScreen hook called")

        // 遍历当前线程的调用栈，看有没有「背屏」相关的方法帧
        val fromRearScreen = Thread.currentThread().stackTrace.any { element ->
            element.className?.contains("rearscreen", ignoreCase = true) == true
        }

        if (fromRearScreen) {
            // 来自背屏：不干预，让真实逻辑继续跑（直接 return，不设置 param.result）
            LogHelper.d("FTM: MamlRearScreen SKIP (rear) -> real flow")
            return
        }

        // 来自主屏：强制返回 false，跳过付费校验，使付费小组件免费可用
        LogHelper.d("FTM: MamlRearScreen AFTER (forced FALSE)")
        param.result = false
    }
}
