package io.github.a110789.gboardhooker.hook

import de.robv.android.xposed.XSharedPreferences

/**
 * 设置项的 key、默认值、以及原版行为常量。
 *
 * 原框架里这些是通过 `HookOption`/`HookFeature` 声明式描述、由宿主 App 统一渲染
 * 设置界面的；独立模块没有那一层，这里的 key 常量同时被设置界面
 * （[io.github.a110789.gboardhooker.ui.SettingsActivity]，写）和 hook 逻辑
 * （这里，读）共用，两边靠这一份保持一致。
 */
object PrefKeys {
    const val FILE = "gboard_hooker_prefs"

    const val FEATURE_COUNT = "feature_clip_count"
    const val FEATURE_TTL = "feature_clip_ttl"
    const val FEATURE_CHARS = "feature_clip_chars"

    const val MAX_ITEMS = "clip_max_items"
    const val TTL_MINUTES = "clip_ttl_minutes"
    const val CHAR_LIMIT = "clip_char_limit"

    // 原版行为，功能没开时按它传 —— 与原始 GboardHooker.kt 完全一致。
    const val ORIGINAL_MAX_ITEMS = 5
    const val ORIGINAL_TTL_MILLIS = 3_600_000L

    // “推荐”预设的数值，同时也是设置界面里各输入框的初始值。
    const val DEFAULT_MAX_ITEMS = 50
    const val DEFAULT_TTL_MINUTES = 10_080 // 7 天
    const val DEFAULT_CHAR_LIMIT = 200_000

    /** 约 10 年，够表达「基本等于永久又不至于溢出」。填 0 才是真的永久。 */
    const val MAX_TTL_MINUTES = 5_256_000
}

/**
 * 在被 hook 的 Gboard 进程里读取本模块设置界面写下的 [android.content.SharedPreferences]。
 *
 * 前提是设置界面那边用 `Context.MODE_WORLD_READABLE` 打开的文件——LSPosed 只在这个
 * mode 下才会把文件标记成这里能读的（之前偷懒写成 `MODE_PRIVATE`，界面上看着保存
 * 成功了，这里其实永远只能读到默认值，调什么都跟没调一样，见
 * [io.github.a110789.gboardhooker.ui.SettingsActivity.openModulePrefs] 的说明）。
 *
 * 每次读取前都 `reload()`，因为设置随时可能在宿主 App 里被改掉，而这里的
 * hook（尤其是滑动那部分）是常驻的，不是只读一次。
 */
internal class Prefs(private val modulePackageName: String) {

    private val delegate by lazy {
        XSharedPreferences(modulePackageName, PrefKeys.FILE).also {
            // 按官方 Wiki 建议在这里检查一次可读性——权限不对时静默掉不如
            // 打一行日志，不然「设置了但没生效」这种问题下次还得从头排查。
            if (!it.file.canRead()) {
                Log.w("设置文件读不了（${it.file}），要么作用域没配好，要么写入那边没用 MODE_WORLD_READABLE")
            }
        }
    }

    private fun fresh(): XSharedPreferences = delegate.apply { reload() }

    fun isEnabled(featureKey: String, default: Boolean): Boolean =
        fresh().getBoolean(featureKey, default)

    fun int(key: String, default: Int): Int = fresh().getInt(key, default)
}
