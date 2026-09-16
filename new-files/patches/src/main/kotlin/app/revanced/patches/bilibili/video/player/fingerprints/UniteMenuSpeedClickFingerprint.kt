package app.revanced.patches.bilibili.video.player.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * Since 8.92.x the unite page more-menu speed click is a static Kotlin lambda
 * `static Unit lambda(MenuService, List, ?, int)` logging "click speed".
 */
object UniteMenuSpeedClickFingerprint : MethodFingerprint(
    strings = listOf("click speed"),
    returnType = "Lkotlin/Unit;",
    customFingerprint = { methodDef, classDef ->
        AccessFlags.STATIC.isSet(methodDef.accessFlags)
                && classDef.type.contains("/theseus/united/")
                && methodDef.parameterTypes.let { it.isNotEmpty() && it.last() == "I" && "Ljava/util/List;" in it }
    }
)
