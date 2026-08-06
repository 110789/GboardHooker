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
    const val FEATURE_SLIDE_UP = "feature_slide_up"
    const val FEATURE_SLIDE_DOWN = "feature_slide_down"

    const val MAX_ITEMS = "clip_max_items"
    const val TTL_MINUTES = "clip_ttl_minutes"
    const val CHAR_LIMIT = "clip_char_limit"
    const val UP_MAP = "slide_up_map"
    const val DOWN_MAP = "slide_down_map"
    const val SLIDE_LEVEL = "slide_level"

    // 原版行为，功能没开时按它传 —— 与原始 GboardHooker.kt 完全一致。
    const val ORIGINAL_MAX_ITEMS = 5
    const val ORIGINAL_TTL_MILLIS = 3_600_000L

    // “推荐”预设的数值，同时也是设置界面里各输入框的初始值。
    const val DEFAULT_MAX_ITEMS = 50
    const val DEFAULT_TTL_MINUTES = 10_080 // 7 天
    const val DEFAULT_CHAR_LIMIT = 200_000
    const val DEFAULT_UP_MAP = "q=1, w=2, e=3, r=4, t=5, y=6, u=7, i=8, o=9, p=0"
    const val DEFAULT_DOWN_MAP = ""
    const val DEFAULT_SLIDE_LEVEL = 2

    /** 约 10 年，够表达「基本等于永久又不至于溢出」。填 0 才是真的永久。 */
    const val MAX_TTL_MINUTES = 5_256_000
}

/**
 * 在被 hook 的 Gboard 进程里读取本模块设置界面写下的 [android.content.SharedPreferences]。
 *
 * LSPosed 会接管 [XSharedPreferences] 的读取路径，不需要模块自己把文件设成
 * world-readable（那是老式 Xposed 的做法，在现代 Android 上也做不到）。
 * 每次读取前都 `reload()`，因为设置随时可能在宿主 App 里被改掉，而这里的
 * hook（尤其是滑动那部分）是常驻的，不是只读一次。
 */
internal class Prefs(private val modulePackageName: String) {

    private val delegate by lazy { XSharedPreferences(modulePackageName, PrefKeys.FILE) }

    private fun fresh(): XSharedPreferences = delegate.apply { reload() }

    fun isEnabled(featureKey: String, default: Boolean): Boolean =
        fresh().getBoolean(featureKey, default)

    fun int(key: String, default: Int): Int = fresh().getInt(key, default)

    fun string(key: String, default: String): String = fresh().getString(key, default) ?: default
}
