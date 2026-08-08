package io.github.a110789.gboardhooker.hook.gboard

import io.github.a110789.gboardhooker.hook.HookCtx
import io.github.a110789.gboardhooker.hook.PrefKeys

/**
 * Gboard（`com.google.android.inputmethod.latin`）—— 解开剪贴板的三处硬限制：
 * 「最近」条数、有效期、单条字数上限。
 *
 * 从 [PrefKeys]（即 [io.github.a110789.gboardhooker.ui.SettingsActivity] 写的那份
 * SharedPreferences）读取用户设置：
 *
 * - 条数 / 有效期共用一次装填接管（[GboardClipboard.installList]），
 *   没开的那一项按原版默认值传进去；
 * - 有效期额外接管过期清理（[GboardClipboard.installCleaner]）；
 * - 字数上限是独立的 flag 覆盖（[GboardClipboard.installCharLimit]）。
 */
internal object GboardModule {

    fun install(ctx: HookCtx) {
        if (!ctx.isMainProcess) return

        val countOn = ctx.isEnabled(PrefKeys.FEATURE_COUNT, true)
        val ttlOn = ctx.isEnabled(PrefKeys.FEATURE_TTL, true)
        val charsOn = ctx.isEnabled(PrefKeys.FEATURE_CHARS, true)

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
}
