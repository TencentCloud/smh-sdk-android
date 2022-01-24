package com.tencent.cloud.smh.transfer

import android.net.Uri
import bolts.Task
import com.tencent.cloud.smh.SMHCollection
import com.tencent.cloud.smh.api.model.*
import com.tencent.cos.xml.CosXmlSimpleService
import com.tencent.cos.xml.ktx.suspendBlock
import com.tencent.cos.xml.listener.CosXmlProgressListener
import com.tencent.cos.xml.model.CosXmlRequest
import com.tencent.cos.xml.model.`object`.*
import java.net.URL
import java.util.concurrent.atomic.AtomicReference

/**
 * <p>
 * Created by rickenwang on 2021/8/2.
 * Copyright 2010-2021 Tencent Cloud. All Rights Reserved.
 */
class TransferApiProxy(
    val cosService: CosXmlSimpleService,
    val smhCollection: SMHCollection
) {


    suspend fun initUpload(smhKey: String, meta: Map<String, String>? = null, conflictStrategy: ConflictStrategy? = null): InitUpload {
        return smhCollection.initUpload(
            name = smhKey,
            meta = meta,
            conflictStrategy = conflictStrategy)
    }

    suspend fun uploadFile(initUpload: InitUpload, uri: Uri, requestReference: AtomicReference<CosXmlRequest>? = null,
                           cosXmlProgressListener: CosXmlProgressListener? = null): String? {
        val bucket = requireNotNull(initUpload.domain.bucket())
        val region = requireNotNull(initUpload.domain.region())

        return suspendBlock<PutObjectResult> {
            val putRequest = PutObjectRequest(
                bucket,
                initUpload.path,
                uri
            )
            putRequest.region = region
            putRequest.setRequestHeaders(initUpload.headers.mapValues {
                listOf(it.value)
            })
            putRequest.progressListener = cosXmlProgressListener
            requestReference?.set(putRequest)

            cosService.putObjectAsync(putRequest, it)
        }.takeIf { it.httpCode in 200..299 }?.eTag
    }

    suspend fun confirmUpload(confirmKey: String, crc64: String): ConfirmUpload {
        return smhCollection.confirmUpload(confirmKey, crc64)
    }


    suspend fun initMultipartUpload(smhKey: String, meta: Map<String, String>? = null, conflictStrategy: ConflictStrategy? = null): InitUpload {

        return smhCollection.initMultipartUpload(
            name = smhKey,
            meta = meta,
            conflictStrategy = conflictStrategy
        )
    }

    suspend fun listUploadMetadata(confirmKey: String): MultiUploadMetadata {
        return smhCollection.listMultipartUpload(confirmKey)
    }


    suspend fun uploadFilePart(initUpload: InitUpload, uri: Uri, partNumber: Int, offset: Long, size: Long,
            requestReference: AtomicReference<CosXmlRequest>? = null,
            cosXmlProgressListener: CosXmlProgressListener? = null): String? {

        return suspendBlock<UploadPartResult> {
            val bucket = requireNotNull(initUpload.domain.bucket())
            val region = requireNotNull(initUpload.domain.region())
            val request = UploadPartRequest(
                bucket,
                initUpload.path,
                partNumber,
                uri,
                offset,
                size,
                initUpload.uploadId
            )
            request.region = region
            request.requestHeaders = initUpload.headers.mapValues {
                listOf(it.value)
            }
            request.progressListener = cosXmlProgressListener
            requestReference?.set(request)

            cosService.uploadPartAsync(request, it)
        }.takeIf { it.httpCode in 200..299 }?.eTag
    }

    suspend fun getFileInfo(smhKey: String, historyId: Long? = null): FileInfo {
        return smhCollection.getFileInfo(smhKey, historyId = historyId)
    }

    suspend fun download(url: String, offset: Long, localFullPath: String,
                               requestReference: AtomicReference<CosXmlRequest>? = null,
                               cosXmlProgressListener: CosXmlProgressListener? = null): GetObjectResult {

        return suspendBlock<GetObjectResult> {

            val httpUrl = URL(url)
            val lastIndex = localFullPath.lastIndexOf("/")
            val request = GetObjectRequest(
                "",
                "",
                localFullPath.substring(0, lastIndex + 1),
                localFullPath.substring(lastIndex + 1)
            )
            request.requestURL = url
            request.requestHeaders["Host"] = listOf(httpUrl.host)
            if (offset > 0) {
                request.setRange(offset)
                request.fileOffset = offset
            }
            request.progressListener = cosXmlProgressListener
            requestReference?.set(request)
            cosService.getObjectAsync(request, it)
        }

    }

    fun cancel(request: CosXmlRequest) {
        cosService.cancel(request)
    }

    fun cancel(request: SMHRequest) {

    }

    suspend fun abortMultiUpload(confirmKey: String) {

    }
}