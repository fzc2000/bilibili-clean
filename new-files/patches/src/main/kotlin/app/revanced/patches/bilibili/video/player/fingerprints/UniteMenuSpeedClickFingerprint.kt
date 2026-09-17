package app.revanced.patches.bilibili.video.player.fingerprints

import app.revanced.patcher.fingerprint.MethodFingerprint

/**
 * Since 9.12.0 the unite page more-menu speed click is a `Function1` lambda class
 * (`com.bilibili.ship.theseus.united.page.toolbar.o`) whose `invoke(Object)Object`
 * logs "click speed"; the speed list is captured in a `java.util.List` field.
 * Up to 8.92.x it was a static `Unit lambda(MenuService, List, ?, int)`.
 */
object UniteMenuSpeedClickFingerprint : MethodFingerprint(
    strings = listOf("click speed"),
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;"),
    customFingerprint = { _, classDef ->
        classDef.type.contains("/theseus/united/")
                && classDef.fields.any { it.type == "Ljava/util/List;" }
    }
)
