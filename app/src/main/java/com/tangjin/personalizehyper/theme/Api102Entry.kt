package com.tangjin.personalizehyper.theme

import android.os.Build
import androidx.annotation.Keep
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.luckypray.dexkit.DexKitBridge
import io.luckypray.dexkit.builder.BatchFindArgs
import miui.drm.DrmManager
import miui.drm.ThemeReceiver
import java.io.File

/**
 * libxposed 102 模块入口。
 *
 * ── 与 legacy（XposedInit : IXposedHookLoadPackage）的对应关系 ─────────
 * legacy 的 handleLoadPackage(lpparam) 按包名分发到四个 hook 函数；libxposed 102 改为
 * 生命周期回调：
 *   - 普通 App（主题管理器 / 桌面 / 智能助理）走 [onPackageReady]，从中拿到宿主 classloader；
 *   - 系统框架（android 包 = system_server）走 [onSystemServerStarting]，同样拿到 classloader。
 * 这样每个 hook 都能用「宿主真实 Class」去定位方法，比 legacy 用桩 Class 传
 * XposedBridge.hookMethod 更稳。
 *
 * 四个作用域的破解语义与 legacy 完全一致，未做任何业务逻辑改动：
 *   - android       ：ThemeReceiver.validateTheme 校验期间临时放行 DrmManager.isLegal。
 *   - personalassistant：付费 Maml 小组件相关判断改常量；区分背屏放行真实逻辑。
 *   - thememanager ：OnlineResourceDetail.toResource 强置 bought=true（背屏除外）；
 *                    DiscountPriceView 去角标；DexKit 定位 DrmResult / LargeIcon。
 *   - home         ：MamlutilKt / MaMlPendingHostView 相关判断改常量。
 */
@Keep
class Api102Entry : XposedModule() {

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        // 把框架日志接口注入 LogHelper，使 hook 内的日志能落盘到 LSPosed 日志文件。
        LogHelper.initLogger { prio, msg -> log(prio, LogHelper.TAG, msg) }
        LogHelper.d("FTM: module loaded | process=${param.processName} systemServer=${param.isSystemServer}")
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        LogHelper.initLogger { prio, msg -> log(prio, LogHelper.TAG, msg) }
        when (val pkg = param.packageName) {
            "com.miui.personalassistant" -> hookPersonalAssistant(param.classLoader)
            "com.android.thememanager" -> hookThemeManager(param)
            "com.miui.home" -> hookHome(param.classLoader)
            else -> { /* 其它包不处理，保持作用域外零干扰 */ }
        }
    }

    override fun onSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        LogHelper.initLogger { prio, msg -> log(prio, LogHelper.TAG, msg) }
        hookSystemFramework(param.classLoader)
    }

    // ---------------------------------------------------------------- android
    // 系统框架：只在 ThemeReceiver.validateTheme 校验期间临时放行 DrmManager.isLegal，
    // 校验结束立刻摘钩，避免影响系统其它 DRM 逻辑。
    private fun hookSystemFramework(loader: ClassLoader) {
        runCatching {
            LogHelper.d("FTM: applying hooks for android (system server)")

            val themeReceiverClass = Xp.loadClass(loader, "miui.drm.ThemeReceiver")
            val drmManagerClass = Xp.loadClass(loader, "miui.drm.DrmManager")
            val validate = Xp.findMethod(themeReceiverClass, "validateTheme")
            val isLegalMethods = Xp.findMethods(drmManagerClass, "isLegal")

            hook(validate).intercept { chain ->
                LogHelper.d("FTM: ThemeReceiver.validateTheme BEFORE")
                // 校验期间：所有 isLegal 重载一律返回 DRM_SUCCESS（不调原方法）
                val isLegalHooks = isLegalMethods.map { m ->
                    hook(m).intercept { _ -> DrmManager.DrmResult.DRM_SUCCESS }
                }
                val result = chain.proceed()
                isLegalHooks.forEach { it.unhook() }
                LogHelper.d("FTM: ThemeReceiver.validateTheme AFTER")
                result
            }
        }.onFailure { LogHelper.ex(it) }
    }

    // ------------------------------------------------- com.miui.personalassistant
    // 智能助理：付费小组件（Maml）相关判断全部改常量；
    // shouldCheckMamlBoughtState / isTargetPositionMamlPayAndDownloading 需要区分背屏，
    // 单独走 RearScreenMamlSkip（调用栈里带 rearscreen 时放行真实逻辑）。
    private fun hookPersonalAssistant(loader: ClassLoader) {
        runCatching {
            LogHelper.d("FTM: applying hooks for com.miui.personalassistant")

            val vmClass =
                Xp.loadClass(loader, "com.miui.personalassistant.picker.business.detail.PickerDetailViewModel")
            val respClass =
                Xp.loadClass(loader, "com.miui.personalassistant.picker.business.detail.bean.PickerDetailResponse")
            val wrapperClass =
                Xp.loadClass(loader, "com.miui.personalassistant.picker.business.detail.bean.PickerDetailResponseWrapper")
            val mamlUtilClass =
                Xp.loadClass(loader, "com.miui.maml.widget.edit.MamlutilKt")

            hookReturnConstant(Xp.findMethod(mamlUtilClass, "themeManagerSupportPaidWidget"), false)
            hookReturnConstant(Xp.findMethod(vmClass, "isCanDirectAddMaMl"), true)
            hookReturnConstant(
                Xp.findMethod(
                    Xp.loadClass(
                        loader,
                        "com.miui.personalassistant.picker.business.detail.utils.PickerDetailDownloadManager\$Companion"
                    ),
                    "isCanDownload"
                ),
                true
            )
            hookReturnConstant(
                Xp.findMethod(
                    Xp.loadClass(
                        loader,
                        "com.miui.personalassistant.picker.business.detail.utils.PickerDetailUtil"
                    ),
                    "isCanAutoDownloadMaMl"
                ),
                true
            )
            hookReturnConstant(Xp.findMethod(respClass, "isPay"), false)
            hookReturnConstant(Xp.findMethod(respClass, "isBought"), true)
            hookReturnConstant(Xp.findMethod(wrapperClass, "isPay"), false)
            hookReturnConstant(Xp.findMethod(wrapperClass, "isBought"), true)

            hookWithHooker(Xp.findMethod(vmClass, "shouldCheckMamlBoughtState"), RearScreenMamlSkip.hooker())
            hookWithHooker(Xp.findMethod(vmClass, "isTargetPositionMamlPayAndDownloading"), RearScreenMamlSkip.hooker())

            hookReturnConstant(
                Xp.findMethod(vmClass, "checkIsIndependentProcessWidgetForPosition"),
                true
            )
        }.onFailure { LogHelper.ex(it) }
    }

    // ------------------------------------------------- com.android.thememanager
    private fun hookThemeManager(param: XposedModuleInterface.PackageReadyParam) {
        val loader = param.classLoader
        runCatching { LogHelper.d("FTM: applying hooks for com.android.thememanager") }

        // 1. 详情转 Resource 时把 bought 置 true；背屏主题除外（强置会导致应用失败）。
        runCatching {
            val detailClass =
                Xp.loadClass(loader, "com.android.thememanager.detail.theme.model.OnlineResourceDetail")
            val toResourceMethods = Xp.findMethods(detailClass, "toResource")
            hookAfterAll(toResourceMethods) { chain ->
                val obj = chain.getThisObject() ?: return@hookAfterAll
                val category =
                    runCatching { Xp.findField(obj.javaClass, "category").get(obj) as? String }.getOrNull()
                if (category == null || !category.lowercase().contains("rear")) {
                    LogHelper.d("FTM: OnlineResourceDetail.toResource AFTER (force bought=true)")
                    runCatching { Xp.findField(obj.javaClass, "bought").set(obj, true) }
                } else {
                    LogHelper.d("FTM: OnlineResourceDetail.toResource SKIP force bought (rear screen)")
                    RearScreenState.markRear()
                }
            }
        }.onFailure { LogHelper.ex(it) }

        // 2. 折扣价显示：把 DiscountPriceView 自身「(int,int)->void」方法的第二个 int 参数置 0，去掉付费角标。
        //    （原 APK 不按方法名筛选，凡符合签名的全部 hook，这里沿用同样语义。）
        //    ⚠️ 关键坑：Xp.findMethods 默认会向上遍历父类，而 DiscountPriceView 继承自 android.view.View，
        //       View 上也有 (int,int)->void 的方法（setMeasuredDimension / scrollTo / scrollBy）。
        //       一旦把这些 framework 方法也 hook 并强制第二参数为 0，会导致主题管理器进程里所有 View
        //       测量高度归零、布局全崩 → 整页黑屏。因此这里必须排除 android.* / java.* / kotlin.* 等框架类，
        //       只 hook 应用自身（com.android.thememanager / miui.*）声明的方法。
        runCatching {
            val priceClass =
                Xp.loadClass(loader, "com.android.thememanager.basemodule.views.DiscountPriceView")
            val priceMethods = Xp.findMethods(
                priceClass,
                paramCount = 2,
                paramTypes = arrayOf(Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!),
                returnType = Void::class.javaPrimitiveType
            ).filter { m ->
                val dn = m.declaringClass.name
                !dn.startsWith("android.") && !dn.startsWith("java.") &&
                    !dn.startsWith("kotlin.") && !dn.startsWith("dalvik.") && !dn.startsWith("javax.")
            }
            if (priceMethods.isEmpty()) {
                LogHelper.e("FTM: DiscountPriceView 未找到可 hook 的 (int,int)->void 方法")
            } else {
                LogHelper.d("FTM: DiscountPriceView hook 命中 ${priceMethods.size} 个方法：${priceMethods.map { it.name }.distinct()}")
            }
            priceMethods.forEach { m ->
                hookBeforeModifyArgs(m) { args ->
                    LogHelper.d("FTM: DiscountPriceView BEFORE (${m.name})")
                    args.copyOf().also { it[1] = 0 }
                }
            }
        }.onFailure { LogHelper.ex(it) }

        runCatching {
            val mamlUtilClass =
                Xp.loadClass(loader, "com.miui.maml.widget.edit.MamlutilKt")
            hookReturnConstant(Xp.findMethod(mamlUtilClass, "themeManagerSupportPaidWidget"), false)
        }.onFailure { LogHelper.ex(it) }

        // 3. 被混淆的方法：交给 DexKit 按字符串特征定位
        runCatching { hookObfuscatedByDexKit(loader, param.applicationInfo.sourceDir) }.onFailure { LogHelper.ex(it) }
    }

    /**
     * 主题管理器里两个关键点已被混淆，方法名每次发版都变，
     * 这里用 DexKit 按「方法体内出现的字符串」反查：
     *   - DrmResult：DRM 校验结果的封装处，强置 DRM_SUCCESS 即破解
     *   - LargeIcon：大图标（large_icons）授权校验，提前造一个空的 .mra 让它认为已授权
     *
     * 注意：libdexkit.so 必须等模块在宿主进程里加载后再 System.loadLibrary，
     * 不能在类初始化阶段加载（REAREye 的 DexKitBootstrap 也是这个教训）。
     */
    private fun hookObfuscatedByDexKit(loader: ClassLoader, sourceDir: String) {
        System.loadLibrary("dexkit")

        val bridge = DexKitBridge.create(sourceDir)
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
                ?.getMethodInstance(loader)
                ?.let { method ->
                    hook(method).intercept { chain ->
                        if (RearScreenState.isRearActive()) {
                            LogHelper.d("FTM: DrmResult SKIP (rear window) -> real DRM")
                            chain.proceed()
                        } else {
                            LogHelper.d("FTM: DrmResult AFTER (forced)")
                            DrmManager.DrmResult.DRM_SUCCESS
                        }
                    }
                } ?: LogHelper.e("FTM: DexKit 未定位到 DrmResult 方法")

            // 3.2 大图标授权：预置空的授权文件
            found["LargeIcon"]?.firstOrNull()
                ?.getMethodInstance(loader)
                ?.let { method ->
                    hook(method).intercept { chain ->
                        LogHelper.d("FTM: large_icons BEFORE")
                        runCatching { prepareLargeIconRights(chain.getThisObject(), loader) }
                            .onFailure { t -> LogHelper.ex(t) }
                        chain.proceed()
                    }
                } ?: LogHelper.e("FTM: DexKit 未定位到 LargeIcon 方法")
        }
    }

    /**
     * 在 .data/rights/theme/ 下造一个空的 {productId}-largeicons.mra，
     * 让大图标的授权检查直接通过。
     */
    private fun prepareLargeIconRights(thisObject: Any?, loader: ClassLoader) {
        val resourceClass =
            Xp.loadClass(loader, "com.android.thememanager.basemodule.resource.model.Resource")
        val field = Xp.findFieldByType(thisObject!!.javaClass, resourceClass)
        val resource = field.get(thisObject)
        val productId = resource?.let { res ->
            runCatching { resourceClass.getMethod("getProductId").invoke(res) }
                .getOrNull()
        }

        // 原 APK 为字符串拼接：.../theme/.data/rights/theme/{productId}-largeicons.mra
        val file = File(
            "/storage/emulated/0/Android/data/com.android.thememanager/files/MIUI/theme/.data/rights/theme",
            "$productId-largeicons.mra"
        )
        file.parentFile?.let { parent -> if (!parent.exists()) parent.mkdirs() }
        file.createNewFile()
        LogHelper.d("FTM: large_icons rights ready -> ${file.name} (exists=${file.exists()})")
    }

    // ------------------------------------------------------------- com.miui.home
    private fun hookHome(loader: ClassLoader) {
        runCatching {
            LogHelper.d("FTM: applying hooks for com.miui.home")

            val mamlUtilClass =
                Xp.loadClass(loader, "com.miui.maml.widget.edit.MamlutilKt")
            hookReturnConstant(Xp.findMethod(mamlUtilClass, "themeManagerSupportPaidWidget"), false)

            val pendingHostClass =
                Xp.loadClass(loader, "com.miui.home.launcher.gadget.MaMlPendingHostView")
            hookReturnConstant(Xp.findMethod(pendingHostClass, "isCanAutoStartDownload"), true)
        }.onFailure { LogHelper.ex(it) }
    }
}
