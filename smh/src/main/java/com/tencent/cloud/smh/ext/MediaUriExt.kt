package com.tencent.cloud.smh.ext

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MILLISECOND = 1000

/**
 * 获取 Uri 对应文件的大小
 */
fun Uri.getSize(context: Context): Long? {
    var resultSize: Long? = null
    if ("file" == this.scheme) {
        this.path?.apply {
            resultSize = File(this).length()
        }
    } else {
        var size: Long? = null
        val columns = arrayOf(
            OpenableColumns.SIZE
        )
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(this, columns, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }
        if (size == null || size == 0L) {
            try {
                size = context.contentResolver.openFileDescriptor(this, "r")?.statSize
            } catch (exception: Exception) {
                exception.printStackTrace()
            }

        }
        resultSize = size
    }
    return resultSize
}

/**
 * 拿到一个 Uri 的最后修改时间
 */
suspend fun Uri.getLastModified(context: Context): Long? {
    var date: Long? = null
    if (isVideo(context)) {
        date = this.getVideoMetaDate(context)
    }

    if (date == null || date == 0L) {
        if ("file" == this.scheme) {
            date = this.path?.let { File(it).lastModified() }
        } else {
            val columns = arrayOf(
                MediaStore.MediaColumns.DATE_MODIFIED,
            )
            val cursor = context.contentResolver.query(this, columns, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val dateModified = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                    if (dateModified != -1) {
                        date = cursor.getLong(dateModified) * MILLISECOND
                    }
                }
            } catch (_: Exception) {
            } finally {
                cursor?.close()
            }
        }
    }

    // 实在获取不到  尝试转换为真实路径进行获取
    if (date == null || date == 0L) {
        date = this.getOriginalPath(context)?.let {
            File(it).lastModified()
        }
    }

    return date
}

private val videoExtensions = arrayOf(
    ".mp4",
    ".rmvb",
    ".mtv",
    ".mov",
    ".vcd",
    ".dat",
    ".mpeg",
    ".mpg",
    ".avi",
    ".rm",
    ".ra",
    ".asf",
    ".wmv",
    ".flv",
    ".swf",
    ".3gp",
    ".amv",
    ".mkv",
    ".qt",
)
private fun String.isVideoFast() = videoExtensions.any { endsWith(it, true) }

/**
 * 判断 Uri 是不是视频
 */
private fun Uri.isVideo(context: Context?): Boolean {
    return if ("file" == this.scheme) {
        this.path?.isVideoFast() ?: false
    } else {
        if (context == null) return false
        val type = context.contentResolver.getType(this)
        type != null && type.startsWith("video")
    }
}

/**
 * 获取视频的 meta data
 */
private fun Uri.getVideoMetaDate(context: Context): Long? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, this)
        var metaDate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE) ?: ""
        if ("19040101T000000.000Z" == metaDate) return null
        try {
            metaDate = metaDate.replace("Z", " UTC") // 注意是空格+UTC
            val format = SimpleDateFormat("yyyyMMdd'T'HHmmss.SSS Z", Locale.getDefault())
            return format.parse(metaDate)?.time
        } catch (_: Exception) { }
        return null
    } catch (ignored: Exception) {
        null
    } finally {
        retriever.release()
    }
}

/**
 * 把 URI 转成路径
 */
suspend fun Uri.getOriginalPath(context: Context): String? {
    return withContext(Dispatchers.IO) {
        // DocumentProvider
        if (DocumentsContract.isDocumentUri(context, this@getOriginalPath)) {
            // ExternalStorageProvider
            if (this@getOriginalPath.isExternalStorageDocument()) {
                val docId: String = DocumentsContract.getDocumentId(this@getOriginalPath)
                val split = docId.split(":").toTypedArray()
                val type = split[0]
                var path = ""
                if (split.size >= 2) {
                    path = split[1]
                }
                if ("primary".equals(type, ignoreCase = true)) {
                    return@withContext Environment.getExternalStorageDirectory()
                        .toString() + "/" + path
                }
            } else if (this@getOriginalPath.isDownloadsDocument()) {
                val idStr: String = DocumentsContract.getDocumentId(this@getOriginalPath)
                try {
                    val id = java.lang.Long.valueOf(idStr)
                    val contentUri: Uri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), id
                    )
                    return@withContext getDataColumn(context, contentUri, null, null)
                } catch (_: NumberFormatException) { }
            } else if (this@getOriginalPath.isMediaDocument()) {
                val docId: String = DocumentsContract.getDocumentId(this@getOriginalPath)
                val split = docId.split(":").toTypedArray()
                val type = split[0]
                var contentUri: Uri? = null
                when (type) {
                    "image" -> contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    "video" -> contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    "audio" -> contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
                var path = ""
                if (split.size >= 2) {
                    path = split[1]
                }
                val selection = "_id=?"
                val selectionArgs = arrayOf(
                    path
                )
                return@withContext getDataColumn(context, contentUri, selection, selectionArgs)
            }
        } else if ("content".equals(this@getOriginalPath.scheme, ignoreCase = true)) {
            if (this@getOriginalPath.isGooglePhotosUri()) {
                return@withContext this@getOriginalPath.lastPathSegment
            }
            return@withContext getDataColumn(context, this@getOriginalPath, null, null)
        } else if ("file".equals(this@getOriginalPath.scheme, ignoreCase = true)) {
            return@withContext this@getOriginalPath.path
        }
        return@withContext null
    }
}

private fun getDataColumn(
    context: Context,
    uri: Uri?,
    selection: String?,
    selectionArgs: Array<String>?
): String? {
    var cursor: Cursor? = null
    val column = "_data"
    val projection = arrayOf(
        column
    )
    try {
        cursor = uri?.let {
            context.contentResolver.query(
                it, projection, selection, selectionArgs,
                null
            )
        }
        if (cursor != null && cursor.moveToFirst()) {
            val columnIndex: Int = cursor.getColumnIndex(column)
            return if (columnIndex >= 0) cursor.getString(columnIndex) else null
        }
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        cursor?.close()
    }
    return null
}

private fun Uri.isExternalStorageDocument() = "com.android.externalstorage.documents" == this.authority

private fun Uri.isDownloadsDocument() = "com.android.providers.downloads.documents" == this.authority

private fun Uri.isMediaDocument() = "com.android.providers.media.documents" == this.authority

private fun Uri.isGooglePhotosUri() = "com.google.android.apps.photos.content" == this.authority

