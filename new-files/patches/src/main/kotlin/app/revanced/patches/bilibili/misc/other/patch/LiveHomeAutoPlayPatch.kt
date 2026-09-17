package app.revanced.patches.bilibili.misc.other.patch

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.fingerprint.MethodFingerprint
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.util.exception

/**
 * LiveInlinePlayManager#play(): picks the next live card in the live home feed and starts
 * inline playback.
 */
object LiveInlinePlayManagerPlayFingerprint : MethodFingerprint(
    returnType = "V",
    parameters = listOf(),
    strings = listOf("LiveInlinePlayManager", "playCard = null")
)

/**
 * BaseVideoBannerHolder#realStartPlay(manual): starts inline playback for the live home top big banner.
 *
 * 仅存在于 9.12.0 之前的版本。9.12.0 起该 holder 被重写，banner 的自动播放统一由
 * LiveInlinePlayManager（见上）发起，所以该指纹在新版解析不到属正常情况，按可选处理。
 */
object LiveBannerRealStartPlayFingerprint : MethodFingerprint(
    returnType = "Z",
    parameters = listOf("Z"),
    strings = listOf("realStartPlay: Starting play for ", ", manual=")
)

@Patch(
    name = "Live home pause",
    description = "直播首页暂停自动播放补丁",
    compatiblePackages = [
        CompatiblePackage(name = "tv.danmaku.bili"),
        CompatiblePackage(name = "tv.danmaku.bilibilihd"),
        CompatiblePackage(name = "com.bilibili.app.in")
    ]
)
object LiveHomeAutoPlayPatch : BytecodePatch(
    setOf(LiveInlinePlayManagerPlayFingerprint, LiveBannerRealStartPlayFingerprint)
) {
    override fun execute(context: BytecodeContext) {
        val inlineResult = LiveInlinePlayManagerPlayFingerprint.result
            ?: throw LiveInlinePlayManagerPlayFingerprint.exception
        if ((inlineResult.method.implementation?.registerCount ?: 0) < 2)
            throw PatchException("live inline play method has no free register")
        inlineResult.mutableMethod.addInstructionsWithLabels(
            0, """
            invoke-static {}, Lapp/revanced/bilibili/patches/LiveRoomPatch;->disableLiveHomeAutoPlay()Z
            move-result v0
            if-eqz v0, :jump
            return-void
            :jump
            nop
        """.trimIndent()
        )

        // 9.12.0 起直播首页顶部 banner 自身的 realStartPlay(manual) 已被删除，
        // banner 的自动播放改为统一走 LiveInlinePlayManager（上面那个 hook）触发，
        // 因此这里按可选处理：命中就打，没命中就跳过，不再让整条补丁失败。
        val bannerResult = LiveBannerRealStartPlayFingerprint.result
        if (bannerResult != null) {
            val parameterRegisterCount = 2 // this + boolean z
            val registerCount = bannerResult.method.implementation?.registerCount ?: 0
            if (registerCount <= parameterRegisterCount)
                throw PatchException("live banner realStartPlay method has no free register")
            bannerResult.mutableMethod.addInstructionsWithLabels(
                0, """
                invoke-static {}, Lapp/revanced/bilibili/patches/LiveRoomPatch;->disableLiveHomeAutoPlay()Z
                move-result v0
                if-eqz v0, :jump_banner
                if-nez p1, :jump_banner
                const/4 v0, 0x0
                return v0
                :jump_banner
                nop
            """.trimIndent()
            )
        }
    }
}
