package io.github.a110789.gboardhooker.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch
import io.github.a110789.gboardhooker.R
import io.github.a110789.gboardhooker.hook.PrefKeys

/**
 * 设置界面——剪贴板三个功能：条数、有效期、字数上限。
 *
 * 读/写同一份 [SharedPreferences]（文件名 [PrefKeys.FILE]），hook 那边通过
 * [de.robv.android.xposed.XSharedPreferences] 读同一个文件。
 *
 * 四个预设按钮做的事情，和原 `GboardHooker.presets` 里四套方案的数值一致（按键滑动
 * 那部分已经整个移除，预设里也去掉了对应字段），点一下就是把对应的开关状态和数值
 * 一次性填进输入框，还需要手动点保存才会写盘——这样点错了预设可以在保存前改回来。
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    private lateinit var switchCount: MaterialSwitch
    private lateinit var switchTtl: MaterialSwitch
    private lateinit var switchChars: MaterialSwitch

    private lateinit var inputMaxItems: EditText
    private lateinit var inputTtlMinutes: EditText
    private lateinit var inputCharLimit: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = openModulePrefs()

        switchCount = findViewById(R.id.switchCount)
        switchTtl = findViewById(R.id.switchTtl)
        switchChars = findViewById(R.id.switchChars)

        inputMaxItems = findViewById(R.id.inputMaxItems)
        inputTtlMinutes = findViewById(R.id.inputTtlMinutes)
        inputCharLimit = findViewById(R.id.inputCharLimit)

        loadFromPrefs()

        findViewById<android.view.View>(R.id.presetStock).setOnClickListener { applyPreset(PRESET_STOCK) }
        findViewById<android.view.View>(R.id.presetLight).setOnClickListener { applyPreset(PRESET_LIGHT) }
        findViewById<android.view.View>(R.id.presetRecommended).setOnClickListener { applyPreset(PRESET_RECOMMENDED) }
        findViewById<android.view.View>(R.id.presetHoard).setOnClickListener { applyPreset(PRESET_HOARD) }

        findViewById<android.view.View>(R.id.saveButton).setOnClickListener { saveToPrefs() }
    }

    /**
     * 用 `MODE_WORLD_READABLE` 打开这份设置文件。
     *
     * 这一步不是可选项——LSPosed 只在模块用这个 mode 打开 `SharedPreferences` 时，
     * 才会把它标记成 hook 那边（[de.robv.android.xposed.XSharedPreferences]）能读的
     * （见 LSPosed Wiki「New XSharedPreferences」），前提是 manifest 里
     * `xposedminversion` 至少是 93（本模块已经是）。用 `MODE_PRIVATE` 的话，界面上
     * 看着保存成功了，hook 那边其实永远读不到真实值，只能读到默认值。
     *
     * 普通 Android 上 `MODE_WORLD_READABLE` 从 API 24 起直接抛
     * `SecurityException`，只有被 LSPosed 接管、且上面条件都满足时才放行，
     * 所以这里必须 try/catch 兜底——不然脱离 LSPosed 环境（或者作用域还没配置好）
     * 打开这个界面就会直接崩溃。
     */
    private fun openModulePrefs(): SharedPreferences = try {
        @Suppress("DEPRECATION")
        getSharedPreferences(PrefKeys.FILE, Context.MODE_WORLD_READABLE)
    } catch (e: SecurityException) {
        getSharedPreferences(PrefKeys.FILE, Context.MODE_PRIVATE)
    }

    private fun loadFromPrefs() {
        switchCount.isChecked = prefs.getBoolean(PrefKeys.FEATURE_COUNT, true)
        switchTtl.isChecked = prefs.getBoolean(PrefKeys.FEATURE_TTL, true)
        switchChars.isChecked = prefs.getBoolean(PrefKeys.FEATURE_CHARS, true)

        inputMaxItems.setText(prefs.getInt(PrefKeys.MAX_ITEMS, PrefKeys.DEFAULT_MAX_ITEMS).toString())
        inputTtlMinutes.setText(prefs.getInt(PrefKeys.TTL_MINUTES, PrefKeys.DEFAULT_TTL_MINUTES).toString())
        inputCharLimit.setText(prefs.getInt(PrefKeys.CHAR_LIMIT, PrefKeys.DEFAULT_CHAR_LIMIT).toString())
    }

    private fun saveToPrefs() {
        prefs.edit()
            .putBoolean(PrefKeys.FEATURE_COUNT, switchCount.isChecked)
            .putBoolean(PrefKeys.FEATURE_TTL, switchTtl.isChecked)
            .putBoolean(PrefKeys.FEATURE_CHARS, switchChars.isChecked)
            .putInt(PrefKeys.MAX_ITEMS, intOf(inputMaxItems, PrefKeys.DEFAULT_MAX_ITEMS).coerceIn(1, 200))
            .putInt(
                PrefKeys.TTL_MINUTES,
                intOf(inputTtlMinutes, PrefKeys.DEFAULT_TTL_MINUTES).coerceIn(0, PrefKeys.MAX_TTL_MINUTES),
            )
            .putInt(PrefKeys.CHAR_LIMIT, intOf(inputCharLimit, PrefKeys.DEFAULT_CHAR_LIMIT).coerceIn(100, 2_000_000))
            .apply()

        Toast.makeText(this, R.string.hint_saved, Toast.LENGTH_LONG).show()
    }

    private fun intOf(view: EditText, default: Int): Int =
        view.text?.toString()?.trim()?.toIntOrNull() ?: default

    private fun applyPreset(preset: Preset) {
        switchCount.isChecked = preset.count
        switchTtl.isChecked = preset.ttl
        switchChars.isChecked = preset.chars

        inputMaxItems.setText(preset.maxItems.toString())
        inputTtlMinutes.setText(preset.ttlMinutes.toString())
        inputCharLimit.setText(preset.charLimit.toString())

        Toast.makeText(this, preset.label, Toast.LENGTH_SHORT).show()
    }

    /** 与原 `GboardHooker.presets` 四套方案的数值一一对应（按键滑动部分已移除）。 */
    private data class Preset(
        val label: String,
        val count: Boolean,
        val ttl: Boolean,
        val chars: Boolean,
        val maxItems: Int,
        val ttlMinutes: Int,
        val charLimit: Int,
    )

    private companion object {
        val PRESET_STOCK = Preset(
            label = "保持原版",
            count = false, ttl = false, chars = false,
            maxItems = PrefKeys.ORIGINAL_MAX_ITEMS,
            ttlMinutes = (PrefKeys.ORIGINAL_TTL_MILLIS / 60_000L).toInt(),
            charLimit = 20_000,
        )

        val PRESET_LIGHT = Preset(
            label = "够用就好",
            count = true, ttl = true, chars = false,
            maxItems = 20, ttlMinutes = 1_440, charLimit = 20_000,
        )

        val PRESET_RECOMMENDED = Preset(
            label = "推荐",
            count = true, ttl = true, chars = true,
            maxItems = 50, ttlMinutes = 10_080, charLimit = 200_000,
        )

        val PRESET_HOARD = Preset(
            label = "绝不丢失",
            count = true, ttl = true, chars = true,
            maxItems = 200, ttlMinutes = 0, charLimit = 2_000_000,
        )
    }
}
