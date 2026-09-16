package app.revanced.bilibili.utils

import app.revanced.bilibili.account.Accounts
import app.revanced.bilibili.http.HttpClient
import app.revanced.bilibili.http.RequestBody
import com.bapis.bilibili.app.dynamic.v2.DynSpaceReq
import com.bapis.bilibili.app.dynamic.v2.DynamicItem
import com.bapis.bilibili.app.dynamic.v2.DynamicMoss

/**
 * Bilibili-Evolved "批量删除动态": list and delete the current account's own dynamics.
 */
object DynamicTools {
    class Item(val idStr: String, val label: String)

    private const val REFERER = "https://t.bilibili.com/"
    private const val WEB_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private fun cookie() = mapOf("Cookie" to "SESSDATA=${Accounts.cookieSESSDATA}")

    private fun DynamicItem.describe(): String {
        val type = cardType?.name?.uppercase() ?: "UNKNOWN"
        var time = ""
        var text = ""

        for (module in modulesList) {
            if (module.hasModuleAuthor()) {
                val author = module.moduleAuthor
                time = author.ptimeLabelText.ifEmpty { author.ptimeLocationText }
            }
            if (text.isEmpty() && module.hasModuleDesc()) {
                text = module.moduleDesc.text
            }
            if (text.isEmpty() && module.hasModuleDynamic()) {
                val dyn = module.moduleDynamic
                text = when {
                    dyn.hasDynArchive() -> dyn.dynArchive.title
                    dyn.hasDynArticle() -> dyn.dynArticle.title
                    dyn.hasDynDraw() -> "[${dyn.dynDraw.itemsCount} 张图片]"
                    else -> ""
                }
            }
        }

        if (text.isEmpty() && hasExtend()) {
            text = extend.origName.ifEmpty { extend.cardUrl }
        }
        if (text.isEmpty()) {
            text = type
        }

        return "[$type] $time\n${text.take(60).replace('\n', ' ')}"
    }

    /** @return items newest first, or null on failure (message in second) */
    fun fetchMine(limit: Int): Pair<List<Item>?, String> {
        val mid = Accounts.mid
        if (mid == 0L) return null to "未登录"
        val items = ArrayList<Item>()
        var offset = ""
        var page = 1L
        val moss = DynamicMoss()

        while (items.size < limit) {
            val rsp = runCatching {
                moss.dynSpace(DynSpaceReq().apply {
                    hostUid = mid
                    historyOffset = offset
                    this.page = page
                })
            }.onFailure {
                Logger.error(it) { "DynamicTools.fetchMine failed via DynamicMoss" }
            }.getOrNull() ?: return items.takeIf { it.isNotEmpty() } to "gRPC 请求失败"

            val list = rsp.listList
            if (list.isNullOrEmpty()) break

            for (item in list) {
                val idStr = if (item.hasExtend()) item.extend.dynIdStr else ""
                if (idStr.isEmpty()) continue
                items.add(Item(idStr, item.describe()))
            }

            if (!rsp.hasMore || list.isEmpty()) break
            val nextOffset = rsp.historyOffset.orEmpty()
            if (nextOffset.isEmpty() || nextOffset == offset) {
                page++
            } else {
                offset = nextOffset
            }
        }
        return items to ""
    }

    fun delete(idStr: String): Boolean {
        val json = HttpClient.post(
            "https://api.bilibili.com/x/dynamic/feed/operate/remove",
            headers = cookie(),
            ua = WEB_UA,
            referer = REFERER,
            body = RequestBody.form("dyn_id_str" to idStr, "csrf" to Accounts.cookieBiliJct),
        )?.json()
        Logger.debug { "DynamicTools.delete, id: $idStr, response: $json" }
        return json?.optInt("code", -1) == 0
    }
}
