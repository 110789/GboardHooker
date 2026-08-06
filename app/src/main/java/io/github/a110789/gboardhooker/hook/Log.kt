package io.github.a110789.gboardhooker.hook

import de.robv.android.xposed.XposedBridge

/**
 * 统一的日志出口。
 *
 * 原框架的 `scope.log` 大概率也是包一层 [XposedBridge.log]，这里独立模块里
 * 直接自己写一个最小实现：所有日志都带 `[GboardHooker]` 前缀，方便在
 * LSPosed 管理器的日志页里用关键字过滤。
 */
internal object Log {
    private const val TAG = "[GboardHooker]"

    fun d(msg: String) = XposedBridge.log("$TAG D $msg")
    fun i(msg: String) = XposedBridge.log("$TAG I $msg")
    fun w(msg: String) = XposedBridge.log("$TAG W $msg")
    fun e(msg: String, t: Throwable? = null) {
        XposedBridge.log("$TAG E $msg")
        if (t != null) XposedBridge.log(t)
    }
}
