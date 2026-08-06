package io.github.a110789.gboardhooker.hook

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import java.lang.reflect.Constructor
import java.lang.reflect.Member
import java.lang.reflect.Method

/**
 * 原代码用的 `io.github.lingqiqi5211.ezhooktool` 是宿主框架另带的一个小 DSL 库，
 * 独立模块不再引入这个额外依赖，这里用原生 `XposedBridge` 直接实现等价的两个方法：
 *
 * - [Method.createReplaceHook]：整个替换方法体，等价于原来的 `createReplaceHook`。
 * - [Member.createAfterHook]：方法/构造执行完之后再跑一段逻辑，等价于
 *   `createAfterHook`，`Method` 和 `Constructor` 都能用。
 *
 * 两者都吞掉 [block] 抛出的异常并打日志，而不是让它们冒泡回目标 App —— 目标进程里
 * 未捕获的异常会直接表现成「Gboard 崩溃」，而不是「这个 hook 没生效」，排查起来
 * 完全是两回事。
 */

internal fun Method.createReplaceHook(tag: String, block: (param: XC_MethodHook.MethodHookParam) -> Any?) {
    isAccessible = true
    XposedBridge.hookMethod(
        this,
        object : XC_MethodReplacement() {
            override fun replaceHookedMethod(param: MethodHookParam): Any? {
                return try {
                    block(param)
                } catch (t: Throwable) {
                    Log.e("[$tag] 替换逻辑抛出异常", t)
                    // 替换失败时不再调用原方法（原方法体已被整体接管，
                    // 没有「原始实现」可退回），交回一个安全的空结果由调用方兜底。
                    null
                }
            }
        },
    )
}

internal fun Member.createAfterHook(tag: String, block: (param: XC_MethodHook.MethodHookParam) -> Unit) {
    (this as? java.lang.reflect.AccessibleObject)?.isAccessible = true
    val hook = object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            try {
                block(param)
            } catch (t: Throwable) {
                Log.e("[$tag] after-hook 逻辑抛出异常", t)
            }
        }
    }
    when (this) {
        is Method -> XposedBridge.hookMethod(this, hook)
        is Constructor<*> -> XposedBridge.hookMethod(this, hook)
        else -> Log.w("[$tag] 不支持的成员类型：$this")
    }
}

/** [XC_MethodHook.MethodHookParam.thisObject]，取不到时给 null 而不是抛异常。 */
internal val XC_MethodHook.MethodHookParam.thisObjectOrNull: Any?
    get() = runCatching { thisObject }.getOrNull()
