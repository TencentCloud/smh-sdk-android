package com.tencent.cloud.smh.transfer

import android.content.Context
import android.text.TextUtils
import bolts.CancellationTokenSource
import bolts.Task
import com.tencent.cloud.smh.*
import com.tencent.cloud.smh.api.model.*
import com.tencent.cos.xml.listener.CosXmlProgressListener
import com.tencent.cos.xml.model.CosXmlRequest
import com.tencent.cos.xml.utils.DigestUtils
import com.tencent.qcloud.core.logger.QCloudLogger
import com.tencent.qcloud.core.util.ContextHolder
import com.tencent.qcloud.core.util.QCloudUtils
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.lang.Exception
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * 支持上传数据源，优先级为 filePath > Uri
 *
 * 1. filePath：支持分片上传、客户端加密上传
 * 2. Uri：支持分片上传、客户端加密上传
 *
 *
 * 3. byte 数组：暂不支持，不支持分片上传
 * 4. String 字符串：暂不支持，不支持分片上传
 * 5. InputStream：暂不支持，会预先将数据写到一个本地文件缓存，然后以文件上传的方式进行上传，否则：
 * 无法支持分片上传（无法预知数据流的长度来判断是否需要分片，并且分片上传需要预先读取数据来校验分块）
 * 无法支持加密上传（加密上传需要预先读取数据计算原始的 md5 值）
 * 一些重试的逻辑也无法支持
 *
 */
class SMHUploadTask(
    context: Context,
    smhCollection: SMHCollection,
    private val uploadFileRequest: UploadFileRequest
) : SMHTransferTask(context, smhCollection, uploadFileRequest) {

    // 是否禁止用户操作
    // 在最后完成分片的过程中，不允许用户手动点击暂停或者取消，
    // 否则可能产生服务端和客户端回调状态不一致的情况。
    @Volatile
    private var frozenManual = false
    private var multipartUploadTask: MultipartUploadTask? = null
    private var simpleUploadTask: SimpleUploadTask? = null
    private val multipartUploadThreshold = (2 * 1024 * 1024).toLong()
    private val confirmKeyReference: AtomicReference<String> = AtomicReference(
        uploadFileRequest.confirmKey
    )

    /**
     * 上传文件、uri 时，会对其初始化
     */
    private var uploadLength: Long = -1
    override fun tag(): String {
        return TAG
    }

    override suspend fun start() {
        super.start()
        frozenManual = false
    }

    fun pause(force: Boolean): Boolean {
        if (taskState != SMHTransferState.RUNNING && taskState != SMHTransferState.WAITING) {
            QCloudLogger.i(TAG, "[%s]: cannot pause upload task in state %s", taskId, taskState)
            return false
        }
        if (frozenManual && !force) {
            QCloudLogger.i(
                TAG,
                "[%s]: cannot pause upload task, frozenManual:%b, force: %b",
                taskId,
                frozenManual,
                frozenManual
            )
            return false
        }
        QCloudLogger.i(TAG, "[%s]: pause upload task", taskId)
        isManualPaused = true
        // 快速回调状态
        onTransferPaused()
        cts.cancel()
        multipartUploadTask?.cancel()
        simpleUploadTask?.cancel()
        return true
    }

    suspend fun resume() {
        if (taskState !== SMHTransferState.PAUSED) {
            QCloudLogger.i(TAG, "[%s]: cannot resume upload task in state %s", taskId, taskState)
            return
        }
        QCloudLogger.i(TAG, "[%s]: resume upload task", taskId, taskState)
        start()
    }

    fun getConfirmKey(): String? {
        return confirmKeyReference.get()
    }

    suspend fun cancel(force: Boolean) {
        if (frozenManual && !force) {
            return
        }
        QCloudLogger.i(TAG, "[%s]: cancel upload task", taskId)
        isManualCanceled = true
        cts.cancel()
        multipartUploadTask?.cancel()
        multipartUploadTask?.abortUpload()
        simpleUploadTask?.cancel()
        simpleUploadTask?.abortUpload()
    }


    @Throws(SMHClientException::class, SMHException::class)
    override suspend fun execute(): SMHTransferResult {
        val cosXmlResult: SMHTransferResult
        if (shouldMultipartUpload()) {
            cosXmlResult = multipartUpload()
        } else {
            cosXmlResult = simpleUpload()
        }
        return cosXmlResult
    }

    @Throws(SMHClientException::class)
    override suspend fun checking() {
        val uri = uploadFileRequest.localUri
        val context = ContextHolder.getAppContext()

        if (context != null) {
            val checkFileExist = QCloudUtils.doesUriFileExist(uri, context.contentResolver)
            if (!checkFileExist) {
                throw FileNotFoundException
            }
            uploadLength = QCloudUtils.getUriContentLength2(uri, context.contentResolver)
        }
    }

    // 分块上传
    @Throws(SMHClientException::class, SMHException::class)
    private suspend fun multipartUpload(): UploadFileResult {
        QCloudLogger.i(TAG, "[%s]: start upload with multipart upload", taskId)
        val uploadTask = MultipartUploadTask(
            transferApiProxy,
            uploadFileRequest,
            uploadLength,
            cts
        )
        uploadTask.setTaskId(taskId)
        uploadTask.smhKey = smhKey
        uploadTask.confirmKeyReference = confirmKeyReference
        uploadTask.transferApiProxy = transferApiProxy
        uploadTask.progressListener = CosXmlProgressListener { progress, target ->
            onTransferProgressChange(progress, target)
        }
        multipartUploadTask = uploadTask
        return uploadTask.execute()
    }

    private suspend fun simpleUpload(): UploadFileResult {

        QCloudLogger.i(TAG, "[%s]: start upload with multipart upload", taskId)
        val uploadTask = SimpleUploadTask(
            transferApiProxy,
            uploadFileRequest,
            uploadLength,
            cts
        )
        uploadTask.setTaskId(taskId)
        uploadTask.smhKey = smhKey
        uploadTask.confirmKeyReference = confirmKeyReference
        uploadTask.transferApiProxy = transferApiProxy
        uploadTask.progressListener = CosXmlProgressListener { progress, target ->
            onTransferProgressChange(progress, target)
        }
        simpleUploadTask = uploadTask
        return uploadTask.execute()
    }

    private fun shouldMultipartUpload(): Boolean {
        return isMultipartUploadRequest && isMultipartUploadLength
    }

    private val isMultipartUploadLength: Boolean
        private get() = uploadLength >= multipartUploadThreshold

    // 通过上传数据源的类型来判断是否可以分片上传
    private val isMultipartUploadRequest: Boolean
        private get() = true

    /**
     * 简单上传
     */
    private class SimpleUploadTask(
        apiDirect: TransferApiProxy,
        private val uploadFileRequest: UploadFileRequest,
        totalUploadSize: Long,
        val cts: CancellationTokenSource
    ) {

        var smhKey: String? = null
        private var totalUploadSize: Long = -1
        var progressListener: CosXmlProgressListener? = null

        var confirmKeyReference: AtomicReference<String>? = null
        //        var initUpload: InitUpload? = null
//        var multiUploadMetadata: MultiUploadMetadata? = null
        val requestReference = AtomicReference<CosXmlRequest>()

        var transferApiProxy: TransferApiProxy
        private var taskId = ""


        fun setTaskId(taskId: String) {
            this.taskId = taskId
        }

        init {
            transferApiProxy = apiDirect
            this.totalUploadSize = totalUploadSize

        }

        suspend fun execute(): UploadFileResult {

            // 1. 查询是否已经上传过了，对于后台自动重命名的文件无法秒传
            checkoutManualCanceled()
            val fileInfo = getFileInfo(key = uploadFileRequest.key)
            val crc64 = getLocalCRC64()
            if (fileInfo != null && fileInfo.crc64 == crc64) {
                return UploadFileResult.fromFileInfo(uploadFileRequest.key, fileInfo)
            }

            // 2. 简单上传没有续传，直接初始化一个新的上传任务
            checkoutManualCanceled()
            QCloudLogger.i(TAG, "[%s]: simple upload, init upload first", taskId)
            val initUpload: InitUpload = initUpload()

            // 3. 上传剩余分片
            checkoutManualCanceled()
            QCloudLogger.i(TAG, "[$taskId]: start upload file ${uploadFileRequest.localUri} -> $smhKey")
            uploadFile(initUpload)

            // 4. 完成分片上传，并通过本地文件 crc64 给服务端校验
            checkoutManualCanceled()
            QCloudLogger.i(TAG, "[%s]: start complete upload file", taskId)
            return confirmUpload(initUpload.confirmKey, crc64)
        }

        // 检查任务是否已经被取消
        private fun checkoutManualCanceled() {
            if (cts.isCancellationRequested) {
                throw ClientManualCancelException
            }
        }


        fun cancel() {
            cts.cancel()
            requestReference.get()?.let {
                transferApiProxy.cancel(it)
            }
        }

        suspend fun abortUpload() {
            val confirmKey = confirmKeyReference?.get()
            confirmKey?.let {
                transferApiProxy.abortMultiUpload(it)
            }
        }


        private suspend fun initUpload(): InitUpload {
            return transferApiProxy.initUpload(
                smhKey = uploadFileRequest.key,
                meta = null,
                conflictStrategy = uploadFileRequest.conflictStrategy
            )
        }

        private suspend fun getFileInfo(key: String): FileInfo? {

            return try {
                transferApiProxy.getFileInfo(key)
            } catch (e: Exception) {
                null
            }
        }

        private suspend fun listUploadMetadata(confirmKey: String): MultiUploadMetadata {
            return transferApiProxy.listUploadMetadata(confirmKey)
        }

        // 上传文件
        private suspend fun uploadFile(uploader: InitUpload) {

            // 开始简单上传
            checkoutManualCanceled()
            val etag = transferApiProxy.uploadFile(
                initUpload = uploader,
                uri = uploadFileRequest.localUri,
                requestReference = requestReference
            ) { progress, target ->
                progressListener?.onProgress(
                    progress,
                    totalUploadSize
                )
            }
        }

        private suspend fun confirmUpload(confirmKey: String, crc64: String): UploadFileResult {
            val crc64Verify = getLocalCRC64()
            if (crc64Verify != crc64) {
                throw FileCRC64InConsistException
            }

            val confirmUpload = transferApiProxy.confirmUpload(confirmKey, crc64)
            return UploadFileResult.fromConfirmUpload(confirmUpload)
        }

        private fun getLocalCRC64(): String {
            val inputStream = openInputStream()
            val crc64 = DigestUtils.getCRC64(inputStream)
            inputStream.close()
            return crc64.toUnsignedLong()
        }

        // 只有分片上传才需要校验
        // 打开原始流或者加密流，用于 MD5 或者 CRC64 校验
        // 对于加密上传，必须要保证加密密钥以及内容加密密钥一致
        @Throws(IOException::class, SMHClientException::class)
        private fun openInputStream(): InputStream {
            val uri = uploadFileRequest.localUri
            return ContextHolder.getAppContext()?.contentResolver?.openInputStream(uri)?:
            throw ClientIOException("Open inputStream failed")

        }

    }

    private class MultipartUploadTask(
        apiDirect: TransferApiProxy,
        private val uploadFileRequest: UploadFileRequest,
        totalUploadSize: Long,
        val cts: CancellationTokenSource
    ) {
        var smhKey: String? = null
        private val uploadParts: TreeSet<UploadPart>

        private val poolNetworkPartSize = (1024 * 1024).toLong()
        private val normalNetworkPartSize = (1024 * 1024).toLong()
        private var totalUploadSize: Long = -1
        private var fileOffsetPointer: Long = 0
        private var partNumberPointer = 1
        var progressListener: CosXmlProgressListener? = null

        var confirmKeyReference: AtomicReference<String>? = null
//        var initUpload: InitUpload? = null
//        var multiUploadMetadata: MultiUploadMetadata? = null
        val requestReference = AtomicReference<CosXmlRequest>()

        var transferApiProxy: TransferApiProxy
        private var taskId = ""


        fun setTaskId(taskId: String) {
            this.taskId = taskId
        }

        init {
            transferApiProxy = apiDirect
            uploadParts = TreeSet(
                Comparator { o1, o2 ->
                    val x = o1.partNumber
                    val y = o2.partNumber
                    if ((x < y)) -1 else (if ((x == y)) 0 else 1)
                })
            this.totalUploadSize = totalUploadSize

        }

        suspend fun execute(): UploadFileResult {

            // 1. 查询是否已经上传过了，对于后台自动重命名的文件无法秒传
            checkoutManualCanceled()
            val fileInfo = getFileInfo(key = uploadFileRequest.key)
            val crc64 = getLocalCRC64()
            if (fileInfo != null && fileInfo.crc64 == crc64) {
                return UploadFileResult.fromFileInfo(uploadFileRequest.key, fileInfo)
            }

            // 2. 如果 confirmKey 为空，则生成一个新的 confirmKey
            //    否则通过 confirmKey 查询续传信息
            checkoutManualCanceled()
            var confirmKey = confirmKeyReference?.get()
            val initUpload: InitUpload
            var isConfirmed = false
            if (confirmKey == null) {
                QCloudLogger.i(TAG, "[%s]: confirmKey is null, init upload first", taskId)
                initUpload = initUpload()
                confirmKey = initUpload.confirmKey
                confirmKeyReference?.set(confirmKey)
            } else {
                QCloudLogger.i(TAG, "[%s]: confirmKey is $confirmKey, list upload first", taskId)
                val multiUploadMetadata = listUploadMetadata(confirmKey)
                initUpload = multiUploadMetadata.uploader
                // 当前 confirmKey 是否已经 confirm 过了
                val confirmed = multiUploadMetadata.confirmed
                if (confirmed != null && confirmed) {
                    isConfirmed = true
                } else {
                    restoreUploadMetadata(multiUploadMetadata)
                }
            }

            // 3. 上传剩余分片
            if (!isConfirmed) {
                checkoutManualCanceled()
                uploadFile(initUpload)
            }

            // 4. 完成分片上传，并通过本地文件 crc64 给服务端校验
            checkoutManualCanceled()
            return confirmUpload(confirmKey, crc64)
        }

        // 检查任务是否已经被取消
        private fun checkoutManualCanceled() {
            if (cts.isCancellationRequested) {
                throw ClientManualCancelException
            }
        }


        fun cancel() {
            cts.cancel()
            requestReference.get()?.let {
                transferApiProxy.cancel(it)
            }
        }

        suspend fun abortUpload() {
            val confirmKey = confirmKeyReference?.get()
            confirmKey?.let {
                transferApiProxy.abortMultiUpload(it)
            }
        }


        private suspend fun initUpload(): InitUpload {
            return transferApiProxy.initMultipartUpload(
                smhKey = uploadFileRequest.key,
                meta = null,
                conflictStrategy = uploadFileRequest.conflictStrategy
            )
        }

        private suspend fun getFileInfo(key: String): FileInfo? {

            return try {
                transferApiProxy.getFileInfo(key)
            } catch (e: Exception) {
                null
            }
        }

        private suspend fun listUploadMetadata(confirmKey: String): MultiUploadMetadata {
            return transferApiProxy.listUploadMetadata(confirmKey)
        }

        // 上传文件
        private suspend fun uploadFile(uploader: InitUpload) {

            // 开始依次上传分片
            while (fileOffsetPointer < totalUploadSize) {
                checkoutManualCanceled()
                val size = Math.min(normalNetworkPartSize, totalUploadSize - fileOffsetPointer)

                val etag = transferApiProxy.uploadFilePart(
                    initUpload = uploader,
                    partNumber = partNumberPointer,
                    uri = uploadFileRequest.localUri,
                    offset = fileOffsetPointer,
                    size = size,
                    requestReference = requestReference
                ) { progress, target ->
                    progressListener?.onProgress(
                        fileOffsetPointer + progress,
                        totalUploadSize
                    )
                }
                uploadParts.add(
                    UploadPart(
                        partNumber = partNumberPointer,
                        lastModified = "",
                        ETag = etag?: "",
                        size = size)
                )
                fileOffsetPointer += size
                partNumberPointer++
            }
        }

        private suspend fun confirmUpload(confirmKey: String, crc64: String): UploadFileResult {
            val crc64Verify = getLocalCRC64()
            if (crc64Verify != crc64) {
                throw FileCRC64InConsistException
            }
            val confirmUpload = transferApiProxy.confirmUpload(confirmKey, crc64)
            return UploadFileResult.fromConfirmUpload(confirmUpload)
        }

        private fun getLocalCRC64(): String {
            val inputStream = openInputStream()
            val crc64 = DigestUtils.getCRC64(inputStream)
            inputStream.close()
            return crc64.toUnsignedLong()
        }

        // 检查当前 UploadMetadata 是否有效
        // 1. 根据 uploadId 获取已经上传的分片
        // 2. 校验分片的有效性，尽可能的去复用分片
        // 3. 生成 uploadParts，用于后续上传
        @Throws(SMHClientException::class, SMHException::class)
        private fun restoreUploadMetadata(uploadMetadata: MultiUploadMetadata): Boolean {
            uploadParts.clear()
            val sortedParts = uploadMetadata.parts
            Collections.sort(sortedParts) { o1, o2 ->
                val x = o1.partNumber
                val y = o2.partNumber
                if ((x < y)) -1 else (if ((x == y)) 0 else 1)
            }
            partNumberPointer = 1
            fileOffsetPointer = 0
            val localInputStream = openInputStream()
            try {
                for (part: UploadPart in sortedParts) {
                    val partNumber = part.partNumber
                    val partSize = part.size

                    // 只复用连续分块
                    if (partNumberPointer != partNumber) {
                        break
                    }
                    // 校验远程和本地的 MD5 是否一致
                    val localMd5 = DigestUtils.getCOSMd5(localInputStream, 0, partSize)
                    if (localMd5 == null || localMd5 != part.ETag) {
                        break
                    }
                    uploadParts.add(part)
                    partNumberPointer++
                    fileOffsetPointer += partSize
                }
            } catch (exception: IOException) {
                QCloudLogger.w(
                    TAG,
                    "[%s]: check parts encounter exception: %s",
                    taskId,
                    exception.message
                )
                throw ClientInternalException(exception.message)
            } finally {
                localInputStream.close()
            }
            QCloudLogger.i(
                TAG,
                "[%s]: you have uploaded %d parts of it, upload offset: %s, partNumber: %d",
                taskId,
                uploadParts.size,
                fileOffsetPointer,
                partNumberPointer
            )
            return true
        }


        // 只有分片上传才需要校验
        // 打开原始流或者加密流，用于 MD5 或者 CRC64 校验
        // 对于加密上传，必须要保证加密密钥以及内容加密密钥一致
        @Throws(IOException::class, SMHClientException::class)
        private fun openInputStream(): InputStream {
            val uri = uploadFileRequest.localUri
            return ContextHolder.getAppContext()?.contentResolver?.openInputStream(uri)?:
                    throw ClientIOException("Open inputStream failed")

        }


    }

    companion object {
        val TAG = "SMHUpload"

        // 最多同时上传两个
        private val UPLOAD_CONCURRENT = 2
        private val uploadTaskExecutor = ThreadPoolExecutor(
            UPLOAD_CONCURRENT, UPLOAD_CONCURRENT, 5L,
            TimeUnit.SECONDS, LinkedBlockingQueue(Int.MAX_VALUE),
            TaskThreadFactory(TAG + "-", 8)
        )
    }
}