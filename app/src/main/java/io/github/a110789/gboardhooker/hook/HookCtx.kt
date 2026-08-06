package io.github.a110789.gboardhooker.hook

import android.content.Context
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 原框架 `HookScope` 的独立版替代品。
 *
 * 只留 gboard 那几个文件实际用到的东西：classloader、进程信息、APK 路径、
 * 拿 app Context 的入口、日志、以及读设置的三个便捷方法。不再有
 * `HookFeature`/`HookOption`/`HookPreset` 那一整套声明式外壳 —— 这些在独立模块里
 * 由 [io.github.a110789.gboardhooker.ui.SettingsActivity] 直接读写
 * `SharedPreferences` 代替。
 */
internal class HookCtx(private val lpparam: XC_LoadPackage.LoadPackageParam) {

    val classLoader: ClassLoader = lpparam.classLoader
    val processName: String = lpparam.processName
    val packageName: String = lpparam.packageName
    val isMainProcess: Boolean = processName == packageName
    val apkPath: String? = lpparam.appInfo?.sourceDir

    val log = Log

    private val prefs = Prefs(HookEntry.MODULE_PACKAGE)

    /** dex 扫描结果按 versionCode 缓存，取不到就返回 0，效果是「每次都当没缓存」。 */
    val versionCode: Long by lazy {
        runCatching {
            val ctx = appContextOrNull() ?: return@runCatching 0L
            val pm = ctx.packageManager
            val info = pm.getPackageInfo(packageName, 0)
            @Suppress("DEPRECATION")
            if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        }.getOrDefault(0L)
    }

    fun classOrNull(name: String): Class<*>? =
        runCatching { Class.forName(name, false, classLoader) }.getOrNull()

    /**
     * 拿目标 App 的 [Context]。
     *
     * 用的是常见的 `ActivityThread.currentApplication()` 反射技巧，不去挂
     * `Application.attach`——两个功能都只在剪贴板面板真正被打开、或键盘初始化时
     * 才用到 Context，那时候宿主早就跑过自己的 `Application.onCreate` 了。
     */
    fun appContextOrNull(): Context? = runCatching {
        val at = Class.forName("android.app.ActivityThread")
        val currentApplication = at.getMethod("currentApplication")
        currentApplication.invoke(null) as? Context
    }.getOrNull()

    fun isEnabled(featureKey: String, default: Boolean): Boolean = prefs.isEnabled(featureKey, default)

    fun int(key: String, default: Int): Int = prefs.int(key, default)

    fun string(key: String): String? = prefs.string(key, "").ifBlank { null }
}
