package com.tencent.cloud.smh

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.collections.ArrayList

/**
 * <p>
 * </p>
 */
object MSHelper {

    @JvmStatic
    @JvmOverloads
    fun fetchMediaLists(
        context: Context,
        contentUri: Uri,
        selection: String? = null,
        selectionArguments: Array<String>? = null,
        sortBy: String? = null
    ): List<Triple<Uri, String, Long>> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DISPLAY_NAME
        )

        try {
            val assets = ArrayList<Triple<Uri, String, Long>>()
            context.contentResolver.query(
                contentUri,
                projection,
                selection,
                selectionArguments,
                sortBy
            )?.use { c ->
                val idColumn = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val sizeColumn = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val nameColumn = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)

                while (c.moveToNext()) {
                    val id = c.getLong(idColumn)
                    assets.add(Triple(
                        ContentUris.withAppendedId(contentUri, id),
                        c.getString(nameColumn),
                        c.getLong(sizeColumn)
                    ))
                }
            }

            return assets

        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }

    @JvmStatic
    @JvmOverloads
    fun createNewPendingAsset(
        context: Context,
        contentUri: Uri,
        assetName: String,
        creationDate: Date? = null
    ): Uri? {
        // Add a specific media item.
        val resolver = context.contentResolver

        val newValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, assetName)
            val extension = assetName.fileExtension()
            if (extension != null) {
                val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                if (mimeType != null) {
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                }
            }
            if (creationDate != null) {
                put(MediaStore.MediaColumns.DATE_ADDED, creationDate.time / 1000)
                put(MediaStore.MediaColumns.DATE_MODIFIED, creationDate.time / 1000)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        // Keeps a handle to the new media's URI in case we need to modify it
        // later.
        return resolver.insert(contentUri, newValues)
    }

    @JvmStatic
    fun endAssetPending(context: Context, contentUri: Uri) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return
        }

        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }

        resolver.update(contentUri, values, null, null)
    }


    internal fun getResourceInfo(context: Context, contentUri: Uri): Triple<Long, String, String>? {
        val resolver = context.contentResolver

        return resolver.query(
            contentUri, null, null,
            null, null
        )?.use { c ->
            if (c.count > 0) {
                val nameColumn =
                    c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeColumn = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dataColumn = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)

                c.moveToFirst()
                Triple(
                    c.getLong(sizeColumn),
                    c.getString(nameColumn),
                    c.getString(dataColumn)
                )
            } else {
                null
            }
        }
    }

    @JvmStatic
    fun removeResourceUri(context: Context, uri: Uri): Int {
        val resolver = context.contentResolver
        return resolver.delete(
            uri,
            null,
            null
        )
    }
}

private fun String.fileExtension(): String? {
    val idx = lastIndexOf(".")
    return if (idx >= 0 && idx < length - 1) substring(idx + 1) else null
}