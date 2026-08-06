package io.github.a110789.gboardhooker.hook.gboard

import io.github.a110789.gboardhooker.hook.HookCtx
import io.github.a110789.gboardhooker.hook.PrefKeys

/**
 * Gboard（`com.google.android.inputmethod.latin`）—— 解开剪贴板的三处硬限制，
 * 再给按键补上上下滑。
 *
 * 这是原框架里 `GboardHooker` 类的独立版：原来靠 `HookFeature`/`HookOption` 声明、
 * 由宿主 App 统一渲染开关和输入框，这里改成直接从 [PrefKeys]（即
 * [io.github.a110789.gboardhooker.ui.SettingsActivity] 写的那份 SharedPreferences）
 * 读取，逻辑跟原版一一对应：
 *
 * - 剪贴板条数 / 有效期共用一次装填接管（[GboardClipboard.installList]），
 *   没开的那一项按原版默认值传进去；
 * - 有效期额外接管过期清理（[GboardClipboard.installCleaner]）；
 * - 字数上限是独立的 flag 覆盖（[GboardClipboard.installCharLimit]）；
 * - 上滑 / 下滑共用一次按键定义接管（[GboardSlide.install]），
 *   灵敏度只在开了上滑时才生效。
 */
internal object GboardModule {

    /** 键盘元数据那一层没被混淆，拿它当兼容性探针。 */
    private const val PROBE_CLASS = "com.google.android.libraries.inputmethod.metadata.SoftKeyDef"

    fun isCompatible(ctx: HookCtx): Boolean {
        if (!ctx.isMainProcess) return false
        if (ctx.classOrNull(PROBE_CLASS) == null) {
            ctx.log.w("找不到 $PROBE_CLASS，Gboard 可能换了实现")
            return false
        }
        return true
    }

    fun install(ctx: HookCtx) {
        ctx.log.i("Gboard ${ctx.versionCode} in ${ctx.processName}")

        val countOn = ctx.isEnabled(PrefKeys.FEATURE_COUNT, true)
        val ttlOn = ctx.isEnabled(PrefKeys.FEATURE_TTL, true)
        val charsOn = ctx.isEnabled(PrefKeys.FEATURE_CHARS, true)
        val upOn = ctx.isEnabled(PrefKeys.FEATURE_SLIDE_UP, true)
        val downOn = ctx.isEnabled(PrefKeys.FEATURE_SLIDE_DOWN, false)

        if (countOn || ttlOn) {
            runCatching { installClipboardList(ctx, countOn, ttlOn) }
                .onFailure { ctx.log.e("剪贴板条数/有效期接管失败", it) }
        }
        if (charsOn) {
            runCatching {
                val limit = ctx.int(PrefKeys.CHAR_LIMIT, PrefKeys.DEFAULT_CHAR_LIMIT)
                    .coerceIn(100, 2_000_000)
                GboardClipboard.installCharLimit(ctx, limit)
            }.onFailure { ctx.log.e("剪贴板字数上限接管失败", it) }
        }
        if (upOn || downOn) {
            runCatching { installSlide(ctx, upOn, downOn) }
                .onFailure { ctx.log.e("按键滑动接管失败", it) }
        }
    }

    /** 装填逻辑只有一处，两个功能都要它 —— 没开的那一项按原版默认值传进去。 */
    private fun installClipboardList(ctx: HookCtx, countOn: Boolean, ttlOn: Boolean) {
        val maxItems = if (countOn) {
            ctx.int(PrefKeys.MAX_ITEMS, PrefKeys.DEFAULT_MAX_ITEMS).coerceIn(1, 200)
        } else {
            PrefKeys.ORIGINAL_MAX_ITEMS
        }
        val ttl = if (ttlOn) ttlMillis(ctx) else PrefKeys.ORIGINAL_TTL_MILLIS
        GboardClipboard.installList(ctx, maxItems, ttl)

        // 有效期还多一步：过期清理那一侧也得换成同一把尺子。
        if (ttlOn) {
            GboardClipboard.installCleaner(ctx, ttlMillis(ctx))
        }
    }

    private fun ttlMillis(ctx: HookCtx): Long {
        val minutes = ctx.int(PrefKeys.TTL_MINUTES, PrefKeys.DEFAULT_TTL_MINUTES)
            .coerceIn(0, PrefKeys.MAX_TTL_MINUTES)
        return minutes * 60_000L
    }

    private fun installSlide(ctx: HookCtx, up: Boolean, down: Boolean) {
        GboardSlide.install(
            ctx = ctx,
            up = up,
            down = down,
            upMap = ctx.string(PrefKeys.UP_MAP) ?: PrefKeys.DEFAULT_UP_MAP,
            downMap = ctx.string(PrefKeys.DOWN_MAP) ?: PrefKeys.DEFAULT_DOWN_MAP,
        )
        if (up) {
            val level = ctx.int(PrefKeys.SLIDE_LEVEL, PrefKeys.DEFAULT_SLIDE_LEVEL).coerceIn(1, 3)
            GboardSlide.installSensitivity(ctx, level)
        }
    }
}
