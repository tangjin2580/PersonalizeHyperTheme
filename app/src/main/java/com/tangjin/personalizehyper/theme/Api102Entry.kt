package com.tangjin.personalizehyper.theme

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.luckypray.dexkit.DexKitBridge
import io.luckypray.dexkit.builder.BatchFindArgs
import miui.drm.DrmManager
import miui.drm.ThemeReceiver
import java.lang.reflect.Method

/**
 * libxposed 102 模块入口（替代旧的 XposedInit / IXposedHookLoadPackage）。
 *
 * 作用域（见 res/values/array.xml）：
 * - 系统框架（onSystemServerStarting）
 * - com.miui.personalassistant 智能助理（背屏/桌面小组件）
 * - com.android.thememanager 主题壁纸
 * - com.miui.home 桌面
 *
 * 每个作用域一个 try/catch：宿主版本对不上时只让该分支失效，不连累其它作用域。
 *
 * 查找语义严格沿用原 APK：有的地方取「第一个匹配方法」，有的地方取「全部匹配方法」，
 * 因为重载数量不同会直接改变行为。所有查找都走 Xp.kt（自身类查找 + 框架类防御过滤）。
 */
class Api102Entry : XposedModule() {

    // 系统框架：在 onSystemServerStarting 里处理（libxposed 下 android 框架不走 onPackageLoaded）
    override fun onSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        hookSystemFramework()
    }

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        LogHelper.d(param.packageName)

        when (param.packageName) {
            "com.miui.personalassistant" -> hookPersonalAssistant(param)
            "com.android.thememanager" -> hookThemeManager(param)
            "com.miui.home" -> hookHome(param)
        }
    }

    // ------------------------------------------------------------ hook helpers
    // libxposed 的 hook 模型是拦截器：hook(method).intercept { intercept(chain) }，
    // chain.proceed() 调用原方法，可在前后读改 thisObject / args / 返回值。

    /** 用常量替换原方法（不调用原方法） */
    private fun hookReplace(method: Method, value: Any?): XposedInterface.HookHandle =
        hook(method).intercept(object : XposedInterface.Hooker {
            override fun intercept(chain: XposedInterface.Chain): Any? = value
        })

    /** 调用前修改参数（返回新参数数组，再用新参数继续原方法） */
    private fun hookBeforeModifyArgs(
        method: Method,
        block: (Array<Any?>) -> Array<Any?>
    ): XposedInterface.HookHandle =
        hook(method).intercept(object : XposedInterface.Hooker {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                val newArgs = block(chain.args.toTypedArray())
                return chain.proceed(newArgs)
            }
        })

    /** 调用后执行副作用（返回值原样透传） */
    private fun hookAfter(
        method: Method,
        block: (XposedInterface.Chain) -> Unit
    ): XposedInterface.HookHandle =
        hook(method).intercept(object : XposedInterface.Hooker {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                val r = chain.proceed()
                block(chain)
                return r
            }
        })

    /** 挂一个自定义拦截器（用于需要区分调用来源的场景） */
    private fun hookWith(method: Method, hooker: XposedInterface.Hooker): XposedInterface.HookHandle =
        hook(method).intercept(hooker)

    // ---------------------------------------------------------------- android
    // 系统框架：只在 ThemeReceiver.validateTheme 校验期间临时放行 DrmManager.isLegal，
    // 校验结束立刻摘钩，避免影响系统其它 DRM 逻辑。
    private fun hookSystemFramework() {
        runCatching {
            LogHelper.d("FTM: applying hooks for android")

            var unhooks: List<XposedInterface.HookHandle> = emptyList()

            val validate = ThemeReceiver::class.java.getDeclaredMethod("validateTheme")
            hook(validate).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    LogHelper.d("FTM: ThemeReceiver.validateTheme BEFORE")
                    // isLegal 可能有重载，原 APK 取全部
                    unhooks = Xp.findMethodsByName(DrmManager::class.java, "isLegal")
                        .map { m -> hookReplace(m, DrmManager.DrmResult.DRM_SUCCESS) }

                    val result = chain.proceed()

                    LogHelper.d("FTM: ThemeReceiver.validateTheme AFTER")
                    unhooks.forEach { it.unhook() }
                    unhooks = emptyList()
                    return result
                }
            })
        }.onFailure { LogHelper.ex(it) }
    }

    // ------------------------------------------------- com.miui.personalassistant
    // 智能助理：付费小组件（Maml）相关判断全部改常量；
    // shouldCheckMamlBoughtState / isTargetPositionMamlPayAndDownloading 需要区分背屏，
    // 单独走 RearScreenMamlSkip（调用栈里带 rearscreen 时放行真实逻辑）。
    private fun hookPersonalAssistant(param: XposedModuleInterface.PackageLoadedParam) {
        runCatching {
            LogHelper.d("FTM: applying hooks for com.miui.personalassistant")

            val cl = param.defaultClassLoader
            val load = { name: String -> cl.loadClass(name) }

            val vmClass = load("com.miui.personalassistant.picker.business.detail.PickerDetailViewModel")
            val respClass =
                load("com.miui.personalassistant.picker.business.detail.bean.PickerDetailResponse")
            val wrapperClass =
                load("com.miui.personalassistant.picker.business.detail.bean.PickerDetailResponseWrapper")

            Xp.findMethodByName(load("com.miui.maml.widget.edit.MamlutilKt"), "themeManagerSupportPaidWidget")
                ?.let { hookReplace(it, false) }

            Xp.findMethodByName(vmClass, "isCanDirectAddMaMl")?.let { hookReplace(it, true) }

            Xp.findMethodByName(
                load("com.miui.personalassistant.picker.business.detail.utils.PickerDetailDownloadManager\$Companion"),
                "isCanDownload"
            )?.let { hookReplace(it, true) }

            Xp.findMethodByName(
                load("com.miui.personalassistant.picker.business.detail.utils.PickerDetailUtil"),
                "isCanAutoDownloadMaMl"
            )?.let { hookReplace(it, true) }

            Xp.findMethodByName(respClass, "isPay")?.let { hookReplace(it, false) }
            Xp.findMethodByName(respClass, "isBought")?.let { hookReplace(it, true) }
            Xp.findMethodByName(wrapperClass, "isPay")?.let { hookReplace(it, false) }
            Xp.findMethodByName(wrapperClass, "isBought")?.let { hookReplace(it, true) }

            Xp.findMethodByName(vmClass, "shouldCheckMamlBoughtState")
                ?.let { hookWith(it, RearScreenMamlSkip()) }
            Xp.findMethodByName(vmClass, "isTargetPositionMamlPayAndDownloading")
                ?.let { hookWith(it, RearScreenMamlSkip()) }

            Xp.findMethodByName(vmClass, "checkIsIndependentProcessWidgetForPosition")
                ?.let { hookReplace(it, true) }
        }.onFailure { LogHelper.ex(it) }
    }

    // ------------------------------------------------- com.android.thememanager
    private fun hookThemeManager(param: XposedModuleInterface.PackageLoadedParam) {
        val cl = param.defaultClassLoader
        val load = { name: String -> cl.loadClass(name) }

        runCatching { LogHelper.d("FTM: applying hooks for com.android.thememanager") }

        // 1. 详情转 Resource 时把 bought 置 true；背屏主题除外（强置会导致应用失败）。
        //    toResource 取全部匹配方法。
        runCatching {
            val onlineResourceDetail =
                load("com.android.thememanager.detail.theme.model.OnlineResourceDetail")
            Xp.findMethodsByName(onlineResourceDetail, "toResource").forEach { m ->
                hookAfter(m) { chain ->
                    val obj = chain.thisObject ?: return@hookAfter
                    val category = Xp.getObjectField(obj, "category") as? String
                    if (category == null || !category.lowercase().contains("rear")) {
                        LogHelper.d("FTM: OnlineResourceDetail.toResource AFTER (force bought=true)")
                        Xp.setObjectField(obj, "bought", true)
                    } else {
                        LogHelper.d("FTM: OnlineResourceDetail.toResource SKIP force bought (rear screen)")
                        RearScreenState.markRear()
                    }
                }
            }
        }.onFailure { LogHelper.ex(it) }

        // 2. 折扣价显示：把第二个 int 参数置 0，去掉付费角标。取全部匹配方法。
        //    关键：Xp.findMethods 只查自身类 + 框架类防御过滤，不能误 hook android.view.View。
        runCatching {
            val priceClass = load("com.android.thememanager.basemodule.views.DiscountPriceView")
            val priceMethods = Xp.findMethods(
                priceClass,
                paramCount = 2,
                paramTypes = arrayOf(
                    Int::class.javaPrimitiveType!!,
                    Int::class.javaPrimitiveType!!
                ),
                returnType = Void::class.javaPrimitiveType
            )
            if (priceMethods.isEmpty()) {
                LogHelper.e("FTM: DiscountPriceView 未找到匹配的 (int,int)->void 方法")
            } else {
                LogHelper.d(
                    "FTM: DiscountPriceView hook 命中 ${priceMethods.size} 个方法：" +
                        "${priceMethods.map { it.name }.distinct()}"
                )
                priceMethods.forEach { m ->
                    hookBeforeModifyArgs(m) { args ->
                        LogHelper.d("FTM: DiscountPriceView BEFORE (${m.name})")
                        args.copyOf().also { it[1] = 0 }
                    }
                }
            }
        }.onFailure { LogHelper.ex(it) }

        runCatching {
            Xp.findMethodByName(load("com.miui.maml.widget.edit.MamlutilKt"), "themeManagerSupportPaidWidget")
                ?.let { hookReplace(it, false) }
        }.onFailure { LogHelper.ex(it) }

        // 3. 被混淆的方法：交给 DexKit 按字符串特征定位
        runCatching { hookObfuscatedByDexKit(param) }.onFailure { LogHelper.ex(it) }
    }

    /**
     * 主题管理器里两个关键点已被混淆，方法名每次发版都变，这里用 DexKit 按
     * 「方法体内出现的字符串」反查：
     * - DrmResult：DRM 校验结果的封装处，强置 DRM_SUCCESS 即破解
     * - LargeIcon：大图标（large_icons）授权校验，提前造一个空的 .mra 让它认为已授权
     */
    private fun hookObfuscatedByDexKit(param: XposedModuleInterface.PackageLoadedParam) {
        System.loadLibrary("dexkit")

        val bridge = DexKitBridge.create(param.applicationInfo.sourceDir)
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
                ?.getMethodInstance(param.defaultClassLoader)
                ?.let { method ->
                    hook(method).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            return if (RearScreenState.isRearActive()) {
                                LogHelper.d("FTM: DrmResult SKIP (rear window) -> real DRM")
                                chain.proceed()
                            } else {
                                LogHelper.d("FTM: DrmResult AFTER (forced)")
                                DrmManager.DrmResult.DRM_SUCCESS
                            }
                        }
                    })
                } ?: LogHelper.e("FTM: DexKit 未定位到 DrmResult 方法")

            // 3.2 大图标授权：预置空的授权文件
            found["LargeIcon"]?.firstOrNull()
                ?.getMethodInstance(param.defaultClassLoader)
                ?.let { method ->
                    hook(method).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            LogHelper.d("FTM: large_icons BEFORE")
                            runCatching { prepareLargeIconRights(param, chain.thisObject) }
                                .onFailure { t -> LogHelper.ex(t) }
                            return chain.proceed()
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
        param: XposedModuleInterface.PackageLoadedParam,
        thisObject: Any
    ) {
        val cl = param.defaultClassLoader
        val resourceClass = Class.forName(
            "com.android.thememanager.basemodule.resource.model.Resource",
            false,
            cl
        )
        val field = Xp.findFieldByType(thisObject.javaClass, resourceClass)
            ?: run {
                LogHelper.e("FTM: 未找到 Resource 类型字段")
                return
            }

        val resource = Xp.getObjectField(thisObject, field.name)
        val productId = resource?.let { res -> Xp.callMethod(res, "getProductId") }

        // 原 APK 为字符串拼接：.../theme/.data/rights/theme/{productId}-largeicons.mra
        val file = java.io.File(
            "/storage/emulated/0/Android/data/com.android.thememanager/files/MIUI/theme/.data/rights/theme",
            "$productId-largeicons.mra"
        )
        file.parentFile?.let { parent ->
            if (!parent.exists()) parent.mkdirs()
        }
        file.createNewFile()
    }

    // ------------------------------------------------------------- com.miui.home
    private fun hookHome(param: XposedModuleInterface.PackageLoadedParam) {
        runCatching {
            LogHelper.d("FTM: applying hooks for com.miui.home")

            val cl = param.defaultClassLoader
            Xp.findMethodByName(cl.loadClass("com.miui.maml.widget.edit.MamlutilKt"), "themeManagerSupportPaidWidget")
                ?.let { hookReplace(it, false) }

            Xp.findMethodByName(cl.loadClass("com.miui.home.launcher.gadget.MaMlPendingHostView"), "isCanAutoStartDownload")
                ?.let { hookReplace(it, true) }
        }.onFailure { LogHelper.ex(it) }
    }
}
