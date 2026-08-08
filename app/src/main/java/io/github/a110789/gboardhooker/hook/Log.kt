package io.github.a110789.gboardhooker.hook

/**
 * 日志出口——调用方不用改，这里直接空实现。
 *
 * 之前排查 hook 装不上、DexKit 装不上原生库那几轮问题全靠这些日志，但功能稳定
 * 之后不再需要往 LSPosed 日志里写东西了，图省事没有把 `ctx.log.xxx(...)` 这些调用点
 * 一个个删掉（改动面太大，容易改出问题），直接让 [d]/[i]/[w]/[e] 什么都不做。
 */
internal object Log {
    fun d(msg: String) {}
    fun i(msg: String) {}
    fun w(msg: String) {}
    fun e(msg: String, t: Throwable? = null) {}
}
