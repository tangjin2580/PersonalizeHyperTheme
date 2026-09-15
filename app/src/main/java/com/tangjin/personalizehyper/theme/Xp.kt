package com.tangjin.personalizehyper.theme

import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * 反射查找助手，替代 EzXHelper。
 *
 * 关键安全约束（血泪教训）：按签名/名字查找方法时**只查给定类自身声明的成员**，
 * 并且对 framework 父类（android.view.View 等）做防御性过滤。
 *
 * 早期版本曾向上遍历所有父类，导致把 android.view.View 的
 * (int,int)->void 方法（setMeasuredDimension / scrollTo / scrollBy）一并 hook，
 * 并强制第二个参数 = 0，使宿主所有 View 的测量高度归零、布局整体崩坏，
 * 表现就是「主题壁纸」整页黑屏。这里只在自身类查找 + 框架类防御过滤，
 * 从根上杜绝该问题（miui.* / com.android.* 等非框架类仍可正常匹配）。
 */
object Xp {

    /** framework 类不参与查找（避免误 hook android.view.View 等系统类） */
    private fun isFrameworkClass(c: Class<*>): Boolean {
        val n = c.name
        return n.startsWith("android.") || n.startsWith("java.") ||
            n.startsWith("kotlin.") || n.startsWith("dalvik.") || n.startsWith("javax.")
    }

    /** 只查自身声明的方法，按签名过滤（不向上遍历父类） */
    fun findMethods(
        clazz: Class<*>,
        paramCount: Int? = null,
        paramTypes: Array<Class<*>>? = null,
        returnType: Class<*>? = null
    ): List<Method> {
        if (isFrameworkClass(clazz)) return emptyList()
        return clazz.declaredMethods.filter { m ->
            (paramCount == null || m.parameterCount == paramCount) &&
                (paramTypes == null || m.parameterTypes.contentEquals(paramTypes)) &&
                (returnType == null || m.returnType == returnType)
        }
    }

    fun findMethodByName(clazz: Class<*>, name: String): Method? =
        clazz.declaredMethods.firstOrNull { it.name == name }

    fun findMethodsByName(clazz: Class<*>, name: String): List<Method> =
        clazz.declaredMethods.filter { it.name == name }

    /** 在类及其父类里按类型找一个字段（用于未知字段名的反射取值） */
    fun findFieldByType(clazz: Class<*>, type: Class<*>): Field? {
        var c: Class<*>? = clazz
        while (c != null) {
            c.declaredFields.firstOrNull { it.type == type }?.let { return it }
            c = c.superclass
        }
        return null
    }

    fun getObjectField(obj: Any, name: String): Any? =
        findField(obj.javaClass, name).get(obj)

    fun setObjectField(obj: Any, name: String, value: Any?) =
        findField(obj.javaClass, name).set(obj, value)

    fun callMethod(obj: Any, name: String, vararg args: Any?): Any? {
        val types = args.map { it?.javaClass ?: Any::class.java }.toTypedArray()
        val m = findMethod(obj.javaClass, name, *types) ?: throw NoSuchMethodException(name)
        m.isAccessible = true
        return m.invoke(obj, *args)
    }

    private fun findField(clazz: Class<*>, name: String): Field {
        var c: Class<*>? = clazz
        while (c != null) {
            c.declaredFields.firstOrNull { it.name == name }?.let { f ->
                f.isAccessible = true
                return f
            }
            c = c.superclass
        }
        throw NoSuchFieldException(name)
    }

    private fun findMethod(clazz: Class<*>, name: String, vararg types: Class<*>): Method? {
        var c: Class<*>? = clazz
        while (c != null) {
            c.declaredMethods.firstOrNull {
                it.name == name && it.parameterTypes.contentEquals(types)
            }?.let { return it }
            c = c.superclass
        }
        return null
    }
}
