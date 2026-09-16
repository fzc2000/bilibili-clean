package app.revanced.bilibili.patches.okhttp.hooks

import app.revanced.bilibili.patches.okhttp.ApiHook
import app.revanced.bilibili.settings.Settings
import app.revanced.bilibili.utils.toJSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bilibili-Evolved "关注时间": show when each user was followed in the followings list,
 * by prefixing the signature line with the follow date.
 */
object Followings : ApiHook() {
    private val dateFormat by lazy { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    override fun shouldHook(url: String, status: Int): Boolean {
        return status.isOk && Settings.ShowFollowTime() && url.contains("/x/relation/followings")
    }

    override fun hook(url: String, status: Int, request: String, response: String): String {
        val json = response.toJSONObject()
        val list = json.optJSONObject("data")?.optJSONArray("list") ?: return response
        var changed = false
        for (i in 0 until list.length()) {
            val item = list.optJSONObject(i) ?: continue
            val mtime = item.optLong("mtime")
            if (mtime <= 0L) continue
            val date = dateFormat.format(Date(mtime * 1000))
            val sign = item.optString("sign")
            item.put("sign", "关注于 $date" + if (sign.isNotEmpty()) " · $sign" else "")
            changed = true
        }
        return if (changed) json.toString() else response
    }
}
