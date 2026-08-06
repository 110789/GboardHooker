package io.github.a110789.gboardhooker.hook

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.a110789.gboardhooker.hook.gboard.GboardModule

/**
 * 模块入口，登记在 `assets/xposed_init` 里，由 LSPosed 反射实例化调用。
 *
 * 只做一件事：判断加载的是不是 Gboard，是的话交给 [GboardModule]。
 * 这个模块本身只做 Gboard 一件事，不像原框架那样要在这里分发给一堆 `AppHooker`。
 */
class HookEntry : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != GBOARD_PACKAGE) return

        val ctx = HookCtx(lpparam)
        runCatching {
            if (!GboardModule.isCompatible(ctx)) {
                ctx.log.w("跳过：不满足兼容性条件（进程=${ctx.processName}）")
                return
            }
            GboardModule.install(ctx)
        }.onFailure {
            ctx.log.e("Gboard hook 初始化失败", it)
        }
    }

    companion object {
        private const val GBOARD_PACKAGE = "com.google.android.inputmethod.latin"

        /**
         * 本模块自己的包名。
         *
         * [Prefs] 需要它来打开 [de.robv.android.xposed.XSharedPreferences]——
         * 不能用 `lpparam.packageName`，那是 Gboard 的包名，不是本模块的。
         * 这里直接写常量而不是反射取，因为 applicationId 本来就是编译期常量，
         * 反射取反而多一层可能失败的操作。
         */
        const val MODULE_PACKAGE = "io.github.a110789.gboardhooker"
    }
}
