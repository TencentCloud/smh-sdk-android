package com.tencent.cloud.smh.transfer

import android.net.Uri
import com.tencent.cloud.smh.api.model.*

/**
 * <p>
 * Created by rickenwang on 2021/8/2.
 * Copyright 2010-2021 Tencent Cloud. All Rights Reserved.
 */
open class SMHRequest(
    val meta: Map<String, String> = mapOf()
) {

    var resultListener: SMHResultListener? = null
}

open class SMHResult(
    val headers: Map<String, String> = mapOf()
)

open class SMHTransferRequest(
    val key: String,
): SMHRequest() {

    var stateListener: SMHStateListener? = null

    var progressListener: SMHProgressListener? = null


}

open class SMHTransferResult(
    val key: String,
    val crc64: String?
): SMHResult()


class UploadFileRequest(
    key: String,
    val localUri: Uri,
    val conflictStrategy: ConflictStrategy? = null,
    val confirmKey: String? = null

): SMHTransferRequest(key)


class UploadFileResult(
    key: String,
    crc64: String?,
    val contentType: String? = null,
    val size: Long? = null,
    val type: MediaType? = null,
    val fileType: FileType? = null,
    val previewAsIcon: Boolean? = null,
    val previewByCI: Boolean? = null,
    val previewByDoc: Boolean? = null,
    val creationTime: String? = null,
    val modificationTime: String? = null,
    val eTag: String? = null,
    val metaData: Map<String, String>? = null,
): SMHTransferResult(key, crc64) {


    companion object {

        fun fromConfirmUpload(confirmUpload: ConfirmUpload): UploadFileResult {

            return UploadFileResult(
                key = confirmUpload.path.joinToString("/"),
                crc64 = confirmUpload.crc64,
                contentType = confirmUpload.contentType,
                size = confirmUpload.size,
                type = confirmUpload.type,
                fileType = confirmUpload.fileType,
                previewAsIcon = confirmUpload.previewAsIcon,
                previewByCI = confirmUpload.previewByCI,
                previewByDoc = confirmUpload.previewByDoc,
                creationTime = confirmUpload.creationTime,
                modificationTime = confirmUpload.modificationTime,
                eTag = confirmUpload.eTag,
                metaData = confirmUpload.metaData
            )
        }

        fun fromFileInfo(key: String, fileInfo: FileInfo): UploadFileResult {
            return UploadFileResult(
                key = key,
                crc64 = fileInfo.crc64,
                contentType = fileInfo.contentType,
                size = fileInfo.size,
                type = fileInfo.type,
                fileType = fileInfo.fileType,
                previewAsIcon = fileInfo.previewAsIcon,
                previewByCI = fileInfo.previewByCI,
                previewByDoc = fileInfo.previewByDoc,
                creationTime = fileInfo.creationTime,
                modificationTime = fileInfo.modificationTime,
                eTag = fileInfo.eTag,
                metaData = fileInfo.metaData
            )
        }

    }
}


class DownloadFileRequest(
    key: String,
    val historyId: Long? = null,
    val localFullPath: String
): SMHTransferRequest(key)

class DownloadFileResult(
    key: String,
    crc64: String?
): SMHTransferResult(key, crc64)

enum class SMHTransferState {

    WAITING,

    RUNNING,

    PAUSED,

    COMPLETE,

    FAILURE
}