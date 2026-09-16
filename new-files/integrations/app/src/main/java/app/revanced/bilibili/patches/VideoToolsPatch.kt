package app.revanced.bilibili.patches

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import app.revanced.bilibili.account.Accounts
import app.revanced.bilibili.http.HttpClient
import app.revanced.bilibili.http.RequestBody
import app.revanced.bilibili.patches.main.ApplicationDelegate
import app.revanced.bilibili.patches.main.Player
import app.revanced.bilibili.patches.main.VideoInfoHolder
import app.revanced.bilibili.patches.protobuf.hooks.PlayURLPlayViewUnite
import app.revanced.bilibili.settings.Settings
import app.revanced.bilibili.utils.*
import com.bapis.bilibili.app.viewunite.v1.ViewReply
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

/**
 * "哔哩漫游X 工具面板": Bilibili-Evolved style video tools, opened by long pressing
 * the share button (or the title when the share button is hidden) on the unite video page.
 */
object VideoToolsPatch {
    private const val MAX_ATTACH_ATTEMPTS = 8
    private const val FRAME_STEP_MS = 40
    private const val WEB_REFERER = "https://www.bilibili.com/"
    private const val WEB_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val attachedTag by lazy { Utils.getResId("biliroaming_video_tools_attached", "id") }

    private class Info(
        val aid: Long,
        val bvid: String,
        val cid: Long,
        val title: String,
        val cover: String,
        val view: ViewReply,
    )

    private fun currentInfo(): Info? {
        val videoInfo = VideoInfoHolder.current ?: return null
        val view = videoInfo.view as? ViewReply ?: return null
        if (!view.hasArc()) return null
        val arc = view.arc
        val cid = videoInfo.cid.takeIf { it != 0L } ?: arc.cid
        return Info(arc.aid, arc.bvid, cid, arc.title, arc.cover, view)
    }

    /** called whenever a unite view reply arrives */
    @JvmStatic
    fun onVideoLoaded() {
        if (Settings.ExpandVideoDesc())
            expandDescription(1)
        if (!Settings.VideoTools()) return
        attach(1)
    }

    private var lastExpandedAid = 0L

    /** Bilibili-Evolved "展开视频简介": click the "展开更多" arrow once the page is ready. */
    private fun expandDescription(attempt: Int) {
        Utils.async(700L) {
            val activity = ApplicationDelegate.getTopActivity() ?: return@async
            if (activity.isFinishing || activity.isDestroyed) return@async
            val aid = currentInfo()?.aid ?: return@async
            if (aid == lastExpandedAid) return@async
            Utils.runOnMainThread {
                val arrowId = Utils.getResId("arrow", "id")
                val decor = activity.window.decorView
                // prefer the real "arrow" id, fall back to the accessibility label of the button
                val arrow = decor.findDescendant { it.isShown && arrowId != 0 && it.id == arrowId }
                    ?: decor.findDescendant {
                        it.isShown && it.contentDescription?.toString()?.startsWith("展开更多") == true
                    }
                Logger.debug { "VideoTools, expandDescription attempt $attempt, aid $aid, arrow: $arrow" }
                if (arrow != null) {
                    lastExpandedAid = aid
                    // the click listener may live on an ancestor row instead of the icon itself
                    var target: View? = arrow
                    while (target != null && !target.isClickable) target = target.parent as? View
                    val clicked = (target ?: arrow).performClick()
                    Logger.debug { "VideoTools, expandDescription clicked ${target ?: arrow}: $clicked" }
                } else if (attempt < MAX_ATTACH_ATTEMPTS) {
                    expandDescription(attempt + 1)
                }
            }
        }
    }

    private fun View.findDescendant(predicate: (View) -> Boolean): View? {
        if (predicate(this)) return this
        if (this is android.view.ViewGroup)
            for (i in 0 until childCount)
                getChildAt(i).findDescendant(predicate)?.let { return it }
        return null
    }

    private fun attach(attempt: Int) {
        Utils.async(500L) {
            val activity = ApplicationDelegate.getTopActivity() ?: return@async
            if (activity.isFinishing || activity.isDestroyed) return@async
            Utils.runOnMainThread {
                val target = findEntrance(activity)
                if (target != null) {
                    installListener(target, activity)
                } else if (attempt < MAX_ATTACH_ATTEMPTS) {
                    attach(attempt + 1)
                }
            }
        }
    }

    private fun findEntrance(activity: Activity): View? {
        if (!Settings.HideVideoShare()) {
            val share = activity.findView<View?>("frame_share")
            if (share != null && share.isShown) return share
        }
        // share button hidden (or not present): fall back to the title text view
        val title = currentInfo()?.title?.takeIf { it.isNotEmpty() } ?: return null
        return activity.window.decorView.findTextView(title)
    }

    private fun View.findTextView(text: String): TextView? {
        if (this is TextView && this.text?.toString() == text && isShown) return this
        if (this is ViewGroup) for (i in 0 until childCount) {
            getChildAt(i).findTextView(text)?.let { return it }
        }
        return null
    }

    private fun installListener(root: View, activity: Activity) {
        if (root.getTag(attachedTag) == true) return
        root.setTag(attachedTag, true)
        val listener = View.OnLongClickListener {
            showPanel(activity)
            true
        }
        fun View.install() {
            isLongClickable = true
            setOnLongClickListener(listener)
            if (this is ViewGroup) for (i in 0 until childCount) getChildAt(i).install()
        }
        root.install()
        Logger.debug { "VideoToolsPatch, entrance attached to ${root.javaClass.name}" }
    }

    private fun dialogTheme() = Utils.getResId("AppTheme.Dialog.Alert", "style")

    private fun showPanel(activity: Activity) {
        val info = currentInfo() ?: run {
            Toasts.showShort("视频信息尚未加载")
            return
        }
        val items = arrayOf(
            "查看封面",
            "保存封面",
            "下载弹幕 (XML)",
            "下载音频",
            "保存视频元数据 (JSON)",
            "在 BiliPlus 打开",
            "快速收藏（默认收藏夹）",
            "添加到稍后再看",
            "视频快照（保存当前画面）",
            "逐帧调整",
            "合集全部收藏到默认收藏夹",
        )
        AlertDialog.Builder(activity, dialogTheme())
            .setTitle("哔哩漫游X 工具 · av${info.aid}")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showCover(activity, info)
                    1 -> saveImage(info.cover)
                    2 -> Utils.async { downloadDanmaku(info) }
                    3 -> downloadAudio(activity, info)
                    4 -> Utils.async { saveMetadata(info) }
                    5 -> openBiliPlus(activity, info)
                    6 -> Utils.async { quickFavorite(info) }
                    7 -> Utils.async { addToWatchLater(info) }
                    8 -> snapshot(activity, info)
                    9 -> showFrameStepper(activity)
                    10 -> favoriteSeason(activity, info)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // region cover
    private fun showCover(activity: Activity, info: Info) {
        if (info.cover.isEmpty()) {
            Toasts.showShort("没有封面")
            return
        }
        Utils.async {
            val bitmap = runCatching {
                URL(info.cover).openStream().use { android.graphics.BitmapFactory.decodeStream(it) }
            }.getOrNull()
            Utils.runOnMainThread {
                if (bitmap == null) {
                    Toasts.showShort("封面加载失败")
                    return@runOnMainThread
                }
                val imageView = ImageView(activity).apply {
                    setImageBitmap(bitmap)
                    adjustViewBounds = true
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
                AlertDialog.Builder(activity, dialogTheme())
                    .setTitle(info.title)
                    .setView(imageView)
                    .setPositiveButton("保存") { _, _ -> saveImage(info.cover) }
                    .setNegativeButton("关闭", null)
                    .show()
            }
        }
    }
    // endregion

    // region danmaku
    private fun downloadDanmaku(info: Info) {
        if (info.cid == 0L) {
            Toasts.showShort("没有 cid")
            return
        }
        val bytes = runCatching {
            val conn = URL("https://api.bilibili.com/x/v1/dm/list.so?oid=${info.cid}")
                .openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", WEB_UA)
            conn.setRequestProperty("Referer", WEB_REFERER)
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            val raw = conn.inputStream.use { it.readBytes() }
            // the endpoint answers raw deflate without zlib header
            if (raw.isNotEmpty() && raw[0] == '<'.code.toByte()) raw
            else InflaterInputStream(raw.inputStream(), Inflater(true)).use { it.readBytes() }
        }.onFailure { Logger.error(it) { "download danmaku failed, cid: ${info.cid}" } }.getOrNull()
        if (bytes == null || bytes.isEmpty()) {
            Toasts.showShort("弹幕下载失败")
            return
        }
        val name = "av${info.aid}_${info.cid}.xml"
        val path = savePublicFile(Environment.DIRECTORY_DOWNLOADS, name, "text/xml") { it.write(bytes) }
        if (path != null) Toasts.showLong("弹幕已保存到 $path")
        else Toasts.showShort("弹幕保存失败")
    }
    // endregion

    // region audio
    private fun downloadAudio(activity: Activity, info: Info) {
        val vod = PlayURLPlayViewUnite.vodInfoOf(info.cid)
        val audio = vod?.dashAudioList?.maxByOrNull { it.bandwidth }
        if (audio == null || audio.baseUrl.isEmpty()) {
            Toasts.showLong("没有拿到音频流，请等视频开始播放后再试")
            return
        }
        val name = "av${info.aid}_${info.cid}_${audio.id}.m4a"
        val request = DownloadManager.Request(Uri.parse(audio.baseUrl)).apply {
            addRequestHeader("Referer", WEB_REFERER)
            addRequestHeader("User-Agent", WEB_UA)
            setTitle(info.title)
            setDescription("音频 ${audio.id}")
            setMimeType("audio/mp4")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "bili/$name")
        }
        runCatching {
            activity.getSystemService(DownloadManager::class.java).enqueue(request)
            Toasts.showLong("已交给系统下载器：Download/bili/$name")
        }.onFailure {
            Logger.error(it) { "enqueue audio download failed" }
            Toasts.showShort("下载失败")
        }
    }
    // endregion

    // region metadata
    private fun saveMetadata(info: Info) {
        val view = info.view
        val owner = view.owner
        val intro = view.tab.tabModuleList.find { it.hasIntroduction() }
            ?.introduction?.modulesList?.find { it.hasUgcIntroduction() }?.ugcIntroduction
        val season = view.tab.tabModuleList.find { it.hasIntroduction() }
            ?.introduction?.modulesList?.find { it.hasUgcSeason() }?.ugcSeason
        val json = JSONObject().apply {
            put("aid", info.aid)
            put("bvid", info.bvid)
            put("cid", info.cid)
            put("title", info.title)
            put("cover", info.cover)
            put("duration", view.arc.duration)
            put("copyright", view.arc.copyright)
            put("type_id", view.arc.typeId)
            put("owner", JSONObject().apply {
                put("mid", owner.mid)
                put("name", owner.title)
                put("face", owner.face)
            })
            if (view.arc.hasStat()) view.arc.stat.let { stat ->
                put("stat", JSONObject().apply {
                    put("reply", stat.reply)
                    put("like", stat.like)
                    put("coin", stat.coin)
                    put("fav", stat.fav)
                    put("share", stat.share)
                })
            }
            if (intro != null) {
                put("pubdate", intro.pubdate)
                put("tags", JSONArray(intro.tagsList.map { it.name }))
            }
            if (season != null) {
                put("season", JSONObject().apply {
                    put("id", season.id)
                    put("title", season.title)
                    put("episodes", JSONArray(season.sectionList.flatMap { s ->
                        s.episodesList.map { e -> JSONObject().put("aid", e.aid).put("cid", e.cid).put("title", e.title) }
                    }))
                })
            }
            put("url", "https://www.bilibili.com/video/${info.bvid.ifEmpty { "av${info.aid}" }}")
            put("saved_at", System.currentTimeMillis())
        }
        val path = savePublicFile(Environment.DIRECTORY_DOWNLOADS, "av${info.aid}.json", "application/json") {
            it.write(json.toString(2).toByteArray())
        }
        if (path != null) Toasts.showLong("元数据已保存到 $path")
        else Toasts.showShort("保存失败")
    }
    // endregion

    private fun openBiliPlus(activity: Activity, info: Info) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.biliplus.com/video/av${info.aid}/"))
        runCatching { activity.startActivity(intent) }
            .onFailure { Toasts.showShort("没有可用的浏览器") }
    }

    // region favorite / watch later
    private fun webCookie() = mapOf("Cookie" to "SESSDATA=${Accounts.cookieSESSDATA}")

    private fun requireLogin(): Boolean {
        if (Accounts.isLogin && Accounts.cookieSESSDATA.isNotEmpty() && Accounts.cookieBiliJct.isNotEmpty())
            return true
        Toasts.showShort("请先登录")
        return false
    }

    private fun defaultFavoriteFolderId(): Long? {
        val json = HttpClient.get(
            "https://api.bilibili.com/x/v3/fav/folder/created/list-all",
            headers = webCookie(),
            params = mapOf("up_mid" to Accounts.mid),
            referer = WEB_REFERER,
        )?.json() ?: return null
        if (json.optInt("code", -1) != 0) return null
        val list = json.optJSONObject("data")?.optJSONArray("list") ?: return null
        if (list.length() == 0) return null
        // the default folder is flagged in attr bit 0 (0 = default) and always comes first
        return list.getJSONObject(0).optLong("id").takeIf { it != 0L }
    }

    private fun favoriteDeal(aid: Long, folderId: Long): Pair<Boolean, String> {
        val json = HttpClient.post(
            "https://api.bilibili.com/x/v3/fav/resource/deal",
            headers = webCookie(),
            referer = WEB_REFERER,
            body = RequestBody.form(
                "rid" to aid,
                "type" to 2,
                "add_media_ids" to folderId,
                "csrf" to Accounts.cookieBiliJct,
            ),
        )?.json() ?: return false to "网络错误"
        val code = json.optInt("code", -1)
        return (code == 0) to (if (code == 0) "" else json.optString("message", "code $code"))
    }

    private fun quickFavorite(info: Info) {
        if (!requireLogin()) return
        val folderId = defaultFavoriteFolderId() ?: run {
            Toasts.showShort("获取默认收藏夹失败")
            return
        }
        val (ok, message) = favoriteDeal(info.aid, folderId)
        Toasts.showShort(if (ok) "已收藏到默认收藏夹" else "收藏失败：$message")
    }

    private fun addToWatchLater(info: Info) {
        if (!requireLogin()) return
        val json = HttpClient.post(
            "https://api.bilibili.com/x/v2/history/toview/add",
            headers = webCookie(),
            referer = WEB_REFERER,
            body = RequestBody.form("aid" to info.aid, "csrf" to Accounts.cookieBiliJct),
        )?.json()
        val code = json?.optInt("code", -1) ?: -1
        Toasts.showShort(if (code == 0) "已添加到稍后再看" else "添加失败：${json?.optString("message") ?: "网络错误"}")
    }

    private fun favoriteSeason(activity: Activity, info: Info) {
        if (!requireLogin()) return
        val season = info.view.tab.tabModuleList.find { it.hasIntroduction() }
            ?.introduction?.modulesList?.find { it.hasUgcSeason() }?.ugcSeason
        val aids = season?.sectionList?.flatMap { s -> s.episodesList.map { it.aid } }
            ?.filter { it != 0L }?.distinct().orEmpty()
        if (aids.isEmpty()) {
            Toasts.showShort("当前视频不属于合集")
            return
        }
        AlertDialog.Builder(activity, dialogTheme())
            .setTitle("合集：${season?.title}")
            .setMessage("将 ${aids.size} 个视频全部收藏到默认收藏夹？")
            .setPositiveButton(android.R.string.ok) { _, _ ->
                Utils.async {
                    val folderId = defaultFavoriteFolderId() ?: run {
                        Toasts.showShort("获取默认收藏夹失败")
                        return@async
                    }
                    var success = 0
                    aids.forEach { aid ->
                        if (favoriteDeal(aid, folderId).first) success++
                        Thread.sleep(300)
                    }
                    Toasts.showLong("合集收藏完成：$success / ${aids.size}")
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
    // endregion

    // region snapshot
    private fun View.findRenderView(): View? {
        if (this is TextureView || this is SurfaceView) return this
        if (this is ViewGroup) for (i in 0 until childCount) {
            getChildAt(i).findRenderView()?.let { return it }
        }
        return null
    }

    private fun snapshot(activity: Activity, info: Info) {
        val render = activity.window.decorView.findRenderView() ?: run {
            Toasts.showShort("没有找到播放器画面")
            return
        }
        val width = render.width
        val height = render.height
        if (width <= 0 || height <= 0) {
            Toasts.showShort("播放器尚未渲染")
            return
        }
        fun save(bitmap: Bitmap) = Utils.async {
            val name = "av${info.aid}_${info.cid}_${System.currentTimeMillis()}.png"
            val path = savePublicFile(Environment.DIRECTORY_PICTURES, name, "image/png") {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            if (path != null) Toasts.showLong("快照已保存到 $path")
            else Toasts.showShort("快照保存失败")
        }
        when {
            render is TextureView -> render.getBitmap(width, height)?.let { save(it) }
                ?: Toasts.showShort("截取画面失败")

            render is SurfaceView && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> {
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                PixelCopy.request(render, bitmap, { result ->
                    if (result == PixelCopy.SUCCESS) save(bitmap)
                    else Toasts.showShort("截取画面失败: $result")
                }, Handler(Looper.getMainLooper()))
            }

            else -> Toasts.showShort("当前系统不支持截取播放画面")
        }
    }
    // endregion

    // region frame stepper
    private fun playerPosition(player: Any): Int? =
        runCatching { player.callMethod("getCurrentPosition") as? Int }.getOrNull()

    private fun stepFrame(delta: Int) {
        val player = Player.current() ?: run {
            Toasts.showShort("播放器未就绪")
            return
        }
        val position = playerPosition(player) ?: run {
            Toasts.showShort("读取进度失败")
            return
        }
        runCatching { player.callMethod("pause") }
        runCatching { player.callMethod("seekTo", (position + delta).coerceAtLeast(0), false) }
            .onFailure { Logger.error(it) { "step frame failed" } }
    }

    private fun showFrameStepper(activity: Activity) {
        val label = TextView(activity).apply {
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 24)
            text = "每次前进/后退 ${FRAME_STEP_MS}ms（约一帧），对话框保持打开"
        }
        val dialog = AlertDialog.Builder(activity, dialogTheme())
            .setTitle("逐帧调整")
            .setView(label)
            .setPositiveButton("下一帧 ▶", null)
            .setNegativeButton("◀ 上一帧", null)
            .setNeutralButton("关闭", null)
            .create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { stepFrame(FRAME_STEP_MS) }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { stepFrame(-FRAME_STEP_MS) }
    }
    // endregion
}
