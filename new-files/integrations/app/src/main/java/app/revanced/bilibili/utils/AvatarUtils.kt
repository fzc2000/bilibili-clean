package app.revanced.bilibili.utils

import com.bapis.bilibili.dagw.component.avatar.v1.AvatarItem
import com.bapis.bilibili.dagw.component.avatar.v1.LayerGroup

// layer ids used by the app's avatar renderer, see com.bilibili.lib.avatar
private const val PENDANT_LAYER_ID = "PENDENT_LAYER"

private fun LayerGroup.removePendantLayers() {
    layersList.withIndex()
        .filter { (_, layer) -> layer.layerId == PENDANT_LAYER_ID }
        .map { it.index }
        .asReversed()
        .forEach { removeLayers(it) }
}

/**
 * Strip avatar pendants (头像框) from a dagw [AvatarItem], keeping the face and icon layers.
 */
fun AvatarItem.removePendantLayers() {
    layersList.forEach { it.removePendantLayers() }
    fallbackLayers.removePendantLayers()
}
