package com.tangjin.personalizehyper.theme

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * 自写的轻量反射 + hook 工具，替代 EzXHelper / XposedHelpers。
 *
 * ── 为什么不用 EzXHelper ──────────────────────────────────────────────
 * EzXHelper 只支持 legacy 的 `de.robv.android.xposed` 体系（IXposedHookLoadPackage /
 * XC_MethodHook / XposedBridge），在 libxposed 102 下完全不可用。本项目切换到
 * io.github.libxposed 新版 API 后，EzXHelper 的 hook 扩展（createHook）在 Kotlin 侧
 * 也调不到，因此 hook 一律走 libxposed 原生 API（见下方的 DSL 扩展），方法 / 字段查找
 * 用本文件的纯反射封装，零额外依赖。
 *
 * ── 设计要点 ─────────────────────────────────────────────────────────
 * 1. 所有查找都基于「宿主 classloader 加载出来的真实 Class」，不依赖模块桩 jar 的 Class
 *    对象去 hook —— 这是 libxposed 下最稳的做法（legacy 用桩 Class 传 XposedBridge.hookMethod
 *    属于「靠类名在目标运行时重新定位」，能跑但脆弱）。
 * 2. findMethod / findField 会向上遍历父类，等价于 EzXHelper 的查找语义。
 * 3. hook DSL 是 libxposed 102 的 interceptor 模式封装：
 *      - 不调 chain.proceed() 而直接返回某值  => 等效 legacy 的「before 里 set result」，跳过原方法
 *      - 先 chain.proceed() 再改结果          => 等效 legacy 的 afterHookedMethod
 *      - 先改参数再 chain.proceed(新参数)      => 等效 legacy 的 beforeHookedMethod 改 args
 */

/** 纯反射查找工具（基于真实 Class）。 */
object Xp {

    /**
     * 判断一个类是不是 AOSP / Kotlin / JVM 框架类。
     * 遍历父类查找方法时，必须跳过这些框架类 —— 否则会误 hook 到 android.view.View
     * 等框架方法（例如 DiscountPriceView 继承自 View，而 View 上也有 (int,int)->void 的
     * setMeasuredDimension / scrollTo / scrollBy；一旦被强制改参，会导致宿主整页布局崩坏 / 黑屏）。
     * miui.* / com.android.thememanager.* 等「厂商 / 应用」类不算框架，照常匹配。
     */
    private fun isFrameworkClass(c: Class<*>): Boolean {
        val n = c.name
        return n.startsWith("android.") || n.startsWith("java.") ||
            n.startsWith("kotlin.") || n.startsWith("dalvik.") || n.startsWith("javax.")
    }

    /** 用宿主 classloader 加载类。 */
    fun loadClass(loader: ClassLoader, name: String): Class<*> = loader.loadClass(name)

    /** 全部同名方法（含父类，但跳过 framework 类），legacy MethodFinder 默认也搜父类。 */
    fun findMethods(clazz: Class<*>, name: String): List<Method> {
        val out = mutableListOf<Method>()
        var c: Class<*>? = clazz
        while (c != null) {
            if (!isFrameworkClass(c)) {
                out += c.declaredMethods.filter { it.name == name }
            }
            c = c.superclass
        }
        return out
    }

    /** 按 参数个数 + 参数类型 + 返回类型 查找（不约束方法名）。参数类型 / 返回类型为 null 表示不约束。 */
    fun findMethods(
        clazz: Class<*>,
        paramCount: Int,
        paramTypes: Array<Class<*>>? = null,
        returnType: Class<*>? = null
    ): List<Method> {
        val out = mutableListOf<Method>()
        var c: Class<*>? = clazz
        while (c != null) {
            if (!isFrameworkClass(c)) {
                out += c.declaredMethods.filter { m ->
                    m.parameterTypes.size == paramCount &&
                        (paramTypes == null || m.parameterTypes.contentEquals(paramTypes)) &&
                        (returnType == null || m.returnType == returnType)
                }
            }
            c = c.superclass
        }
        return out
    }

    /** 第一个同名方法（含父类）。对应原 APK 的 MethodFinder.first。 */
    fun findMethod(clazz: Class<*>, name: String): Method =
        findMethods(clazz, name).firstOrNull()
            ?: throw NoSuchMethodException("no method $name in $clazz")

    /** 按 参数个数 + 参数类型 + 返回类型 精准定位（参数类型 / 返回类型为 null 表示不约束）。 */
    fun findMethod(
        clazz: Class<*>,
        name: String,
        paramCount: Int,
        paramTypes: Array<Class<*>>? = null,
        returnType: Class<*>? = null
    ): Method {
        return findMethods(clazz, name).firstOrNull { m ->
            m.parameterTypes.size == paramCount &&
                (paramTypes == null || m.parameterTypes.contentEquals(paramTypes)) &&
                (returnType == null || m.returnType == returnType)
        } ?: throw NoSuchMethodException("no matching method $name(${paramTypes?.contentToString()}) in $clazz")
    }

    /** 按字段名查找（含父类）。 */
    fun findField(clazz: Class<*>, name: String): Field {
        var c: Class<*>? = clazz
        while (c != null) {
            c.declaredFields.firstOrNull { it.name == name }?.let {
                it.isAccessible = true   // 宿主字段几乎全是 private，不设必抛 IllegalAccessException
                return it
            }
            c = c.superclass
        }
        throw NoSuchFieldException("no field $name in $clazz")
    }

    /** 按字段类型查找第一个匹配字段（含父类）。 */
    fun findFieldByType(clazz: Class<*>, type: Class<*>): Field {
        var c: Class<*>? = clazz
        while (c != null) {
            c.declaredFields.firstOrNull { it.type == type }?.let {
                it.isAccessible = true   // 同上：private 字段必须先开放访问权限
                return it
            }
            c = c.superclass
        }
        throw NoSuchFieldException("no field of type $type in $clazz")
    }
}

/**
 * libxposed 102 的 hook DSL（扩展在 XposedModule 上，因为 hook() 是 XposedInterface 的成员）。
 */

/** 直接返回常量并跳过原方法体（等效 legacy 的 beforeHookedMethod 里 set result）。 */
fun XposedModule.hookReturnConstant(method: Method, value: Any?): XposedInterface.HookHandle =
    hook(method).intercept { _ -> value }

/** 对全部匹配方法：调用前执行 block（可读取 chain），然后 proceed 原方法。 */
fun XposedModule.hookBeforeAll(
    methods: List<Method>,
    block: (XposedInterface.Chain) -> Unit
): List<XposedInterface.HookHandle> =
    methods.map { m ->
        hook(m).intercept { chain -> block(chain); chain.proceed() }
    }

/** 对全部匹配方法：先 proceed 原方法，再执行 block（可读取 / 修改 chain 状态），最终返回原方法的真实结果。 */
fun XposedModule.hookAfterAll(
    methods: List<Method>,
    block: (XposedInterface.Chain) -> Unit
): List<XposedInterface.HookHandle> =
    methods.map { m ->
        hook(m).intercept { chain -> val r = chain.proceed(); block(chain); r }
    }

/** 调用前修改参数（block 返回新参数数组），再 proceed。用于 DiscountPriceView 这类改 args 的场景。 */
fun XposedModule.hookBeforeModifyArgs(
    method: Method,
    block: (Array<Any?>) -> Array<Any?>
): XposedInterface.HookHandle =
    hook(method).intercept { chain ->
        chain.proceed(block(chain.getArgs().toTypedArray()))
    }

/** 用自定义 Hooker 挂第一个匹配方法（用于需要区分调用来源的场景，如 RearScreenMamlSkip）。 */
fun XposedModule.hookWithHooker(
    method: Method,
    hooker: XposedInterface.Hooker
): XposedInterface.HookHandle = hook(method).intercept(hooker)

/** 取 Executable 的更友好名字（日志用）。 */
val Executable.friendlyName: String
    get() = "${declaringClass.name}#$name(${parameterTypes.joinToString(",") { it.simpleName }})"
