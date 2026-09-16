package app.revanced.patches.bilibili.misc.other.patch

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.fingerprint.MethodFingerprint
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.util.exception

/**
 * tv.danmaku.bili.ui.theme.X#(Context, int themeId, boolean notify): persists
 * "theme_entries_current_key" and applies the theme.
 */
object ThemeSwitchFingerprint : MethodFingerprint(
    returnType = "V",
    parameters = listOf("Landroid/content/Context;", "I", "Z"),
    strings = listOf("theme_entries_last_key", "theme_entries_current_key")
)

@Patch(
    name = "Theme switch hook",
    description = "主题切换方法钩子（夜间模式计划时段）",
    compatiblePackages = [
        CompatiblePackage(name = "tv.danmaku.bili"),
        CompatiblePackage(name = "tv.danmaku.bilibilihd"),
        CompatiblePackage(name = "com.bilibili.app.in")
    ]
)
object ThemeSwitchPatch : BytecodePatch(setOf(ThemeSwitchFingerprint)) {
    override fun execute(context: BytecodeContext) {
        val result = ThemeSwitchFingerprint.result ?: throw ThemeSwitchFingerprint.exception
        val provider = context.findClass("Lapp/revanced/bilibili/utils/ThemeHookProvider;")!!.mutableClass
        val classField = provider.fields.first { it.name == "switchThemeClassName" }
        val methodField = provider.fields.first { it.name == "switchThemeMethodName" }
        provider.methods.first { it.name == "init" }.addInstructions(
            0, """
            const-string v0, "${result.classDef.type}"
            sput-object v0, $classField
            const-string v0, "${result.method.name}"
            sput-object v0, $methodField
        """.trimIndent()
        )
    }
}
