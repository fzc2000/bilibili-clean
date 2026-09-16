package app.revanced.bilibili.utils

import app.revanced.bilibili.account.Accounts
import app.revanced.bilibili.http.HttpClient
import app.revanced.bilibili.settings.Settings

/**
 * Follow groups (关注分组) of the current account, used by the dynamic feed filter.
 */
object FollowGroups {
    data class Group(val id: Long, val name: String, val count: Int)

    private const val CACHE_TTL = 6 * 3600_000L
    private const val PAGE_SIZE = 50
    private const val MAX_PAGES = 40
    private const val WEB_REFERER = "https://www.bilibili.com/"

    @Volatile
    private var refreshing = false

    @Volatile
    private var cachedText = ""

    @Volatile
    private var cachedMids: Set<Long> = emptySet()

    private fun cookie() = mapOf("Cookie" to "SESSDATA=${Accounts.cookieSESSDATA}")

    fun fetchGroups(): List<Group>? {
        val json = HttpClient.get(
            "https://api.bilibili.com/x/relation/tags",
            headers = cookie(),
            referer = WEB_REFERER,
        )?.json() ?: return null
        if (json.optInt("code", -1) != 0) return null
        val array = json.optJSONArray("data") ?: return null
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }.map {
            Group(it.optLong("tagid"), it.optString("name"), it.optInt("count"))
        }
    }

    private fun fetchMids(tagIds: Collection<Long>): Set<Long> {
        val mids = HashSet<Long>()
        for (tagId in tagIds) {
            for (page in 1..MAX_PAGES) {
                val json = HttpClient.get(
                    "https://api.bilibili.com/x/relation/tag",
                    headers = cookie(),
                    params = mapOf("tagid" to tagId, "pn" to page, "ps" to PAGE_SIZE),
                    referer = WEB_REFERER,
                )?.json() ?: break
                if (json.optInt("code", -1) != 0) break
                val list = json.optJSONArray("data") ?: break
                if (list.length() == 0) break
                for (i in 0 until list.length())
                    list.optJSONObject(i)?.optLong("mid")?.takeIf { it > 0 }?.let { mids.add(it) }
                if (list.length() < PAGE_SIZE) break
            }
        }
        return mids
    }

    /** refresh the cached mid set in background when stale */
    fun refreshCache(force: Boolean) {
        if (!Accounts.isLogin) return
        val ids = Settings.DynFollowGroupIds().mapNotNull { it.toLongOrNull() }
        if (ids.isEmpty()) {
            Settings.DynFollowGroupMids.set("")
            Settings.DynFollowGroupMidsTime.set(0L)
            return
        }
        val now = System.currentTimeMillis()
        val fresh = now - Settings.DynFollowGroupMidsTime() < CACHE_TTL
                && Settings.DynFollowGroupMids().isNotEmpty()
        if (!force && fresh) return
        if (refreshing) return
        refreshing = true
        Utils.async {
            runCatching {
                val mids = fetchMids(ids)
                Logger.debug { "FollowGroups, refreshed ${mids.size} mids for groups $ids" }
                Settings.DynFollowGroupMids.set(mids.joinToString(","))
                Settings.DynFollowGroupMidsTime.set(System.currentTimeMillis())
            }.onFailure { Logger.error(it) { "FollowGroups, refresh failed" } }
            refreshing = false
        }
    }

    /** mids of the selected groups, empty when nothing selected or not loaded yet */
    fun cachedMids(): Set<Long> {
        refreshCache(force = false)
        val text = Settings.DynFollowGroupMids()
        if (text != cachedText) {
            cachedText = text
            cachedMids = text.splitToSequence(',').mapNotNull { it.toLongOrNull() }.toHashSet()
        }
        return cachedMids
    }
}
