package com.tangjin.personalizehyper.theme

import de.robv.android.xposed.XC_MethodHook

/**
 * 背屏 Maml 相关的跳过钩子。
 *
 * 调用栈里出现 rearscreen 说明当前是背屏流程，放行真实逻辑；
 * 否则强制返回 false，跳过付费校验。
 */
class RearScreenMamlSkip : XC_MethodHook() {

    override fun beforeHookedMethod(param: MethodHookParam) {
        param.thisObject?.let {
            LogHelper.d("FTM: MamlRearScreen thisObject=${it.javaClass.name}")
        }
        LogHelper.d("FTM: MamlRearScreen hook called")

        val fromRearScreen = Thread.currentThread().stackTrace.any { element ->
            element.className?.contains("rearscreen", ignoreCase = true) == true
        }

        if (fromRearScreen) {
            LogHelper.d("FTM: MamlRearScreen SKIP (rear) -> real flow")
            return
        }

        LogHelper.d("FTM: MamlRearScreen AFTER (forced FALSE)")
        param.result = false
    }
}
