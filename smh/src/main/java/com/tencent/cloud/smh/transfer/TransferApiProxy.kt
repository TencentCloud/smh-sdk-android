package com.tencent.cloud.smh.transfer

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Log
import com.tencent.cloud.smh.InvalidArgumentException
import com.tencent.cloud.smh.SMHClientException
import com.tencent.cloud.smh.SMHCollection
import com.tencent.cloud.smh.api.SMHService
import com.tencent.cloud.smh.api.model.ConfirmUpload
import com.tencent.cloud.smh.api.model.ConflictStrategy
import com.tencent.cloud.smh.api.model.FileInfo
import com.tencent.cloud.smh.api.model.InitMultipartUpload
import com.tencent.cloud.smh.api.model.InitUpload
import com.tencent.cloud.smh.api.model.MultiUploadMetadata
import com.tencent.cloud.smh.api.model.PublicMultiUploadMetadata
import com.tencent.cloud.smh.api.model.QuickUpload
import com.tencent.cloud.smh.api.model.RawResponse
import com.tencent.cloud.smh.ext.cosXmlListenerWrapper
import com.tencent.cloud.smh.ext.runWithSuspend
import com.tencent.cloud.smh.ext.suspendBlock
import com.tencent.cloud.smh.ext.utc2normalTimeMillis
import com.tencent.cos.xml.CosXmlBaseService
import com.tencent.cos.xml.exception.CosXmlClientException
import com.tencent.cos.xml.exception.CosXmlServiceException
import com.tencent.cos.xml.listener.CosXmlProgressListener
import com.tencent.cos.xml.listener.CosXmlResultListener
import com.tencent.cos.xml.model.CosXmlRequest
import com.tencent.cos.xml.model.CosXmlResult
import com.tencent.cos.xml.model.`object`.BasePutObjectRequest
import com.tencent.cos.xml.model.`object`.BasePutObjectResult
import com.tencent.cos.xml.model.`object`.GetObjectRequest
import com.tencent.cos.xml.model.`object`.GetObjectResult
import com.tencent.cos.xml.model.`object`.UploadPartRequest
import com.tencent.cos.xml.model.`object`.UploadPartResult
import com.tencent.qcloud.core.common.QCloudTaskStateListener
import com.tencent.qcloud.core.http.HttpTask
import com.tencent.qcloud.core.task.QCloudTask
import com.tencent.qcloud.core.task.TaskExecutors
import com.tencent.qcloud.core.task.TaskManager
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.InputStream
import java.lang.Exception
import java.net.URL
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.jvm.Throws

/**
 * <p>
 * Created by rickenwang on 2021/8/2.
 * Copyright 2010-2021 Tencent Cloud. All Rights Reserved.
 */
class TransferApiProxy(
    val cosService: CosXmlBaseService,
    val smhCollection: SMHCollection
) {
    suspend fun quickUpload(
        name: String,
        quickUpload: QuickUpload,
        conflictStrategy: ConflictStrategy? = null,
        meta: Map<String, String>? = null
    ): RawResponse = smhCollection.quickUpload(
        name = name,
        quickUpload = quickUpload,
        conflictStrategy = conflictStrategy,
        meta = meta
    )

    suspend fun initUpload(smhKey: String, meta: Map<String, String>? = null, conflictStrategy: ConflictStrategy? = null, filesize: Long? = null): InitUpload {
        return smhCollection.initUpload(
            name = smhKey,
            meta = meta,
            conflictStrategy = conflictStrategy,
            filesize = filesize
        )
    }

    suspend fun uploadFile(
        initUpload: InitUpload,
        uri: Uri? = null,
        inputStream: InputStream? = null,
        requestReference: AtomicReference<CosXmlRequest>? = null,
        cosXmlProgressListener: CosXmlProgressListener? = null
    ): String? {
        if (uri == null && inputStream == null){
            throw InvalidArgumentException
        }

        return suspendBlock<BasePutObjectResult> {
            val putRequest = when {
                uri != null -> {
                    BasePutObjectRequest(
                        "",
                        initUpload.path,
                        uri
                    )
                }
                inputStream != null -> {
                    BasePutObjectRequest(
                        "",
                        initUpload.path,
                        inputStream
                    )
                }
                else -> {
                    throw InvalidArgumentException
                }
            }
            putRequest.setRequestHeaders(initUpload.headers.mapValues {
                listOf(it.value)
            })

            putRequest.requestURL = "${SMHService.getProtocol()}://${initUpload.domain}${initUpload.path}"
            putRequest.requestHeaders["Host"] = listOf(initUpload.domain)

            putRequest.progressListener = cosXmlProgressListener
            requestReference?.set(putRequest)

            cosService.basePutObjectAsync(putRequest, it)
        }.takeIf { it.httpCode in 200..299 }?.eTag
    }

    suspend fun confirmUpload(confirmKey: String, crc64: String?): ConfirmUpload {
        return smhCollection.confirmUpload(confirmKey, crc64)
    }

    suspend fun publicInitMultipartUpload(smhKey: String, meta: Map<String, String>? = null, conflictStrategy: ConflictStrategy? = null, filesize: Long? = null): InitUpload {

        return smhCollection.publicInitMultipartUpload(
            name = smhKey,
            meta = meta,
            conflictStrategy = conflictStrategy,
            filesize = filesize
        )
    }

    suspend fun initMultipartUpload(smhKey: String, partNumberRange: String, meta: Map<String, String>? = null, conflictStrategy: ConflictStrategy? = null, filesize: Long? = null): InitMultipartUpload {

        return smhCollection.initMultipartUpload(
            name = smhKey,
            partNumberRange = partNumberRange,
            meta = meta,
            conflictStrategy = conflictStrategy,
            filesize = filesize
        )
    }

    suspend fun publicListUploadMetadata(confirmKey: String): PublicMultiUploadMetadata {
        return smhCollection.publicListMultipartUpload(confirmKey)
    }

    suspend fun listUploadMetadata(confirmKey: String): MultiUploadMetadata {
        return smhCollection.listMultipartUpload(confirmKey)
    }

    /**
     * 同步续期
     */
    fun publicRenewMultipartUpload(confirmKey: String): InitUpload {
        return runBlocking {
            Log.d("TransferApiProxy", "renewMultipartUpload")
            return@runBlocking smhCollection.publicRenewMultipartUpload(confirmKey)
        }
    }

    /**
     * 同步续期
     */
    fun renewMultipartUpload(confirmKey: String, partNumberRange: String): InitMultipartUpload {
        return runBlocking {
            Log.d("TransferApiProxy", "renewMultipartUpload")
            return@runBlocking smhCollection.renewMultipartUpload(confirmKey, partNumberRange)
        }
    }


    suspend fun publicUploadFilePart(
        initUpload: InitUpload,
        partNumber: Int,
        uri: Uri? = null,
        offset: Long? = null,
        size: Long? = null,
        byteArray: ByteArray? = null,
        requestReference: AtomicReference<CosXmlRequest>? = null,
        cosXmlProgressListener: CosXmlProgressListener? = null,
        renewMultipartUploadFunction: (Boolean) -> InitUpload?
    ): String? {
        return suspendBlock<UploadPartResult> {
            publicUploadFilePart(initUpload, partNumber, uri, offset, size,
                byteArray, requestReference, cosXmlProgressListener, it, renewMultipartUploadFunction)
        }.takeIf { it.httpCode in 200..299 }?.eTag
    }

    fun publicUploadFilePartAsync(
        initUpload: InitUpload,
        partNumber: Int,
        uri: Uri? = null,
        offset: Long? = null,
        size: Long? = null,
        byteArray: ByteArray? = null,
        requestReference: AtomicReference<CosXmlRequest>? = null,
        cosXmlProgressListener: CosXmlProgressListener? = null,
        cosXmlResultListener: CosXmlResultListener,
        renewMultipartUploadFunction: (Boolean) -> InitUpload?
    ): CosXmlRequest {
        return publicUploadFilePart(initUpload, partNumber, uri, offset, size, byteArray,
            requestReference, cosXmlProgressListener, cosXmlResultListener, renewMultipartUploadFunction)
    }

    private fun publicUploadFilePart(
        initUpload: InitUpload,
        partNumber: Int,
        uri: Uri? = null,
        offset: Long? = null,
        size: Long? = null,
        byteArray: ByteArray? = null,
        requestReference: AtomicReference<CosXmlRequest>? = null,
        cosXmlProgressListener: CosXmlProgressListener? = null,
        cosXmlResultListener: CosXmlResultListener,
        renewMultipartUploadFunction: (Boolean) -> InitUpload?
    ): CosXmlRequest {
        val request = if(uri != null && offset != null && size != null){
            UploadPartRequest(
                "",
                initUpload.path,
                partNumber,
                uri,
                offset,
                size,
                initUpload.uploadId
            )
        } else if (byteArray != null) {
            UploadPartRequest(
                "",
                initUpload.path,
                partNumber,
                byteArray,
                initUpload.uploadId
            )
        } else {
            throw InvalidArgumentException
        }
        request.requestHeaders = initUpload.headers.mapValues {
            listOf(it.value)
        }
        request.requestURL = "${SMHService.getProtocol()}://${initUpload.domain}${initUpload.path}?partNumber=${partNumber}&uploadId=${initUpload.uploadId}"
        request.requestHeaders["Host"] = listOf(initUpload.domain)
        request.progressListener = cosXmlProgressListener
        requestReference?.set(request)

        cosService.uploadPartAsync(request, object : CosXmlResultListener{
            override fun onSuccess(request: CosXmlRequest?, result: CosXmlResult?) {
                cosXmlResultListener.onSuccess(request, result)
            }

            override fun onFail(
                request: CosXmlRequest?,
                clientException: CosXmlClientException?,
                serviceException: CosXmlServiceException?
            ) {
                if(serviceException != null && serviceException.statusCode == 403 &&
                    serviceException.errorCode == "AccessDenied" && serviceException.errorMessage == "Request has expired"
                ){
                    renewMultipartUploadFunction.invoke(true)
                    publicUploadFilePartAsync(initUpload, partNumber, uri, offset, size, byteArray, requestReference,
                        cosXmlProgressListener, cosXmlResultListener, renewMultipartUploadFunction)
                } else {
                    cosXmlResultListener.onFail(request, clientException, serviceException)
                }
            }
        })

        //真正执行之前检查是否过期 如果过期重新获取
        request.httpTask.addStateListener { taskId, state ->
            if(state == QCloudTask.STATE_EXECUTING) {
                renewMultipartUploadFunction.invoke(false)?.apply {
                    val newInitUpload = this
                    //修改为续期后的header
                    TaskManager.getInstance().get(taskId)?.apply {
                        if(this is HttpTask<*>){
                            for (key in newInitUpload.headers.keys){
                                this.request().addOrReplaceHeader(key, newInitUpload.headers[key])
                            }
                        }
                    }
                }
            }
        }

        return request
    }

    suspend fun uploadFilePart(
        initUpload: InitMultipartUpload,
        partNumber: Int,
        uri: Uri? = null,
        offset: Long? = null,
        size: Long? = null,
        byteArray: ByteArray? = null,
        requestReference: AtomicReference<CosXmlRequest>? = null,
        cosXmlProgressListener: CosXmlProgressListener? = null,
        renewMultipartUploadFunction: (Boolean, Int) -> InitMultipartUpload?
    ): String? {
        return suspendBlock<UploadPartResult> {
            uploadFilePart(initUpload, partNumber, uri, offset, size,
                byteArray, requestReference, cosXmlProgressListener, it, renewMultipartUploadFunction)
        }.takeIf { it.httpCode in 200..299 }?.eTag
    }

    fun uploadFilePartAsync(
        initUpload: InitMultipartUpload,
        partNumber: Int,
        uri: Uri? = null,
        offset: Long? = null,
        size: Long? = null,
        byteArray: ByteArray? = null,
        requestReference: AtomicReference<CosXmlRequest>? = null,
        cosXmlProgressListener: CosXmlProgressListener? = null,
        cosXmlResultListener: CosXmlResultListener,
        renewMultipartUploadFunction: (Boolean, Int) -> InitMultipartUpload?
    ): CosXmlRequest {
        return uploadFilePart(initUpload, partNumber, uri, offset, size, byteArray,
            requestReference, cosXmlProgressListener, cosXmlResultListener, renewMultipartUploadFunction)
    }

    private fun uploadFilePart(
        initUpload: InitMultipartUpload,
        partNumber: Int,
        uri: Uri? = null,
        offset: Long? = null,
        size: Long? = null,
        byteArray: ByteArray? = null,
        requestReference: AtomicReference<CosXmlRequest>? = null,
        cosXmlProgressListener: CosXmlProgressListener? = null,
        cosXmlResultListener: CosXmlResultListener,
        renewMultipartUploadFunction: (Boolean, Int) -> InitMultipartUpload?
    ): CosXmlRequest {
        val request = if(uri != null && offset != null && size != null){
            UploadPartRequest(
                "",
                initUpload.path,
                partNumber,
                uri,
                offset,
                size,
                initUpload.uploadId
            )
        } else if (byteArray != null) {
            UploadPartRequest(
                "",
                initUpload.path,
                partNumber,
                byteArray,
                initUpload.uploadId
            )
        } else {
            throw InvalidArgumentException
        }

        //获取分块上传header
        val initUploadParts = if(initUpload.parts.containsKey(partNumber.toString())){
            initUpload.parts[partNumber.toString()]
        } else {
            null
        }
        initUploadParts?.apply {
            //header不为空 则进行上传
            request.requestHeaders = this.headers.mapValues {
                listOf(it.value)
            }
        }?:apply {
            //header为空 则renew获取后 再进行上传
            renewMultipartUploadFunction.invoke(true, partNumber)?.apply {
                uploadFilePartAsync(this, partNumber, uri, offset, size, byteArray, requestReference,
                    cosXmlProgressListener, cosXmlResultListener, renewMultipartUploadFunction)
            }
            return request
        }

        request.requestURL = "${SMHService.getProtocol()}://${initUpload.domain}${initUpload.path}?partNumber=${partNumber}&uploadId=${initUpload.uploadId}"
        request.requestHeaders["Host"] = listOf(initUpload.domain)

        request.progressListener = cosXmlProgressListener
        requestReference?.set(request)

        cosService.uploadPartAsync(request, object : CosXmlResultListener{
            override fun onSuccess(request: CosXmlRequest?, result: CosXmlResult?) {
                cosXmlResultListener.onSuccess(request, result)
            }

            override fun onFail(
                request: CosXmlRequest?,
                clientException: CosXmlClientException?,
                serviceException: CosXmlServiceException?
            ) {
                if(serviceException != null && serviceException.statusCode == 403 &&
                    ((serviceException.errorCode == "AccessDenied" && serviceException.errorMessage == "Request has expired") || serviceException.errorCode == "RequestTimeTooSkewed")
                ){
                    renewMultipartUploadFunction.invoke(true, partNumber)?.apply {
                        uploadFilePartAsync(this, partNumber, uri, offset, size, byteArray, requestReference,
                            cosXmlProgressListener, cosXmlResultListener, renewMultipartUploadFunction)
                    }
                } else {
                    cosXmlResultListener.onFail(request, clientException, serviceException)
                }
            }
        })

        //真正执行之前检查是否过期 如果过期重新获取
        request.httpTask.addStateListener { taskId, state ->
            if(state == QCloudTask.STATE_EXECUTING) {
                renewMultipartUploadFunction.invoke(false, partNumber)?.apply {
                    val newInitUpload = this
                    //修改为续期后的header
                    TaskManager.getInstance().get(taskId)?.apply {
                        if(this is HttpTask<*>){
                            if(newInitUpload.parts.containsKey(partNumber.toString())){
                                newInitUpload.parts[partNumber.toString()]?.also {
                                    for (key in it.headers.keys){
                                        this.request().addOrReplaceHeader(key, it.headers[key])
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return request
    }

    suspend fun getFileInfo(smhKey: String, historyId: Long? = null): FileInfo {
        return smhCollection.getFileInfo(smhKey, historyId = historyId)
    }

    suspend fun download(url: String, offset: Long, localFullPath: String, requestReference: AtomicReference<CosXmlRequest>? = null,
                         executor: Executor? = null,
                         cosXmlProgressListener: CosXmlProgressListener? = null): GetObjectResult {

        return (executor?: TaskExecutors.DOWNLOAD_EXECUTOR).runWithSuspend<GetObjectResult>() {

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
            cosService.getObject(request)
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