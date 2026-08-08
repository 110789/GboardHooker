package io.github.a110789.gboardhooker.hook

import android.content.Context
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 原框架 `HookScope` 的独立版替代品。
 *
 * 只留 gboard 那几个文件实际用到的东西：classloader、进程信息、APK 路径、
 * app Context、日志、以及读设置的三个便捷方法。不再有
 * `HookFeature`/`HookOption`/`HookPreset` 那一整套声明式外壳 —— 这些在独立模块里
 * 由 [io.github.a110789.gboardhooker.ui.SettingsActivity] 直接读写
 * `SharedPreferences` 代替。
 *
 * [appContext] 由 [HookEntry] 在 `Application.attach(Context)` 之后才构造出这个
 * 对象时传入——早前用 `ActivityThread.currentApplication()` 反射猜的做法在实测中
 * 会返回 null：`handleLoadPackage` 触发的时机比想象的早得多，是在
 * `LoadedApk.getClassLoader()` 阶段，Application 对象都还没创建。等到 attach() 真正
 * 被调用时，传进来的 Context 已经是可用的了，不用再猜。
 */
internal class HookCtx(private val lpparam: XC_LoadPackage.LoadPackageParam, private val appContext: Context) {

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
            val pm = appContext.packageManager
            val info = pm.getPackageInfo(packageName, 0)
            @Suppress("DEPRECATION")
            if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        }.getOrDefault(0L)
    }

    fun classOrNull(name: String): Class<*>? =
        runCatching { Class.forName(name, false, classLoader) }.getOrNull()

    /** 一定非空——只有 attach() 之后才会构造出这个对象。 */
    fun appContextOrNull(): Context? = appContext

    fun isEnabled(featureKey: String, default: Boolean): Boolean = prefs.isEnabled(featureKey, default)

    fun int(key: String, default: Int): Int = prefs.int(key, default)
}
