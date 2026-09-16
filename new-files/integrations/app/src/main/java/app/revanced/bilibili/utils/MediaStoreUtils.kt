package app.revanced.bilibili.utils

import android.content.ContentValues
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.OutputStream

/**
 * Write a file into a public directory (Downloads / Pictures ...) under a "bili" sub dir.
 * Uses MediaStore on Android 10+, plain files below that.
 *
 * @param dirType one of [Environment.DIRECTORY_DOWNLOADS], [Environment.DIRECTORY_PICTURES] ...
 * @return the saved location for display, or null on failure
 */
fun savePublicFile(
    dirType: String,
    name: String,
    mimeType: String,
    subDir: String = "bili",
    write: (OutputStream) -> Unit,
): String? = runCatching {
    val context = Utils.getContext()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val collection = when (dirType) {
            Environment.DIRECTORY_PICTURES, Environment.DIRECTORY_DCIM ->
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$dirType/$subDir")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri: Uri = resolver.insert(collection, values) ?: return@runCatching null
        resolver.openOutputStream(uri)?.use(write) ?: return@runCatching null
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        "$dirType/$subDir/$name"
    } else {
        val dir = File(Environment.getExternalStoragePublicDirectory(dirType), subDir)
            .also { it.mkdirs() }
        val file = File(dir, name)
        file.outputStream().use(write)
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
        file.absolutePath
    }
}.onFailure {
    Logger.error(it) { "savePublicFile failed, dir: $dirType, name: $name" }
}.getOrNull()
