package io.github.a110789.gboardhooker.hook.gboard

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import io.github.a110789.gboardhooker.hook.HookCtx
import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Method

/**
 * 按特征串在 dex 里找类和方法。
 *
 * Gboard 的剪贴板整包都被 R8 重命名过，但**每个类的 Flogger TAG 里留着原始全限定
 * 名**（`wbu.i("com/google/.../ClipboardAdapter")`），日志方法调用里还留着原始方法
 * 名（`"deleteExpiredItemsInternal"`）。这些串是 Google 内部日志系统的定位信息，
 * 跨版本不会变 —— 比任何混淆后的名字都稳。
 *
 * ## 为什么必须缓存
 *
 * Gboard 的 base.apk 有 88 MB、四个 dex，扫一遍要几百毫秒到数秒。而这段代码跑在
 * **输入法的启动路径**上 —— 用户点开输入框到键盘出现之间。所以扫描结果按
 * versionCode 缓存在目标自己的数据目录（模块的远端配置从被 hook 的进程只能读），
 * 之后每次启动只是一次 SharedPreferences 读取。
 *
 * 缓存里存的是类名与方法描述符，回来时再按名字反射 —— 名字对不上（原地覆盖安装但
 * versionCode 没变）就当没缓存，重新扫。
 */
internal object GboardDex {

    private const val CACHE_FILE = "gboard_hooker_dex_cache"
    private const val KEY_VERSION = "version"

    /**
     * 加载剪贴板列表的那个 `Callable`。
     *
     * 锚点是它拼 SQL 用的格式串：`item_type` 的两个位（固定 / 最近使用）加时间下限，
     * 这是查询语义本身，改了它剪贴板就查错了 —— 比类名稳得多。
     */
    const val ANCHOR_LOADER = "(%s & %d) = 0 AND (%s & %d) = 0 AND %s >= ?"

    /** 清理过期项的那个 `Callable`。锚点是它出错时打的原始方法名。 */
    const val ANCHOR_CLEANER = "deleteExpiredItemsInternal"

    /**
     * 按类里出现过的字符串找类。
     *
     * `usingStrings` 是**包含**匹配，而 R8 会把一堆 lambda 与工具方法合并进共享类，
     * 那些类里也带着原主人的日志 TAG —— 光凭串会命中好几个，顺序还不确定。所以
     * 调用方要给一个 [verify]：命中的类身上得真有它要的那个成员，才算找对。
     */
    fun classByString(
        ctx: HookCtx,
        anchor: String,
        verify: (Class<*>) -> Boolean = { true },
    ): Class<*>? {
        cachedName(ctx, "class:$anchor")
            ?.let { ctx.classOrNull(it) }
            ?.takeIf { runCatching { verify(it) }.getOrDefault(false) }
            ?.let { return it }

        return scan(ctx, "扫类 $anchor") { dex ->
            dex.findClass { matcher { usingStrings(anchor) } }
                .asSequence()
                .mapNotNull { runCatching { it.getInstance(ctx.classLoader) }.getOrNull() }
                .firstOrNull { runCatching { verify(it) }.getOrDefault(false) }
        }?.also { remember(ctx, "class:$anchor", it.name) }
    }

    /**
     * 按字符串找一个无参、返回 `Object` 的方法 —— 也就是某个 `Callable.call()`。
     *
     * 这两个 Callable 都是匿名内部类，没有 TAG 可用，只能从它们方法体里的常量入手。
     */
    fun callableByString(ctx: HookCtx, anchor: String): Method? {
        cachedName(ctx, "callable:$anchor")?.let { className ->
            ctx.classOrNull(className)
                ?.declaredMethods
                ?.firstOrNull { it.parameterCount == 0 && it.returnType == Any::class.java }
                ?.let { return it.apply { isAccessible = true } }
        }
        return scan(ctx, "扫 Callable $anchor") { dex ->
            dex.findMethod {
                matcher {
                    paramCount = 0
                    returnType = "java.lang.Object"
                    usingStrings(anchor)
                }
            }.firstOrNull()?.getMethodInstance(ctx.classLoader)
        }?.apply {
            isAccessible = true
            remember(ctx, "callable:$anchor", declaringClass.name)
        }
    }

    // -----------------------------------------------------------------------

    /**
     * DexKit 的原生库。
     *
     * 2.x 起 `DexKitBridge` **不再自己 `loadLibrary`** —— 官方要求使用方显式加载，
     * 好让模块能自定义加载路径。漏掉这一步的表现不是「找不到 so」，而是调用时报
     * `No implementation found for … nativeInitDexKit`，看起来像版本不匹配。
     *
     * `System.loadLibrary` 按**调用类所在 classloader 关联的 native 库目录**找库——
     * 这段代码虽然是模块自己的类，但运行在 Gboard 进程里，这个目录是不是正确指向
     * 模块 APK 自带的 so，完全取决于 LSPosed 内部怎么构造这个 classloader，实测并不
     * 可靠（`findLibrary` 反射兜底同理，问的是同一个 classloader）。
     *
     * 靠谱的办法是不猜路径：直接把模块自己 APK（一个 zip）里 `lib/<abi>/libdexkit.so`
     * 这个条目读出来，写到 Gboard 自己能写的缓存目录，再用绝对路径 `System.load`——
     * 这样无论 classloader 内部怎么实现都绕得过去。
     */
    @Volatile
    private var nativeLoaded = false

    private fun ensureNative(ctx: HookCtx): Boolean {
        if (nativeLoaded) return true
        synchronized(this) {
            if (nativeLoaded) return true
            nativeLoaded = runCatching { System.loadLibrary("dexkit"); true }
                .getOrElse { extractAndLoad(ctx) }
            return nativeLoaded
        }
    }

    /** 从模块自己的 APK 里把 so 挖出来，落地到 Gboard 的缓存目录再加载。 */
    private fun extractAndLoad(ctx: HookCtx): Boolean = runCatching {
        val moduleApk = GboardDex::class.java.protectionDomain?.codeSource?.location?.path
            ?: return@runCatching false
        val context = ctx.appContextOrNull() ?: return@runCatching false
        val dest = java.io.File(context.cacheDir, "gboardhooker_libdexkit.so")

        java.util.zip.ZipFile(moduleApk).use { zip ->
            val entry = android.os.Build.SUPPORTED_ABIS
                .mapNotNull { abi -> zip.getEntry("lib/$abi/libdexkit.so") }
                .firstOrNull() ?: return@runCatching false

            if (dest.length() != entry.size) {
                zip.getInputStream(entry).use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
        System.load(dest.absolutePath)
        true
    }.onFailure { ctx.log.w("从模块 APK 解出 libdexkit.so 失败：${it.message}") }.getOrDefault(false)

    /**
     * 开一次 DexKit 做一件事。
     *
     * 打不开就返回 null（本机 ABI 没有 `libdexkit.so` 时会抛 `UnsatisfiedLinkError`）
     * —— 调用方各自决定这一项功能是跳过还是报错，不影响别的功能。
     */
    private fun <T> scan(ctx: HookCtx, what: String, block: (DexKitBridge) -> T?): T? {
        val apkPath = ctx.apkPath
        if (apkPath.isNullOrEmpty()) {
            ctx.log.w("拿不到 APK 路径，跳过 $what")
            return null
        }
        if (!ensureNative(ctx)) {
            ctx.log.w("libdexkit.so 装不上，跳过 $what")
            return null
        }
        val startedAt = SystemClock.elapsedRealtime()
        val bridge = runCatching { DexKitBridge.create(apkPath) }
            .onFailure { ctx.log.w("DexKit 打不开 $apkPath：${it.message}") }
            .getOrNull() ?: return null

        return bridge.use { dex ->
            val result = runCatching { block(dex) }
                .onFailure { ctx.log.w("$what 失败：${it.message}") }
                .getOrNull()
            val elapsed = SystemClock.elapsedRealtime() - startedAt
            if (result == null) {
                ctx.log.w("$what 没有结果（${elapsed}ms）")
            } else {
                ctx.log.d("$what 用时 ${elapsed}ms")
            }
            result
        }
    }

    private fun cache(ctx: HookCtx): SharedPreferences? =
        ctx.appContextOrNull()?.let {
            runCatching { it.getSharedPreferences(CACHE_FILE, Context.MODE_PRIVATE) }.getOrNull()
        }

    private fun cachedName(ctx: HookCtx, key: String): String? {
        val prefs = cache(ctx) ?: return null
        if (prefs.getLong(KEY_VERSION, Long.MIN_VALUE) != ctx.versionCode) return null
        return prefs.getString(key, null)?.takeIf { it.isNotBlank() }
    }

    /**
     * 版本变了就先清空再写：旧版本扫出来的类名放在那儿，
     * 下一次 [cachedName] 虽然会因为版本对不上而不用它，但它会一直占着位置，
     * 而且一旦某次写入把版本号抬上来，那些陈旧的条目就会突然"生效"。
     */
    private fun remember(ctx: HookCtx, key: String, name: String) {
        val prefs = cache(ctx) ?: return
        runCatching {
            val editor = prefs.edit()
            if (prefs.getLong(KEY_VERSION, Long.MIN_VALUE) != ctx.versionCode) {
                editor.clear().putLong(KEY_VERSION, ctx.versionCode)
            }
            editor.putString(key, name).apply()
        }
    }
}
