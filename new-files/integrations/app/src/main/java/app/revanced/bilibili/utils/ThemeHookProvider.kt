package app.revanced.bilibili.utils

import android.content.Context
import androidx.annotation.Keep

/**
 * Names resolved at patch time by ThemeSwitchPatch: the obfuscated helper that persists
 * and applies a theme id ("theme_entries_current_key").
 */
object ThemeHookProvider {
    @Keep
    @JvmStatic
    private var switchThemeClassName = ""

    @Keep
    @JvmStatic
    private var switchThemeMethodName = ""

    init {
        init()
    }

    @Keep
    @JvmStatic
    private fun init(): Int {
        // keep one register
        return 0
    }

    /**
     * @param themeId 1 = night, 8 = white(day), 2 = pink(day)
     */
    fun switchTheme(context: Context, themeId: Int): Boolean = runCatching {
        if (switchThemeClassName.isEmpty() || switchThemeMethodName.isEmpty())
            return false
        val className = switchThemeClassName.removePrefix("L").removeSuffix(";").replace('/', '.')
        val clazz = Class.forName(className)
        clazz.getDeclaredMethod(
            switchThemeMethodName,
            Context::class.java,
            Int::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType
        ).apply { isAccessible = true }.invoke(null, context, themeId, true)
        val watcherClass = Class.forName("com.bilibili.lib.ui.theme.ThemeWatcher")
        val watcher = watcherClass.getMethod("getInstance").invoke(null)
        watcherClass.getMethod("onChanged").invoke(watcher)
        true
    }.onFailure {
        Logger.error(it) { "ThemeHookProvider.switchTheme failed, id: $themeId" }
    }.getOrDefault(false)
}
