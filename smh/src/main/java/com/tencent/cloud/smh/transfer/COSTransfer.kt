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

    suspend fun multipartUpload(metadata: MultiUploadMetadata, uri: Uri, size: Long): String? {
        val bucket = requireNotNull(metadata.uploader.domain.bucket())
        val region = requireNotNull(metadata.uploader.domain.region())
        val sliceSize = 1024 * 1024L
        val partNumber = ceil(size.toDouble() / sliceSize).toInt()
        val remains = mutableListOf<UploadPartRequest>()
        val partNumbersUploaded = metadata.parts.map { partNumber }

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
                remains.add(request)
            }
        }
        var eTag: String? = null
        if (remains.isNotEmpty()) {
            eTag = suspendBlock<UploadPartResult> {
                val counter = AtomicInteger(remains.count())
                val counterListener = object : CosXmlResultListener {
                    override fun onSuccess(request: CosXmlRequest?, result: CosXmlResult?) {
                        if (counter.decrementAndGet() == 0) {
                            it.onSuccess(request, result)
                        }
                    }

                    override fun onFail(
                        request: CosXmlRequest?,
                        exception: CosXmlClientException?,
                        serviceException: CosXmlServiceException?
                    ) {
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

    suspend fun upload(uploader: InitUpload, uri: Uri): String? {
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
            putRequest.progressListener = CosXmlProgressListener { complete, target ->

            }

            cos.putObjectAsync(putRequest, it)
        }.takeIf { it.httpCode in 200..299 }?.eTag
    }

    suspend fun download(url: String, contentUri: Uri, offset: Long): GetObjectResult {
        return suspendBlock<GetObjectResult> {
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
            getRequest.setQueryEncodedString(httpUrl.query)
            getRequest.region = region
            if (offset > 0) {
                getRequest.setRange(offset)
            }

            cos.getObjectAsync(getRequest, it)
        }
    }
}

fun String.bucket() :String? = split(".").firstOrNull()

fun String.region() :String? = split(".").firstOrNull { it.startsWith("ap-") }