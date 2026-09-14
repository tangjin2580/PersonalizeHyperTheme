package com.tangjin.personalizehyper.theme

import com.github.kyuubiran.ezxhelper.ClassUtils
import com.github.kyuubiran.ezxhelper.finders.FieldFinder
import com.github.kyuubiran.ezxhelper.finders.MethodFinder
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.luckypray.dexkit.DexKitBridge
import io.luckypray.dexkit.builder.BatchFindArgs
import miui.drm.DrmManager
import miui.drm.ThemeReceiver
import java.io.File
import java.lang.reflect.Method

/**
 * 模块入口，逻辑与设备上 1.9.0（versionCode 19）的 APK 保持一致。
 *
 * 作用域（见 res/values/array.xml）：
 * - android 系统框架
 * - com.miui.personalassistant 智能助理（背屏/桌面小组件）
 * - com.android.thememanager 主题壁纸
 * - com.miui.home 桌面
 *
 * 每个作用域一个 try/catch：宿主版本对不上时只让该分支失效，不连累其它作用域。
 *
 * 关于 hook 范围：原 APK 里有的地方取「第一个匹配方法」（MethodFinder.first），
 * 有的地方取「全部匹配方法」（MethodFinder.toList）。这里严格沿用同样语义，
 * 因为重载数量不同会直接改变行为。
 *
 * 注：原 APK 还会设置 EzXHelper 的 `Log.defaultLogger.logTag` 与
 * `LogExtensions.classLoader`（用于 ClassUtils 的隐式类加载器）。
 * 依赖升级到 EzXHelper 2.2.1 后这两个成员已不存在，因此改为显式传 classLoader，
 * 效果等价且更明确。
 */
class XposedInit : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        LogHelper.d(lpparam.packageName)

        when (lpparam.packageName) {
            "android" -> hookSystemFramework()
            "com.miui.personalassistant" -> hookPersonalAssistant(lpparam)
            "com.android.thememanager" -> hookThemeManager(lpparam)
            "com.miui.home" -> hookHome(lpparam)
        }
    }

    // ---------------------------------------------------------------- android
    // 系统框架：只在 ThemeReceiver.validateTheme 校验期间临时放行 DrmManager.isLegal，
    // 校验结束立刻摘钩，避免影响系统其它 DRM 逻辑。
    private fun hookSystemFramework() {
        runCatching {
            LogHelper.d("FTM: applying hooks for android")

            var unhooks: List<XC_MethodHook.Unhook> = emptyList()

            MethodFinder.fromClass(ThemeReceiver::class.java)
                .filterByName("validateTheme")
                .firstMethod()
                .let { validate ->
                    XposedBridge.hookMethod(validate, object : XC_MethodHook() {

                        override fun beforeHookedMethod(param: MethodHookParam) {
                            LogHelper.d("FTM: ThemeReceiver.validateTheme BEFORE")
                            // isLegal 可能有重载，原 APK 取全部
                            unhooks = MethodFinder.fromClass(DrmManager::class.java)
                                .filterByName("isLegal")
                                .beforeAll { it.result = DrmManager.DrmResult.DRM_SUCCESS }
                        }

                        override fun afterHookedMethod(param: MethodHookParam) {
                            LogHelper.d("FTM: ThemeReceiver.validateTheme AFTER")
                            unhooks.forEach { hook -> hook.unhook() }
                            unhooks = emptyList()
                        }
                    })
                }
        }.onFailure { LogHelper.ex(it) }
    }

    // ------------------------------------------------- com.miui.personalassistant
    // 智能助理：付费小组件（Maml）相关判断全部改常量；
    // shouldCheckMamlBoughtState / isTargetPositionMamlPayAndDownloading 需要区分背屏，
    // 单独走 RearScreenMamlSkip（调用栈里带 rearscreen 时放行真实逻辑）。
    private fun hookPersonalAssistant(lpparam: XC_LoadPackage.LoadPackageParam) {
        runCatching {
            LogHelper.d("FTM: applying hooks for com.miui.personalassistant")

            val vmClass =
                lpparam.loadClass("com.miui.personalassistant.picker.business.detail.PickerDetailViewModel")
            val respClass =
                lpparam.loadClass("com.miui.personalassistant.picker.business.detail.bean.PickerDetailResponse")
            val wrapperClass =
                lpparam.loadClass("com.miui.personalassistant.picker.business.detail.bean.PickerDetailResponseWrapper")

            MethodFinder.fromClass(lpparam.loadClass("com.miui.maml.widget.edit.MamlutilKt"))
                .filterByName("themeManagerSupportPaidWidget")
                .returnConstant(false)

            MethodFinder.fromClass(vmClass)
                .filterByName("isCanDirectAddMaMl")
                .returnConstant(true)

            MethodFinder.fromClass(
                lpparam.loadClass("com.miui.personalassistant.picker.business.detail.utils.PickerDetailDownloadManager\$Companion")
            ).filterByName("isCanDownload").returnConstant(true)

            MethodFinder.fromClass(
                lpparam.loadClass("com.miui.personalassistant.picker.business.detail.utils.PickerDetailUtil")
            ).filterByName("isCanAutoDownloadMaMl").returnConstant(true)

            MethodFinder.fromClass(respClass).filterByName("isPay").returnConstant(false)
            MethodFinder.fromClass(respClass).filterByName("isBought").returnConstant(true)
            MethodFinder.fromClass(wrapperClass).filterByName("isPay").returnConstant(false)
            MethodFinder.fromClass(wrapperClass).filterByName("isBought").returnConstant(true)

            MethodFinder.fromClass(vmClass)
                .filterByName("shouldCheckMamlBoughtState")
                .hookFirst(RearScreenMamlSkip())

            MethodFinder.fromClass(vmClass)
                .filterByName("isTargetPositionMamlPayAndDownloading")
                .hookFirst(RearScreenMamlSkip())

            MethodFinder.fromClass(vmClass)
                .filterByName("checkIsIndependentProcessWidgetForPosition")
                .returnConstant(true)
        }.onFailure { LogHelper.ex(it) }
    }

    // ------------------------------------------------- com.android.thememanager
    private fun hookThemeManager(lpparam: XC_LoadPackage.LoadPackageParam) {
        runCatching { LogHelper.d("FTM: applying hooks for com.android.thememanager") }

        // 1. 详情转 Resource 时把 bought 置 true；背屏主题除外（强置会导致应用失败）。
        //    toResource 取全部匹配方法。
        runCatching {
            MethodFinder.fromClass(
                lpparam.loadClass("com.android.thememanager.detail.theme.model.OnlineResourceDetail")
            ).filterByName("toResource").afterAll { param ->
                val obj = param.thisObject
                val category = XposedHelpers.getObjectField(obj, "category") as? String
                if (category == null || !category.lowercase().contains("rear")) {
                    LogHelper.d("FTM: OnlineResourceDetail.toResource AFTER (force bought=true)")
                    XposedHelpers.setObjectField(obj, "bought", true)
                } else {
                    LogHelper.d("FTM: OnlineResourceDetail.toResource SKIP force bought (rear screen)")
                    RearScreenState.markRear()
                }
            }
        }.onFailure { LogHelper.ex(it) }

        // 2. 折扣价显示：把第二个 int 参数置 0，去掉付费角标。取全部匹配方法。
        runCatching {
            MethodFinder.fromClass(
                lpparam.loadClass("com.android.thememanager.basemodule.views.DiscountPriceView")
            )
                .filterByParamCount(2)
                .filterByParamTypes(java.lang.Integer.TYPE, java.lang.Integer.TYPE)
                .filterByReturnType(java.lang.Void.TYPE)
                .beforeAll { param ->
                    LogHelper.d("FTM: DiscountPriceView BEFORE")
                    param.args[1] = 0
                }
        }.onFailure { LogHelper.ex(it) }

        runCatching {
            MethodFinder.fromClass(lpparam.loadClass("com.miui.maml.widget.edit.MamlutilKt"))
                .filterByName("themeManagerSupportPaidWidget")
                .returnConstant(false)
        }.onFailure { LogHelper.ex(it) }

        // 3. 被混淆的方法：交给 DexKit 按字符串特征定位
        runCatching { hookObfuscatedByDexKit(lpparam) }.onFailure { LogHelper.ex(it) }
    }

    /**
     * 主题管理器里两个关键点已被混淆，方法名每次发版都变，
     * 这里用 DexKit 按「方法体内出现的字符串」反查：
     *
     * - DrmResult：DRM 校验结果的封装处，强置 DRM_SUCCESS 即破解
     * - LargeIcon：大图标（large_icons）授权校验，提前造一个空的 .mra 让它认为已授权
     */
    private fun hookObfuscatedByDexKit(lpparam: XC_LoadPackage.LoadPackageParam) {
        System.loadLibrary("dexkit")

        val bridge = DexKitBridge.create(lpparam.appInfo.sourceDir)
        if (bridge == null || !bridge.isValid) {
            LogHelper.e("FTM: DexKit 初始化失败")
            return
        }

        bridge.use {
            val found = it.batchFindMethodsUsingStrings(
                BatchFindArgs.builder()
                    .queryMap(
                        mapOf(
                            "DrmResult" to setOf(
                                "theme",
                                "ThemeManagerTag",
                                "/system",
                                "check rights isLegal:"
                            ),
                            "LargeIcon" to setOf(
                                "apply failed",
                                "/data/system/theme/large_icons/",
                                "default_large_icon_product_id",
                                "largeicons",
                                "relativePackageList is empty"
                            )
                        )
                    )
                    .build()
            )

            // 3.1 DRM 校验结果：背屏窗口内放行真实校验（原 APK 取第一个匹配方法）
            found["DrmResult"]?.firstOrNull()
                ?.getMethodInstance(lpparam.classLoader)
                ?.let { method ->
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (RearScreenState.isRearActive()) {
                                LogHelper.d("FTM: DrmResult SKIP (rear window) -> real DRM")
                            } else {
                                LogHelper.d("FTM: DrmResult AFTER (forced)")
                                param.result = DrmManager.DrmResult.DRM_SUCCESS
                            }
                        }
                    })
                } ?: LogHelper.e("FTM: DexKit 未定位到 DrmResult 方法")

            // 3.2 大图标授权：预置空的授权文件
            found["LargeIcon"]?.firstOrNull()
                ?.getMethodInstance(lpparam.classLoader)
                ?.let { method ->
                    XposedBridge.hookMethod(method, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            LogHelper.d("FTM: large_icons BEFORE")
                            runCatching { prepareLargeIconRights(lpparam, param.thisObject) }
                                .onFailure { t -> LogHelper.ex(t) }
                        }
                    })
                } ?: LogHelper.e("FTM: DexKit 未定位到 LargeIcon 方法")
        }
    }

    /**
     * 在 .data/rights/theme/ 下造一个空的 {productId}-largeicons.mra，
     * 让大图标的授权检查直接通过。
     */
    private fun prepareLargeIconRights(
        lpparam: XC_LoadPackage.LoadPackageParam,
        thisObject: Any
    ) {
        val resourceClass = Class.forName(
            "com.android.thememanager.basemodule.resource.model.Resource",
            false,
            lpparam.classLoader
        )
        val field = FieldFinder.fromClass(thisObject.javaClass)
            .filterByType(resourceClass)
            .first()

        val resource = XposedHelpers.getObjectField(thisObject, field.name)
        val productId = resource?.let { res -> XposedHelpers.callMethod(res, "getProductId") }

        // 原 APK 为字符串拼接：.../theme/.data/rights/theme/{productId}-largeicons.mra
        val file = File(
            "/storage/emulated/0/Android/data/com.android.thememanager/files/MIUI/theme/.data/rights/theme",
            "$productId-largeicons.mra"
        )
        file.parentFile?.let { parent ->
            if (!parent.exists()) parent.mkdirs()
        }
        file.createNewFile()
    }

    // ------------------------------------------------------------- com.miui.home
    private fun hookHome(lpparam: XC_LoadPackage.LoadPackageParam) {
        runCatching {
            LogHelper.d("FTM: applying hooks for com.miui.home")

            MethodFinder.fromClass(lpparam.loadClass("com.miui.maml.widget.edit.MamlutilKt"))
                .filterByName("themeManagerSupportPaidWidget")
                .returnConstant(false)

            MethodFinder.fromClass(lpparam.loadClass("com.miui.home.launcher.gadget.MaMlPendingHostView"))
                .filterByName("isCanAutoStartDownload")
                .returnConstant(true)
        }.onFailure { LogHelper.ex(it) }
    }

    // ------------------------------------------------------------------ helpers

    private fun XC_LoadPackage.LoadPackageParam.loadClass(name: String): Class<*> =
        ClassUtils.loadClass(name, classLoader)

    /** 第一个匹配方法（对应原 APK 的 MethodFinder.first） */
    private fun MethodFinder.firstMethod(): Method = toList().first()

    /** 全部匹配方法：调用前执行 block（对应原 APK 的 toList 遍历） */
    private fun MethodFinder.beforeAll(
        block: (XC_MethodHook.MethodHookParam) -> Unit
    ): List<XC_MethodHook.Unhook> = toList().map { method ->
        XposedBridge.hookMethod(method, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) = block(param)
        })
    }

    /** 全部匹配方法：调用后执行 block */
    private fun MethodFinder.afterAll(
        block: (XC_MethodHook.MethodHookParam) -> Unit
    ): List<XC_MethodHook.Unhook> = toList().map { method ->
        XposedBridge.hookMethod(method, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) = block(param)
        })
    }

    /** 第一个匹配方法：直接返回常量，原方法体不执行 */
    private fun MethodFinder.returnConstant(value: Any?): XC_MethodHook.Unhook =
        XposedBridge.hookMethod(firstMethod(), object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                param.result = value
            }
        })

    /** 第一个匹配方法：挂一个自定义钩子（用于需要区分调用来源的场景） */
    private fun MethodFinder.hookFirst(hook: XC_MethodHook): XC_MethodHook.Unhook =
        XposedBridge.hookMethod(firstMethod(), hook)
}
