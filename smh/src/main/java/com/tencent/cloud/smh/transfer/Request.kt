package com.tencent.cloud.smh.transfer

import android.net.Uri
import com.google.gson.Gson
import com.tencent.cloud.smh.InvalidArgumentException
import com.tencent.cloud.smh.PoorNetworkException
import com.tencent.cloud.smh.api.model.ConfirmUpload
import com.tencent.cloud.smh.api.model.ConflictStrategy
import com.tencent.cloud.smh.api.model.FileInfo
import com.tencent.cloud.smh.api.model.FileType
import com.tencent.cloud.smh.api.model.MediaType
import com.tencent.qcloud.core.http.HttpConstants
import com.tencent.qcloud.core.http.HttpResponse
import com.tencent.qcloud.core.http.HttpTask
import java.io.InputStream

/**
 * <p>
 * Created by rickenwang on 2021/8/2.
 * Copyright 2010-2021 Tencent Cloud. All Rights Reserved.
 */
open class SMHRequest(
    internal var httpMethod: String = "GET",
    internal var httpHost: String = "",
    internal var httpPath: String = "",
    internal var httpQueries: Map<String, String> = mapOf(),
    internal var httpUrl: String? = null,
    internal val httpHeaders: MutableMap<String, String> = mutableMapOf(),
    internal var httpTask: HttpTask<*>? = null,
) {
    var resultListener: SMHResultListener? = null
}

class DownloadRequest(
    val url: String,
    val progressListener: SMHProgressListener? = null,
): SMHRequest(
    httpMethod = "GET",
    httpUrl = url,
) {

}

class DownloadResult(
    var inputStream: InputStream? = null,
    var bytesTotal: Long = 0
): SMHResult() {
    override fun parseResponseBody(response: HttpResponse<*>) {
        super.parseResponseBody(response)
    }
}


open class SMHResult(
    var headers: Map<String, String> = mapOf()
) {

    open fun parseResponseBody(response: HttpResponse<*>) {
        headers = response.headers().mapValues {
            it.value.joinToString(";")
        }
    }
}

open class SMHTransferRequest(
    val key: String,
): SMHRequest(

) {

    var stateListener: SMHStateListener? = null

    var progressListener: SMHProgressListener? = null
}

open class SMHTransferResult(
    val key: String,
    val crc64: String?
): SMHResult()


class UploadFileRequest(
    key: String,
    val localUri: Uri? = null,
    val inputStream: InputStream? = null,
    val conflictStrategy: ConflictStrategy? = null,
    val confirmKey: String? = null,
    val meta: Map<String, String>? = null,
    val labels: List<String>? = null,
    val category: String? = null,
    val localCreationTime: String? = null,
    val localModificationTime: String? = null
): SMHTransferRequest(key){
    var initMultipleUploadListener: SMHInitMultipleUploadListener? = null
    init {
        if (localUri == null && inputStream == null){
            throw InvalidArgumentException
        }
    }
}


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
    val isQuick: Boolean = false
): SMHTransferResult(key, crc64) {
    companion object {
        fun fromConfirmUpload(confirmUpload: ConfirmUpload, isQuick: Boolean = false): UploadFileResult {
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
                metaData = confirmUpload.metaData,
                isQuick = isQuick
            )
        }

        fun fromQuickUpload(confirmUploadRawString: String?): UploadFileResult {
            return fromConfirmUpload(
                Gson().fromJson(confirmUploadRawString, ConfirmUpload::class.java),
                true
            )
        }
    }
}


class DownloadFileRequest(
    key: String,
    val historyId: Long? = null,
    val localFullPath: String? = null,
): SMHTransferRequest(key) {

    /**
     * 设置下载范围
     */
    fun setRange(start: Long, end: Long? = null) {
        val range = if (end == null) {
            Range(start)
        } else {
            Range(start, end)
        }
        httpHeaders[HttpConstants.Header.RANGE] = range.range
    }
}

class DownloadFileResult(
    val content: InputStream?,
    val bytesTotal: Long,
    key: String,
    crc64: String?,
    val meta: Map<String, String>?
): SMHTransferResult(key, crc64)

/**
 * 请求头 Range: bytes=x-x
 * <ul>
 *     <li>表示头500个字节：bytes=0-499 </li>
 *     <li>表示第二个500字节：bytes=500-999</li>
 *     <li>表示500字节以后的范围：bytes=500- </li>
 *     <li>表示最后500个字节：bytes=-500 :暂不支持</li>
 * </ul>
 */
class Range @JvmOverloads constructor(val start: Long, val end: Long = -1) {
    val range: String
        get() = String.format("bytes=%s-%s", start, if (end == -1L) "" else end.toString())

}

/**
 * 传输状态
 * @property WAITING 等待中
 * @property RUN_BEFORE 运行前：目前为计算文件hash, 命中秒传全量计算
 * @property RUNNING 普通上传中（有进度）
 * @property PAUSED 暂停
 * @property RUN_AFTER 运行后：目前为计算crc64用于校验
 * @property COMPLETE 完成
 * @property FAILURE 失败
 */
enum class SMHTransferState {

    WAITING,

    RUN_BEFORE,

    RUNNING,

    RUN_AFTER,

    PAUSED,

    COMPLETE,

    FAILURE
}