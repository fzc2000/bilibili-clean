package app.revanced.bilibili.settings.fragments

import android.app.AlertDialog
import android.os.Bundle
import androidx.preference.Preference
import app.revanced.bilibili.account.Accounts
import app.revanced.bilibili.settings.Settings
import app.revanced.bilibili.settings.search.annotation.SettingFragment
import app.revanced.bilibili.utils.*

@SettingFragment("biliroaming_setting_customize_dynamic")
class CustomizeDynamicFragment : BiliRoamingBaseSettingFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        findPreference<Preference>(Settings.DynFollowGroupIds.key)?.run {
            updateSummary(this)
            onClick { selectGroups(this); true }
        }
    }

    private fun updateSummary(pref: Preference) {
        val count = Settings.DynFollowGroupIds().size
        pref.summary = if (count == 0) Utils.getString("biliroaming_dynamic_follow_group_ids_none")
        else Utils.getString("biliroaming_dynamic_follow_group_ids_selected", count)
    }

    private fun selectGroups(pref: Preference) {
        if (!Accounts.isLogin) {
            Toasts.showShort("请先登录")
            return
        }
        val ctx = context ?: return
        Utils.async {
            val groups = FollowGroups.fetchGroups()
            Utils.runOnMainThread {
                if (groups.isNullOrEmpty()) {
                    Toasts.showShort("获取关注分组失败")
                    return@runOnMainThread
                }
                val selected = Settings.DynFollowGroupIds().toMutableSet()
                val labels = groups.map { "${it.name}（${it.count}）" }.toTypedArray()
                val checked = BooleanArray(groups.size) { groups[it].id.toString() in selected }
                AlertDialog.Builder(ctx)
                    .setTitle(Utils.getString("biliroaming_dynamic_follow_group_ids_title"))
                    .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                        val id = groups[which].id.toString()
                        if (isChecked) selected.add(id) else selected.remove(id)
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        Settings.DynFollowGroupIds.set(selected)
                        FollowGroups.refreshCache(force = true)
                        updateSummary(pref)
                    }
                    .show()
            }
        }
    }
}
