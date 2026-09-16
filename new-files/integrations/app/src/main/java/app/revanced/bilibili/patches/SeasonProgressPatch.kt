package app.revanced.bilibili.patches

import android.net.Uri
import app.revanced.bilibili.patches.main.ApplicationDelegate
import app.revanced.bilibili.settings.Settings
import app.revanced.bilibili.utils.Logger
import app.revanced.bilibili.utils.Toasts
import app.revanced.bilibili.utils.Utils
import com.bapis.bilibili.app.viewunite.v1.ViewReply
import org.json.JSONObject

/**
 * Bilibili-Evolved "记忆合集进度": remember the last watched episode of each UGC season and
 * jump back to it when the season is opened from its first episode.
 */
object SeasonProgressPatch {
    private const val MAX_ENTRIES = 300
    private val lock = Any()

    private fun load() = runCatching { JSONObject(Settings.SeasonProgressMap()) }.getOrDefault(JSONObject())

    @JvmStatic
    fun onView(reply: ViewReply) {
        if (!Settings.RememberSeasonProgress() || !reply.hasArc()) {
            Logger.debug { "SeasonProgress, skip: enabled=${Settings.RememberSeasonProgress()}, hasArc=${reply.hasArc()}" }
            return
        }
        val intro = reply.tab.tabModuleList.find { it.hasIntroduction() }?.introduction
        Logger.debug {
            "SeasonProgress, tabs=${reply.tab.tabModuleCount}, intro=${intro != null}, " +
                "modules=${intro?.modulesList?.joinToString { m -> m.dataCase.name }}"
        }
        val season = intro?.modulesList?.find { it.hasUgcSeason() }?.ugcSeason
        if (season == null) {
            Logger.debug { "SeasonProgress, no ugcSeason module" }
            return
        }
        val seasonId = season.id
        if (seasonId == 0L) return
        val aid = reply.arc.aid
        val episodes = season.sectionList.flatMap { it.episodesList }.map { it.aid }
        Logger.debug { "SeasonProgress, seasonId=$seasonId, aid=$aid, episodes=${episodes.size}, first=${episodes.firstOrNull()}" }
        if (episodes.isEmpty()) return
        synchronized(lock) {
            val map = load()
            val key = seasonId.toString()
            val saved = map.optLong(key)
            if (saved != 0L && saved != aid && episodes.first() == aid && episodes.contains(saved)) {
                Logger.debug { "SeasonProgress, season $seasonId opened at $aid, jump to $saved" }
                Utils.runOnMainThread {
                    val activity = ApplicationDelegate.getTopActivity() ?: return@runOnMainThread
                    Toasts.showShort("已跳转到上次观看的合集进度")
                    Utils.routeTo(Uri.parse("bilibili://video/$saved"), activity)
                }
                return
            }
            map.put(key, aid)
            if (map.length() > MAX_ENTRIES) {
                val overflow = map.length() - MAX_ENTRIES
                map.keys().asSequence().take(overflow).toList().forEach { map.remove(it) }
            }
            Settings.SeasonProgressMap.set(map.toString())
        }
    }
}
