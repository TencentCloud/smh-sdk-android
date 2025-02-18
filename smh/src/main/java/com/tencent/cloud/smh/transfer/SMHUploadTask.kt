package com.tencent.cloud.smh.transfer

import android.content.Context
import android.util.Log
import bolts.CancellationTokenSource
import com.google.gson.Gson
import com.tencent.cloud.smh.ClientIOException
import com.tencent.cloud.smh.ClientInternalException
import com.tencent.cloud.smh.ClientManualCancelException
import com.tencent.cloud.smh.FileCRC64InConsistException
import com.tencent.cloud.smh.FileNotFoundException
import com.tencent.cloud.smh.InvalidArgumentException
import com.tencent.cloud.smh.SMHClientException
import com.tencent.cloud.smh.SMHCollection
import com.tencent.cloud.smh.SMHException
import com.tencent.cloud.smh.api.SMHService
import com.tencent.cloud.smh.api.model.InitMultipartUpload
import com.tencent.cloud.smh.api.model.InitUpload
import com.tencent.cloud.smh.api.model.MultiUploadMetadata
import com.tencent.cloud.smh.api.model.PartsHeaders
import com.tencent.cloud.smh.api.model.PublicMultiUploadMetadata
import com.tencent.cloud.smh.api.model.QuickUpload
import com.tencent.cloud.smh.api.model.UploadPart
import com.tencent.cloud.smh.ext.getLastModified
import com.tencent.cloud.smh.ext.getSize
import com.tencent.cloud.smh.ext.resumeIfActive
import com.tencent.cloud.smh.ext.resumeWithExceptionIfActive
import com.tencent.cloud.smh.ext.utc2normalTimeMillis
import com.tencent.cos.xml.exception.CosXmlClientException
import com.tencent.cos.xml.exception.CosXmlServiceException
import com.tencent.cos.xml.listener.CosXmlProgressListener
import com.tencent.cos.xml.listener.CosXmlResultListener
import com.tencent.cos.xml.model.CosXmlRequest
import com.tencent.cos.xml.model.CosXmlResult
import com.tencent.cos.xml.model.`object`.UploadPartResult
import com.tencent.cos.xml.utils.DigestUtils
import com.tencent.cos.xml.utils.StringUtils
import com.tencent.qcloud.core.http.HttpConfiguration
import com.tencent.qcloud.core.logger.QCloudLogger
import com.tencent.qcloud.core.util.ContextHolder
import com.tencent.qcloud.core.util.IOUtils
import com.tencent.qcloud.core.util.QCloudUtils
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import java.io.InputStream
import java.util.Collections
import java.util.LinkedList
import java.util.TreeSet
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * 支持上传数据源，优先级为 filePath > Uri
 *
 * 1. filePath：支持分片上传、客户端加密上传
 * 2. Uri：支持分片上传、客户端加密上传
 * 3. InputStream 支持分片上传、客户端加密上传
 * 流只能是本地流，需要能正确的拿到available
 * 如果流不支持reset则不支持秒传和不校验crc64、不支持暂停和取消（因为再次开始时流没有在最开始 也无法校验已上传的数据是否正确）
 * 注意：数据源流需要外部在上传成功或失败后自行关闭
 *
 * 3. byte 数组：暂不支持，不支持分片上传
 * 4. String 字符串：暂不支持，不支持分片上传
 *
 */
open class SMHUploadTask(
    context: Context,
    smhCollection: SMHCollection,
    private val uploadFileRequest: UploadFileRequest,
    private val quickUploadSwitch: Boolean = true
) : SMHTransferTask(context, smhCollection, uploadFileRequest) {

    // 是否禁止用户操作
    // 在最后完成分片的过程中，不允许用户手动点击暂停或者取消，
    // 否则可能产生服务端和客户端回调状态不一致的情况。
    @Volatile
    private var frozenManual = false
    private var publicMultipartUploadTask: PublicMultipartUploadTask? = null
    private var multipartUploadTask: MultipartUploadTask? = null
    private var simpleUploadTask: SimpleUploadTask? = null
    private var quickUploadTask: QuickUploadTask? = null
    private val multipartUploadThreshold = (2 * 1024 * 1024).toLong()
    private val confirmKeyReference: AtomicReference<String> = AtomicReference(
        uploadFileRequest.confirmKey
    )
    private var quickUploadResult: QuickUploadResult? = null
    private lateinit var executeConfirmKey: String

    /**
     * 上传文件、uri 时，会对其初始化
     */
    private var uploadLength: Long = -1

    /**
     * 计算的crc64值
     */
    private var mLocalCRC64: String? = null
    /**
     * 计算完crc64的时间
     */
    private var mLocalCRC64Time: Long? = null

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
        if(!requestStreamCanReuse()){
            QCloudLogger.i(TAG, "[%s]: cannot pause upload task in inputStream not support reset", taskId)
            return false
        }

        QCloudLogger.i(TAG, "[%s]: pause upload task", taskId)
        isManualPaused = true
        // 快速回调状态
        onTransferPaused()
        cts.cancel()
        multipartUploadTask?.cancel()
        publicMultipartUploadTask?.cancel()
        simpleUploadTask?.cancel()
        quickUploadTask?.cancel()
        return true
    }

    override suspend fun resume() {
        if (taskState !== SMHTransferState.PAUSED) {
            QCloudLogger.i(TAG, "[%s]: cannot resume upload task in state %s", taskId, taskState)
            return
        }
        QCloudLogger.i(TAG, "[%s]: resume upload task", taskId, taskState)
        super.resume()
        frozenManual = false
    }

    @Deprecated("This method is deprecated, use UploadFileRequest.initMultipleUploadListener")
    fun getConfirmKey(): String? {
        return confirmKeyReference.get()
    }

    suspend fun cancel(force: Boolean) {
        if (frozenManual && !force) {
            return
        }

        if(!requestStreamCanReuse()){
            QCloudLogger.i(TAG, "[%s]: cannot cancel upload task in inputStream not support reset", taskId)
            return
        }

        QCloudLogger.i(TAG, "[%s]: cancel upload task", taskId)
        isManualCanceled = true
        cts.cancel()
        multipartUploadTask?.cancel()
        multipartUploadTask?.abortUpload()
        publicMultipartUploadTask?.cancel()
        publicMultipartUploadTask?.abortUpload()
        simpleUploadTask?.cancel()
        simpleUploadTask?.abortUpload()
        quickUploadTask?.cancel()
    }

    @Throws(SMHClientException::class, IOException::class)
    override fun checking() {
        if (uploadFileRequest.localUri != null) {
            val uri = uploadFileRequest.localUri
            val context = ContextHolder.getAppContext()
            if (context != null) {
                val checkFileExist = QCloudUtils.doesUriFileExist(uri, context.contentResolver)
                if (!checkFileExist) {
                    throw FileNotFoundException
                }
                uploadLength = QCloudUtils.getUriContentLength(uri, context.contentResolver)
            }
        } else if (uploadFileRequest.inputStream != null) {
            closeInputStream()
            uploadLength = uploadFileRequest.inputStream.available().toLong()
        } else {
            throw InvalidArgumentException
        }
    }

    override suspend fun runBefore(){
        super.runBefore()
        if(quickUploadSwitch) {
            quickUploadResult = quickUpload()
        }
    }

    @Throws(SMHClientException::class, SMHException::class)
    override suspend fun execute() {
        quickUploadResult?.run {
            this.uploadFileResult?.run {
            } ?: run {
                executeConfirmKey = if (shouldMultipartUpload()) {
                    multipartUpload()
                } else {
                    simpleUpload(this.initUpload)
                }
            }
        } ?: run {
            executeConfirmKey = if (shouldMultipartUpload()) {
                multipartUpload()
            } else {
                simpleUpload()
            }
        }
    }

    override suspend fun runAfter(): SMHTransferResult {
        if (cts.isCancellationRequested) {
            throw ClientManualCancelException
        }

        return if(quickUploadResult != null && quickUploadResult!!.uploadFileResult != null){
            QCloudLogger.i(TAG, "[%s]: complete upload with quick", taskId)
            quickUploadResult!!.uploadFileResult!!
        } else {
            //已经计算好了mLocalCRC64且uri文件没有变化 则不再计算CRC64
            val localCRC64 = if(mLocalCRC64 != null && !uriHasChanged()){
                QCloudLogger.i(TAG, "crc64 already exists", taskId)
                mLocalCRC64
            } else {
                QCloudLogger.i(TAG, "crc64 non-existent recalculate", taskId)
                //如果流不能复用则服务端不校验crc64  crc64传空
                if (requestStreamCanReuse()) { getLocalCRC64() } else { null }
            }
            val confirmUpload = transferApiProxy.confirmUpload(executeConfirmKey, localCRC64)
            QCloudLogger.i(TAG, "[%s]: complete upload with default", taskId)
            UploadFileResult.fromConfirmUpload(confirmUpload)
        }
    }

    private suspend fun uriHasChanged(): Boolean{
        val size = uploadFileRequest.localUri?.getSize(context)
        val lastModified = uploadFileRequest.localUri?.getLastModified(context)
        if(size != uploadLength) return true
        if(lastModified == null) return true
        if(mLocalCRC64Time == null) return true
        if(mLocalCRC64Time!! < lastModified) return true
        return false
    }

    // 分块上传
    @Throws(SMHClientException::class, SMHException::class)
    private suspend fun multipartUpload(): String {
        QCloudLogger.i(TAG, "[%s]: start upload with multipart upload", taskId)
        if(SMHService.isPrivate) {
            val uploadTask = MultipartUploadTask(
                transferApiProxy,
                uploadFileRequest,
                uploadLength,
                cts
            )
            uploadTask.setTaskId(taskId)
            uploadTask.smhKey = smhKey
            uploadTask.confirmKeyReference = confirmKeyReference
            uploadTask.apiDirect = transferApiProxy
            uploadTask.progressListener = CosXmlProgressListener { progress, target ->
                onTransferProgressChange(progress, target)
            }
            multipartUploadTask = uploadTask

            //大于50M上传的时候并行计算crc64
            if(uploadLength > 50 * 1024 * 1024){
                thread {
                    //如果流不能复用则服务端不校验crc64  crc64传空
                    mLocalCRC64 = if (requestStreamCanReuse()) { getLocalCRC64() } else { null }
                    mLocalCRC64Time = System.currentTimeMillis()
                }
            }

            return uploadTask.execute()
        } else {
            val uploadTask = PublicMultipartUploadTask(
                transferApiProxy,
                uploadFileRequest,
                uploadLength,
                cts
            )
            uploadTask.setTaskId(taskId)
            uploadTask.smhKey = smhKey
            uploadTask.confirmKeyReference = confirmKeyReference
            uploadTask.apiDirect = transferApiProxy
            uploadTask.progressListener = CosXmlProgressListener { progress, target ->
                onTransferProgressChange(progress, target)
            }
            publicMultipartUploadTask = uploadTask

            //大于50M上传的时候并行计算crc64
            if(uploadLength > 50 * 1024 * 1024){
                thread {
                    //如果流不能复用则服务端不校验crc64  crc64传空
                    mLocalCRC64 = if (requestStreamCanReuse()) { getLocalCRC64() } else { null }
                    mLocalCRC64Time = System.currentTimeMillis()
                }
            }

            return uploadTask.execute()
        }
    }

    private suspend fun simpleUpload(initUpload: InitUpload? = null): String {
        QCloudLogger.i(TAG, "[%s]: start upload with simple upload", taskId)
        val uploadTask = SimpleUploadTask(
            transferApiProxy,
            uploadFileRequest,
            uploadLength,
            cts
        )
        uploadTask.setTaskId(taskId)
        uploadTask.smhKey = smhKey
        uploadTask.confirmKeyReference = confirmKeyReference
        uploadTask.initUpload = initUpload
        uploadTask.apiDirect = transferApiProxy
        uploadTask.progressListener = CosXmlProgressListener { progress, target ->
            onTransferProgressChange(progress, target)
        }
        simpleUploadTask = uploadTask
        return uploadTask.execute()
    }

    /**
     * 进行快速上传 返回null代表快速上传失败
     * 发生异常 代表快速上传失败
     */
    private suspend fun quickUpload(): QuickUploadResult? {
        try {
            QCloudLogger.i(TAG, "[%s]: start upload with quick upload", taskId)
            val uploadTask = QuickUploadTask(
                transferApiProxy,
                uploadFileRequest,
                uploadLength,
                cts
            )
            uploadTask.setTaskId(taskId)
            uploadTask.smhKey = smhKey
            uploadTask.confirmKeyReference = confirmKeyReference
            uploadTask.apiDirect = transferApiProxy
            uploadTask.progressListener = CosXmlProgressListener { progress, target ->
                onTransferProgressChange(progress, target)
            }
            quickUploadTask = uploadTask
            return uploadTask.execute()
        } catch (e: Exception) {
            e.printStackTrace()
            closeInputStream()
            return null
        }
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
     * 上传请求流是否可以复用
     */
    private fun requestStreamCanReuse(): Boolean =
        uploadFileRequest.localUri != null ||
                (uploadFileRequest.inputStream != null && uploadFileRequest.inputStream.markSupported())

    /**
     * 和openInputStream配合使用
     * 如果inputStream是localUri则close
     * 如果inputStream是数据源流则reset（数据源流需要外部在上传成功或失败后自行关闭）
     */
    private fun closeInputStream(inputStream: InputStream? = null) {
        if (uploadFileRequest.localUri != null && inputStream != null) {
            IOUtils.closeQuietly(inputStream)
        } else if (uploadFileRequest.inputStream != null && uploadFileRequest.inputStream.markSupported()) {
            uploadFileRequest.inputStream.reset()
        }
    }

    // 只有分片上传才需要校验
    // 打开原始流或者加密流，用于 MD5 或者 CRC64 校验
    // 对于加密上传，必须要保证加密密钥以及内容加密密钥一致
    @Throws(IOException::class, SMHClientException::class)
    private fun openInputStream(): InputStream {
        return when {
            uploadFileRequest.localUri != null -> {
                val uri = uploadFileRequest.localUri
                ContextHolder.getAppContext()?.contentResolver?.openInputStream(uri)
                    ?: throw ClientIOException("Open inputStream failed")
            }
            uploadFileRequest.inputStream != null -> {
                uploadFileRequest.inputStream
            }
            else -> {
                throw InvalidArgumentException
            }
        }
    }

    private fun getLocalCRC64(): String {
        val inputStream = openInputStream()
        val crc64 = DigestUtils.getCRC64(inputStream)
        closeInputStream(inputStream)
        return crc64.toUnsignedLong()
    }

    open inner class BaseUploadTask(
        var apiDirect: TransferApiProxy,
        val uploadFileRequest: UploadFileRequest,
        val totalUploadSize: Long,
        val cts: CancellationTokenSource
    ) {
        protected fun requestStreamCanReuse(): Boolean =
            this@SMHUploadTask.requestStreamCanReuse()

        // 检查任务是否已经被取消
        protected fun checkoutManualCanceled() {
            if (cts.isCancellationRequested) {
                throw ClientManualCancelException
            }
        }

        open fun cancel() {
            cts.cancel()
        }

        protected fun closeInputStream(inputStream: InputStream? = null) {
            this@SMHUploadTask.closeInputStream(inputStream)
        }
    }

    /**
     * 快速上传
     */
    private inner class QuickUploadTask(
        apiDirect: TransferApiProxy,
        uploadFileRequest: UploadFileRequest,
        totalUploadSize: Long,
        cts: CancellationTokenSource
    ) : BaseUploadTask(apiDirect, uploadFileRequest, totalUploadSize, cts) {
        /**
         * 定义文件开头的size
         */
        private val beginningSize = 1048576

        var smhKey: String? = null

        var progressListener: CosXmlProgressListener? = null
        var confirmKeyReference: AtomicReference<String>? = null

        private var taskId = ""

        fun setTaskId(taskId: String) {
            this.taskId = taskId
        }

        /**
         * 返回null说明快速上传失败
         */
        suspend fun execute(): QuickUploadResult? {
            if (totalUploadSize < beginningSize) {
                return null
            }

            if (!requestStreamCanReuse()) {
                return null
            }

            // 1. 头部hash校验
            checkoutManualCanceled()
            QCloudLogger.i(TAG, "[%s]: quick upload, beginning hash", taskId)
            val inputStream = openInputStream()
            val beginningByteArr = ByteArray(beginningSize)
            if (inputStream.read(beginningByteArr) == -1) {
                closeInputStream(inputStream)
                return null
            }
            val beginningHash =
                com.tencent.cloud.smh.utils.DigestUtils.getSHA256FromBytes(beginningByteArr)
            val beginningHashString = StringUtils.toHexString(beginningHash)
            val beginningQuickResult = apiDirect.quickUpload(
                uploadFileRequest.key,
                QuickUpload(totalUploadSize, beginningHashString),
                uploadFileRequest.conflictStrategy,
                uploadFileRequest.meta
            )
            QCloudLogger.i(TAG, "[%s]: quick upload, full hash", taskId)
            return if (beginningQuickResult.statusCode == 202) {
                // 2. 整体hash校验
                checkoutManualCanceled()
                var fullHash = beginningHash.clone()
                var readLen: Int
                var hashByteArray = ByteArray(fullHash.size + beginningSize)
                for (i in fullHash.indices) {
                    hashByteArray[i] = fullHash[i]
                }
                var hashLength: Long = beginningSize.toLong()
                while (inputStream.read(beginningByteArr).also { readLen = it } != -1) {
                    checkoutManualCanceled()
                    hashLength += readLen
//                    fullHash += beginningByteArr.slice(IntRange(0, readLen - 1))
                    if(readLen < beginningSize){
                        hashByteArray = ByteArray(readLen + fullHash.size)
                    }
                    for (i in fullHash.indices) {
                        hashByteArray[i] = fullHash[i]
                    }
                    for (i in beginningByteArr.slice(IntRange(0, readLen - 1)).indices) {
                        hashByteArray[fullHash.size+i] = beginningByteArr[i]
                    }
                    fullHash = com.tencent.cloud.smh.utils.DigestUtils.getSHA256FromBytes(hashByteArray)
                    progressListener?.onProgress(
                        hashLength,
                        totalUploadSize
                    )
                }
                val fullHashString = StringUtils.toHexString(fullHash)
                val fullQuickResult = apiDirect.quickUpload(
                    uploadFileRequest.key,
                    QuickUpload(totalUploadSize, beginningHashString, fullHashString),
                    uploadFileRequest.conflictStrategy,
                    uploadFileRequest.meta
                )
                if (fullQuickResult.statusCode == 200) {
                    closeInputStream(inputStream)
                    QuickUploadResult(
                        UploadFileResult.fromQuickUpload(fullQuickResult.rawString),
                        null
                    )
                } else {
                    closeInputStream(inputStream)
                    QuickUploadResult(
                        null,
                        Gson().fromJson(fullQuickResult.rawString, InitUpload::class.java)
                    )
                }
            } else {
                closeInputStream(inputStream)
                QuickUploadResult(
                    null,
                    Gson().fromJson(beginningQuickResult.rawString, InitUpload::class.java)
                )
            }
        }
    }

    data class QuickUploadResult(
        val uploadFileResult: UploadFileResult?,
        val initUpload: InitUpload?
    )

    /**
     * 简单上传
     */
    private inner class SimpleUploadTask(
        apiDirect: TransferApiProxy,
        uploadFileRequest: UploadFileRequest,
        totalUploadSize: Long,
        cts: CancellationTokenSource,
    ) : BaseUploadTask(apiDirect, uploadFileRequest, totalUploadSize, cts) {
        var smhKey: String? = null
        var progressListener: CosXmlProgressListener? = null

        var confirmKeyReference: AtomicReference<String>? = null

        val requestReference = AtomicReference<CosXmlRequest>()

        var initUpload: InitUpload? = null

        private var taskId = ""

        fun setTaskId(taskId: String) {
            this.taskId = taskId
        }

        suspend fun execute(): String {
            // 1. 简单上传没有续传，直接初始化一个新的上传任务
            checkoutManualCanceled()
            QCloudLogger.i(TAG, "[%s]: simple upload, init upload first", taskId)
            if (initUpload == null) {
                initUpload = initUpload()
            }

            // 2. 上传剩余
            checkoutManualCanceled()
            QCloudLogger.i(
                TAG,
                "[$taskId]: start upload file ${uploadFileRequest.localUri} -> $smhKey"
            )
            uploadFile(initUpload!!)

            // 3. 完成上传
            QCloudLogger.i(TAG, "[%s]: start complete upload file", taskId)
            return initUpload!!.confirmKey
        }

        public override fun cancel() {
            cts.cancel()
            requestReference.get()?.let {
                apiDirect.cancel(it)
            }
        }

        suspend fun abortUpload() {
            val confirmKey = confirmKeyReference?.get()
            confirmKey?.let {
                apiDirect.abortMultiUpload(it)
            }
        }


        private suspend fun initUpload(): InitUpload {
            return apiDirect.initUpload(
                smhKey = uploadFileRequest.key,
                meta = uploadFileRequest.meta,
                conflictStrategy = uploadFileRequest.conflictStrategy,
                filesize = totalUploadSize
            )
        }

        // 上传文件
        private suspend fun uploadFile(uploader: InitUpload) {
            // 开始简单上传
            checkoutManualCanceled()
            val etag = apiDirect.uploadFile(
                initUpload = uploader,
                uri = uploadFileRequest.localUri,
                inputStream = uploadFileRequest.inputStream,
                requestReference = requestReference
            ) { progress, target ->
                progressListener?.onProgress(
                    progress,
                    totalUploadSize
                )
            }
        }
    }

    /**
     * 公有云 分块上传 后续删掉
     */
    private inner class PublicMultipartUploadTask(
        apiDirect: TransferApiProxy,
        uploadFileRequest: UploadFileRequest,
        totalUploadSize: Long,
        cts: CancellationTokenSource
    ) : BaseUploadTask(apiDirect, uploadFileRequest, totalUploadSize, cts) {
        var smhKey: String? = null
        private val uploadParts: TreeSet<UploadPart> = TreeSet { o1, o2 ->
            val x = o1.partNumber
            val y = o2.partNumber
            if ((x < y)) -1 else (if ((x == y)) 0 else 1)
        }

        //        private val poolNetworkPartSize = (1024 * 1024).toLong()
        private val normalNetworkPartSize: Long

        private var fileOffsetPointer: Long = 0
        private var partNumberPointer = 1
        var progressListener: CosXmlProgressListener? = null

        var confirmKeyReference: AtomicReference<String>? = null

        //        var initUpload: InitUpload? = null
//        var multiUploadMetadata: MultiUploadMetadata? = null
        val requestReference = AtomicReference<CosXmlRequest>()
        //并发上传分片request
        private val uploadPartRequestList: LinkedList<CosXmlRequest> = LinkedList()

        private var taskId = ""

        /**
         * 首片上传的字节数组
         */
        private var firstUploadPartData: ByteArray? = null

        fun setTaskId(taskId: String) {
            this.taskId = taskId
        }

        init {
            //根据size计算分块大小
            val defaultPartSize = (1024 * 1024).toLong()
            val partCount = ceil(totalUploadSize / defaultPartSize.toDouble())
            normalNetworkPartSize = if(partCount > 10000){
                ceil(totalUploadSize / 10000.0).toLong()
            } else {
                defaultPartSize
            }
        }

        suspend fun execute(): String {
            // 1. 如果 confirmKey 为空，则生成一个新的 confirmKey
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
                uploadFileRequest.initMultipleUploadListener?.onSuccess(confirmKey)
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

            // 2. 上传剩余分片
            if (!isConfirmed) {
                checkoutManualCanceled()
                QCloudLogger.i(TAG, "[%s]: confirmKey is $confirmKey, start uploading remaining chunks", taskId)
                when {
                    uploadFileRequest.localUri != null -> {
                        uploadFileAsync(initUpload)
                    }
                    uploadFileRequest.inputStream != null -> {
                        uploadInputStream(initUpload)
                    }
                    else -> {
                        throw InvalidArgumentException
                    }
                }
            }

            // 3. 完成分片上传
            return confirmKey
        }

        public override fun cancel() {
            cts.cancel()
            requestReference.get()?.let {
                apiDirect.cancel(it)
            }

            synchronized(uploadPartRequestList) {
                uploadPartRequestList.forEach { apiDirect.cancel(it) }
                uploadPartRequestList.clear()
            }
        }

        suspend fun abortUpload() {
            val confirmKey = confirmKeyReference?.get()
            confirmKey?.let {
                apiDirect.abortMultiUpload(it)
            }
        }

        private suspend fun initUpload(): InitUpload {
            return apiDirect.publicInitMultipartUpload(
                smhKey = uploadFileRequest.key,
                meta = uploadFileRequest.meta,
                conflictStrategy = uploadFileRequest.conflictStrategy,
                filesize = totalUploadSize
            )
        }


        private suspend fun listUploadMetadata(confirmKey: String): PublicMultiUploadMetadata {
            return apiDirect.publicListUploadMetadata(confirmKey)
        }

        /**
         * 用于并发分片上传
         */
        private inner class NotUploadedPartStruct(
            var partNumber: Int = 0,
            var offset: Long = 0,
            var sliceSize: Long = 0
        )

        /**
         * 如果输入源是uri 则并行上传
         */
        private suspend fun uploadFileAsync(uploaderArg: InitUpload) {
            return suspendCancellableCoroutine { cont ->
                try {
                    var uploader = uploaderArg

                    checkoutManualCanceled()

                    val isFailed = AtomicBoolean(false)
                    val uploadPartCount = AtomicInteger(0)
                    val alreadySendDataLen = AtomicLong(fileOffsetPointer)
                    val syncUploadPart = Any()

                    // 分块
                    val notUploadedParts = arrayListOf<NotUploadedPartStruct>()
                    while (fileOffsetPointer < totalUploadSize) {
                        val size = Math.min(normalNetworkPartSize, totalUploadSize - fileOffsetPointer)
                        notUploadedParts.add(NotUploadedPartStruct(partNumberPointer, fileOffsetPointer, size))
                        uploadPartCount.addAndGet(1)
                        fileOffsetPointer += size
                        partNumberPointer++
                    }

                    QCloudLogger.i(TAG, "[%s]: notUploadedParts size is ${notUploadedParts.size}", taskId)

                    if(notUploadedParts.size == 0){
                        cont.resume(Unit)
                    }

                    // 并行上传 通过分块数组是否全部成功决定是否完成
                    notUploadedParts.forEach {
                        if (isFailed.get()) return@forEach
                        val cosXmlRequest = apiDirect.publicUploadFilePartAsync(
                            initUpload = uploader,
                            partNumber = it.partNumber,
                            uri = uploadFileRequest.localUri,
                            offset = it.offset,
                            size = it.sliceSize,
                            cosXmlProgressListener = object : CosXmlProgressListener{
                                var progress: Long = 0
                                override fun onProgress(complete: Long, target: Long) {
                                    if (isFailed.get()) return
                                    val dataLen = alreadySendDataLen.addAndGet(
                                        complete - progress
                                    )
                                    progress = complete
                                    progressListener?.onProgress(
                                        dataLen,
                                        totalUploadSize
                                    )
                                }
                            },
                            cosXmlResultListener = object : CosXmlResultListener{
                                override fun onSuccess(
                                    request: CosXmlRequest?,
                                    result: CosXmlResult?
                                ) {
                                    if (isFailed.get()) return
                                    val eTag = (result as UploadPartResult).eTag
                                    uploadParts.add(
                                        UploadPart(
                                            partNumber = partNumberPointer,
                                            lastModified = "",
                                            ETag = eTag ?: "",
                                            size = it.sliceSize
                                        )
                                    )
                                    //检查是否上传完成
                                    synchronized(syncUploadPart) {
                                        uploadPartCount.decrementAndGet()
                                        if (uploadPartCount.get() == 0) {
                                            //完成
                                            synchronized(uploadPartRequestList) {
                                                uploadPartRequestList.clear()
                                            }
                                            cont.resumeIfActive(Unit)
                                        }
                                    }
                                }

                                override fun onFail(
                                    request: CosXmlRequest?,
                                    clientException: CosXmlClientException?,
                                    serviceException: CosXmlServiceException?
                                ) {
                                    synchronized(syncUploadPart) {
                                        if (isFailed.get()) return  //已经失败了
                                        isFailed.set(true)
                                        cont.resumeWithExceptionIfActive(
                                            clientException ?: serviceException!!
                                        )
                                    }
                                }
                            },
                            renewMultipartUploadFunction = { force ->
                                try {
                                    renewMultipartUploadFunction(force, uploader)?.apply {
                                        uploader = this
                                    }
                                } catch (e: SMHException){
                                    synchronized(syncUploadPart) {
                                        cont.resumeWithExceptionIfActive(e)
                                    }
                                }
                                uploader
                            }
                        )
                        synchronized(uploadPartRequestList) {
                            uploadPartRequestList.add(cosXmlRequest)
                        }
                    }
                } catch (exception: Exception) {
                    QCloudLogger.w(
                        TAG,
                        "[%s]: upload parts encounter exception: %s",
                        taskId,
                        exception.message
                    )
                    cont.resumeWithExceptionIfActive(ClientInternalException(exception.message))
                }
            }
        }

        /**
         * 如果输入源是流则进行串行上传 后续可以优化为并行
         */
        private suspend fun uploadInputStream(uploaderArg: InitUpload) {
            try {
                var uploader = uploaderArg

                // 开始依次上传分片
                while (fileOffsetPointer < totalUploadSize) {
                    checkoutManualCanceled()
                    var size = Math.min(normalNetworkPartSize, totalUploadSize - fileOffsetPointer)

                    var byteArray: ByteArray? = null
                    firstUploadPartData?.apply {
                        size = this.size.toLong()
                        byteArray = this.clone()
                        firstUploadPartData = null
                    } ?: apply {
                        byteArray = ByteArray(size.toInt())
                        val inputStream = openInputStream()
                        inputStream.read(byteArray, 0, size.toInt())
                    }
                    val etag = apiDirect.publicUploadFilePart(
                        initUpload = uploader,
                        partNumber = partNumberPointer,
                        byteArray = byteArray,
                        requestReference = requestReference,
                        cosXmlProgressListener = { progress, target ->
                            progressListener?.onProgress(
                                fileOffsetPointer + progress,
                                totalUploadSize
                            )
                        },
                        renewMultipartUploadFunction = { force ->
                            renewMultipartUploadFunction(force, uploader)?.apply {
                                uploader = this
                            }
                            uploader
                        }
                    )

                    uploadParts.add(
                        UploadPart(
                            partNumber = partNumberPointer,
                            lastModified = "",
                            ETag = etag ?: "",
                            size = size
                        )
                    )
                    fileOffsetPointer += size
                    partNumberPointer++
                }
            } catch (exception: Exception) {
                QCloudLogger.w(
                    TAG,
                    "[%s]: upload parts encounter exception: %s",
                    taskId,
                    exception.message
                )
                throw ClientInternalException(exception.message)
            }
        }

        private fun renewMultipartUploadFunction(force: Boolean, uploader: InitUpload): InitUpload?{
            if(force) {
                return apiDirect.publicRenewMultipartUpload(uploader.confirmKey)
            } else {
                //检查过期 余量5分钟
                val isExpiration = uploader.expiration?.utc2normalTimeMillis()?.run {
                    (this/1000 - HttpConfiguration.getDeviceTimeWithOffset()) < 300
                }?:true
                if(isExpiration){
                    //续期
                    return apiDirect.publicRenewMultipartUpload(uploader.confirmKey)
                }
            }
            return null
        }

        // 检查当前 UploadMetadata 是否有效
        // 1. 根据 uploadId 获取已经上传的分片
        // 2. 校验分片的有效性，尽可能的去复用分片
        // 3. 生成 uploadParts，用于后续上传
        @Throws(SMHClientException::class, SMHException::class)
        private fun restoreUploadMetadata(uploadMetadata: PublicMultiUploadMetadata): Boolean {
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
                    val cosMd5AndReadData =
                        DigestUtils.getCOSMd5AndReadData(localInputStream, partSize.toInt())
                    if (cosMd5AndReadData.md5 == null || cosMd5AndReadData.md5 != part.ETag) {
                        //保存校验失败的第一片数据作为续传的第一片
                        firstUploadPartData = cosMd5AndReadData.readData
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
                //只有uri上传才关闭流，流数据上传不关闭，因为剩余分片上传需要继续用这个流对象
                if (uploadFileRequest.localUri != null) {
                    localInputStream.close()
                }
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
    }

    private inner class MultipartUploadTask(
        apiDirect: TransferApiProxy,
        uploadFileRequest: UploadFileRequest,
        totalUploadSize: Long,
        cts: CancellationTokenSource
    ) : BaseUploadTask(apiDirect, uploadFileRequest, totalUploadSize, cts) {
        var smhKey: String? = null
        private val uploadParts: TreeSet<UploadPart> = TreeSet { o1, o2 ->
            val x = o1.partNumber
            val y = o2.partNumber
            if ((x < y)) -1 else (if ((x == y)) 0 else 1)
        }

        //        private val poolNetworkPartSize = (1024 * 1024).toLong()
        private val normalNetworkPartSize: Long

        private var fileOffsetPointer: Long = 0
        private var partNumberPointer = 1
        var progressListener: CosXmlProgressListener? = null

        var confirmKeyReference: AtomicReference<String>? = null

        //        var initUpload: InitUpload? = null
//        var multiUploadMetadata: MultiUploadMetadata? = null
        val requestReference = AtomicReference<CosXmlRequest>()
        //并发上传分片request
        private val uploadPartRequestList: LinkedList<CosXmlRequest> = LinkedList()

        private var taskId = ""

        // 最大分块上传签名数量（最多100）
        private val maxPartNumberRangeCount = 100

        /**
         * 首片上传的字节数组
         */
        private var firstUploadPartData: ByteArray? = null

        fun setTaskId(taskId: String) {
            this.taskId = taskId
        }

        init {
            //根据size计算分块大小
            val defaultPartSize = (1024 * 1024).toLong()
            val partCount = ceil(totalUploadSize / defaultPartSize.toDouble())
            normalNetworkPartSize = if(partCount > 10000){
                ceil(totalUploadSize / 10000.0).toLong()
            } else {
                defaultPartSize
            }
        }

        suspend fun execute(): String {
            // 1. 如果 confirmKey 为空，则生成一个新的 confirmKey
            //    否则通过 confirmKey 查询续传信息
            checkoutManualCanceled()
            var confirmKey = confirmKeyReference?.get()
            var initUpload: InitMultipartUpload? = null
            var isConfirmed = false

            if (confirmKey == null) {
                QCloudLogger.i(TAG, "[%s]: confirmKey is null, init upload first", taskId)
                initUpload = initUpload(
                    buildPartNumberRange(totalUploadSize, fileOffsetPointer, normalNetworkPartSize, partNumberPointer)
                )
                confirmKey = initUpload.confirmKey
                confirmKeyReference?.set(confirmKey)
                uploadFileRequest.initMultipleUploadListener?.onSuccess(confirmKey)
            } else {
                QCloudLogger.i(TAG, "[%s]: confirmKey is $confirmKey, list upload first", taskId)
                val multiUploadMetadata = listUploadMetadata(confirmKey)
                // 当前 confirmKey 是否已经 confirm 过了
                val confirmed = multiUploadMetadata.confirmed
                if (confirmed != null && confirmed) {
                    isConfirmed = true
                } else {
                    restoreUploadMetadata(multiUploadMetadata)
                    initUpload = apiDirect.renewMultipartUpload(
                        confirmKey,
                        buildPartNumberRange(totalUploadSize, fileOffsetPointer, normalNetworkPartSize, partNumberPointer)
                    )
                }
            }

            // 2. 上传剩余分片
            if (!isConfirmed) {
                checkoutManualCanceled()
                QCloudLogger.i(TAG, "[%s]: confirmKey is $confirmKey, start uploading remaining chunks", taskId)
                when {
                    uploadFileRequest.localUri != null -> {
                        uploadFileAsync(initUpload!!)
                    }
                    uploadFileRequest.inputStream != null -> {
                        uploadInputStream(initUpload!!)
                    }
                    else -> {
                        throw InvalidArgumentException
                    }
                }
            }

            // 3. 完成分片上传
            return confirmKey
        }

        public override fun cancel() {
            cts.cancel()
            requestReference.get()?.let {
                apiDirect.cancel(it)
            }

            synchronized(uploadPartRequestList) {
                uploadPartRequestList.forEach { apiDirect.cancel(it) }
                uploadPartRequestList.clear()
            }
        }

        suspend fun abortUpload() {
            val confirmKey = confirmKeyReference?.get()
            confirmKey?.let {
                apiDirect.abortMultiUpload(it)
            }
        }

        private suspend fun initUpload(partNumberRange: String): InitMultipartUpload {
            return apiDirect.initMultipartUpload(
                smhKey = uploadFileRequest.key,
                partNumberRange = partNumberRange,
                meta = uploadFileRequest.meta,
                conflictStrategy = uploadFileRequest.conflictStrategy,
                filesize = totalUploadSize
            )
        }


        private suspend fun listUploadMetadata(confirmKey: String): MultiUploadMetadata {
            return apiDirect.listUploadMetadata(confirmKey)
        }

        // 上传文件
        private suspend fun uploadFile(uploader: InitUpload) {
//            try {
//                // 开始依次上传分片
//                while (fileOffsetPointer < totalUploadSize) {
//                    checkoutManualCanceled()
//                    var size = Math.min(normalNetworkPartSize, totalUploadSize - fileOffsetPointer)
//
//                    val etag = when {
//                        uploadFileRequest.localUri != null -> {
//                            apiDirect.uploadFilePart(
//                                initUpload = uploader,
//                                partNumber = partNumberPointer,
//                                uri = uploadFileRequest.localUri,
//                                offset = fileOffsetPointer,
//                                size = size,
//                                requestReference = requestReference
//                            ) { progress, target ->
//                                progressListener?.onProgress(
//                                    fileOffsetPointer + progress,
//                                    totalUploadSize
//                                )
//                            }
//                        }
//                        uploadFileRequest.inputStream != null -> {
//                            var byteArray: ByteArray? = null
//                            firstUploadPartData?.apply {
//                                size = this.size.toLong()
//                                byteArray = this.clone()
//                                firstUploadPartData = null
//                            } ?: apply {
//                                byteArray = ByteArray(size.toInt())
//                                val inputStream = openInputStream()
//                                inputStream.read(byteArray, 0, size.toInt())
//                            }
//
//                            apiDirect.uploadFilePart(
//                                initUpload = uploader,
//                                partNumber = partNumberPointer,
//                                byteArray = byteArray,
//                                requestReference = requestReference
//                            ) { progress, target ->
//                                progressListener?.onProgress(
//                                    fileOffsetPointer + progress,
//                                    totalUploadSize
//                                )
//                            }
//                        }
//                        else -> {
//                            throw InvalidArgumentException
//                        }
//                    }
//                    uploadParts.add(
//                        UploadPart(
//                            partNumber = partNumberPointer,
//                            lastModified = "",
//                            ETag = etag ?: "",
//                            size = size
//                        )
//                    )
//                    fileOffsetPointer += size
//                    partNumberPointer++
//                }
//            } catch (exception: IOException) {
//                QCloudLogger.w(
//                    TAG,
//                    "[%s]: upload parts encounter exception: %s",
//                    taskId,
//                    exception.message
//                )
//                throw ClientInternalException(exception.message)
//            }
        }

        /**
         * 用于并发分片上传
         */
        private inner class NotUploadedPartStruct(
            var partNumber: Int = 0,
            var offset: Long = 0,
            var sliceSize: Long = 0
        )

        /**
         * 如果输入源是uri 则并行上传
         */
        private suspend fun uploadFileAsync(uploaderArg: InitMultipartUpload) {
            return suspendCancellableCoroutine { cont ->
                try {
                    var uploader = uploaderArg

                    checkoutManualCanceled()

                    val isFailed = AtomicBoolean(false)
                    val uploadPartCount = AtomicInteger(0)
                    val alreadySendDataLen = AtomicLong(fileOffsetPointer)
                    val syncUploadPart = Any()

                    // 分块
                    val notUploadedParts = arrayListOf<NotUploadedPartStruct>()
                    while (fileOffsetPointer < totalUploadSize) {
                        val size = Math.min(normalNetworkPartSize, totalUploadSize - fileOffsetPointer)
                        notUploadedParts.add(NotUploadedPartStruct(partNumberPointer, fileOffsetPointer, size))
                        uploadPartCount.addAndGet(1)
                        fileOffsetPointer += size
                        partNumberPointer++
                    }

                    QCloudLogger.i(TAG, "[%s]: notUploadedParts size is ${notUploadedParts.size}", taskId)

                    if(notUploadedParts.size == 0){
                        cont.resume(Unit)
                    }

                    // 并行上传 通过分块数组是否全部成功决定是否完成
                    notUploadedParts.forEach {
                        if (isFailed.get()) return@forEach
                        val cosXmlRequest = apiDirect.uploadFilePartAsync(
                            initUpload = uploader,
                            partNumber = it.partNumber,
                            uri = uploadFileRequest.localUri,
                            offset = it.offset,
                            size = it.sliceSize,
                            cosXmlProgressListener = object : CosXmlProgressListener{
                                var progress: Long = 0
                                override fun onProgress(complete: Long, target: Long) {
                                    if (isFailed.get()) return
                                    val dataLen = alreadySendDataLen.addAndGet(
                                        complete - progress
                                    )
                                    progress = complete
                                    progressListener?.onProgress(
                                        dataLen,
                                        totalUploadSize
                                    )
                                }
                            },
                            cosXmlResultListener = object : CosXmlResultListener{
                                override fun onSuccess(
                                    request: CosXmlRequest?,
                                    result: CosXmlResult?
                                ) {
                                    if (isFailed.get()) return
                                    val eTag = (result as UploadPartResult).eTag
                                    uploadParts.add(
                                        UploadPart(
                                            partNumber = partNumberPointer,
                                            lastModified = "",
                                            ETag = eTag ?: "",
                                            size = it.sliceSize
                                        )
                                    )
                                    //检查是否上传完成
                                    synchronized(syncUploadPart) {
                                        uploadPartCount.decrementAndGet()
                                        if (uploadPartCount.get() == 0) {
                                            //完成
                                            synchronized(uploadPartRequestList) {
                                                uploadPartRequestList.clear()
                                            }
                                            cont.resumeIfActive(Unit)
                                        }
                                    }
                                }

                                override fun onFail(
                                    request: CosXmlRequest?,
                                    clientException: CosXmlClientException?,
                                    serviceException: CosXmlServiceException?
                                ) {
                                    synchronized(syncUploadPart) {
                                        if (isFailed.get()) return  //已经失败了
                                        isFailed.set(true)
                                        cont.resumeWithExceptionIfActive(
                                            clientException ?: serviceException!!
                                        )
                                    }
                                }
                            },
                            renewMultipartUploadFunction = { force, partNumber ->
                                try {
                                    //最后一个块的变化 用来计算range
                                    val lastPartNumber = partNumberPointer -1
                                    val partNumberRange =  "${partNumber}-${partNumber+ min(maxPartNumberRangeCount-1, lastPartNumber - partNumber)}"
                                    renewMultipartUploadFunction(
                                        force,
                                        uploader,
                                        partNumberRange
                                    )?.apply {
                                        //合并之前的parts和本次new的parts
                                        val partMap = mutableMapOf<String, PartsHeaders>()
                                        partMap.putAll(uploader.parts)
                                        partMap.putAll(this.parts)
                                        uploader = InitMultipartUpload(
                                            domain = this.domain,
                                            path = this.path,
                                            uploadId = this.uploadId,
                                            confirmKey = this.confirmKey,
                                            expiration = this.expiration,
                                            parts = partMap
                                        )
                                    }
                                } catch (e: SMHException){
                                    synchronized(syncUploadPart) {
                                        cont.resumeWithExceptionIfActive(e)
                                    }
                                }
                                uploader
                            }
                        )
                        synchronized(uploadPartRequestList) {
                            uploadPartRequestList.add(cosXmlRequest)
                        }
                    }
                } catch (exception: Exception) {
                    QCloudLogger.w(
                        TAG,
                        "[%s]: upload parts encounter exception: %s",
                        taskId,
                        exception.message
                    )
                    cont.resumeWithExceptionIfActive(ClientInternalException(exception.message))
                }
            }
        }

        /**
         * 如果输入源是流则进行串行上传 后续可以优化为并行
         */
        private suspend fun uploadInputStream(uploaderArg: InitMultipartUpload) {
            try {
                var uploader = uploaderArg

                // 开始依次上传分片
                while (fileOffsetPointer < totalUploadSize) {
                    checkoutManualCanceled()
                    var size = Math.min(normalNetworkPartSize, totalUploadSize - fileOffsetPointer)

                    var byteArray: ByteArray? = null
                    firstUploadPartData?.apply {
                        size = this.size.toLong()
                        byteArray = this.clone()
                        firstUploadPartData = null
                    } ?: apply {
                        byteArray = ByteArray(size.toInt())
                        val inputStream = openInputStream()
                        inputStream.read(byteArray, 0, size.toInt())
                    }
                    val etag = apiDirect.uploadFilePart(
                        initUpload = uploader,
                        partNumber = partNumberPointer,
                        byteArray = byteArray,
                        requestReference = requestReference,
                        cosXmlProgressListener = { progress, target ->
                            progressListener?.onProgress(
                                fileOffsetPointer + progress,
                                totalUploadSize
                            )
                        },
                        renewMultipartUploadFunction = { force, partNumber ->
                            renewMultipartUploadFunction(
                                force,
                                uploader,
                                buildPartNumberRange(totalUploadSize, fileOffsetPointer, normalNetworkPartSize, partNumber)
                            )?.apply {
                                //合并之前的parts和本次new的parts
                                val partMap = mutableMapOf<String, PartsHeaders>()
                                partMap.putAll(uploader.parts)
                                partMap.putAll(this.parts)
                                uploader = InitMultipartUpload(
                                    domain = this.domain,
                                    path = this.path,
                                    uploadId = this.uploadId,
                                    confirmKey = this.confirmKey,
                                    expiration = this.expiration,
                                    parts = partMap
                                )
                            }
                            uploader
                        }
                    )

                    uploadParts.add(
                        UploadPart(
                            partNumber = partNumberPointer,
                            lastModified = "",
                            ETag = etag ?: "",
                            size = size
                        )
                    )
                    fileOffsetPointer += size
                    partNumberPointer++
                }
            } catch (exception: Exception) {
                QCloudLogger.w(
                    TAG,
                    "[%s]: upload parts encounter exception: %s",
                    taskId,
                    exception.message
                )
                throw ClientInternalException(exception.message)
            }
        }

        private fun buildPartNumberRange(totalUploadSize: Long, fileOffsetPointer:Long, normalNetworkPartSize:Long, partNumberPointer:Int): String{
            //计算剩余块数
            val notUploadedPartCount: Int = ceil((totalUploadSize - fileOffsetPointer)/normalNetworkPartSize.toDouble()).toInt()
            //区间最多100个数字
            return "${partNumberPointer}-${partNumberPointer+ min(maxPartNumberRangeCount-1, notUploadedPartCount)}"
        }

        private fun renewMultipartUploadFunction(force: Boolean, uploader: InitMultipartUpload, partNumberRange: String): InitMultipartUpload?{
            if(force) {
                return apiDirect.renewMultipartUpload(uploader.confirmKey, partNumberRange)
            } else {
                //检查过期 余量5分钟
                val isExpiration = uploader.expiration?.utc2normalTimeMillis()?.run {
                    (this/1000 - HttpConfiguration.getDeviceTimeWithOffset()) < 300
                }?:true
                if(isExpiration){
                    //续期
                    return apiDirect.renewMultipartUpload(uploader.confirmKey, partNumberRange)
                }
            }
            return null
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
                    val cosMd5AndReadData =
                        DigestUtils.getCOSMd5AndReadData(localInputStream, partSize.toInt())
                    if (cosMd5AndReadData.md5 == null || cosMd5AndReadData.md5 != part.ETag) {
                        //保存校验失败的第一片数据作为续传的第一片
                        firstUploadPartData = cosMd5AndReadData.readData
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
                //只有uri上传才关闭流，流数据上传不关闭，因为剩余分片上传需要继续用这个流对象
                if (uploadFileRequest.localUri != null) {
                    localInputStream.close()
                }
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
    }

    companion object {
        val TAG = "SMHUpload"

//        // 最多同时上传两个
//        private val UPLOAD_CONCURRENT = 2
//        private val uploadTaskExecutor = ThreadPoolExecutor(
//            UPLOAD_CONCURRENT, UPLOAD_CONCURRENT, 5L,
//            TimeUnit.SECONDS, LinkedBlockingQueue(Int.MAX_VALUE),
//            TaskThreadFactory(TAG + "-", 8)
//        )
    }
}