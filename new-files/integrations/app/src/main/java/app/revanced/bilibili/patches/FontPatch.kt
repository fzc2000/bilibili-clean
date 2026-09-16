package app.revanced.bilibili.patches

import android.graphics.Typeface
import android.os.Build
import app.revanced.bilibili.settings.Settings
import app.revanced.bilibili.utils.Logger
import app.revanced.bilibili.utils.Utils
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.io.File

/**
 * Custom font (Bilibili-Evolved "自定义字体"): replace the system default typefaces
 * of this process with a user provided font file.
 */
object FontPatch {
    const val FONT_FILE = "biliroaming_custom_font.ttf"

    val fontFile: File
        get() = File(Utils.getContext().filesDir, FONT_FILE)

    @JvmStatic
    fun apply() {
        if (!Settings.CustomFont()) return
        val file = fontFile
        if (!file.isFile || file.length() == 0L) return
        runCatching {
            val regular = Typeface.createFromFile(file)
            val bold = Typeface.create(regular, Typeface.BOLD)
            val italic = Typeface.create(regular, Typeface.ITALIC)
            val boldItalic = Typeface.create(regular, Typeface.BOLD_ITALIC)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                HiddenApiBypass.addHiddenApiExemptions("Landroid/graphics/Typeface;")
            val clazz = Typeface::class.java
            fun setStatic(name: String, value: Any?) = runCatching {
                clazz.getDeclaredField(name).apply { isAccessible = true }.set(null, value)
            }.onFailure { Logger.debug { "FontPatch, skip field $name: $it" } }
            // Java side statics, used by Typeface.defaultFromStyle()/create(null, style)
            setStatic("DEFAULT", regular)
            setStatic("DEFAULT_BOLD", bold)
            setStatic("SANS_SERIF", regular)
            setStatic("SERIF", regular)
            setStatic("MONOSPACE", regular)
            setStatic("sDefaults", arrayOf(regular, bold, italic, boldItalic))
            // native default, used by Paint.setTypeface(null) (TextView without fontFamily)
            runCatching {
                clazz.getDeclaredMethod("setDefault", Typeface::class.java)
                    .apply { isAccessible = true }.invoke(null, regular)
            }.onFailure {
                Logger.debug { "FontPatch, Typeface.setDefault failed: $it" }
                setStatic("sDefaultTypeface", regular)
                runCatching {
                    val native = clazz.getDeclaredField("native_instance")
                        .apply { isAccessible = true }.getLong(regular)
                    clazz.getDeclaredMethod("nativeSetDefault", Long::class.javaPrimitiveType)
                        .apply { isAccessible = true }.invoke(null, native)
                }.onFailure { Logger.debug { "FontPatch, nativeSetDefault failed: $it" } }
            }
            // family map, used by Typeface.create("sans-serif", style) from text appearances
            runCatching {
                val mapField = clazz.getDeclaredField("sSystemFontMap").apply { isAccessible = true }
                @Suppress("UNCHECKED_CAST")
                val old = mapField.get(null) as? Map<String, Typeface> ?: return@runCatching
                // the system map is unmodifiable, always build a fresh copy and swap the field
                val map = HashMap(old)
                for (key in old.keys) {
                    map[key] = if (key.contains("bold") || key.contains("black")) bold else regular
                }
                for (key in listOf("sans-serif", "sans-serif-medium", "sans-serif-light", "serif", "monospace"))
                    map[key] = regular
                mapField.set(null, map)
                Logger.debug { "FontPatch, sSystemFontMap replaced, ${map.size} families" }
            }.onFailure { Logger.debug { "FontPatch, sSystemFontMap not replaced: $it" } }
            Logger.debug { "FontPatch, custom font applied, size: ${file.length()}" }
        }.onFailure { Logger.error(it) { "FontPatch, apply failed" } }
    }
}
