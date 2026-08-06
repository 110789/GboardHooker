# GboardHooker

独立的 LSPosed 模块，只做一件事：解开 Gboard（`com.google.android.inputmethod.latin`）
剪贴板面板的三处硬限制，并给按键补上可自定义的上下滑。

这是从一个更大的多 App hooker 框架里拆出来的 Gboard 部分，重构成不依赖那套框架的
独立模块，可以单独编译、单独安装。

## 功能

- **剪贴板「最近」条数**：原版固定 5 条，可自定义（1–200）。
- **剪贴板有效期**：原版固定 1 小时，可自定义（分钟数，0 = 永久保留）。
- **单条字数上限**：原版 20000 字，可自定义（100–2,000,000）。
- **按键上滑 / 下滑**：可指定任意键的滑动输出（如字母行上滑打数字），没指定的键
  保持原版行为或用长按候选补上；滑动灵敏度可调三档。

四个功能各自独立开关，互不依赖。设置界面里有四套预设（保持原版 / 够用就好 /
推荐 / 绝不丢失）可以一键填入，改完记得点保存。

## 怎么用

1. 装上 APK，在 **LSPosed 管理器**里给 `GboardHooker` 勾选作用域
   `com.google.android.inputmethod.latin`（Gboard）。
2. 打开 GboardHooker 这个 App，调整设置，点保存。
3. **重启 Gboard**（设置里找到 Gboard 应用信息强行停止，或重启手机）让新设置生效。
4. 之后改设置同样需要重启 Gboard 才会应用 —— 设置只在 Gboard 进程启动时读一次
   （除按键滑动那部分是常驻读取，改了滑动映射不重启也能生效，但为保险起见还是
   建议统一重启）。

日志能在 LSPosed 管理器的日志页搜索 `[GboardHooker]` 看到，包括每一步 hook 有没有
装上、原始查询返回了什么、有没有报错。

## 相比原框架版本的改动

原代码依赖宿主框架的 `AppHooker`/`HookScope`/`HookFeature`/`HookOption`/`HookPreset`
一整套抽象（声明式开关+设置界面渲染），以及一个第三方 hook DSL 库
`io.github.lingqiqi5211.ezhooktool`。独立出来之后：

| 原来 | 现在 |
|---|---|
| `HookScope` | [`HookCtx`](app/src/main/java/io/github/a110789/gboardhooker/hook/HookCtx.kt)，只留 gboard 逻辑真正用到的字段 |
| `ezhooktool` 的 `createReplaceHook`/`createAfterHook` | [`HookUtils.kt`](app/src/main/java/io/github/a110789/gboardhooker/hook/HookUtils.kt)，用原生 `XposedBridge` 重写，去掉这个外部依赖 |
| `HookFeature`/`HookOption`/`HookPreset` + 宿主渲染的设置 UI | 一个简单的 [`SettingsActivity`](app/src/main/java/io/github/a110789/gboardhooker/ui/SettingsActivity.kt)（开关 + 输入框 + 预设按钮），读写标准 `SharedPreferences`，hook 侧用 `XSharedPreferences` 读 |
| `AppHooker.features`/`options`/`presets` 声明 | [`GboardModule.kt`](app/src/main/java/io/github/a110789/gboardhooker/hook/gboard/GboardModule.kt) 里直接读设置、直接调用 |

**没有改动的部分**（逻辑与原版一一对应，只是把 `HookScope` 参数换成了 `HookCtx`）：

- [`GboardDex.kt`](app/src/main/java/io/github/a110789/gboardhooker/hook/gboard/GboardDex.kt) —— DexKit 扫描 + 按 versionCode 缓存
- [`GboardRefs.kt`](app/src/main/java/io/github/a110789/gboardhooker/hook/gboard/GboardRefs.kt) —— 按形状认混淆类/成员
- [`GboardClipboard.kt`](app/src/main/java/io/github/a110789/gboardhooker/hook/gboard/GboardClipboard.kt) —— 剪贴板三处限制
- [`GboardSlide.kt`](app/src/main/java/io/github/a110789/gboardhooker/hook/gboard/GboardSlide.kt) —— 按键滑动

依赖保留了 DexKit（核心功能靠它），去掉了 ezhooktool；新增 androidx
core/appcompat/material 用于设置界面。

## 构建

本地没有桌面开发环境的话，直接推到 GitHub，`.github/workflows/build.yml`
会在 push / PR / 打 `v*` tag 时自动编译，产物在 Actions 的 Artifacts 里
（`GboardHooker-debug` 可以直接装，是用 debug key 签过名的；
`GboardHooker-release-unsigned` 没签名，需要自己签）。

本地用 Android Studio 打开也可以，Gradle 会自动处理 wrapper。

## 已知限制

- 依赖 Gboard 剪贴板/按键那几层的类结构基本不变。Google 大版本重写这几块时
  （表现为日志里"找不到 XXX，Gboard 可能换了实现"或 DexKit 扫描没结果）需要重新
  核对 [`GboardRefs.kt`](app/src/main/java/io/github/a110789/gboardhooker/hook/gboard/GboardRefs.kt)
  里的判定条件。
- 设置界面写完这份代码后没有在真机上跑过完整编译（当前环境没有网络/Android SDK），
  建议先跑一次 Actions 构建看有没有编译期问题，再装到手机上验证 hook 是否生效。
