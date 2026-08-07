package io.github.a110789.gboardhooker.hook

import android.app.Application
import android.content.Context
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.a110789.gboardhooker.hook.gboard.GboardModule

/**
 * 模块入口，登记在 `assets/xposed_init` 里，由 LSPosed 反射实例化调用。
 *
 * 只做一件事：判断加载的是不是 Gboard，是的话交给 [GboardModule]——但不是立刻交，
 * 而是先挂 `Application.attach(Context)`，等它真正被调用、拿到一个可用的 Context
 * 之后再安装。
 *
 * 起初图省事直接在这里同步跑安装逻辑，结果剪贴板那几个功能全部失败，日志显示
 * `handleLoadPackage` 触发的时机比想象的早得多——是在 `LoadedApk.getClassLoader()`
 * 阶段，从 `ActivityThread.handleBindApplication()` 一路调下来的，这时候 Gboard 的
 * `Application` 对象都还没创建。凡是需要 Context 的地方（`SharedPreferences`、
 * `cacheDir`、`PackageManager`）在这个时间点统统拿不到。`Application.attach()` 是
 * 框架里第一个明确保证「Context 已经可用」的时间点，参数里直接带着，不用再猜。
 */
class HookEntry : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != GBOARD_PACKAGE) return

        runCatching {
            XposedHelpers.findAndHookMethod(
                Application::class.java,
                "attach",
                Context::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val context = param.args.getOrNull(0) as? Context ?: run {
                            Log.w("Application.attach 没带 Context 参数，跳过")
                            return
                        }
                        val ctx = HookCtx(lpparam, context)
                        runCatching {
                            if (!GboardModule.isCompatible(ctx)) {
                                ctx.log.w("跳过：不满足兼容性条件（进程=${ctx.processName}）")
                                return@runCatching
                            }
                            GboardModule.install(ctx)
                        }.onFailure { ctx.log.e("Gboard hook 初始化失败", it) }
                    }
                },
            )
        }.onFailure { Log.e("挂 Application.attach 失败", it) }
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
