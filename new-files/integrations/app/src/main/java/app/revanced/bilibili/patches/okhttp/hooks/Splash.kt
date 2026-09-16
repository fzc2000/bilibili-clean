package app.revanced.bilibili.patches.okhttp.hooks

import app.revanced.bilibili.patches.okhttp.ApiHook
import app.revanced.bilibili.settings.Settings
import app.revanced.bilibili.utils.Logger
import app.revanced.bilibili.utils.toJSONObject
import org.json.JSONArray

/**
 * Purify splash at the http layer. 8.92.1 parses the brand splash config with Gson instead of
 * fastjson, so the JSONPatch model hooks never see it; rewriting the raw response works for all.
 */
object Splash : ApiHook() {
    private const val AD_LIST = "/x/v2/splash/list"
    private const val AD_SHOW = "/x/v2/splash/show"
    private const val BRAND_LIST = "/x/v2/splash/brand/list"
    private const val EVENT_LIST = "/x/v2/splash/event/list2"

    override fun shouldHook(url: String, status: Int): Boolean {
        return Settings.PurifySplash() && status.isOk && (url.contains(AD_LIST)
                || url.contains(AD_SHOW) || url.contains(BRAND_LIST) || url.contains(EVENT_LIST))
    }

    override fun hook(url: String, status: Int, request: String, response: String): String {
        return runCatching {
            val json = response.toJSONObject()
            val data = json.optJSONObject("data") ?: return response
            when {
                url.contains(BRAND_LIST) -> {
                    data.put("list", JSONArray())
                    data.put("show", JSONArray())
                    data.put("preload", JSONArray())
                    data.put("query_list", JSONArray())
                    data.put("forcibly", false)
                    data.put("force_show_times", 0)
                }

                url.contains(EVENT_LIST) -> {
                    // keep birthday splash only, same as JSONPatch#EventSplashDataList
                    val events = data.optJSONArray("event_list") ?: JSONArray()
                    val kept = JSONArray()
                    for (i in 0 until events.length()) {
                        val event = events.optJSONObject(i) ?: continue
                        if (event.optInt("event_type", -1) == 0) kept.put(event)
                    }
                    data.put("event_list", kept)
                }

                else -> {
                    data.put("list", JSONArray())
                    data.put("show", JSONArray())
                }
            }
            Logger.debug { "Splash, purified $url" }
            json.toString()
        }.onFailure {
            Logger.error(it) { "Splash, failed to purify $url" }
        }.getOrDefault(response)
    }
}
