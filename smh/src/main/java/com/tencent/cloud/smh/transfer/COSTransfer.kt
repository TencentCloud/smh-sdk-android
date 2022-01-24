/*
 *
 *  * Copyright (C) 2021 Tencent, Inc.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.tencent.cloud.smh.transfer

import android.content.Context
import android.net.Uri
import android.util.SparseArray
import com.tencent.cloud.smh.api.model.InitUpload
import com.tencent.cloud.smh.api.model.MultiUploadMetadata
import com.tencent.cos.xml.CosXmlService
import com.tencent.cos.xml.exception.CosXmlClientException
import com.tencent.cos.xml.exception.CosXmlServiceException
import com.tencent.cos.xml.ktx.cosService
import com.tencent.cos.xml.ktx.suspendBlock
import com.tencent.cos.xml.listener.CosXmlProgressListener
import com.tencent.cos.xml.listener.CosXmlResultListener
import com.tencent.cos.xml.model.CosXmlRequest
import com.tencent.cos.xml.model.CosXmlResult
import com.tencent.cos.xml.model.`object`.*
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil

class COSFileTransfer constructor(
    private val context: Context
) {

    private val cos: CosXmlService = cosService(context = context) {
        configuration {
            setRegion("ap-guangzhou") // 一个有效值即可
            isHttps(true)
        }
    }

    suspend fun multipartUpload(metadata: MultiUploadMetadata, uri: Uri, size: Long,
                                cancelHandler: CancelHandler? = null,
                                progressListener: CosXmlProgressListener? = null): String? {
        val bucket = requireNotNull(metadata.uploader.domain.bucket())
        val region = requireNotNull(metadata.uploader.domain.region())
        val sliceSize = 1024 * 1024L
        val partNumber = ceil(size.toDouble() / sliceSize).toInt()
        val remains = mutableListOf<UploadPartRequest>()
        val partNumbersUploaded = metadata.parts.map { it.partNumber } // 已经上传的分片
        cancelHandler?.cos = cos
        var hasUploadSize: Long = 0
        val uploadPartProgress = SparseArray<Long>()
        val totalProgress = AtomicLong()
        val offset = partNumbersUploaded.size * sliceSize
        for (i in 1 until partNumber + 1) {
            if (!partNumbersUploaded.contains(i)) {
                val request = UploadPartRequest(
                    bucket,
                    metadata.uploader.path,
                    i,
                    uri,
                    (i - 1) * sliceSize,
                    sliceSize,
                    metadata.uploader.uploadId
                )
                request.region = region
                request.setRequestHeaders(metadata.uploader.headers.mapValues {
                    listOf(it.value)
                })
                request.progressListener = CosXmlProgressListener { progress, total ->
                    val totalUpload = updateProgress(request, progress, uploadPartProgress, totalProgress)
                    progressListener?.onProgress((offset + totalUpload), size)
                }

                remains.add(request)
            } else {
                hasUploadSize += sliceSize
            }
        }

        var eTag: String? = null
        if (remains.isNotEmpty()) {
            eTag = suspendBlock<UploadPartResult> {
                val counter = AtomicInteger(remains.count())
                cancelHandler?.addRequests(remains)
                val counterListener = object : CosXmlResultListener {
                    override fun onSuccess(request: CosXmlRequest?, result: CosXmlResult?) {
                        request?.let {
                            cancelHandler?.removeRequest(request)
                        }
                        if (counter.decrementAndGet() == 0) {
                            it.onSuccess(request, result)
                        }
                    }

                    override fun onFail(
                        request: CosXmlRequest?,
                        exception: CosXmlClientException?,
                        serviceException: CosXmlServiceException?
                    ) {
                        request?.let {
                            cancelHandler?.removeRequest(request)
                        }
                        if (counter.decrementAndGet() == 0) {
                            it.onFail(request, exception, serviceException)
                        }
                    }
                }
                remains.forEach {request ->
                    cos.uploadPartAsync(request, counterListener)
                }
            }.takeIf { it.httpCode in 200..299 }?.eTag
        }

        return eTag
    }

    @Synchronized
    private fun updateProgress(request: UploadPartRequest, progress: Long, uploadPartProgress: SparseArray<Long>, totalProgress: AtomicLong): Long {
        val partNumber = request.partNumber
        val lastComplete: Long = uploadPartProgress.get(partNumber, 0L)
        val delta = progress - lastComplete
        uploadPartProgress.put(partNumber, progress)
        return totalProgress.addAndGet(delta)
    }


    suspend fun upload(uploader: InitUpload, uri: Uri, progressListener: CosXmlProgressListener? = null): String? {
        val bucket = requireNotNull(uploader.domain.bucket())
        val region = requireNotNull(uploader.domain.region())

        return suspendBlock<PutObjectResult> {
            val putRequest = PutObjectRequest(
                bucket,
                uploader.path,
                uri
            )
            putRequest.region = region
            putRequest.setRequestHeaders(uploader.headers.mapValues {
                listOf(it.value)
            })
            putRequest.progressListener = progressListener
            cos.putObjectAsync(putRequest, it)
        }.takeIf { it.httpCode in 200..299 }?.eTag
    }

    suspend fun download(url: String, contentUri: Uri, offset: Long, cancelHandler: CancelHandler? = null,
        cosXmlProgressListener: CosXmlProgressListener? = null): GetObjectResult {
        cancelHandler?.cos = cos
        val httpUrl = URL(url)
        val bucket = httpUrl.host.bucket()
            ?: throw IllegalArgumentException("host ${httpUrl.host} is invalid")
        val region = httpUrl.host.region()
            ?: throw IllegalArgumentException("host ${httpUrl.host} is invalid")
        val cosKey = httpUrl.path
        val getRequest = GetObjectRequest(
            bucket,
            cosKey,
            contentUri
        )
        getRequest.region = region

        return download(url, getRequest, offset, cancelHandler, cosXmlProgressListener)
    }

    suspend fun download(url: String, fullPath: String, offset: Long, cancelHandler: CancelHandler? = null,
                         cosXmlProgressListener: CosXmlProgressListener? = null): GetObjectResult {
        cancelHandler?.cos = cos
        val httpUrl = URL(url)
        val bucket = httpUrl.host.bucket()
            ?: throw IllegalArgumentException("host ${httpUrl.host} is invalid")
        val region = httpUrl.host.region()
            ?: throw IllegalArgumentException("host ${httpUrl.host} is invalid")
        val cosKey = httpUrl.path
        val lastIndex = fullPath.lastIndexOf("/")
        val getRequest = GetObjectRequest(
            bucket,
            cosKey,
            fullPath.substring(0, lastIndex + 1),
            fullPath.substring(lastIndex + 1)
        )
        getRequest.region = region

        return download(url, getRequest, offset, cancelHandler, cosXmlProgressListener)
    }

    suspend fun download(url: String, getRequest: GetObjectRequest, offset: Long, cancelHandler: CancelHandler? = null,
                         cosXmlProgressListener: CosXmlProgressListener? = null): GetObjectResult {
        return suspendBlock<GetObjectResult> {
            val httpUrl = URL(url)
            getRequest.progressListener = CosXmlProgressListener { complete, target ->
                cosXmlProgressListener?.onProgress(complete + offset, target + offset)
            }
            getRequest.setQueryEncodedString(httpUrl.query)
            if (offset > 0) {
                getRequest.setRange(offset)
            }
            getRequest.fileOffset = offset
            cancelHandler?.addRequests(listOf(getRequest))

            cos.getObjectAsync(getRequest, it)
        }
    }
}

fun String.bucket() :String? = split(".").firstOrNull()

fun String.region() :String? = split(".").firstOrNull { it.startsWith("ap-") }