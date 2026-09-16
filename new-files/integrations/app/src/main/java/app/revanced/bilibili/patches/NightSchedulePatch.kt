package app.revanced.bilibili.patches

import android.content.Context
import app.revanced.bilibili.settings.Settings
import app.revanced.bilibili.utils.Logger
import app.revanced.bilibili.utils.ThemeHookProvider
import app.revanced.bilibili.utils.Themes
import app.revanced.bilibili.utils.Utils
import java.util.Calendar

/**
 * Bilibili-Evolved "夜间模式计划时段": switch between night and day theme by time of day.
 */
object NightSchedulePatch {
    private const val NIGHT_THEME_ID = 1
    private const val WHITE_THEME_ID = 8
    private const val CHECK_INTERVAL = 60_000L

    @Volatile
    private var lastCheck = 0L

    private fun parseMinutes(text: String): Int? {
        val (h, m) = text.trim().split(':', '：').takeIf { it.size == 2 } ?: return null
        val hour = h.toIntOrNull() ?: return null
        val minute = m.toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    @JvmStatic
    fun check(context: Context, force: Boolean) {
        if (!Settings.NightSchedule()) return
        val now = System.currentTimeMillis()
        if (!force && now - lastCheck < CHECK_INTERVAL) return
        lastCheck = now
        val start = parseMinutes(Settings.NightScheduleStart()) ?: return
        val end = parseMinutes(Settings.NightScheduleEnd()) ?: return
        if (start == end) return
        val calendar = Calendar.getInstance()
        val current = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val wantNight = if (start < end) current in start until end
        else current >= start || current < end
        val isNight = Themes.isNightTheme
        if (wantNight == isNight) return
        val targetId = if (wantNight) NIGHT_THEME_ID
        else Themes.lastThemeId.takeIf { it != 0 && it != NIGHT_THEME_ID } ?: WHITE_THEME_ID
        Logger.debug { "NightSchedule, switch theme to $targetId, wantNight: $wantNight" }
        Utils.runOnMainThread {
            ThemeHookProvider.switchTheme(context.applicationContext, targetId)
        }
    }
}
